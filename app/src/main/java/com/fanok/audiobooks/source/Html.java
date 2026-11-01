package com.fanok.audiobooks.source;

import androidx.annotation.Nullable;

import com.fanok.audiobooks.pojo.BookPOJO;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.pojo.SeriesPOJO;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * What every HTML source does with a page: lists of cards or rows where one broken item is skipped (never the page),
 * texts and links that may be absent, pager numbers and lazily loaded images. Pure and static.
 */
public final class Html {

    private static final Logger LOG = Logger.getLogger("parsers");

    private Html() {
    }

    public interface ItemParser<T> {
        @Nullable
        T parse(Element element);
    }

    /**
     * Applies {@code parser} to every element: an element that throws is skipped and counted (logged once
     * per list), so one odd card never loses the whole list. The parser throws no checked exception: an item that
     * needs a request handles its failure itself, so a timeout or a block is never taken for one odd card.
     */
    public static <T> ArrayList<T> each(Iterable<Element> elements, ItemParser<T> parser) {
        ArrayList<T> result = new ArrayList<>();
        int skipped = 0;
        RuntimeException first = null;
        for (Element element : elements) {
            try {
                T item = parser.parse(element);
                if (item != null) {
                    result.add(item);
                }
            } catch (RuntimeException e) {
                skipped++;
                if (first == null) {
                    first = e;
                }
            }
        }
        if (skipped > 0) {
            LOG.warning("skipped " + skipped + " unparsable item(s): " + first);
        }
        return result;
    }

    /** {@link #each} over book cards; a card without URL or name ({@link BookPOJO#isNull}) is dropped too (SRCH-08). */
    public static ArrayList<BookPOJO> cards(Iterable<Element> cards, ItemParser<BookPOJO> parser) {
        return each(cards, card -> {
            BookPOJO book = parser.parse(card);
            return book == null || book.isNull() ? null : book;
        });
    }

    /** {@link #each} over genre or people rows; a row without URL or name is dropped too. */
    public static ArrayList<GenrePOJO> rows(Iterable<Element> rows, ItemParser<GenrePOJO> parser) {
        return each(rows, row -> {
            GenrePOJO item = parser.parse(row);
            return item == null || item.isNull() ? null : item;
        });
    }

    /** The largest page number among pager links (text, else {@code data-page}); 0 when there is none. */
    public static int lastPage(Elements links) {
        int last = 0;
        for (Element a : links) {
            last = Math.max(last, number(a.text()));
            if (a.hasAttr("data-page")) {
                last = Math.max(last, number(a.attr("data-page")));
            }
        }
        return last;
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Trimmed text of {@code e}; "" when it is null. */
    public static String text(@Nullable Element e) {
        return e == null ? "" : e.text().trim();
    }

    /** Trimmed text of the first match of {@code css} under {@code scope}; "" when either is absent. */
    public static String text(@Nullable Element scope, String css) {
        return scope == null ? "" : text(scope.selectFirst(css));
    }

    /** True for a link to a path of the page's own site ({@code /x}); false for another host, {@code //host/x} included. */
    public static boolean ownPath(String href) {
        return href.startsWith("/") && !href.startsWith("//");
    }

    /** Absolute href of the first match of {@code css} under {@code scope}; "" when either is absent or unresolvable. */
    public static String href(@Nullable Element scope, String css) {
        Element element = scope == null ? null : scope.selectFirst(css);
        return element == null ? "" : element.absUrl("href");
    }

    /**
     * The first {@code rowsCss} row under {@code scope} whose text starts with {@code label}: the "Автор: ..." rows of a
     * card or a book page. Null when there is none; {@link #afterLabel}, {@link #text} and {@link #href} take null.
     */
    @Nullable
    public static Element labelled(Element scope, String rowsCss, String label) {
        for (Element row : scope.select(rowsCss)) {
            if (row.text().startsWith(label)) {
                return row;
            }
        }
        return null;
    }

    /** The text after a row's "Label:" (both authors of "Автор: A, B"), all of it without a colon; "" for no row. */
    public static String afterLabel(@Nullable Element row) {
        if (row == null) {
            return "";
        }
        String text = row.text();
        int colon = text.indexOf(':');
        return (colon == -1 ? text : text.substring(colon + 1)).trim();
    }

    /** The text next to an icon ({@code <div><i class="fi-rr-comment"></i>3</div>} → "3"); "" without the icon. */
    public static String iconText(Element scope, String iconCss) {
        Element icon = scope.selectFirst(iconCss);
        return icon == null || icon.parent() == null ? "" : icon.parent().text().trim();
    }

    /** Absolute cover of a lazy-loaded image (data-original, data-src, then src), skipping placeholders. */
    public static String imgSrc(@Nullable Element img) {
        if (img == null) {
            return "";
        }
        for (String attr : new String[]{"data-original", "data-src", "src"}) {
            String src = img.absUrl(attr);
            if (!src.isEmpty() && !src.contains("placeholder")) {
                return src;
            }
        }
        return "";
    }

    /** A series entry from a book link and its cover; null when the link is missing. */
    @Nullable
    public static SeriesPOJO seriesItem(@Nullable Element a, @Nullable Element img) {
        String link = a == null ? "" : a.absUrl("href");
        if (link.isEmpty()) {
            return null;
        }
        SeriesPOJO seriesPOJO = new SeriesPOJO();
        seriesPOJO.setUrl(link);
        seriesPOJO.setName(a.text().trim());
        seriesPOJO.setPhoto(imgSrc(img));
        return seriesPOJO;
    }
}
