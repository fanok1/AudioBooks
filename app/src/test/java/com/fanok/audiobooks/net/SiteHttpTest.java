package com.fanok.audiobooks.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.junit.Test;

public class SiteHttpTest {

    @Test
    public void challengeIsRecognisedByHeaderOrTitle() {
        assertTrue(SiteHttp.isChallenge("challenge", "Книга"));
        assertTrue(SiteHttp.isChallenge(null, "Just a moment..."));
        assertTrue(SiteHttp.isChallenge(null, "DDoS-Guard"));
        assertTrue(SiteHttp.isChallenge(null, "Attention Required! | Cloudflare"));
        assertTrue(SiteHttp.isChallenge(null, "Security Verification"));
    }

    @Test
    public void ordinaryPagesAreNotChallenges() {
        assertFalse(SiteHttp.isChallenge(null, "Аудиокниги слушать онлайн"));
        assertFalse(SiteHttp.isChallenge(null, null));
        assertFalse(SiteHttp.isChallenge(null, ""));
    }

    @Test
    public void postedPagesFollowRedirectsWithTheSamePostAndForm() throws IOException {
        try (PostOnlySite site = new PostOnlySite()) {
            Connection connection = SiteHttp.connect(site.base + "/old/page/1/").data("xsort", "1");
            Connection.Response response = SiteHttp.executePosted(connection, site.base);
            assertEquals(200, response.statusCode());
            assertEquals(site.base + "/new/", response.url().toString());
            assertEquals("Книги", SiteHttp.parse(response).title());
            // page 1 is dropped before the request; the redirect is re-POSTed, never downgraded to GET
            assertEquals(Arrays.asList("POST /old/ xsort=1", "POST /new/ xsort=1"), site.log);
        }
    }

    @Test
    public void postedPagesNeverFollowARedirectOffTheSite() throws IOException {
        try (PostOnlySite site = new PostOnlySite()) {
            HttpStatusException e = assertThrows(HttpStatusException.class,
                    () -> SiteHttp.executePosted(SiteHttp.connect(site.base + "/away/"), site.base));
            assertEquals(302, e.getStatusCode());
            assertEquals(1, site.log.size());
        }
    }

    @Test
    public void postedPagesStopAfterFiveRedirectsAndWithoutALocation() throws IOException {
        try (PostOnlySite site = new PostOnlySite()) {
            HttpStatusException loop = assertThrows(HttpStatusException.class,
                    () -> SiteHttp.executePosted(SiteHttp.connect(site.base + "/loop/0/"), site.base));
            assertEquals(302, loop.getStatusCode());
            assertEquals(SiteHttp.MAX_REDIRECTS + 1, site.log.size());
            assertThrows(HttpStatusException.class,
                    () -> SiteHttp.executePosted(SiteHttp.connect(site.base + "/nowhere/"), site.base));
        }
    }

    @Test
    public void aQueryOnlyRedirectKeepsThePath() throws IOException {
        URL base = new URL("https://audioboo.org/index.php?old=1");
        assertEquals("https://audioboo.org/index.php?new=2", SiteHttp.redirectTarget(base, "?new=2").toString());
        assertEquals("https://audioboo.org/", SiteHttp.redirectTarget(base, "/").toString());
        assertEquals("https://audioboo.org/x/", SiteHttp.redirectTarget(base, "https://audioboo.org/x/").toString());
        assertEquals(null, SiteHttp.redirectTarget(base, " "));
    }

    /**
     * A site like audioboo on the loopback: GET is challenged, POST passes, {@code /page/1/} and {@code /old/}
     * redirect. The log shows what was sent: every hop must stay a POST with the form.
     */
    private static final class PostOnlySite implements AutoCloseable {
        private static final String CRLF = "\r\n";
        final List<String> log = new CopyOnWriteArrayList<>();
        final ServerSocket socket = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
        final String base = "http://127.0.0.1:" + socket.getLocalPort();

        PostOnlySite() throws IOException {
            new Thread(this::serve).start();
        }

        private void serve() {
            while (!socket.isClosed()) {
                try (Socket client = socket.accept()) {
                    answer(client);
                } catch (IOException e) {
                    return;
                }
            }
        }

        private void answer(Socket client) throws IOException {
            InputStream in = client.getInputStream();
            String[] head = readHead(in).split(CRLF);
            String[] requestLine = head[0].split(" ");
            int length = 0;
            for (String header : head) {
                if (header.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                    length = Integer.parseInt(header.substring("content-length:".length()).trim());
                }
            }
            byte[] body = new byte[length];
            for (int read = 0, n; read < length && (n = in.read(body, read, length - read)) > 0; ) {
                read += n;
            }
            String method = requestLine[0];
            String path = requestLine[1];
            log.add(method + " " + path + " " + new String(body, StandardCharsets.UTF_8));
            String status;
            String location = null;
            String html = "";
            if (path.equals("/old/") || path.endsWith("/page/1/")) {
                status = "301 Moved Permanently";
                location = path.equals("/old/") ? "/new/" : "/";
            } else if (path.equals("/away/")) {
                status = "302 Found";
                location = "http://localhost:1/elsewhere/";
            } else if (path.startsWith("/loop/")) {
                status = "302 Found";
                location = "/loop/" + (Integer.parseInt(path.substring(6, path.length() - 1)) + 1) + "/";
            } else if (path.equals("/nowhere/")) {
                status = "302 Found";
            } else if (method.equals("POST")) {
                status = "200 OK";
                html = "<html><head><title>Книги</title></head><body>listing</body></html>";
            } else {
                status = "403 Forbidden";
                html = "<html><head><title>Just a moment...</title></head></html>";
            }
            byte[] out = html.getBytes(StandardCharsets.UTF_8);
            StringBuilder response = new StringBuilder("HTTP/1.1 ").append(status).append(CRLF);
            if (location != null) {
                response.append("Location: ").append(location).append(CRLF);
            }
            response.append("Content-Type: text/html; charset=UTF-8").append(CRLF)
                    .append("Content-Length: ").append(out.length).append(CRLF)
                    .append("Connection: close").append(CRLF).append(CRLF);
            OutputStream os = client.getOutputStream();
            os.write(response.toString().getBytes(StandardCharsets.ISO_8859_1));
            os.write(out);
            os.flush();
        }

        /** The request line and headers, up to the blank line. */
        private static String readHead(InputStream in) throws IOException {
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            String end = CRLF + CRLF;
            int matched = 0;
            for (int b; matched < end.length() && (b = in.read()) >= 0; ) {
                head.write(b);
                matched = b == end.charAt(matched) ? matched + 1 : (b == '\r' ? 1 : 0);
            }
            return new String(head.toByteArray(), StandardCharsets.ISO_8859_1);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
