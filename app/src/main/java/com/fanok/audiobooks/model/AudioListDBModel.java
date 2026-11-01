package com.fanok.audiobooks.model;

import android.content.Context;
import androidx.annotation.NonNull;
import com.fanok.audiobooks.interface_pacatge.books.AudioListDBHelperInterfase;
import com.fanok.audiobooks.interface_pacatge.books.BooksDBAbstract;
import com.fanok.audiobooks.pojo.AudioListPOJO;
import com.fanok.audiobooks.room.BooksAudioDao;
import com.fanok.audiobooks.room.BooksAudioEntity;
import com.fanok.audiobooks.util.DeletionPlanner;
import com.fanok.audiobooks.util.DownloadStorageHelper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AudioListDBModel extends BooksDBAbstract implements AudioListDBHelperInterfase {

    public AudioListDBModel(Context context) {
        super(context);
    }

    @Override
    public boolean isset(@NonNull String url) {
        return getDatabase().booksAudioDao().count(url) > 0;
    }

    @Override
    public void add(@NonNull AudioListPOJO audioListPOJO) {
        BooksAudioEntity entity = new BooksAudioEntity();
        entity.urlBook = audioListPOJO.getBookUrl();
        entity.booksName = audioListPOJO.getBookName();
        entity.nameAudio = audioListPOJO.getAudioName();
        entity.urlAudio = audioListPOJO.getAudioUrl();
        entity.time = audioListPOJO.getTime();
        entity.timeStart = audioListPOJO.getTimeStart();
        entity.timeEnd = audioListPOJO.getTimeEnd();
        entity.localUri = audioListPOJO.getLocalUri();
        entity.ordinal = audioListPOJO.getOrdinal();
        getDatabase().booksAudioDao().insert(entity);
    }

    @Override
    public void remove(@NonNull String urlBook) {
        getDatabase().booksAudioDao().deleteByUrl(urlBook);
    }

    public void removeTrack(@NonNull String urlBook, @NonNull String audioUrl) {
        getDatabase().booksAudioDao().deleteTrack(urlBook, audioUrl);
    }

    /** Chapter rows of a book in list order, with their recorded exported copies. */
    @NonNull
    public List<BooksAudioEntity> getRows(@NonNull String url) {
        return getDatabase().booksAudioDao().getByBookUrl(url);
    }

    /** Records the exported copy of every chapter whose remote URL has the given Media3 download id. */
    public void setLocalUri(@NonNull String urlBook, @NonNull String downloadId, @NonNull String localUri) {
        getDatabase().runInTransaction(() -> {
            Set<String> audioUrls = new LinkedHashSet<>();
            for (BooksAudioEntity row : getRows(urlBook)) {
                if (DeletionPlanner.isRemote(row.urlAudio) && downloadId.equals(DownloadStorageHelper.downloadIdOf(row.urlAudio))) {
                    audioUrls.add(row.urlAudio);
                }
            }
            for (String audioUrl : audioUrls) {
                getDatabase().booksAudioDao().updateLocalUri(urlBook, audioUrl, localUri);
            }
        });
    }

    /**
     * Upserts the parsed chapter list of an online book in one transaction. Existing rows are matched by download id,
     * then by position, and keep local_uri and their time columns; rows no longer listed are deleted only when they
     * record no exported copy. A row kept that way leaves the ordinals of the list: it gets the unique ordinal
     * -(id)-1 (never -1, which means "not recorded"), so its download identity is its legacy URL alone and no listed
     * chapter takes its position.
     */
    @Override
    public void syncChapters(@NonNull String urlBook, @NonNull List<AudioListPOJO> chapters) {
        BooksAudioDao dao = getDatabase().booksAudioDao();
        getDatabase().runInTransaction(() -> {
            List<BooksAudioEntity> old = dao.getByBookUrl(urlBook);
            boolean[] used = new boolean[old.size()];
            // Exact remote URL first (so get.php?file=1 and ?file=2 stay apart); the same path with a changed token
            // (download id) only when the name agrees, so a copy never moves to a different chapter.
            Map<String, ArrayDeque<Integer>> byUrl = new HashMap<>();
            Map<String, ArrayDeque<Integer>> byId = new HashMap<>();
            for (int j = 0; j < old.size(); j++) {
                String url = old.get(j).urlAudio;
                if (url == null) continue;
                if (!byUrl.containsKey(url)) byUrl.put(url, new ArrayDeque<>());
                byUrl.get(url).add(j);
                if (DeletionPlanner.isRemote(url)) {
                    String id = DownloadStorageHelper.downloadIdOf(url);
                    if (!byId.containsKey(id)) byId.put(id, new ArrayDeque<>());
                    byId.get(id).add(j);
                }
            }
            BooksAudioEntity[] match = new BooksAudioEntity[chapters.size()];
            for (int i = 0; i < chapters.size(); i++) {
                String url = chapters.get(i).getAudioUrl();
                Integer j = url == null ? null : pick(byUrl.get(url), old, chapters.get(i), used, false);
                if (j == null && DeletionPlanner.isRemote(url)) {
                    j = pick(byId.get(DownloadStorageHelper.downloadIdOf(url)), old, chapters.get(i), used, true);
                }
                if (j != null) {
                    match[i] = old.get(j);
                    used[j] = true;
                }
            }
            for (int i = 0; i < chapters.size(); i++) {
                // Same position is only evidence when the name agrees; a different chapter must not inherit a copy.
                if (match[i] == null && i < old.size() && !used[i] && sameName(old.get(i), chapters.get(i))) {
                    match[i] = old.get(i);
                    used[i] = true;
                }
            }
            for (int i = 0; i < chapters.size(); i++) {
                AudioListPOJO chapter = chapters.get(i);
                BooksAudioEntity row = match[i];
                if (row == null) {
                    row = new BooksAudioEntity();
                    row.urlBook = urlBook;
                    row.time = chapter.getTime();
                    row.timeStart = chapter.getTimeStart();
                    row.timeEnd = chapter.getTimeEnd();
                }
                row.booksName = chapter.getBookName();
                row.nameAudio = chapter.getAudioName();
                row.urlAudio = chapter.getAudioUrl();
                row.ordinal = i;
                dao.insert(row);
            }
            for (int j = 0; j < old.size(); j++) {
                BooksAudioEntity row = old.get(j);
                if (used[j]) continue;
                if (row.localUri == null && DeletionPlanner.isRemote(row.urlAudio)) {
                    dao.deleteById(row.id);
                } else if (row.ordinal != retainedOrdinal(row.id)) {
                    row.ordinal = retainedOrdinal(row.id);
                    dao.insert(row);
                }
            }
        });
    }

    /** Ordinal of a row kept only for its copy: negative, unique per row and below -1. */
    static int retainedOrdinal(int id) {
        return -id - 1;
    }

    /** The first unused candidate with the chapter's name; else the first unused one unless a name is required. */
    private static Integer pick(ArrayDeque<Integer> candidates, List<BooksAudioEntity> old, AudioListPOJO chapter,
                                boolean[] used, boolean requireName) {
        if (candidates == null) return null;
        Integer first = null;
        for (Integer k : candidates) {
            if (used[k]) continue;
            if (sameName(old.get(k), chapter)) return k;
            if (first == null) first = k;
        }
        return requireName ? null : first;
    }

    private static boolean sameName(BooksAudioEntity row, AudioListPOJO chapter) {
        return row.nameAudio != null && row.nameAudio.equals(chapter.getAudioName());
    }

    /**
     * Chapter rows of OTHER entries that are the same book under another URL (trailing slash, moved host): they share
     * download ids ({@link com.fanok.audiobooks.download.DownloadIds#bookKey}), so a deletion must keep what they own.
     */
    public Map<String, List<BooksAudioEntity>> getAliasRows(@NonNull String urlBook) {
        String key = com.fanok.audiobooks.download.DownloadIds.bookKey(urlBook);
        Map<String, List<BooksAudioEntity>> byUrl = new HashMap<>();
        for (BooksAudioEntity row : getDatabase().booksAudioDao().getAll()) {
            if (row.urlBook.equals(urlBook) || !key.equals(com.fanok.audiobooks.download.DownloadIds.bookKey(row.urlBook))) continue;
            if (!byUrl.containsKey(row.urlBook)) byUrl.put(row.urlBook, new ArrayList<>());
            byUrl.get(row.urlBook).add(row);
        }
        return byUrl;
    }

    @Override
    public void clearAll() {
        getDatabase().booksAudioDao().deleteAll();
    }

    @Override
    public ArrayList<AudioListPOJO> get(@NonNull String url) {
        List<BooksAudioEntity> entities = getDatabase().booksAudioDao().getByBookUrl(url);
        ArrayList<AudioListPOJO> list = new ArrayList<>();
        for (BooksAudioEntity entity : entities) {
            AudioListPOJO audio = new AudioListPOJO();
            audio.setBookUrl(entity.urlBook);
            audio.setBookName(entity.booksName);
            audio.setAudioName(entity.nameAudio);
            audio.setAudioUrl(entity.urlAudio);
            audio.setTime(entity.time);
            audio.setTimeStart(entity.timeStart);
            audio.setTimeEnd(entity.timeEnd);
            audio.setLocalUri(entity.localUri);
            audio.setOrdinal(entity.ordinal);
            list.add(audio);
        }
        return list;
    }

    /** The chapter whose url_audio equals {@code audioUrl}, or else whose remote URL has it as Media3 download id. */
    @Override
    public AudioListPOJO get(@NonNull String url, @NonNull String audioUrl) {
        BooksAudioEntity entity = getDatabase().booksAudioDao().getByUrlAndAudioUrl(url, audioUrl);
        if (entity == null) {
            for (BooksAudioEntity row : getRows(url)) {
                if (DeletionPlanner.isRemote(row.urlAudio) && audioUrl.equals(DownloadStorageHelper.downloadIdOf(row.urlAudio))) {
                    entity = row;
                    break;
                }
            }
        }
        AudioListPOJO audio = new AudioListPOJO();
        if (entity != null) {
            audio.setBookUrl(entity.urlBook);
            audio.setBookName(entity.booksName);
            audio.setAudioName(entity.nameAudio);
            audio.setAudioUrl(entity.urlAudio);
            audio.setTime(entity.time);
            audio.setTimeStart(entity.timeStart);
            audio.setTimeEnd(entity.timeEnd);
            audio.setLocalUri(entity.localUri);
            audio.setOrdinal(entity.ordinal);
        }
        return audio;
    }

    @Override
    public ArrayList<AudioListPOJO> getAll() {
        List<BooksAudioEntity> entities = getDatabase().booksAudioDao().getAll();
        ArrayList<AudioListPOJO> list = new ArrayList<>();
        for (BooksAudioEntity entity : entities) {
            AudioListPOJO audio = new AudioListPOJO();
            audio.setBookUrl(entity.urlBook);
            audio.setBookName(entity.booksName);
            audio.setAudioName(entity.nameAudio);
            audio.setAudioUrl(entity.urlAudio);
            audio.setTime(entity.time);
            audio.setTimeStart(entity.timeStart);
            audio.setTimeEnd(entity.timeEnd);
            audio.setLocalUri(entity.localUri);
            audio.setOrdinal(entity.ordinal);
            list.add(audio);
        }
        return list;
    }
}
