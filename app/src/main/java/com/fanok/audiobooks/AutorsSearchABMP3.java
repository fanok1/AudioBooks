package com.fanok.audiobooks;

import com.fanok.audiobooks.pojo.GenrePOJO;
import java.io.IOException;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class AutorsSearchABMP3 {

    public ArrayList<GenrePOJO> getAutors(@NotNull String url, int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .maxBodySize(0)
                .get();

        Elements elements = doc.getElementsByClass("b-statictop-search");
        if (elements != null) {
            for (Element item : elements) {
                Elements title = item.getElementsByClass("b-statictop__title");
                if (title != null && title.size() != 0) {
                    if (title.first().text().contains("по авторам")) {
                        Elements parent = item.getElementsByClass("b-statictop__items");
                        if (parent != null && parent.size() != 0) {
                            Elements books = parent.first().children();
                            for (int i = (page - 1) * 40; i < page * 40; i++) {
                                if (i >= books.size()) {
                                    break;
                                }
                                Element book = books.get(i);
                                GenrePOJO genrePOJO = new GenrePOJO();
                                Elements aTag = book.getElementsByTag("a");
                                if (aTag != null && aTag.size() != 0) {
                                    String href = aTag.first().attr("href");
                                    if (href != null && !href.isEmpty()) {
                                        genrePOJO.setUrl(Url.SERVER_ABMP3 + href + "?page=");
                                    }
                                    String fullText = aTag.first().text();
                                    if (fullText != null && !fullText.isEmpty()) {
                                        genrePOJO.setName(fullText.trim());
                                    }

                                    Elements raitings = book.getElementsByClass("rate");
                                    if (raitings != null && raitings.size() != 0) {
                                        String text = raitings.first().text().trim();
                                        if (!text.isEmpty()) {
                                            genrePOJO.setDescription(text);
                                        }
                                    }

                                }

                                if (!genrePOJO.isNull()) {
                                    result.add(genrePOJO);
                                }
                            }
                        }
                    }
                }
            }
        }

        return result;
    }
}
