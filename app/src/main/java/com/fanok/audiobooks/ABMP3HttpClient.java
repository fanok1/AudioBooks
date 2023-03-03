package com.fanok.audiobooks;

import com.downloader.Constants;
import com.downloader.httpclient.HttpClient;
import com.downloader.request.DownloadRequest;
import com.fanok.audiobooks.service.DownloadABMP3;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ABMP3HttpClient implements HttpClient {

    private final long bytes;

    private URLConnection connection;

    public ABMP3HttpClient(final long bytes) {
        this.bytes = bytes;
    }

    @Override
    public HttpClient clone() {
        return new ABMP3HttpClient(bytes);
    }

    @Override
    public void close() {
        // no operation
    }

    @Override
    public void connect(DownloadRequest request) throws IOException {
        connection = new URL(request.getUrl()).openConnection();
        connection.setReadTimeout(request.getReadTimeout());
        connection.setConnectTimeout(request.getConnectTimeout());
        connection.addRequestProperty(Constants.RANGE,
                "bytes=" + bytes + "-" + bytes + (DownloadABMP3.fragmentSize - 1));
        connection.addRequestProperty(Constants.USER_AGENT, request.getUserAgent());
        connection.addRequestProperty("referer", Url.SERVER_ABMP3 + "/");
        addHeaders(request);
        connection.connect();
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
    public InputStream getErrorStream() {
        if (connection instanceof HttpURLConnection) {
            return ((HttpURLConnection) connection).getErrorStream();
        }
        return null;
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        return connection.getHeaderFields();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return connection.getInputStream();
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
    public String getResponseHeader(String name) {
        return connection.getHeaderField(name);
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
