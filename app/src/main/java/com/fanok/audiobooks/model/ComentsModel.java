package com.fanok.audiobooks.model;


import static de.blinkt.openvpn.core.VpnStatus.waitVpnConetion;

import androidx.annotation.NonNull;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.SubComentsPOJO;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.reactivex.Observable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ComentsModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.ComentsModel {


    private ArrayList<ComentsPOJO> loadComentsList(String url) throws IOException {
        ArrayList<ComentsPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Element comentsElement = doc.getElementById("comments_list");

        if (comentsElement == null) return result;

        for (Element comentConteiner : comentsElement.children()) {
            ComentsPOJO comentsPOJO = new ComentsPOJO();
            Elements imageConteiner = comentConteiner.getElementsByTag("img");
            if (imageConteiner.size() != 0) {
                comentsPOJO.setImage(imageConteiner.first().attr("src"));
            }
            Elements nameConteiner = comentConteiner.getElementsByClass("comment_head_user");
            if (nameConteiner.size() != 0) {
                comentsPOJO.setName(nameConteiner.first().text());
            }

            Elements time = comentConteiner.getElementsByClass("comment_head_time");
            if (time.size() != 0) {
                comentsPOJO.setDate(time.first().text());
            }

            Elements reting = comentConteiner.getElementsByClass("comment_head_votes_count");
            if (reting.size() != 0) {
                comentsPOJO.setReting(reting.first().text());
            }

            Elements text = comentConteiner.getElementsByClass("comment_body");
            if (text.size() != 0) {
                comentsPOJO.setText(text.first().text());
            }


            Elements children = comentConteiner.children();

            for (int i = 1; i < children.size(); i++) {
                if (children.get(i).attr("class").contains("comments_list")) {
                    comentsPOJO.setChildComents(
                            getChildCOments(children.get(i), comentsPOJO.getName()));
                }
            }
            if (!comentsPOJO.isEmty()) {
                result.add(comentsPOJO);
            }

        }
        return result;
    }

    private ArrayList<SubComentsPOJO> getChildCOments(Element child, String parent) {
        ArrayList<SubComentsPOJO> result = new ArrayList<>();
        SubComentsPOJO subComentsPOJO = new SubComentsPOJO();
        Elements imageConteiner = child.getElementsByTag("img");
        if (imageConteiner.size() != 0) {
            subComentsPOJO.setImage(imageConteiner.first().attr("src"));
        }
        subComentsPOJO.setParentName(parent);

        Elements nameConteiner = child.getElementsByClass("comment_head_user");
        if (nameConteiner.size() != 0) {
            subComentsPOJO.setName(nameConteiner.first().text());
        }

        Elements reting = child.getElementsByClass("comment_head_votes_count");
        if (reting.size() != 0) {
            subComentsPOJO.setReting(reting.first().text());
        }

        Elements time = child.getElementsByClass("comment_head_time");
        if (time.size() != 0) {
            subComentsPOJO.setDate(time.first().text());
        }

        Elements text = child.getElementsByClass("comment_body");
        if (text.size() != 0) {
            subComentsPOJO.setText(text.first().text());
        }

        if (!subComentsPOJO.isEmty()) {
            result.add(subComentsPOJO);
        }

        Elements elements = child.children();

        for (int i = 1; i < elements.size(); i++) {
            if (elements.get(i).attr("class").contains("comments_list")) {
                result.addAll(getChildCOments(elements.get(i), subComentsPOJO.getName()));
            }
        }

        return result;
    }


    @Override
    public Observable<ArrayList<ComentsPOJO>> getComents(@NonNull String url) {

        return Observable.create(observableEmitter -> {
            waitVpnConetion();
            ArrayList<ComentsPOJO> articlesModels;
            try {
                if (url.contains("knigavuhe.org")) {
                    articlesModels = loadComentsList(url);
                } else if (url.contains("audiobook-mp3.com")) {
                    articlesModels = loadComentsListABMP3(url);
                } else if (url.contains("akniga.org")) {
                    articlesModels = loadComentsListAbook(url);
                } else if (url.contains("baza-knig.ru")) {
                    articlesModels = loadComentsListBazaKnig(url);
                } else {
                    articlesModels = new ArrayList<>();
                }
                observableEmitter.onNext(articlesModels);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }

    private SubComentsPOJO getChildCOmentsABMP3(Element comentConteiner, String parent) {

        SubComentsPOJO comentsPOJO = new SubComentsPOJO();
        Elements imageConteiner = comentConteiner.getElementsByTag("img");
        if (imageConteiner != null && imageConteiner.size() != 0) {
            comentsPOJO.setImage(imageConteiner.first().attr("src"));
        }
        Elements nameConteiner = comentConteiner.getElementsByClass("comment-author-name");
        if (nameConteiner != null && nameConteiner.size() != 0) {
            Element child = nameConteiner.first().child(0);
            if (child != null) {
                comentsPOJO.setName(child.text());
            }

            Element time = nameConteiner.first().child(1);
            if (time != null) {
                comentsPOJO.setDate(time.text());
            }
        }

        Elements text = comentConteiner.getElementsByClass("comment-body");
        if (text.size() != 0) {
            comentsPOJO.setText(text.first().text());
        }

        comentsPOJO.setParentName(parent);

        return comentsPOJO;


    }

    private ArrayList<ComentsPOJO> loadComentsListABMP3(String url) throws IOException {
        ArrayList<ComentsPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Element comentsElement = doc.getElementById("w0");
        if (comentsElement == null) {
            return result;
        }

        Elements empty = comentsElement.getElementsByClass("empty");
        if (empty != null && empty.size() != 0) {
            return result;
        }

        for (Element comentConteiner : comentsElement.children()) {
            ComentsPOJO comentsPOJO = new ComentsPOJO();
            Elements imageConteiner = comentConteiner.getElementsByTag("img");
            if (imageConteiner != null && imageConteiner.size() != 0) {
                comentsPOJO.setImage(imageConteiner.first().attr("src"));
            }
            Elements nameConteiner = comentConteiner.getElementsByClass("comment-author-name");
            if (nameConteiner != null && nameConteiner.size() != 0) {
                Element child = nameConteiner.first().child(0);
                if (child != null) {
                    comentsPOJO.setName(child.text());
                }

                Element time = nameConteiner.first().child(1);
                if (time != null) {
                    comentsPOJO.setDate(time.text());
                }
            }

            Elements text = comentConteiner.getElementsByClass("comment-body");
            if (text.size() != 0) {
                comentsPOJO.setText(text.first().text());
            }

            Elements childrenConteiner = comentConteiner.getElementsByClass("children");
            if (childrenConteiner != null && childrenConteiner.size() != 0) {
                Elements children = childrenConteiner.first().getElementsByClass("item");
                if (children != null) {
                    ArrayList<SubComentsPOJO> subComentsPOJOS = new ArrayList<>();
                    for (int i = 0; i < children.size(); i++) {
                        SubComentsPOJO subComentsPOJO = getChildCOmentsABMP3(children.get(i), comentsPOJO.getName());
                        if (!subComentsPOJO.isEmty()) {
                            subComentsPOJOS.add(subComentsPOJO);
                        }
                    }
                    if (subComentsPOJOS.size() != 0) {
                        comentsPOJO.setChildComents(subComentsPOJOS);
                    }
                }
            }

            if (!comentsPOJO.isEmty()) {
                result.add(comentsPOJO);
            }

        }
        return result;
    }

    private ArrayList<ComentsPOJO> loadComentsListAbook(String url) throws IOException {
        ArrayList<ComentsPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(Consts.USER_AGENT)
                .referrer("http://www.google.com")
                .sslSocketFactory(Consts.socketFactory())
                .get();

        Element comentsElement = doc.getElementById("comments");
        if (comentsElement != null) {
            Elements elements = comentsElement.getElementsByClass("caption--inline js-comments-title");
            if (elements != null && elements.size() != 0) {
                String text = elements.first().text();
                if (text != null) {
                    if (text.equals("Нет комментариев")) {
                        return result;
                    }
                    Elements comentsList = comentsElement.getElementsByClass("comments__block js-comment-list");
                    if (comentsList != null && comentsList.size() != 0) {
                        for (Element comentConteiner : comentsList.first().children()) {
                            ComentsPOJO comentsPOJO = new ComentsPOJO();
                            Element conteiner = comentConteiner.child(0);
                            if (conteiner != null) {
                                Elements comentsText = conteiner.getElementsByClass("comments__block--item--comment");
                                if (comentsText != null && comentsText.size() != 0) {
                                    comentsPOJO.setText(comentsText.first().text());
                                }

                                Elements autorInfos = conteiner.getElementsByClass("comments__block--item-content");
                                if (autorInfos != null && autorInfos.size() != 0) {
                                    Element autorInfo = autorInfos.first();
                                    Elements imageConteiner = autorInfo.getElementsByTag("img");
                                    if (imageConteiner != null && imageConteiner.size() != 0) {
                                        comentsPOJO.setImage(imageConteiner.first().attr("data-src"));
                                    }

                                    Elements nameConteiner = autorInfo
                                            .getElementsByClass("comments__block--item--name");
                                    if (nameConteiner != null && nameConteiner.size() != 0) {
                                        comentsPOJO.setName(nameConteiner.first().ownText());
                                    }

                                    Elements time = autorInfo.getElementsByTag("time");
                                    if (time != null && time.size() != 0) {
                                        comentsPOJO.setDate(time.first().text());
                                    }

                                    int up = 0;
                                    int down = 0;

                                    Elements upElements = autorInfo.getElementsByClass("js-vote-rating-up");
                                    if (upElements != null && upElements.size() != 0) {
                                        text = upElements.first().text();
                                        if (text != null && !text.isEmpty()) {
                                            up = Integer.parseInt(text);
                                        }
                                    }

                                    Elements downElements = autorInfo.getElementsByClass("js-vote-rating-down");
                                    if (downElements != null && downElements.size() != 0) {
                                        text = downElements.first().text();
                                        if (text != null && !text.isEmpty()) {
                                            down = Integer.parseInt(text);
                                        }
                                    }

                                    int raiting = up - down;
                                    if (raiting != 0) {
                                        comentsPOJO.setReting(String.valueOf(raiting));
                                    }

                                }

                                Elements childrenConteiner = comentConteiner.getElementsByClass("has-parent");
                                if (childrenConteiner != null) {
                                    ArrayList<SubComentsPOJO> arrayList = new ArrayList<>();
                                    for (Element child : childrenConteiner) {
                                        SubComentsPOJO subComentsPOJO = new SubComentsPOJO();
                                        conteiner = child.child(0);
                                        if (conteiner != null) {
                                            comentsText = conteiner
                                                    .getElementsByClass("comments__block--item--comment");
                                            if (comentsText != null && comentsText.size() != 0) {
                                                subComentsPOJO.setText(comentsText.first().text());
                                            }

                                            autorInfos = conteiner
                                                    .getElementsByClass("comments__block--item-content");
                                            if (autorInfos != null && autorInfos.size() != 0) {
                                                Element autorInfo = autorInfos.first();
                                                Elements imageConteiner = autorInfo.getElementsByTag("img");
                                                if (imageConteiner != null && imageConteiner.size() != 0) {
                                                    subComentsPOJO.setImage(imageConteiner.first().attr("data-src"));
                                                }

                                                Elements nameConteiner = autorInfo
                                                        .getElementsByClass("comments__block--item--name");
                                                if (nameConteiner != null && nameConteiner.size() != 0) {
                                                    subComentsPOJO.setName(nameConteiner.first().ownText());
                                                }

                                                Elements time = autorInfo.getElementsByTag("time");
                                                if (time != null && time.size() != 0) {
                                                    subComentsPOJO.setDate(time.first().text());
                                                }

                                                int up = 0;
                                                int down = 0;

                                                Elements upElements = autorInfo
                                                        .getElementsByClass("js-vote-rating-up");
                                                if (upElements != null && upElements.size() != 0) {
                                                    text = upElements.first().text();
                                                    if (text != null && !text.isEmpty()) {
                                                        up = Integer.parseInt(text);
                                                    }
                                                }

                                                Elements downElements = autorInfo
                                                        .getElementsByClass("js-vote-rating-down");
                                                if (downElements != null && downElements.size() != 0) {
                                                    text = downElements.first().text();
                                                    if (text != null && !text.isEmpty()) {
                                                        down = Integer.parseInt(text);
                                                    }
                                                }

                                                int raiting = up - down;
                                                if (raiting != 0) {
                                                    subComentsPOJO.setReting(String.valueOf(raiting));
                                                }

                                                Elements replyto = conteiner.getElementsByClass("replyto");
                                                if (replyto != null && replyto.size() != 0) {
                                                    text = replyto.first().text();
                                                    if (text != null && !text.isEmpty()) {
                                                        subComentsPOJO.setParentName(text);
                                                    }
                                                }

                                                if (!subComentsPOJO.isEmty()) {
                                                    arrayList.add(subComentsPOJO);
                                                }

                                            }
                                        }

                                    }

                                    if (!arrayList.isEmpty()) {
                                        comentsPOJO.setChildComents(arrayList);
                                    }

                                }

                                if (!comentsPOJO.isEmty()) {
                                    result.add(comentsPOJO);
                                }
                            }

                        }


                    }
                }
            }

        }

        return result;
    }

    private ArrayList<ComentsPOJO> loadComentsListBazaKnig(String url) throws IOException {
        ArrayList<ComentsPOJO> result = new ArrayList<>();
        int page = 0;
        url = url.substring(url.lastIndexOf("/") + 1);
        url = url.substring(0, url.indexOf("-"));
        String id = url;
        String baseUrl
                = "https://baza-knig.ru/engine/ajax/controller.php?mod=comments&cstart=<page>&news_id=<bookId>&skin=knigi-pk&massact=disable";
        while (true) {
            page++;
            Connection connection = Jsoup
                    .connect(baseUrl.replace("<page>", String.valueOf(page)).replace("<bookId>", id))
                    .userAgent(Consts.USER_AGENT)
                    .referrer("http://www.google.com")
                    .sslSocketFactory(Consts.socketFactory())
                    .ignoreContentType(true);

            if (!Consts.getBazaKnigCookies().isEmpty()) {
                connection.cookie("PHPSESSID", Consts.getBazaKnigCookies());
            }

            String text = connection.execute().body();
            JsonElement json = JsonParser.parseString(text.replaceAll("\\n", "").replaceAll("\\t", ""));
            if (json.isJsonObject()) {
                String comentsList = json.getAsJsonObject().get("comments").getAsString();
                if (comentsList.isEmpty()) {
                    break;
                } else {
                    Document doc = Jsoup.parse(comentsList);
                    Elements elements = doc.body().children();
                    for (Element element : elements) {
                        ComentsPOJO comentsPOJO = new ComentsPOJO();
                        Elements imgConteiner = element.getElementsByClass("comm-ava");
                        if (imgConteiner != null && imgConteiner.size() > 0) {
                            Elements img = imgConteiner.first().getElementsByTag("img");
                            if (img != null && img.size() > 0) {
                                String src = img.first().attr("src");
                                if (src != null && !src.isEmpty()) {
                                    comentsPOJO.setImage(src);
                                }
                            }

                            Elements commInfo = element.getElementsByClass("comm-info");
                            if (commInfo != null && commInfo.size() > 0) {
                                Elements b = commInfo.first().getElementsByTag("b");
                                if (b != null && b.size() > 0) {
                                    String name = b.first().ownText();
                                    if (name != null && !name.isEmpty()) {
                                        comentsPOJO.setName(name);
                                    }
                                }
                                String date = commInfo.first().ownText();
                                if (date != null && !date.isEmpty()) {
                                    comentsPOJO.setDate(date);
                                }
                            }
                        }
                        Elements comText = element.getElementsByClass("comm-text");
                        if (comText != null && comText.size() != 0) {
                            Element parent = comText.first().child(0);
                            if (parent != null) {
                                String textComent = parent.ownText();
                                if (textComent != null && !textComent.isEmpty()) {
                                    comentsPOJO.setText(textComent);
                                }
                                Elements quoteTitle = parent.getElementsByClass("title_quote");
                                if (quoteTitle != null && quoteTitle.size() > 0) {
                                    String name = quoteTitle.first().text();
                                    if (name != null && !name.isEmpty()) {
                                        comentsPOJO.setQuoteName(name.replace("Цитата: ", ""));
                                    }
                                }
                                Elements quote = parent.getElementsByClass("quote");
                                if (quote != null && quote.size() > 0) {
                                    String quoteText = quote.first().text();
                                    if (quoteText != null && !quoteText.isEmpty()) {
                                        comentsPOJO.setQuoteText(quoteText);
                                    }
                                }
                            }
                        }

                        if (!comentsPOJO.isEmty()) {
                            result.add(comentsPOJO);
                        }
                    }
                }
            } else {
                break;
            }
        }
        Collections.reverse(result);
        return result;

    }
}
