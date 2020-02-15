package com.fanok.audiobooks.model;

import com.fanok.audiobooks.Consts;
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
                .userAgent(Consts.USER_AGENT)
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

    @Override
    protected ArrayList<GenrePOJO> loadBooksListIzibuk(String url, int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .get();

        Element bootom = doc.getElementById("authors_list__pn");
        if (bootom == null) {
            bootom = doc.getElementById("readers_list__pn");
        }
        boolean b = false;
        if (bootom != null) {
            Element bootomChild = bootom.child(0);
            if (bootomChild != null) {
                Elements list = bootomChild.children();
                if (list != null && list.size() > 1) {
                    Element pagesParent = list.last();
                    Elements pages = pagesParent.children();
                    if (pages != null && pages.size() > 0) {
                        Element lastPage = pages.last();
                        if (lastPage != null) {
                            String last = lastPage.text();
                            if (last != null) {
                                if (page > Integer.parseInt(last)) {
                                    throw new NullPointerException();
                                }
                            } else {
                                b = true;
                            }
                        } else {
                            b = true;
                        }
                    } else {
                        b = true;
                    }
                } else {
                    b = true;
                }
            } else {
                b = true;
            }
        } else {
            b = true;
        }

        if (page > 1 && b) {
            throw new NullPointerException();
        }

        Element itemParent = doc.getElementById("authors_list");
        if (itemParent == null) {
            itemParent = doc.getElementById("readers_list");
        }
        if (itemParent != null) {
            Elements list = itemParent.children();
            if (list != null) {
                for (Element item : list) {
                    GenrePOJO genrePOJO = new GenrePOJO();

                    String src = item.attr("href");
                    if (src != null) {
                        genrePOJO.setUrl(Url.SERVER_IZIBUK + src + "?p=");
                    }


                    Elements children = item.children();
                    if (children != null && children.size() == 3) {
                        String name = children.get(1).text();
                        if (name != null && !name.isEmpty()) {
                            genrePOJO.setName(name);
                        }

                        String desc = children.last().text();
                        if (desc != null) {
                            genrePOJO.setDescription(desc);
                        }
                    }


                    if (!genrePOJO.isNull()) result.add(genrePOJO);
                }
            }
        }
        return result;
    }
}
