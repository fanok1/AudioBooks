package com.fanok.audiobooks;

import java.util.regex.Pattern;

public class Consts {
    public static final Pattern REGEXP_URL = Pattern.compile(
            "^https?://.+\\..+$");
    public static final Pattern REGEXP_URL_PHOTO = Pattern.compile(
            "^https?://.+\\.((jpg)|(png)|(jpeg))$");
    public static final Pattern REGEXP_RETING = Pattern.compile("^([+-]\\d+)|0$");

}
