package com.fanok.audiobooks.model;


import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.ContentParentPOJO;
import com.fanok.audiobooks.pojo.SubComentsPOJO;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.reactivex.Observable;
import io.reactivex.functions.BooleanSupplier;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

/**
 * Comments of a book page. Fetching (through {@link SiteHttp}) is kept apart from parsing: the
 * {@code parseComments*} functions take already-fetched content, never touch Android APIs and skip
 * (and count) a comment they cannot read instead of failing the whole list.
 */
public class ComentsModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.ComentsModel {

    private static final String TAG = "ComentsModel";

    /** baza-knig serves 30 comments per page; the bound stops the walk on a site that never sends an empty page. */
    static final int BAZA_KNIG_MAX_PAGES = 50;

    private static final Pattern BAZA_KNIG_NEWS_ID = Pattern.compile("/(\\d+)[^/]*/?$");

    private static final Pattern AKNIGA_FEED_ID = Pattern.compile("#comment(\\d+)$");

    /** Comments read from one page and how many malformed ones were skipped. */
    static final class Parsed {

        final ArrayList<ComentsPOJO> comments = new ArrayList<>();

        int skipped;

        void add(Parsed more) {
            comments.addAll(more.comments);
            skipped += more.skipped;
        }
    }

    /** Fills a POJO from one comment element; leaves the text empty (or throws) when the markup is unexpected. */
    private interface CommentReader {

        void read(Element comment, ContentParentPOJO into);
    }

    /** GETs one baza-knig comments page (JSON); a seam so the paging walk is testable without the network. */
    interface PageSource {

        String get(String url) throws IOException;
    }

    @Override
    public Observable<ArrayList<ComentsPOJO>> getComents(@NonNull String url) {
        return Observable.create(emitter -> {
            ArrayList<ComentsPOJO> comments;
            try {
                comments = load(url, emitter::isDisposed);
            } catch (Exception e) {
                emitter.tryOnError(e);
                return;
            }
            if (!emitter.isDisposed()) {
                emitter.onNext(comments);
                emitter.onComplete();
            }
        });
    }

    static ArrayList<ComentsPOJO> load(String url, BooleanSupplier cancelled) throws Exception {
        url = Url.canonical(url);
        Parsed parsed;
        if (Url.isHost(url, Url.SERVER)) {
            parsed = parseCommentsKnigavuhe(SiteHttp.get(url), url);
        } else if (Url.isHost(url, Url.SERVER_ABMP3)) {
            parsed = parseCommentsAbmp3(SiteHttp.get(url), url);
        } else if (Url.isHost(url, Url.SERVER_AKNIGA)) {
            parsed = loadAkniga(url);
        } else if (Url.isHost(url, Url.SERVER_BAZA_KNIG)) {
            parsed = loadBazaKnig(url, ComentsModel::getJson, cancelled);
        } else if (Url.isHost(url, Url.SERVER_AUDIOBOO)) {
            parsed = parseCommentsAudioboo(SiteHttp.get(url), url);
        } else if (Url.isHost(url, Url.SERVER_IZIBUK) || Url.isHost(url, Url.SERVER_KNIGOBLUD)
                || Url.isHost(url, Url.SERVER_OTRUB) || Url.isHost(url, Url.SERVER_POLEKNIG)) {
            return new ArrayList<>(); // the app reads no comments from these sites
        } else {
            throw new IllegalArgumentException("unsupported host: " + Url.hostOf(url));
        }
        if (parsed.skipped > 0) {
            Log.w(TAG, "skipped " + parsed.skipped + " malformed comments on " + url);
        }
        return parsed.comments;
    }

    static Parsed parseCommentsKnigavuhe(Document doc, String bookUrl) {
        Element list = doc.getElementById("comments_list");
        if (list == null) {
            return new Parsed();
        }
        return nest(list.select("div.comment"), "div.comment", (comment, pojo) -> {
            Element head = comment.selectFirst("> .comment_head");
            pojo.setName(text(head, ".comment_head_user"));
            pojo.setDate(text(head, ".comment_head_time"));
            pojo.setReting(text(head, ".comment_head_votes_count"));
            pojo.setImage(absUrl(head, ".comment_head_userpic img", "src"));
            pojo.setText(text(comment, "> .comment_body_wrap > .comment_body"));
        });
    }

    static Parsed parseCommentsAbmp3(Document doc, String bookUrl) {
        Element list = doc.getElementById("w0");
        if (list == null) {
            return new Parsed();
        }
        return nest(list.select("li.comment"), "li.comment", (comment, pojo) -> {
            Element content = comment.selectFirst("> .comment-content");
            pojo.setImage(absUrl(content, ".comment-author-avatar img", "src"));
            pojo.setName(text(content, ".comment-author-name > span:not(.comment-date)"));
            pojo.setDate(text(content, ".comment-date"));
            pojo.setText(text(content, ".comment-body"));
        });
    }

