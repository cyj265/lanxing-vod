package com.fongmi.android.tv.dlna;

import android.content.Context;

import com.fongmi.android.tv.bean.Device;
import com.github.catvod.net.OkHttp;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import okhttp3.Request;
import okhttp3.Response;

/**
 * 广播兜底发现：当 ColorOS/一加等 ROM 在内核层硬拦「应用组播发送」(sendto EPERM，NEARBY 权限给了也拦)时，
 * jupnp 的 M-SEARCH 永远发不出去、主动发现失效。本类完全绕开组播，改用「广播」SSDP M-SEARCH：
 * 发到 255.255.255.255:1900 与 WiFi 子网广播地址(如 192.168.1.255:1900)。DLNA 渲染端一般把 UDP 1900
 * 绑在广播上，会回单播响应；收到 LOCATION 后再拉设备描述 XML 解析出 AVTransport 控制地址，
 * 喂给现有 CastDialog 列表与控制链(DLNACast 只吃 Device.url)。
 *
 * 这是针对「组播发送被 ROM 硬拦」的唯一应用层绕行——广播包与组播包在内核是不同路径，多数拦截只针对组播。
 */
public final class DlnaBroadcastDiscovery {

    private static final String MSEARCH = "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 3\r\n" +
            "ST: ssdp:all\r\n" +
            "\r\n";

    public interface Callback {
        void onDevice(Device device);

        void onLog(String msg);
    }

    public static void run(Context context, Callback cb) {
        new Thread(() -> discover(context, cb), "dlna-bcast").start();
    }

