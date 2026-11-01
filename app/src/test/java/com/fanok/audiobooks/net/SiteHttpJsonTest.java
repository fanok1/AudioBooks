package com.fanok.audiobooks.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.fanok.audiobooks.CookesExeption;
import com.google.gson.JsonElement;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A JSON API answer is a {@link JsonPage}; anything that is no JSON is an error, whatever its status or type. */
public class SiteHttpJsonTest {

    private static final String CRLF = "\r\n";
    /** path → {status line, content type, body} */
    private final Map<String, String[]> answers = new ConcurrentHashMap<>();
    private ServerSocket server;
    private String base;

    @Before
    public void start() throws IOException {
        answers.put("/books", new String[]{"200 OK", "application/json", "{\"items\":[{\"id\":1}]}"});
        answers.put("/as-html", new String[]{"200 OK", "text/html", "﻿  [1,2]"});
        answers.put("/antibot", new String[]{"200 OK", "text/html",
                "<html><head><title>Проверка</title></head><body>js</body></html>"});
        answers.put("/challenge", new String[]{"403 Forbidden", "text/html",
                "<html><head><title>Just a moment...</title></head></html>"});
        answers.put("/error", new String[]{"500 Internal Server Error", "application/json", "{\"error\":\"down\"}"});
        answers.put("/broken", new String[]{"200 OK", "application/json", "{\"items\":["});
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        base = "http://127.0.0.1:" + server.getLocalPort();
        new Thread(this::serve).start();
    }

    private void serve() {
        while (!server.isClosed()) {
            try (Socket client = server.accept()) {
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(client.getInputStream(), StandardCharsets.ISO_8859_1));
                String path = in.readLine().split(" ")[1];
                for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
                    // headers: not needed
                }
                String[] answer = answers.get(path);
                byte[] body = answer[2].getBytes(StandardCharsets.UTF_8);
                OutputStream out = client.getOutputStream();
                out.write(("HTTP/1.1 " + answer[0] + CRLF + "Content-Type: " + answer[1] + "; charset=UTF-8" + CRLF
                        + "Content-Length: " + body.length + CRLF + "Connection: close" + CRLF + CRLF)
                        .getBytes(StandardCharsets.ISO_8859_1));
                out.write(body);
                out.flush();
            } catch (IOException | RuntimeException e) {
                if (server.isClosed()) {
                    return;
                }
            }
        }
    }

    @After
    public void stop() throws IOException {
        server.close();
    }

    @Test
    public void aJsonAnswerIsAJsonPageAtItsUrl() throws IOException {
        JsonPage page = SiteHttp.getJson(base + "/books");
        JsonElement json = JsonPage.of(page);
        assertEquals(1, json.getAsJsonObject().getAsJsonArray("items").get(0).getAsJsonObject().get("id").getAsInt());
        assertEquals(base + "/books", page.location());
        assertTrue("no DOM text to mistake for content", page.text().isEmpty());
    }

    @Test
    public void theBodyDecidesNotTheContentType() throws IOException {
        assertEquals(2, JsonPage.of(SiteHttp.getJson(base + "/as-html")).getAsJsonArray().size());
    }

    @Test
    public void anAnswerThatIsNoJsonIsAnError() {
        IOException antibot = assertThrows(IOException.class, () -> SiteHttp.getJson(base + "/antibot"));
        assertTrue(antibot.getMessage().startsWith("not JSON"));
        assertThrows(CookesExeption.class, () -> SiteHttp.getJson(base + "/challenge"));
        assertEquals(500, assertThrows(HttpStatusException.class, () -> SiteHttp.getJson(base + "/error"))
                .getStatusCode());
        assertThrows(IOException.class, () -> SiteHttp.getJson(base + "/broken"));
    }

    @Test
    public void anHtmlPageIsNoJsonPage() {
        assertThrows(IllegalStateException.class, () -> JsonPage.of(Jsoup.parse("<p>x</p>", base)));
    }

    @Test
    public void jsonIsRecognisedByItsFirstCharacter() {
        assertTrue(SiteHttp.looksLikeJson(" \n{\"a\":1}"));
        assertTrue(SiteHttp.looksLikeJson("﻿[]"));
        assertFalse(SiteHttp.looksLikeJson("<!doctype html>"));
        assertFalse(SiteHttp.looksLikeJson(""));
    }
}