    static Parsed parseCommentsAkniga(Document doc, String bookUrl) {
        Element list = doc.getElementById("comments");
        if (list == null) {
            return new Parsed();
        }
        return nest(list.select(".js-comment-wrapper"), ".js-comment-wrapper", (wrapper, pojo) -> {
            Element section = wrapper.selectFirst("> section");
            Element name = section == null ? null : section.selectFirst(".comments__block--item--name");
            pojo.setName(name == null ? "" : name.ownText());
            String image = absUrl(section, "img.comments__block--item--avatar", "src");
            pojo.setImage(image.isEmpty() ? absUrl(section, "img.comments__block--item--avatar", "data-src") : image);
            pojo.setDate(text(section, "time"));
            pojo.setText(text(section, ".comments__block--item--comment"));
            String rating = wrapper.attr("data-rating");
            pojo.setReting(rating.equals("0") ? "" : rating);
        });
    }

    /** The comments feed when the page holds only part of the comments; null when it holds them all. */
    @Nullable
    static String aknigaFeedUrl(Document doc) {
        Element more = doc.selectFirst(".js-comments-load-more");
        Element list = doc.getElementById("comments");
        if (more == null || list == null) {
            return null;
        }
        String target = list.attr("data-target-id");
        if (toInt(more.attr("data-total")) <= toInt(more.attr("data-loaded")) || !target.matches("\\d+")) {
            return null;
        }
        return Url.SERVER_AKNIGA + "/rss/comments/" + target + "/";
    }

    /** Feed items that are not on {@code page}, as top-level comments (the feed carries no reply structure). */
    static Parsed parseCommentsAknigaFeed(Document feed, Document page) {
        Parsed parsed = new Parsed();
        for (Element item : feed.select("item")) {
            Matcher id = AKNIGA_FEED_ID.matcher(text(item, "guid"));
            if (id.find() && page.getElementById("comment" + id.group(1)) != null) {
                continue;
            }
            ComentsPOJO pojo = new ComentsPOJO();
            String creator = text(item, "dc|creator");
            pojo.setName(creator.isEmpty() ? text(item, "author") : creator);
            // the XML parser keeps the CDATA description, which is HTML, as text
            pojo.setText(Jsoup.parseBodyFragment(text(item, "description")).text());
            pojo.setDate(text(item, "pubDate").replaceAll(" [+-]\\d{4}$", ""));
            if (pojo.isEmty()) {
                parsed.skipped++;
            } else {
                parsed.comments.add(pojo);
            }
        }
        return parsed;
    }

    /** One page of baza-knig comments: flat, newest first; a reply quotes the comment it answers. */
    static Parsed parseCommentsBazaKnig(Document doc, String bookUrl) {
        return nest(doc.select("div.comm"), null, (comment, pojo) -> {
            pojo.setImage(absUrl(comment, ".comm-ava img", "src"));
            Element info = comment.selectFirst(".comm-info");
            pojo.setName(text(info, "b"));
            pojo.setDate(info == null ? "" : info.ownText());
            Element body = comment.selectFirst(".comm-text > div");
            if (body == null) {
                return;
            }
            Element quoteTitle = body.selectFirst(".title_quote");
            if (quoteTitle != null) {
                String user = quoteTitle.attr("data-commentuser");
                pojo.setQuoteName(user.isEmpty() ? quoteTitle.text().replace("Цитата: ", "") : user);
            }
            Element quote = body.selectFirst(".quote");
            if (quote != null) {
                pojo.setQuoteText(withoutQuotes(quote));
            }
            pojo.setText(withoutQuotes(body));
        });
    }

