package com.fanok.audiobooks.search;

import com.fanok.audiobooks.Consts;
import com.fanok.audiobooks.Url;

/** The sites the app scrapes: one row of data per site, used by search and listings instead of {@code url.contains}. */
public enum Provider {
    KNIGAVUHE(Url.SERVER, "search_kniga_v_uhe", Consts.SOURCE_KNIGA_V_UHE),
    IZIBUK(Url.SERVER_IZIBUK, "search_izibuc", Consts.SOURCE_IZI_BUK),
    ABMP3(Url.SERVER_ABMP3, "search_abmp3", Consts.SOURCE_AUDIO_BOOK_MP3),
    AKNIGA(Url.SERVER_AKNIGA, "search_abook", Consts.SOURCE_ABOOK),
    BAZA_KNIG(Url.SERVER_BAZA_KNIG, "search_baza_knig", Consts.SOURCE_BAZA_KNIG),
    KNIGOBLUD(Url.SERVER_KNIGOBLUD, "search_knigoblud", Consts.SOURCE_KNIGOBLUD),
    AUDIOBOO(Url.SERVER_AUDIOBOO, "search_audioboo", Consts.SOURCE_AUDIOBOO),
    OTRUB(Url.SERVER_OTRUB, "search_otrub", Consts.SOURCE_OTRUB),
    POLEKNIG(Url.SERVER_POLEKNIG, "search_poleknig", Consts.SOURCE_POLEKNIG);

    /** {@code https://host} without a trailing slash. */
    public final String server;
    /** Preference key that enables the site in the multi-source search. */
    public final String prefKey;
    /** The {@code Consts.SOURCE_*} id of the site. */
    public final int sourceId;

    Provider(String server, String prefKey, int sourceId) {
        this.server = server;
        this.prefKey = prefKey;
        this.sourceId = sourceId;
    }

    /** Host without {@code www.}, used to filter a mixed result list by site. */
    public String host() {
        return Url.hostOf(server);
    }

    /** The provider serving {@code url} (after {@link Url#canonical}), or null for an unknown host. */
    public static Provider ofUrl(String url) {
        String canonical = url == null ? null : Url.canonical(url);
        for (Provider p : values()) {
            if (Url.isHost(canonical, p.server)) {
                return p;
            }
        }
        return null;
    }

    /** The provider for a {@code Consts.SOURCE_*} id, or null. */
    public static Provider ofSource(int sourceId) {
        for (Provider p : values()) {
            if (p.sourceId == sourceId) {
                return p;
            }
        }
        return null;
    }
}
