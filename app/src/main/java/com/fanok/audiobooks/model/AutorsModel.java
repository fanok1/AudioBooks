package com.fanok.audiobooks.model;

import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.Url;
import com.fanok.audiobooks.pojo.GenrePOJO;
import java.io.IOException;
import java.util.ArrayList;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class AutorsModel extends GenreModel {

    private boolean end = false;

    @Override
    protected ArrayList<GenrePOJO> loadBooksList(String url, int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
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
                .sslSocketFactory(Consts.socketFactory())
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

                    if (!genrePOJO.isNull()) {
                        result.add(genrePOJO);
                    }
                }
            }
        }
        return result;
    }

    @Override
    protected ArrayList<GenrePOJO> loadBooksListABMP3(String url, int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Elements autors = doc.getElementsByClass("authors_list");
        if (autors == null || autors.size() == 0) {

            Elements parent = doc.getElementsByClass("b-posts");

            if (parent != null && parent.size() != 0) {

                Elements pagesConteiner = doc.getElementsByClass("pagination");
                if (pagesConteiner.size() != 0) {
                    Elements pagesElements = pagesConteiner.first().children();
                    if (pagesElements.size() != 0) {
                        Element lastPageElement = pagesElements.get(pagesElements.size() - 2);
                        Element aLastPage = lastPageElement.child(0);
                        if (aLastPage != null) {
                            int lastPage = Integer.parseInt(aLastPage.text());
                            if (lastPage < page) {
                                throw new NullPointerException();
                            }
                        } else {
                            throw new NullPointerException();
                        }
                    } else {
                        throw new NullPointerException();
                    }
                } else if (page > 1) {
                    throw new NullPointerException();
                }

                Elements list = parent.first().children();
                if (list != null && list.size() > 0) {
                    for (Element item : list) {
                        GenrePOJO genrePOJO = new GenrePOJO();
                        Elements titles = item.getElementsByClass("title");
                        if (titles != null && titles.size() != 0) {
                            Elements aGenre = titles.first().getElementsByTag("a");
                            if (aGenre != null && aGenre.size() != 0) {
                                genrePOJO.setUrl(
                                        Url.SERVER_ABMP3 + aGenre.first().attr("href") + "?page=");
                                String text = aGenre.first().text();
                                if (!text.isEmpty()) {
                                    genrePOJO.setName(text);
                                }
                            }
                        }
                        Elements ratings = item.getElementsByClass("rating");
                        if (ratings != null && ratings.size() != 0) {
                            genrePOJO.setDescription(
                                    ratings.first().text().trim().replace("кни", " кни"));
                        }

                        if (!genrePOJO.isNull()) {
                            result.add(genrePOJO);
                        }
                    }

                }
            }
        } else {
            Elements chars = doc.getElementsByClass("alphabet");
            if (chars != null && chars.size() != 0) {
                Elements rus = chars.first().child(chars.first().childrenSize() - 1).children();
                if (rus != null && rus.size() != 0) {
                    if (rus.size() >= page) {
                        String text = Url.SERVER_ABMP3 + rus.get(page - 1).attr("href");
                        result.addAll(getGenre(text));
                    } else {
                        int sizeRus = rus.size();
                        Elements eng = chars.first().child(0).children();
                        if (eng.size() >= page - sizeRus) {
                            String text = Url.SERVER_ABMP3 + eng.get(page - sizeRus - 1).attr("href");
                            result.addAll(getGenre(text));
                        }
                    }
                }
            }
        }

        return result;
    }

    @Override
    protected ArrayList<GenrePOJO> loadBooksListAbook(final String url, final int page) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Elements bootom = doc.getElementsByClass("page__nav");
        if (bootom != null && bootom.size() != 0) {
            Elements nextButton = bootom.get(0).getElementsByClass("page__nav--next");
            if (!(nextButton != null && nextButton.size() != 0)) {
                if (end) {
                    throw new NullPointerException();
                }
                end = true;
            }
        } else if (page > 1) {
            throw new NullPointerException();
        }

        Elements items = doc.getElementsByClass("table-authors");
        if (items != null && items.size() != 0) {
            Elements tbody = items.first().getElementsByTag("tbody");
            if (tbody != null && tbody.size() != 0) {
                Elements list = tbody.first().children();
                if (list != null && list.size() > 0) {
                    for (Element item : list) {
                        GenrePOJO genrePOJO = new GenrePOJO();
                        Elements titles = item.getElementsByClass("name-obj");
                        if (titles != null && titles.size() != 0) {
                            Elements aGenre = titles.first().getElementsByTag("a");
                            if (aGenre != null && aGenre.size() != 0) {
                                genrePOJO.setUrl(aGenre.first().attr("href") + "/page<page>/");
                                genrePOJO.setName(aGenre.first().text());
                            }
                        }
                        Elements description = item.getElementsByClass("description");
                        if (description != null && description.size() != 0) {
                            genrePOJO.setDescription(description.first().text().trim());
                        }

                        Elements reting = item.getElementsByClass("cell-rating");
                        if (reting != null && reting.size() != 0) {
                            genrePOJO.setReting(Integer.parseInt(reting.first().text().trim()));
                        }

                        if (!genrePOJO.isNull()) {
                            result.add(genrePOJO);
                        }
                    }
                }
            }
        }

        if (result.size() == 0) {
            throw new NullPointerException();
        }

        return result;
    }

    private ArrayList<GenrePOJO> getGenre(String url) throws IOException {
        ArrayList<GenrePOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();
        Elements parent = doc.getElementsByClass("authors_list");
        if (parent != null && parent.size() != 0) {
            Elements list = parent.first().children();
            if (list != null && list.size() > 0) {
                for (Element item : list) {
                    GenrePOJO genrePOJO = new GenrePOJO();
                    Elements titles = item.getElementsByClass("title");
                    if (titles != null && titles.size() != 0) {
                        Elements aGenre = titles.first().getElementsByTag("a");
                        if (aGenre != null && aGenre.size() != 0) {
                            genrePOJO.setUrl(
                                    Url.SERVER_ABMP3 + aGenre.first().attr("href") + "?page=");
                            String text = aGenre.first().text();
                            if (!text.isEmpty()) {
                                genrePOJO.setName(text);
                            }
                        }
                    }
                    Elements ratings = item.getElementsByClass("rating");
                    if (ratings != null && ratings.size() != 0) {
                        genrePOJO.setDescription(
                                ratings.first().text().trim().replace("кни", " кни"));
                    }

                    if (!genrePOJO.isNull()) {
                        result.add(genrePOJO);
                    }
                }

            }
        }
        return result;
    }
}
