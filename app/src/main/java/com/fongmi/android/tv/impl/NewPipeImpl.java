package com.fongmi.android.tv.impl;

import androidx.annotation.NonNull;

import com.github.catvod.net.OkHttp;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;

public final class NewPipeImpl extends Downloader {

    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0";

    private static class Loader {
        static volatile NewPipeImpl INSTANCE = new NewPipeImpl();
    }

    public static NewPipeImpl get() {
        return Loader.INSTANCE;
    }

    @Override
    public Response execute(@NonNull Request request) throws IOException, ReCaptchaException {
        String httpMethod = request.httpMethod();
        String url = request.url();
        Map<String, List<String>> headers = request.headers();
        byte[] dataToSend = request.dataToSend();

        RequestBody requestBody = null;
        if (dataToSend != null) {
            requestBody = RequestBody.create(null, dataToSend);
        }

        okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder().method(httpMethod, requestBody).url(url).addHeader("User-Agent", USER_AGENT);

        for (Map.Entry<String, List<String>> pair : headers.entrySet()) {
            String headerName = pair.getKey();
            List<String> headerValueList = pair.getValue();
            if (headerValueList.size() > 1) {
                requestBuilder.removeHeader(headerName);
                for (String headerValue : headerValueList) {
                    requestBuilder.addHeader(headerName, headerValue);
                }
            } else if (headerValueList.size() == 1) {
                requestBuilder.header(headerName, headerValueList.get(0));
            }
        }

        okhttp3.Response response = OkHttp.client().newCall(requestBuilder.build()).execute();

        if (response.code() == 429) {
            response.close();
            throw new ReCaptchaException("reCaptcha Challenge requested", url);
        }

        ResponseBody body = response.body();
        String responseBodyToReturn = body.string();
        String latestUrl = response.request().url().toString();
        return new Response(response.code(), response.message(), response.headers().toMultimap(), responseBodyToReturn, latestUrl);
    }
}
