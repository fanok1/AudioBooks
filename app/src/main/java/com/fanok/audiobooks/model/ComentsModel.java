package com.fanok.audiobooks.model;

import android.support.annotation.NonNull;

import com.fanok.audiobooks.pojo.ComentsPOJO;
import com.fanok.audiobooks.pojo.SubComentsPOJO;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;

import io.reactivex.Observable;

public class ComentsModel implements
        com.fanok.audiobooks.interface_pacatge.book_content.ComentsModel {


    ArrayList<ComentsPOJO> loadComentsList(String url) throws IOException {
        ArrayList<ComentsPOJO> result = new ArrayList<>();
        Document doc = Jsoup.connect(url)
                .userAgent(
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36")
                .referrer("http://www.google.com")
                .get();

        Elements comentsElements = doc.getElementsByClass("ls-comment-list js-comment-list");

        if (comentsElements.size() == 0) return result;

        for (Element comentConteiner : comentsElements.first().children()) {
            ComentsPOJO comentsPOJO = new ComentsPOJO();
            Elements imageConteiner = comentConteiner.getElementsByTag("img");
            if (imageConteiner.size() != 0) {
                comentsPOJO.setImage(imageConteiner.first().attr("src"));
            }
            Elements nameConteiner = comentConteiner.getElementsByClass("ls-comment-username");
            if (nameConteiner.size() != 0) {
                comentsPOJO.setName(nameConteiner.first().child(0).text());
            }

            Elements time = comentConteiner.getElementsByTag("time");
            if (time.size() != 0) {
                comentsPOJO.setDate(time.first().text());
            }

            Elements reting = comentConteiner.getElementsByClass("ls-vote-rating");
            if (reting.size() != 0) {
                comentsPOJO.setReting(reting.first().text());
            }

            Elements text = comentConteiner.getElementsByClass("ls-comment-text");
            if (text.size() != 0) {
                comentsPOJO.setText(text.first().text());
            }


            Elements children = comentConteiner.children();

            for (int i = 1; i < children.size(); i++) {
                if (children.get(i).attr("class").contains("ls-comment-wrapper")) {
                    comentsPOJO.setChildComents(getChildCOments(children.get(i)));
                }
            }
            if (!comentsPOJO.isEmty()) {
                result.add(comentsPOJO);
            }

        }
        return result;
    }

    private ArrayList<SubComentsPOJO> getChildCOments(Element child) {
        ArrayList<SubComentsPOJO> result = new ArrayList<>();
        SubComentsPOJO subComentsPOJO = new SubComentsPOJO();
        Elements imageConteiner = child.getElementsByTag("img");
        if (imageConteiner.size() != 0) {
            subComentsPOJO.setImage(imageConteiner.first().attr("src"));
        }
        Elements parentConteiner = child.getElementsByClass("reply-to");
        if (parentConteiner.size() != 0) {
            Element parent = parentConteiner.first().child(0);
            subComentsPOJO.setParentName(parent.text());
        }
        Elements nameConteiner = child.getElementsByClass("ls-comment-username");
        if (nameConteiner.size() != 0) {
            subComentsPOJO.setName(nameConteiner.first().child(0).text());
        }

        Elements reting = child.getElementsByClass("ls-vote-rating");
        if (reting.size() != 0) {
            subComentsPOJO.setReting(reting.first().text());
        }

        Elements time = child.getElementsByTag("time");
        if (time.size() != 0) {
            subComentsPOJO.setDate(time.first().text());
        }

        Elements text = child.getElementsByClass("ls-comment-text");
        if (text.size() != 0) {
            subComentsPOJO.setText(text.first().text());
        }

        if (!subComentsPOJO.isEmty()) {
            result.add(subComentsPOJO);
        }

        Elements elements = child.children();

        for (int i = 1; i < elements.size(); i++) {
            if (elements.get(i).attr("class").contains("ls-comment-wrapper")) {
                result.addAll(getChildCOments(elements.get(i)));
            }
        }

        return result;
    }

    @Override
    public Observable<ArrayList<ComentsPOJO>> getComents(@NonNull String url) {

        return Observable.create(observableEmitter -> {
            ArrayList<ComentsPOJO> articlesModels;
            try {
                articlesModels = loadComentsList(url);
                observableEmitter.onNext(articlesModels);
            } catch (Exception e) {
                observableEmitter.onError(e);
            } finally {
                observableEmitter.onComplete();
            }
        });
    }
}
