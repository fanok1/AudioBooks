package com.fanok.audiobooks.pojo;

public class ParentControlPOJO {

    private String name;

    private String sorceName;

    private String url;

    public ParentControlPOJO() {
    }

    public ParentControlPOJO(final String sorceName, final String name, final String url) {
        this.sorceName = sorceName;
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getSorceName() {
        return sorceName;
    }

    public void setSorceName(final String sorceName) {
        this.sorceName = sorceName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }
}
