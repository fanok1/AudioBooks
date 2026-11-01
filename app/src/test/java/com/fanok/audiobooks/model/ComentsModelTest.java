package com.fanok.audiobooks.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fanok.audiobooks.source.Akniga;
import com.fanok.audiobooks.source.BazaKnig;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.SubComentsPOJO;
import com.fanok.audiobooks.source.Abmp3;
import com.fanok.audiobooks.source.Audioboo;
import com.fanok.audiobooks.source.Knigavuhe;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.Test;

/** Comment parsers against pages captured once from the sites (fixtures/<source>/), plus the paging walk. */
public class ComentsModelTest {

    private static final String KNIGAVUHE = "https://knigavuhe.org/book/22147-stalker-intelligentnyjj-stalker-1/";

    private static final String ABMP3 = "https://audiobook-mp3.com/audio-292-sapiens-kratkaja-istorija-chelovechestva";

    private static final String AKNIGA = "https://akniga.org/arsenev-sergey-studentka-komsomolka-sportsmenka";

    private static final String BAZA_KNIG
            = "https://baza-knig.top/alternativnaya-istoriiya/21849-studentka-komsomolka-sportsmenka-sergey-arsenev.html";

    private static final String AUDIOBOO = "https://audioboo.org/roman/40195-ross-yan-basad.html";

    private static final String NO_MORE_PAGES = "{\"navigation\":\"\",\"comments\":\"\"}";

    @Test
    public void knigavuheThreadsKeepEveryReplyUnderItsTopLevelComment() throws IOException {
        ComentsModel.Parsed parsed = Knigavuhe.parseComments(page("knigavuhe/comments.html", KNIGAVUHE));

        assertEquals(12, parsed.comments.size());
        assertEquals(0, parsed.skipped);
        ComentsPOJO first = parsed.comments.get(0);
        assertEquals("Ал Ку", first.getName());
        assertEquals("Согласен полностью чтец восхитительный. Даже неинтересный рассказ вытащит.", first.getText());
        assertEquals("+22", first.getReting());
        assertEquals("https://s5.knigavuhe.org/1/avatars/69338/a_50.jpg?1564829311", first.getImage());
        assertEquals(1, first.getChildComents().size());
        assertEquals("Александр 18", first.getChildComents().get(0).getName());
        assertEquals("Ал Ку", first.getChildComents().get(0).getParentName());

        // 98974 has two replies, one of which has its own reply: all three stay in this thread, in page order
        ComentsPOJO thread = parsed.comments.get(5);
        assertEquals("Александр Кот", thread.getName());
        assertEquals(Arrays.asList("Сергей", "Александр Кот", "Мёртвый Анархист"), names(thread.getChildComents()));
        assertEquals(Arrays.asList("Александр Кот", "Сергей", "Александр Кот"), parentNames(thread.getChildComents()));
        assertAllImagesHttps(parsed.comments);
    }

    @Test
    public void knigavuheCommentWithoutBodyIsSkippedAndCounted() throws IOException {
        Document doc = page("knigavuhe/comments.html", KNIGAVUHE);
        doc.getElementById("comment94864_body").remove();

        ComentsModel.Parsed parsed = Knigavuhe.parseComments(doc);

        assertEquals(11, parsed.comments.size());
        assertEquals(1, parsed.skipped);
        assertEquals("Влад Терпугов", parsed.comments.get(1).getName());
    }

    @Test
    public void abmp3NestedReplyBelongsToItsParent() throws IOException {
        ComentsModel.Parsed parsed = Abmp3.parseComments(page("abmp3/comments.html", ABMP3));

        assertEquals(14, parsed.comments.size());
        assertEquals(0, parsed.skipped);
        ComentsPOJO first = parsed.comments.get(0);
        assertEquals("Ираида", first.getName());
        assertEquals("6 years ago", first.getDate());
        assertTrue(first.getText().startsWith("Спасибо"));
        ComentsPOJO vlad = parsed.comments.get(7);
        assertEquals("Влад", vlad.getName());
        assertEquals(1, vlad.getChildComents().size());
        SubComentsPOJO reply = vlad.getChildComents().get(0);
        assertEquals("Юрий", reply.getName());
        assertEquals("Многие чувства имеют другие животные.", reply.getText());
        assertEquals("Влад", reply.getParentName());
        assertAllImagesHttps(parsed.comments);
    }

