package com.fanok.audiobooks;

import static com.fanok.audiobooks.Consts.PROXY_HOST;
import static com.fanok.audiobooks.Consts.PROXY_PORT;

import androidx.annotation.NonNull;
import com.downloader.httpclient.HttpClient;

/*
 *    Copyright (C) 2017 MINDORKS NEXTGEN PRIVATE LIMITED
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

import com.downloader.Constants;
import com.downloader.request.DownloadRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Created by amitshekhar on 13/11/17.
 */

public class BazaKnigHttpClient implements HttpClient {

    private URLConnection connection;
    private String referer;

    public BazaKnigHttpClient() {

    }

    public BazaKnigHttpClient(final String referer) {
        this.referer = referer;
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(final String referer) {
        this.referer = referer;
    }

    @NonNull
    @Override
    public HttpClient clone() {
        return new BazaKnigHttpClient(referer);
    }

    @Override
    public void connect(DownloadRequest request) throws IOException {

        connection = new URL(request.getUrl()).openConnection();
        connection.setReadTimeout(request.getReadTimeout());
        connection.setConnectTimeout(request.getConnectTimeout());
        final String range = String.format(Locale.ENGLISH,
                "bytes=%d-", request.getDownloadedBytes());
        connection.addRequestProperty(Constants.RANGE, range);
        connection.addRequestProperty(Constants.USER_AGENT, request.getUserAgent());
        if(referer!=null&&!referer.isEmpty()){
            connection.addRequestProperty("referer", referer);
        }
        addHeaders(request);


        connection.connect();
    }

    @Override
    public int getResponseCode() throws IOException {
        int responseCode = 0;
        if (connection instanceof HttpURLConnection) {
            responseCode = ((HttpURLConnection) connection).getResponseCode();
        }
        return responseCode;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return connection.getInputStream();
    }

    @Override
    public long getContentLength() {
        String length = connection.getHeaderField("Content-Length");
        try {
            return Long.parseLong(length);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public String getResponseHeader(String name) {
        return connection.getHeaderField(name);
    }

    @Override
    public void close() {
        // no operation
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        return connection.getHeaderFields();
    }

    @Override
    public InputStream getErrorStream() {
        if (connection instanceof HttpURLConnection) {
            return ((HttpURLConnection) connection).getErrorStream();
        }
        return null;
    }

    private void addHeaders(DownloadRequest request) {
        final HashMap<String, List<String>> headers = request.getHeaders();
        if (headers != null) {
            Set<Map.Entry<String, List<String>>> entries = headers.entrySet();
            for (Map.Entry<String, List<String>> entry : entries) {
                String name = entry.getKey();
                List<String> list = entry.getValue();
                if (list != null) {
                    for (String value : list) {
                        connection.addRequestProperty(name, value);
                    }
                }
            }
        }
    }

}

