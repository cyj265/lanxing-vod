package com.fongmi.android.tv.dlna;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.net.OkHttp;

import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * DLNA 投屏控制：直接向 AVTransport controlURL 发 SOAP 指令（SetAVTransportURI/Play/Seek）。
 */
public record DLNACast(CastVideo video, Runnable runnable) {

    private static final String AVT_NS = "urn:schemas-upnp-org:service:AVTransport:1";

    public void cast(Device item) {
        if (TextUtils.isEmpty(item.getUrl())) {
            App.post(() -> Notify.show(R.string.device_offline));
            return;
        }
        new Thread(() -> execute(item), "DLNACastThread").start();
    }

    private void execute(Device item) {
        try {
            String uri = video.url();
            soap(item.getUrl(), "SetAVTransportURI", "<InstanceID>0</InstanceID><CurrentURI>" + escape(uri) + "</CurrentURI><CurrentURIMetaData>" + escape(buildMetaData(uri)) + "</CurrentURIMetaData>");
            soap(item.getUrl(), "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>");
            if (video.position() > 0) soap(item.getUrl(), "Seek", "<InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>" + formatMs(video.position()) + "</Target>");
            App.post(runnable);
        } catch (Exception e) {
            String message = e.getMessage();
            App.post(() -> Notify.show(TextUtils.isEmpty(message) ? "cast failed" : message));
        }
    }

    private void soap(String controlUrl, String action, String args) throws Exception {
        String envelope = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>" +
                "<u:" + action + " xmlns:u=\"" + AVT_NS + "\">" + args + "</u:" + action + ">" +
                "</s:Body></s:Envelope>";
        Request request = new Request.Builder().url(controlUrl)
                .post(RequestBody.create(envelope, MediaType.parse("text/xml; charset=\"utf-8\"")))
                .header("SOAPACTION", "\"" + AVT_NS + "#" + action + "\"")
                .build();
        try (Response response = OkHttp.client(15000).newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful() || body.contains("UPnPError")) throw new Exception(action + " failed: " + response.code());
        }
    }

    private String buildMetaData(String url) {
        String description = video.headers().isEmpty() ? "" : "<dc:description>" + App.gson().toJson(video.headers()) + "</dc:description>";
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
                "<item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
                "<dc:title>" + escape(video.name()) + "</dc:title>" +
                "<upnp:class>object.item.videoItem</upnp:class>" + description +
                "<res protocolInfo=\"http-get:*:video/*:*\">" + escape(url) + "</res>" +
                "</item></DIDL-Lite>";
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String formatMs(long ms) {
        if (ms <= 0) return "00:00:00";
        long s = ms / 1000;
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}
