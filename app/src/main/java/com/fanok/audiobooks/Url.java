package com.fanok.audiobooks;

public class Url {
    public static final String SERVER = "https://knigavuhe.org";
    public static final String INDEX = SERVER + "/new/?page=";
    public static final String SECTIONS = SERVER + "/genres/";
    public static final String AUTHORS = SERVER + "/authors/";
    public static final String PERFORMERS = SERVER + "/readers/";
    public static final String NEW_BOOK = SERVER + "/new/?page=";
    public static final String BEST_TODAY = SERVER + "/popular/?w=today&page=";
    public static final String BEST_WEEK = SERVER + "/popular/?w=week&page=";
    public static final String BEST_MONTH = SERVER + "/popular/?w=month&page=";
    public static final String BEST_ALL_TIME = SERVER + "/popular/?w=alltime&page=";
    public static final String RATING_TODATY = SERVER + "/rating/?w=today&page=";
    public static final String RATING_WEEK = SERVER + "/rating/?w=week&page=";
    public static final String RATING_MONTH = SERVER + "/rating/?w=month&page=";
    public static final String RATING_ALL_TIME = SERVER + "/rating/?w=alltime&page=";

    public static final String SERVER_IZIBUK = "https://izib.uk";

    public static final String INDEX_IZIBUK = SERVER_IZIBUK + "/?p=";
    public static final String SECTIONS_IZIBUK = SERVER_IZIBUK + "/genres?p=";
    public static final String AUTHORS_IZIBUK = SERVER_IZIBUK + "/authors?p=";
    public static final String PERFORMERS_IZIBUK = SERVER_IZIBUK + "/readers?p=";


    public static final String SERVER_ABMP3 = "https://audiobook-mp3.com";
    public static final String INDEX_ABMP3 = SERVER_ABMP3 + "/?page=";
    public static final String SECTIONS_ABMP3 = SERVER_ABMP3 + "/genres";
    public static final String AUTHORS_ABMP3 = SERVER_ABMP3 + "/authors?page=";
    public static final String PERFORMERS_ABMP3 = SERVER_ABMP3 + "/performers?page=";


    public static final String SERVER_AKNIGA = "https://akniga.org";

    public static final String INDEX_AKNIGA = SERVER_AKNIGA + "/index/page<page>/";
    public static final String SECTIONS_AKNIGA = SERVER_AKNIGA + "/sections/";
    public static final String AUTHORS_AKNIGA = SERVER_AKNIGA + "/authors/page<page>/";
    public static final String PERFORMERS_AKNIGA = SERVER_AKNIGA + "/performers/page<page>/";

    public static final String NEW_BOOK_AKNIGA = SERVER_AKNIGA + "/index/page<page>/";
    public static final String BEST_TODAY_AKNIGA = SERVER_AKNIGA + "/index/top/page<page>/?period=1";
    public static final String BEST_WEEK_AKNIGA = SERVER_AKNIGA + "/index/top/page<page>/?period=7";
    public static final String BEST_MONTH_AKNIGA = SERVER_AKNIGA + "/index/top/page<page>/?period=30";
    public static final String BEST_ALL_TIME_AKNIGA = SERVER_AKNIGA + "/index/top/page<page>/?period=all";
    public static final String RATING_TODATY_AKNIGA = SERVER_AKNIGA + "/index/discussed/page<page>/?period=1";
    public static final String RATING_WEEK_AKNIGA = SERVER_AKNIGA + "/index/discussed/page<page>/?period=7";
    public static final String RATING_MONTH_AKNIGA = SERVER_AKNIGA + "/index/discussed/page<page>/?period=30";
    public static final String RATING_ALL_TIME_AKNIGA = SERVER_AKNIGA + "/index/discussed/page<page>/?period=all";


    public static final String SERVER_BAZA_KNIG = "https://baza-knig.top";
    public static final String INDEX_BAZA_KNIG = SERVER_BAZA_KNIG + "/page/";
    public static final String SECTIONS_BAZA_KNIG = SERVER_BAZA_KNIG;
    public static final String NEW_BOOK_BAZA_KNIG = SERVER_BAZA_KNIG + "/page/";
    public static final String BEST_BAZA_KNIG = SERVER_BAZA_KNIG + "/f/sort=news_read/order=desc/page/";
    public static final String RATING_BAZA_KNIG = SERVER_BAZA_KNIG + "/f/sort=rating/order=desc/page/";
    public static final String COMENTS_BAZA_KNIG = SERVER_BAZA_KNIG + "/f/sort=comm_num/order=desc/page/";
    public static final String YEARS_BAZA_KNIG = SERVER_BAZA_KNIG + "/f/sort=d.god/order=desc/page/";


    public static final String SERVER_KNIGOBLUD = "https://www.knigoblud.club";
    public static final String INDEX_KNIGOBLUD = SERVER_KNIGOBLUD;
    public static final String SECTIONS_KNIGOBLUD = SERVER_KNIGOBLUD + "/genres";
}