    private static void discover(Context ctx, Callback cb) {
        DatagramSocket s = null;
        try {
            s = new DatagramSocket(0);
            s.setReuseAddress(true);
            s.setSoTimeout(1500);
            s.setBroadcast(true);
            if (DlnaNet.ready()) {
                try {
                    DlnaNet.wifiNetwork.bindSocket(s);
                    cb.onLog("bcast: Network.bindSocket(wifi) OK");
                } catch (Throwable t) {
                    cb.onLog("bcast: Network.bindSocket(wifi) FAILED: " + t.getMessage());
                }
            } else {
                cb.onLog("bcast: DlnaNet not ready, send on default network");
            }

            byte[] data = MSEARCH.getBytes(StandardCharsets.UTF_8);

            // 1) 受限广播 255.255.255.255:1900
            try {
                s.send(new DatagramPacket(data, data.length, InetAddress.getByName("255.255.255.255"), 1900));
                cb.onLog("bcast: sent to 255.255.255.255:1900 OK");
            } catch (Throwable t) {
                cb.onLog("bcast: send 255.255.255.255 FAILED (" + t.getMessage() + ") -> try subnet bcast");
            }

            // 2) WiFi 子网定向广播(192.168.x.255:1900)，比受限广播更不容易被 ROM 过滤
            InetAddress sub = subnetBroadcast();
            if (sub != null) {
                try {
                    s.send(new DatagramPacket(data, data.length, sub, 1900));
                    cb.onLog("bcast: sent to " + sub.getHostAddress() + ":1900 OK");
                } catch (Throwable t) {
                    cb.onLog("bcast: send " + sub.getHostAddress() + " FAILED: " + t.getMessage());
                }
            } else {
                cb.onLog("bcast: no subnet broadcast addr (255.255.255.255 only)");
            }

            Set<String> seen = new HashSet<>();
            byte[] buf = new byte[8192];
            long t0 = System.currentTimeMillis();
            int responses = 0;
            // 收到超时不中断，M-SEARCH 响应会分布在数秒内(MX:3)，直到总时限 5s
            while (System.currentTimeMillis() - t0 < 5000) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    s.receive(p);
                    responses++;
                    String resp = new String(buf, 0, p.getLength(), StandardCharsets.UTF_8);
                    String location = header(resp, "LOCATION");
                    cb.onLog("bcast: GOT RESPONSE #" + responses + " from " + p.getAddress() + " LOCATION=" + location);
                    if (location == null || !seen.add(location)) continue;
                    Device d = fetchDevice(location, cb);
                    if (d != null) cb.onDevice(d);
                } catch (SocketTimeoutException ignore) {
                    // 继续等，直到 5s 总时限
                } catch (Throwable t) {
                    cb.onLog("bcast: receive exc: " + t.getMessage());
                }
            }
            cb.onLog("bcast VERDICT: responses=" + responses +
                    (responses > 0 ? " -> broadcast discovery WORKS (bypassed ROM multicast block)"
                            : " -> no reply via broadcast either (ROM may block broadcast too, or no renderer on net)"));
        } catch (Throwable t) {
            cb.onLog("bcast FAILED: " + t.getMessage());
        } finally {
            if (s != null) try {
                s.close();
            } catch (Throwable ignore) {
            }
        }
    }

    private static InetAddress subnetBroadcast() {
        try {
            NetworkInterface ni = DlnaNet.wifiInterface;
            if (ni == null) return null;
            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                InetAddress b = ia.getBroadcast();
                if (b != null && b instanceof Inet4Address) return b;
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    private static String header(String resp, String key) {
        for (String line : resp.split("\r\n")) {
            int idx = line.indexOf(':');
            if (idx > 0 && line.substring(0, idx).trim().equalsIgnoreCase(key)) {
                return line.substring(idx + 1).trim();
            }
        }
        return null;
    }

    private static Device fetchDevice(String location, Callback cb) {
        try {
            Request request = new Request.Builder().url(location).get().build();
            try (Response r = OkHttp.client(8000).newCall(request).execute()) {
                if (r.body() == null) return null;
                return parse(r.body().string(), location, cb);
            }
        } catch (Throwable t) {
            cb.onLog("bcast: fetch " + location + " FAILED: " + t.getMessage());
            return null;
        }
    }

    private static Device parse(String xml, String location, Callback cb) {
        try {
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            doc.getDocumentElement().normalize();
            // getElementsByTagName 返回所有后代 <device>，可覆盖 deviceList 嵌套结构
            NodeList devices = doc.getElementsByTagName("device");
            for (int i = 0; i < devices.getLength(); i++) {
                Element dev = (Element) devices.item(i);
                Element svc = findService(dev, "AVTransport");
                if (svc == null) continue;
                String name = text(dev, "friendlyName");
                String udn = text(dev, "UDN");
                String control = text(svc, "controlURL");
                if (name == null || udn == null || control == null) continue;
                if (udn.startsWith("uuid:")) udn = udn.substring(5);
                String ctrlUrl = resolve(location, control);
                Device d = new Device();
                d.setUuid(udn);
                d.setName(name);
                d.setType(2);
                d.setUrl(ctrlUrl);
                d.setIp(hostOf(ctrlUrl));
                cb.onLog("bcast: device parsed name=" + name + " avt=" + ctrlUrl);
                return d;
            }
            cb.onLog("bcast: no MediaRenderer(AVTransport) in " + location);
            return null;
        } catch (Throwable t) {
            cb.onLog("bcast: parse " + location + " FAILED: " + t.getMessage());
            return null;
        }
    }

    private static Element findService(Element dev, String contains) {
        NodeList services = dev.getElementsByTagName("service");
        for (int i = 0; i < services.getLength(); i++) {
            Element s = (Element) services.item(i);
            String st = text(s, "serviceType");
            if (st != null && st.contains(contains)) return s;
        }
        return null;
    }

    private static String text(Element e, String tag) {
        NodeList nl = e.getElementsByTagName(tag);
        if (nl.getLength() > 0 && nl.item(0).getTextContent() != null) return nl.item(0).getTextContent().trim();
        return null;
    }

    private static String resolve(String base, String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        try {
            return new java.net.URI(base).resolve(url).toString();
        } catch (Throwable t) {
            return url;
        }
    }

    private static String hostOf(String url) {
        try {
            java.net.URI u = new java.net.URI(url);
            return u.getPort() > 0 ? u.getHost() + ":" + u.getPort() : u.getHost();
        } catch (Throwable t) {
            return "";
        }
    }
}