    /** The HTML of one baza-knig comments page from the site's JSON answer; empty past the last page. */
    static String bazaKnigCommentsHtml(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            return "";
        }
        JsonElement comments = root.getAsJsonObject().get("comments");
        return comments != null && comments.isJsonPrimitive() ? comments.getAsString() : "";
    }

    /** The DLE news id of a baza-knig book URL ({@code .../182353-name.html} → {@code 182353}). */
    static String bazaKnigNewsId(String url) {
        String path = URI.create(url.trim()).getPath();
        Matcher matcher = BAZA_KNIG_NEWS_ID.matcher(path == null ? "" : path);
        if (!matcher.find()) {
            throw new IllegalArgumentException("no book id in " + url);
        }
        return matcher.group(1);
    }

    /** Walks the comment pages until an empty or repeated page, at most {@link #BAZA_KNIG_MAX_PAGES}; oldest first. */
    static Parsed loadBazaKnig(String url, PageSource source, BooleanSupplier cancelled) throws Exception {
        String newsId = bazaKnigNewsId(url);
        Parsed all = new Parsed();
        String previous = "";
        for (int page = 1; page <= BAZA_KNIG_MAX_PAGES && !cancelled.getAsBoolean(); page++) {
            String pageUrl = Url.SERVER_BAZA_KNIG + "/engine/ajax/controller.php?mod=comments&cstart=" + page
                    + "&news_id=" + newsId + "&skin=knigi-pk&massact=disable";
            String html = bazaKnigCommentsHtml(source.get(pageUrl));
            if (html.isEmpty() || html.equals(previous)) {
                break;
            }
            previous = html;
            all.add(parseCommentsBazaKnig(Jsoup.parseBodyFragment(html, pageUrl), url));
        }
        Collections.reverse(all.comments);
        return all;
    }

    static Parsed parseCommentsAudioboo(Document doc, String bookUrl) {
        Element list = doc.getElementById("dle-comments-list");
        if (list == null) {
            return new Parsed();
        }
        //a reply sits inside the block of the comment it answers
        String block = "div[id^=comment-id-]";
        return nest(list.select(block), block, (comment, pojo) -> {
            pojo.setName(text(comment, ".comment-item__author"));
            pojo.setDate(text(comment, ".comment-item__date"));
            pojo.setText(text(comment, ".comment-item__main"));
        });
    }

    /**
     * Builds the comment list: an element inside no other {@code commentQuery} element (every element when the
     * query is null) is a top-level comment; any other becomes a reply of its top-level comment, in page order,
     * naming the comment it answers. A comment that cannot be read is skipped and counted, and so are the
     * replies of a skipped top-level comment.
     */
    private static Parsed nest(Elements comments, @Nullable String commentQuery, CommentReader reader) {
        Parsed parsed = new Parsed();
        Map<Element, ComentsPOJO> rootOf = new IdentityHashMap<>();
        Map<Element, String> nameOf = new IdentityHashMap<>();
        for (Element comment : comments) {
            Element parent = commentQuery == null || comment.parent() == null
                    ? null : comment.parent().closest(commentQuery);
            ComentsPOJO root = parent == null ? null : rootOf.get(parent);
            if (parent != null && root == null) {
                parsed.skipped++;
                continue;
            }
            ContentParentPOJO pojo = root == null ? new ComentsPOJO() : new SubComentsPOJO();
            try {
                reader.read(comment, pojo);
            } catch (RuntimeException e) {
                pojo.setText("");
            }
            nameOf.put(comment, pojo.getName());
            if (root != null) {
                rootOf.put(comment, root); // replies to a skipped reply still belong to the thread
            }
            if (pojo.isEmty()) {
                parsed.skipped++;
            } else if (root == null) {
                rootOf.put(comment, (ComentsPOJO) pojo);
                parsed.comments.add((ComentsPOJO) pojo);
            } else {
                ((SubComentsPOJO) pojo).setParentName(nameOf.get(parent));
                root.setChildComents((SubComentsPOJO) pojo);
            }
        }
        return parsed;
    }

    private static Parsed loadAkniga(String url) throws IOException {
        Document page = SiteHttp.get(url);
        Parsed parsed = parseCommentsAkniga(page, url);
        String feedUrl = aknigaFeedUrl(page);
        if (feedUrl != null) {
            try {
                Connection feed = SiteHttp.connect(feedUrl).parser(Parser.xmlParser());
                parsed.add(parseCommentsAknigaFeed(SiteHttp.parse(SiteHttp.execute(feed)), page));
            } catch (IOException e) {
                Log.w(TAG, "akniga comments feed failed; showing the comments on the page", e);
            }
        }
        return parsed;
    }

    private static String getJson(String url) throws IOException {
        Connection.Response response = SiteHttp.execute(SiteHttp.connect(url).ignoreContentType(true));
        String body = response.body();
        if (response.statusCode() >= 400 || !body.trim().startsWith("{")) {
            SiteHttp.parse(response); // throws for a challenge or an HTTP error page
            throw new IOException("not a comments page: " + url);
        }
        return body;
    }

    /** Text of a baza-knig comment body (or quote) without the quotes nested in it. */
    private static String withoutQuotes(Element body) {
        Element copy = body.clone();
        for (Element nested : copy.select(".quote_block, .title_quote, .quote")) {
            if (nested != copy) {
                nested.remove();
            }
        }
        return copy.text();
    }

    private static String text(@Nullable Element scope, String query) {
        Element element = scope == null ? null : scope.selectFirst(query);
        return element == null ? "" : element.text();
    }

    private static String absUrl(@Nullable Element scope, String query, String attribute) {
        Element element = scope == null ? null : scope.selectFirst(query);
        return element == null ? "" : element.absUrl(attribute);
    }

    private static int toInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
