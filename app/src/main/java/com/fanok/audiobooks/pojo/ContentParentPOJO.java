package com.fanok.audiobooks.pojo;

import androidx.annotation.Nullable;

/** Fields shared by comments and replies; setters accept null (stored as "") so a missing element never aborts a list. */
public abstract class ContentParentPOJO {

    private String quoteName = "";

    private String quoteText = "";

    private String image = "";

    private String name = "";

    private String date = "";

    private String reting = "";

    private String text = "";

    static String orEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    public String getImage() {
        return image;
    }

    public void setImage(@Nullable String image) {
        this.image = orEmpty(image);
    }

    public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = orEmpty(name);
    }

    public String getDate() {
        return date;
    }

    public void setDate(@Nullable String date) {
        this.date = orEmpty(date);
    }

    public String getReting() {
        return reting;
    }

    public void setReting(@Nullable String reting) {
        this.reting = orEmpty(reting);
    }

    public String getText() {
        return text;
    }

    public void setText(@Nullable String text) {
        this.text = orEmpty(text);
    }

    public String getQuoteName() {
        return quoteName;
    }

    public void setQuoteName(@Nullable final String quoteName) {
        this.quoteName = orEmpty(quoteName);
    }

    public String getQuoteText() {
        return quoteText;
    }

    public void setQuoteText(@Nullable final String quoteText) {
        this.quoteText = orEmpty(quoteText);
    }

    /** A comment is its text: author and avatar are optional (the adapters show what is there). */
    public boolean isEmty() {
        return getText().isEmpty();
    }
}
