package com.fanok.audiobooks.model;


import androidx.annotation.NonNull;
import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.SubComentsPOJO;
import io.reactivex.Observable;
import java.io.IOException;
import java.util.ArrayList;
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
            ArrayList<ComentsPOJO> articlesModels;
            try {
                if (url.contains("knigavuhe.org")) {
                    articlesModels = loadComentsList(url);
                } else if (url.contains("audiobook-mp3.com")) {
                    articlesModels = loadComentsListABMP3(url);
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
}
