package com.fanok.audiobooks.model;

import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.GenrePOJO;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;

public class AutorsModel extends GenreModel {

    @Override
    protected ArrayList<GenrePOJO> loadBooksList(String url, int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36")
                .referrer("http://www.google.com")
                .get();

        Elements pagesConteiner = doc.getElementsByClass("pn_page_buttons");
        if (pagesConteiner.size() != 0) {
            Elements pagesElements = pagesConteiner.first().children();
            if (pagesElements.size() != 0) {
                Element lastPageElement = pagesElements.last();
                int lastPage = Integer.parseInt(lastPageElement.text());
                if (lastPage < page) throw new NullPointerException();
            }
        } else if (page > 1) throw new NullPointerException();

        Elements items = doc.getElementsByClass("author_item");
        for (Element item : items) {
            GenrePOJO genrePOJO = new GenrePOJO();
            Elements a = item.getElementsByTag("a");
            if (a.size() != 0) {
                String src = a.first().attr("href");
                if (src != null && !src.isEmpty()) genrePOJO.setUrl(Url.SERVER + src);
                String name = a.first().text();
                if (name != null && !name.isEmpty()) genrePOJO.setName(name);
            }
            Elements booksCount = item.getElementsByClass("author_item_books_count");
            if (booksCount.size() != 0) {
                genrePOJO.setDescription(booksCount.first().text());
            }
            if (!genrePOJO.isNull()) result.add(genrePOJO);
        }
        return result;
    }
}
