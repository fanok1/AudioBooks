package com.fanok.audiobooks;

import java.util.regex.Pattern;

/** The source took the book down at a rights holder's request: its page is there, the player is not. */
public class BookRemovedException extends IllegalStateException {

    /** "Удалено правообладателем", "Удалена по жалобе правообладателя", "заблокирована по требованию …". */
    private static final Pattern NOTICE = Pattern.compile(
            "(?iu)(удален|заблокирован|недоступн|ограничен|снят|изъят)[а-я]*\\s+(по\\s+[а-я]+\\s+)?правообладател");

    public BookRemovedException(String url) {
        super(url);
    }

    /** Whether the page text carries a takedown notice. */
    public static boolean isNotice(String pageText) {
        return NOTICE.matcher(pageText).find();
    }
}
