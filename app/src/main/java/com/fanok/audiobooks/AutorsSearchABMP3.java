package com.fanok.audiobooks;

import com.fanok.audiobooks.net.SiteHttp;
import com.fanok.audiobooks.pojo.GenrePOJO;
import com.fanok.audiobooks.search.ProviderPage;
import java.io.IOException;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/** The author block of ABMP3's one-page search, sliced into pages of {@link #PAGE_SIZE}. */
public class AutorsSearchABMP3 {

    public static final int PAGE_SIZE = 40;

    public ProviderPage<GenrePOJO> getAutors(@NotNull String url, int page) throws IOException {
        return parse(SiteHttp.get(url), page);
    }

    public static ProviderPage<GenrePOJO> parse(Document doc, int page) {
        Elements hits = new Elements();
        for (Element item : doc.getElementsByClass("b-statictop-search")) {
            Element title = item.selectFirst(".b-statictop__title");
            Element parent = item.selectFirst(".b-statictop__items");
            if (title != null && title.text().contains("по авторам") && parent != null) {
                hits = parent.children();
                break;
            }
        }
        int from = Math.min((page - 1) * PAGE_SIZE, hits.size());
        int to = Math.min(page * PAGE_SIZE, hits.size());
        ArrayList<GenrePOJO> result = new ArrayList<>();
        for (Element book : hits.subList(from, to)) {
            try {
                Element aTag = book.selectFirst("a");
                if (aTag == null) {
                    continue;
                }
                GenrePOJO genrePOJO = new GenrePOJO();
                if (!aTag.attr("href").isEmpty()) {
                    genrePOJO.setUrl(Url.SERVER_ABMP3 + aTag.attr("href") + "?page=");
                }
                if (!aTag.text().trim().isEmpty()) {
                    genrePOJO.setName(aTag.text().trim());
                }
                Element rating = book.selectFirst(".rate");
                if (rating != null && !rating.text().trim().isEmpty()) {
                    genrePOJO.setDescription(rating.text().trim());
                }
                if (!genrePOJO.isNull()) {
                    result.add(genrePOJO);
                }
            } catch (RuntimeException ignored) {
                // one malformed row is skipped, not the page
            }
        }
        return ProviderPage.of(result, hits.size() > to);
    }
}