    @Test
    public void aknigaReplyToAReplyStaysInTheThread() throws IOException {
        Document doc = page("akniga/comments.html", AKNIGA);
        ComentsModel.Parsed parsed = Akniga.parseComments(doc, AKNIGA);

        assertEquals(3, parsed.comments.size());
        assertEquals(0, parsed.skipped);
        ComentsPOJO first = parsed.comments.get(0);
        assertEquals("Viacheslav Nazarov", first.getName());
        assertEquals("4 июня 2018", first.getDate());
        assertEquals("73", first.getReting());
        assertTrue(first.getText().startsWith("Это не фантастика. Это добрая сказка."));
        assertTrue(first.getImage().startsWith("https://akniga.org/uploads/images/users/000/038/246/"));
        assertEquals(Arrays.asList("alina liss", "Евгений Кульба", "Алексей Мартыненко", "gakinos"),
                names(first.getChildComents()));
        assertEquals(Arrays.asList("Viacheslav Nazarov", "Viacheslav Nazarov", "Евгений Кульба", "Viacheslav Nazarov"),
                parentNames(first.getChildComents()));
        assertEquals("", first.getChildComents().get(2).getReting()); // data-rating="0"
        assertEquals(0, parsed.comments.get(1).getChildComents().size());
        assertEquals(2, parsed.comments.get(2).getChildComents().size());
        assertAllImagesHttps(parsed.comments);

        assertEquals("https://akniga.org/rss/comments/17378/", Akniga.feedUrl(doc));
    }

    @Test
    public void aknigaFeedKeepsCdataTextAndSkipsCommentsAlreadyOnThePage() throws IOException {
        Document feed = xml("akniga/comments-rss.xml", "https://akniga.org/rss/comments/17378/");

        ComentsModel.Parsed all = Akniga.parseCommentsFeed(feed, Jsoup.parse("<p></p>"));
        assertEquals(5, all.comments.size());
        assertEquals("bivis-bez-batheda", all.comments.get(0).getName());
        assertEquals("Очень круто!", all.comments.get(0).getText());
        assertEquals("Mon, 28 Sep 2026 17:44:16", all.comments.get(0).getDate());

        Document pageWithFirst = Jsoup.parse("<div id=\"comment1765480\"></div>");
        ComentsModel.Parsed rest = Akniga.parseCommentsFeed(feed, pageWithFirst);
        assertEquals(4, rest.comments.size());
        assertEquals("roman-orlov", rest.comments.get(0).getName());
    }

    @Test
    public void aknigaPageWithAllCommentsNeedsNoFeed() {
        Document doc = Jsoup.parse("<div id=\"comments\" data-target-id=\"1\">"
                + "<a class=\"js-comments-load-more\" data-total=\"5\" data-loaded=\"5\"></a></div>", AKNIGA);
        assertNull(Akniga.feedUrl(doc));
    }

    @Test
    public void bazaKnigPageReadsQuotesAndProtocolRelativeAvatars() throws IOException {
        String pageUrl = "https://baza-knig.top/engine/ajax/controller.php?mod=comments&cstart=1";
        String html = BazaKnig.commentsHtml(resource("baza-knig/comments.json"));
        ComentsModel.Parsed parsed = BazaKnig.parseComments(Jsoup.parseBodyFragment(html, pageUrl), BAZA_KNIG);

        assertEquals(30, parsed.comments.size());
        assertEquals(0, parsed.skipped);
        ComentsPOJO first = parsed.comments.get(0);
        assertEquals("demiik", first.getName());
        assertEquals("13/10/2026", first.getDate());
        assertEquals("Slimer91", first.getQuoteName());
        assertTrue(first.getText().startsWith("Это таким как ты в уши надули"));
        assertTrue(!first.getQuoteText().isEmpty() && !first.getQuoteText().contains("Цитата"));
        assertTrue(images(parsed.comments).contains("https://baza-knig.top/uploads/fotos/foto_18399.webp"));
        assertAllImagesHttps(parsed.comments);
    }

    @Test
    public void bazaKnigPagingStopsAtTheEmptyPageAndReturnsOldestFirst() throws Exception {
        List<String> requested = new ArrayList<>();
        String page1 = resource("baza-knig/comments.json");
        ComentsModel.Parsed parsed = BazaKnig.loadComments(BAZA_KNIG, url -> {
            requested.add(url);
            return url.contains("cstart=1&") ? page1 : NO_MORE_PAGES;
        }, () -> false);

        assertEquals(2, requested.size());
        assertTrue(requested.get(0).startsWith("https://baza-knig.top/engine/ajax/controller.php?mod=comments&cstart=1&news_id=21849&"));
        assertEquals(30, parsed.comments.size());
        assertEquals("demiik", parsed.comments.get(29).getName());
    }

    @Test
    public void bazaKnigPagingIsBounded() throws Exception {
        String page1 = resource("baza-knig/comments.json");
        int[] calls = {0};
        ComentsModel.Parsed repeated = BazaKnig.loadComments(BAZA_KNIG, url -> {
            calls[0]++;
            return page1; // a site that clamps cstart to its last page
        }, () -> false);
        assertEquals(2, calls[0]);
        assertEquals(30, repeated.comments.size());

        calls[0] = 0;
        ComentsModel.Parsed endless = BazaKnig.loadComments(BAZA_KNIG, url -> {
            calls[0]++;
            return "{\"comments\":\"<div class='comm'><div class='comm-text'><div>n" + calls[0] + "</div></div></div>\"}";
        }, () -> false);
        assertEquals(BazaKnig.MAX_COMMENT_PAGES, calls[0]);
        assertEquals(BazaKnig.MAX_COMMENT_PAGES, endless.comments.size());

        calls[0] = 0;
        BazaKnig.loadComments(BAZA_KNIG, url -> {
            calls[0]++;
            return page1;
        }, () -> true);
        assertEquals(0, calls[0]);
    }

