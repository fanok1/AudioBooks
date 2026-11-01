package com.fanok.audiobooks.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.fanok.audiobooks.R;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The app's own genres: every site's genre name maps onto one of them ({@link #of}), so parental control and the
 * "favorite genres" order see "Ужасы, мистика" on one site and "Мистика и ужасы" on another as the same genre.
 * {@link #name()} is persisted (parental allow-list, genre picks): never rename a constant.
 * <p>
 * {@link #keys} is the matcher's data ({@link GenreMatcher}), space-separated and folded like the names:
 * {@code stem} — a word starting with it names the genre; {@code ~stem} — a generic head ("роман", "литература")
 * that an adjective before it may refine ("любовный роман"); {@code a+b} — two words in a row.
 */
public enum Genre {
    SF_FANTASY(R.string.genre_sf_fantasy, "фантаст фантаз фэнтез нф панк космоопер хроноопер антиутоп утопи "
            + "бояр дозор этногенез артуриан мифолог магическ+реализм альтернатив+мир warhammer eve fantasy "
            + "fantastic sci+fi science+fiction science+fantasy punk dystopi utopi myth legend"),
    LITRPG(R.string.genre_litrpg, "литрпг реалрпг рпг litrpg realrpg rpg gamelit"),
    POPADANCY(R.string.genre_popadancy, "попадан исекай isekai"),
    POSTAPOCALYPSE(R.string.genre_postapocalypse, "постапок апокалип сталкер stalker стикс stiks метро metro "
            + "технотьм выживан postapoc apocalyp survival"),
    ALT_HISTORY(R.string.genre_alt_history, "альтернатив+истор альтистор alternat+histor"),
    ROMANTIC_FANTASY(R.string.genre_romantic_fantasy, "любовн+фэнтез любовн+фантаст романтич+фэнтез "
            + "романтич+фантаст лффр лфр romantic+fantasy romance+fantasy"),
    DETECTIVE(R.string.genre_detective, "детектив триллер криминал шпион полицейск нуар остросюжет "
            + "detective thriller crime mystery mysteri noir suspense spy espion"),
    ACTION(R.string.genre_action, "боевик экшн action"),
    TRUE_CRIME(R.string.genre_true_crime, "крайм трукрайм true+crime"),
    HORROR(R.string.genre_horror, "ужас хоррор мистик мистич готи зомби вампир привиден сплаттер треш страшил "
            + "horror supernatural ghost gothic zombie vampire"),
    ADVENTURE(R.string.genre_adventure, "приключ вестерн пират путешеств морск adventure western travel "
            + "pirate nautic"),
    ROMANCE(R.string.genre_romance, "любовн романт сентимент мелодрам romance romantic love"),
    EROTICA(R.string.genre_erotica, "эроти взросл 18 erotic sex секс"),
    PROSE(R.string.genre_prose, "~роман ~проз ~литератур ~повест ~рассказ ~истории ~произведен ~fiction ~literatur "
            + "~novel ~stories ~story новелл драм трагед сага деревн реализм постмодерн сюрреал психодел филгуд "
            + "saga tragedy feelgood drama"),
    CLASSIC(R.string.genre_classic, "~класси ~classic"),
    HUMOR(R.string.genre_humor, "юмор сатир комеди комич анекдот иронич humor humour comedy satire funny"),
    KIDS(R.string.genre_kids, "детей дети детям детск ребен ребят малыш сказк сказоч стишк колыбел "
            + "школьн children child kids kid fairy"),
    TEENS(R.string.genre_teens, "подрост юношеск young+adult teen juvenile"),
    POETRY(R.string.genre_poetry, "поэз поэм стих лирик poem poetry verse"),
    PERFORMANCE(R.string.genre_performance, "спектак постанов театр литературн+чтен play audio+drama "
            + "audiodrama dramatic+reading"),
    RANOBE(R.string.genre_ranobe, "ранобэ манга манхва аниме light+novel ranobe manga anime"),
    HISTORY(R.string.genre_history, "истор культуролог античн средневек histor antiqu"),
    WAR(R.string.genre_war, "воен войн вов war military"),
    BIOGRAPHY(R.string.genre_biography, "биограф мемуар жзл воспоминан biograph memoir"),
    SCIENCE(R.string.genre_science, "наук научн научпоп познават документальн публицист технолог science nature "
            + "nonfiction non+fiction documentar"),
    PSYCHOLOGY(R.string.genre_psychology, "психолог философ саморазвит личностн мотивац воспитан "
            + "семейн+жизн psycholog philosoph family self+help selfhelp self+improvement"),
    BUSINESS(R.string.genre_business, "бизнес эконом финанс маркетинг менеджмент карьер предприним инвест "
            + "business econom financ marketing management"),
    HEALTH(R.string.genre_health, "медицин здоров health medic fitness"),
    EDUCATION(R.string.genre_education, "обучен учебн лекци образован ораторск риторик education learning "
            + "lecture textbook"),
    FOREIGN(R.string.genre_foreign, "иностран foreign"),
    RELIGION(R.string.genre_religion, "религ христиан православ библ богослов притч ислам religio christian "
            + "bible theolog"),
    ESOTERIC(R.string.genre_esoteric, "эзотер оккульт астролог мистиц esoteric occult astrolog"),
    PODCASTS(R.string.genre_podcasts, "подкаст интервью podcast interview"),
    /** "Разное" as a site names it; a name nothing matches has no genre at all. */
    OTHER(R.string.genre_other, "разн проч друг misc other");

    @StringRes
    public final int title;
    final String keys;

    Genre(@StringRes int title, String keys) {
        this.title = title;
        this.keys = keys;
    }

    /**
     * The genre of a site's genre name, for preferences: erotica wherever it is named, else the first phrase's
     * ("Детективы, триллеры, боевики" → {@link #DETECTIVE}); null when nothing matches.
     */
    @Nullable
    public static Genre of(@Nullable String name) {
        List<Genre> parts = GenreMatcher.INSTANCE.parts(name);
        return parts.isEmpty() ? null : parts.get(0);
    }

    /**
     * Every genre a site's genre name names, for permissions: {@link #DETECTIVE} and {@link #ACTION} for "Детективы,
     * триллеры, боевики"; empty when nothing matches.
     */
    @NonNull
    public static Set<Genre> allOf(@Nullable String name) {
        Set<Genre> genres = EnumSet.noneOf(Genre.class);
        genres.addAll(GenreMatcher.INSTANCE.parts(name));
        return genres;
    }
}
