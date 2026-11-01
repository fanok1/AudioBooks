package com.fanok.audiobooks.model;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.ContentParentPOJO;
import com.fanok.audiobooks.pojo.SubComentsPOJO;
import com.fanok.audiobooks.search.Provider;
import com.fanok.audiobooks.source.SourceParser;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BooleanSupplier;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Comments of a book page. Fetching (through {@link SiteHttp}) is kept apart from parsing: the
 * {@code parseComments*} functions take already-fetched content, never touch Android APIs and skip
 * (and count) a comment they cannot read instead of failing the whole list.
 */
public class ComentsModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.ComentsModel {

    private static final String TAG = "ComentsModel";

    /** Comments read from one page and how many malformed ones were skipped. */
    public static final class Parsed {

        public final ArrayList<ComentsPOJO> comments = new ArrayList<>();

        public int skipped;

        void add(Parsed more) {
            comments.addAll(more.comments);
            skipped += more.skipped;
        }
    }

    /** Fills a POJO from one comment element; leaves the text empty (or throws) when the markup is unexpected. */
    public interface CommentReader {

        void read(Element comment, ContentParentPOJO into);
    }

    private static String sSharedUrl;

    private static Observable<ArrayList<ComentsPOJO>> sShared;

    /** The running fetch of {@link #sShared}, so leaving the book can stop its paging. */
    private static Disposable sConnection;

    /**
     * The comments of the book screen opened last, fetched once and shared by its tab title (the count) and its
     * list. {@code fresh} starts a new fetch (the book opened anew); otherwise one of the same book is reused.
     */
    public static synchronized Observable<ArrayList<ComentsPOJO>> shared(@NonNull String url, boolean fresh) {
        if (fresh || sShared == null || !url.equals(sSharedUrl)) {
            sSharedUrl = url;
            ConnectableObservable<ArrayList<ComentsPOJO>> shared =
                    new ComentsModel().getComents(url).subscribeOn(Schedulers.io()).replay();
            sConnection = shared.connect();
            sShared = shared;
        }
        return sShared;
    }

    /** The book screen of {@code url} is closed for good: its comments fetch, if still the shared one, stops. */
    public static synchronized void cancel(@NonNull String url) {
        if (url.equals(sSharedUrl) && sConnection != null) {
            sConnection.dispose();
            sConnection = null;
            sShared = null;
            sSharedUrl = null;
        }
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
        SourceParser parser = Provider.parserOf(url);
        if (parser != null) {
            return parser.comments(url, cancelled);
        }
        throw new IllegalArgumentException("unsupported host: " + Url.hostOf(url));
    }

    /**
     * Builds the comment list: an element inside no other {@code commentQuery} element (every element when the
     * query is null) is a top-level comment; any other becomes a reply of its top-level comment, in page order,
     * naming the comment it answers. A comment that cannot be read is skipped and counted, and so are the
     * replies of a skipped top-level comment.
     */
    public static Parsed nest(Elements comments, @Nullable String commentQuery, CommentReader reader) {
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
}