    @Test
    public void bazaKnigAnswerWithoutCommentsIsEmpty() {
        assertEquals("", BazaKnig.commentsHtml(NO_MORE_PAGES));
        assertEquals("", BazaKnig.commentsHtml("{\"error\":\"no access\"}"));
        assertEquals("", BazaKnig.commentsHtml("{\"comments\":null}"));
        assertEquals("", BazaKnig.commentsHtml("[]"));
    }

    @Test
    public void bazaKnigNewsIdIsTheLeadingNumberOfTheLastPathSegment() {
        assertEquals("182353", BazaKnig.newsId(
                "https://baza-knig.top/fantastika-fentezii/182353-taksist-iz-forbes-nik-tarasov.html"));
        assertEquals("182353", BazaKnig.newsId("https://baza-knig.top/182353.html"));
        assertEquals("14278", BazaKnig.newsId("https://baza-knig.top/popadanci/14278-igrat/"));
        assertEquals("1", BazaKnig.newsId("https://baza-knig.top/kniga/1.html?x=1#top"));
        try {
            BazaKnig.newsId("https://baza-knig.top/f/sort=comm_num/order=desc/");
            fail("a URL without a book id must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("no book id"));
        }
    }

    @Test
    public void audiobooCommentsInPageOrderWithoutAnInventedAvatar() throws IOException {
        ComentsModel.Parsed parsed = Audioboo.parseComments(page("audioboo/comments.html", AUDIOBOO));

        assertEquals(25, parsed.comments.size());
        assertEquals(0, parsed.skipped);
        assertEquals("Sparta", parsed.comments.get(0).getName());
        assertEquals("23 февраля 2023 01:11", parsed.comments.get(0).getDate());
        assertEquals("Несомненно достойное произведение.", parsed.comments.get(0).getText());
        assertEquals("Stoper", parsed.comments.get(24).getName());
        assertEquals("", parsed.comments.get(0).getImage());
    }

    @Test
    public void pagesWithoutCommentsGiveEmptyLists() {
        Document empty = Jsoup.parse("<html><body><p>Комментариев нет</p></body></html>", KNIGAVUHE);
        assertEmpty(Knigavuhe.parseComments(empty));
        assertEmpty(Abmp3.parseComments(empty));
        assertEmpty(Akniga.parseComments(empty, AKNIGA));
        assertEmpty(BazaKnig.parseComments(Jsoup.parseBodyFragment(""), BAZA_KNIG));
        assertEmpty(Audioboo.parseComments(empty));
        assertNull(Akniga.feedUrl(empty));
    }

    @Test
    public void unknownHostIsAnErrorWithoutComplete() {
        new ComentsModel().getComents("https://example.com/book/1").test()
                .assertNoValues()
                .assertNotComplete()
                .assertError(e -> e instanceof IllegalArgumentException
                        && "unsupported host: example.com".equals(e.getMessage()));
    }

    @Test
    public void sitesWithoutCommentsCompleteEmptyIncludingTheirOldHosts() {
        new ComentsModel().getComents("https://izib.uk/book/1").test()
                .assertValue(List::isEmpty)
                .assertComplete();
        new ComentsModel().getComents("http://izibuk.ru/book/1").test() // moved host, rewritten before matching
                .assertValue(List::isEmpty)
                .assertComplete();
    }

    private static void assertEmpty(ComentsModel.Parsed parsed) {
        assertEquals(0, parsed.comments.size());
        assertEquals(0, parsed.skipped);
    }

    private static void assertAllImagesHttps(List<ComentsPOJO> comments) {
        for (String image : images(comments)) {
            assertTrue(image, image.startsWith("https://"));
        }
    }

    private static List<String> images(List<ComentsPOJO> comments) {
        List<String> images = new ArrayList<>();
        for (ComentsPOJO comment : comments) {
            images.add(comment.getImage());
            for (SubComentsPOJO reply : comment.getChildComents()) {
                images.add(reply.getImage());
            }
        }
        return images;
    }

    private static List<String> names(List<SubComentsPOJO> replies) {
        List<String> names = new ArrayList<>();
        for (SubComentsPOJO reply : replies) {
            names.add(reply.getName());
        }
        return names;
    }

    private static List<String> parentNames(List<SubComentsPOJO> replies) {
        List<String> names = new ArrayList<>();
        for (SubComentsPOJO reply : replies) {
            names.add(reply.getParentName());
        }
        return names;
    }

    private static Document page(String path, String baseUri) throws IOException {
        return Jsoup.parse(resource(path), baseUri);
    }

    private static Document xml(String path, String baseUri) throws IOException {
        return Jsoup.parse(resource(path), baseUri, Parser.xmlParser());
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = ComentsModelTest.class.getResourceAsStream("/fixtures/" + path)) {
            assertNotNull(path, in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) != -1; ) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
