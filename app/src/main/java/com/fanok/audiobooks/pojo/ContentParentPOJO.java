package com.fanok.audiobooks.pojo;

import androidx.annotation.NonNull;

public abstract class ContentParentPOJO {

    private String quoteName = "";

    private String quoteText = "";

    private String image = "";

    private String name = "";

    private String date = "";

    private String reting = "";

    private String text = "";

    public String getImage() {
        return image;
    }

    public void setImage(@NonNull String image) {
        this.image = image;
    }

    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    public String getDate() {
        return date;
    }

    public void setDate(@NonNull String date) {
        this.date = date;
    }

    public String getReting() {
        return reting;
    }

    public void setReting(@NonNull String reting) {
        this.reting = reting;
    }

    public String getText() {
        return text;
    }

    public void setText(@NonNull String text) {
        this.text = text;
    }

    public String getQuoteName() {
        return quoteName;
    }

    public void setQuoteName(@NonNull final String quoteName) {
        this.quoteName = quoteName;
    }

    public String getQuoteText() {
        return quoteText;
    }

    public void setQuoteText(@NonNull final String quoteText) {
        this.quoteText = quoteText;
    }

    public boolean isEmty() {
        return getName().isEmpty() || getImage().isEmpty() || getText().isEmpty();
    }
}
