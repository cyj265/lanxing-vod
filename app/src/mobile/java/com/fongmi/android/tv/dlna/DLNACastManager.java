package com.fongmi.android.tv.dlna;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Device;
import com.github.catvod.net.OkHttp;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 自研轻量 DLNA 发送端栈（mobile 专用）：SSDP M-SEARCH 发现 + 设备描述 XML 解析。
 * 不依赖 jupnp，规避其网络栈在部分机型上收不到搜索响应的问题（leanback 接收端仍用 jupnp）。
 */
public class DLNACastManager {

    private static final String TAG = "DLNACast";
    private static final String SSDP_ADDRESS = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final String AVT_NS = "urn:schemas-upnp-org:service:AVTransport";

    private static final String[] SEARCH_TARGETS = {
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            "urn:schemas-upnp-org:device:MediaRenderer:2",
            "ssdp:all"
    };

    private final List<Device> registered;
    private DeviceListener deviceListener;

    public static DLNACastManager get() {
        return Loader.INSTANCE;
    }

    DLNACastManager() {
        registered = Collections.synchronizedList(new ArrayList<>());
    }

    public interface DeviceListener {

        void onDeviceAdded(Device device);

        void onDeviceRemoved(Device device);
    }

    private static class Loader {
        static final DLNACastManager INSTANCE = new DLNACastManager();
    }

    public void init(Context context) {
    }

    public void setDeviceListener(DeviceListener listener) {
        deviceListener = listener;
    }

    public void release(Context context) {
        deviceListener = null;
    }

    public void search() {
        new Thread(this::runSearch, "DLNASearch").start();
    }

    public List<Device> getRegistered() {
        synchronized (registered) {
            List<Device> snapshot = new ArrayList<>(registered);
            Collections.sort(snapshot);
            return snapshot;
        }
    }

    private void runSearch() {
        Map<String, String> found = new LinkedHashMap<>();
        for (InetAddress address : getLocalAddresses()) {
            try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(address, 0))) {
                socket.setSoTimeout(300);
                byte[] buffer = new byte[2048];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                InetAddress group = InetAddress.getByName(SSDP_ADDRESS);
                long deadline = System.currentTimeMillis() + 4500;
                for (String target : SEARCH_TARGETS) socket.send(createSearch(address, group, target));
                while (System.currentTimeMillis() < deadline) {
                    try {
                        socket.receive(packet);
                        parseResponse(new String(packet.getData(), 0, packet.getLength()), found);
                    } catch (SocketTimeoutException e) {
                        packet.setLength(buffer.length);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "search on " + address + " failed: " + e.getMessage());
            }
        }
        for (Map.Entry<String, String> entry : found.entrySet()) fetchDescription(entry.getKey(), entry.getValue());
        Log.d(TAG, "search done, devices=" + registered.size());
    }

    private DatagramPacket createSearch(InetAddress source, InetAddress group, String target) {
        String message = "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: " + SSDP_ADDRESS + ":" + SSDP_PORT + "\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 3\r\n" +
                "ST: " + target + "\r\n" +
                "USER-AGENT: Android DLNACast/1.0\r\n\r\n";
        byte[] data = message.getBytes();
        return new DatagramPacket(data, data.length, group, SSDP_PORT);
    }

    private List<InetAddress> getLocalAddresses() {
        List<InetAddress> addresses = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                String name = ni.getName() == null ? "" : ni.getName();
                if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("clat")) continue;
                for (InetAddress address : Collections.list(ni.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) addresses.add(address);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "list interfaces failed: " + e.getMessage());
        }
        return addresses;
    }

    private void parseResponse(String response, Map<String, String> found) {
        String usn = null;
        String location = null;
        for (String line : response.split("\r?\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String upper = trimmed.toUpperCase();
            if (usn == null && upper.startsWith("USN:")) usn = trimmed.substring(4).trim();
            if (location == null && upper.startsWith("LOCATION:")) location = trimmed.substring(9).trim();
        }
        if (!TextUtils.isEmpty(usn) && !TextUtils.isEmpty(location) && !found.containsKey(usn)) found.put(usn, location);
    }

    private void fetchDescription(String usn, String location) {
        try {
            OkHttpClient client = OkHttp.client(5000);
            Request request = new Request.Builder().url(location).header("USER-AGENT", "Android DLNACast/1.0").build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    Device device = parseDescription(response.body().string(), usn, location);
                    if (device != null) addFound(device);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "fetch description failed: " + location + ", " + e.getMessage());
        }
    }

    private Device parseDescription(String xml, String usn, String location) throws Exception {
        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(new StringReader(xml));
        String friendlyName = null;
        String udn = null;
        String controlUrl = null;
        boolean avt = false;
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                switch (parser.getName()) {
                    case "friendlyName":
                        if (friendlyName == null) friendlyName = readText(parser);
                        break;
                    case "UDN":
                        if (udn == null) udn = readText(parser);
                        break;
                    case "serviceType":
                        avt = readText(parser).startsWith(AVT_NS);
                        break;
                    case "controlURL":
                        String url = readText(parser);
                        if (avt && TextUtils.isEmpty(controlUrl)) controlUrl = url;
                        break;
                }
            }
            event = parser.next();
        }
        if (TextUtils.isEmpty(controlUrl)) return null;
        if (TextUtils.isEmpty(friendlyName)) friendlyName = host(location);
        if (TextUtils.isEmpty(udn)) udn = "uuid:" + usn;
        Device device = new Device();
        device.setUuid(udn.replaceFirst("^uuid:", "").trim());
        device.setName(friendlyName);
        device.setType(2);
        device.setUrl(new URL(new URL(location), controlUrl.trim()).toString());
        device.setIp(host(location));
        Log.d(TAG, "device found: " + friendlyName + ", control=" + device.getUrl());
        return device;
    }

    private String readText(XmlPullParser parser) throws Exception {
        StringBuilder text = new StringBuilder();
        int event = parser.next();
        while (event != XmlPullParser.END_TAG) {
            if (event == XmlPullParser.TEXT) text.append(parser.getText());
            event = parser.next();
        }
        return text.toString().trim();
    }

    private String host(String url) {
        try {
            URL parsed = new URL(url);
            return parsed.getPort() > 0 ? parsed.getHost() + ":" + parsed.getPort() : parsed.getHost();
        } catch (Exception e) {
            return "";
        }
    }

    private void addFound(Device device) {
        boolean added = false;
        synchronized (registered) {
            int index = registered.indexOf(device);
            if (index < 0) {
                registered.add(device);
                added = true;
            } else {
                registered.get(index).setUrl(device.getUrl());
            }
        }
        if (added && deviceListener != null) App.post(() -> {
            if (deviceListener != null) deviceListener.onDeviceAdded(device);
        });
    }
}
