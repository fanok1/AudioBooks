package com.fanok.audiobooks.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.database.DatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.ResolvingDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheKeyFactory;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.media3.exoplayer.offline.DownloadManager;

import com.fanok.audiobooks.App;
import com.fanok.audiobooks.BuildConfig;
import com.fanok.audiobooks.Url;

import java.io.IOException;
import java.util.HashMap;

@UnstableApi
public class DownloadUtil {


    private static DataSource.Factory resolvingUpstreamFactory;

    @UnstableApi
    public static DownloadManager getDownloadManager() {
        return App.getInstance().getDownloadManager();
    }

    @UnstableApi
    public static synchronized Cache getDownloadCache() {
        return App.getInstance().getDownloadCache();
    }


    @UnstableApi
    public static synchronized DataSource.Factory getResolvingUpstreamFactory(Context context) {
        if (resolvingUpstreamFactory == null) {
            DefaultHttpDataSource.Factory httpBase =
                    new DefaultHttpDataSource.Factory()
                            .setUserAgent(context.getPackageName() + "/" + BuildConfig.VERSION_NAME);
            DataSource.Factory upstream = new DefaultDataSource.Factory(context.getApplicationContext(), httpBase);

            // Пер-запросное добавление заголовков по URI
            resolvingUpstreamFactory = new ResolvingDataSource.Factory(upstream, dataSpec -> {
                Uri u = dataSpec.uri;
                String ref = u.getQueryParameter("__ref");
                HashMap<String, String> map = new HashMap<>();

                if (ref != null) {
                    if (ref.contains("%2F") || ref.contains("%3A")) {
                        ref = android.net.Uri.decode(ref);
                    }
                    if (!ref.startsWith("http://") && !ref.startsWith("https://")) {
                        ref = "https://" + ref;
                    }
                    Uri bookUri = Uri.parse(ref);

                    String bookHost = bookUri.getHost() != null ? bookUri.toString() : "";
                    if (bookHost.contains(Url.SERVER_ABMP3)) {
                        map.put("user-agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.82 Safari/537.36");
                        map.put("Referer", Url.SERVER_ABMP3 + "/");
                    } else if (bookHost.contains(Url.SERVER_BAZA_KNIG)) {
                        map.put("user-agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.82 Safari/537.36");
                        map.put("referer", Url.SERVER_BAZA_KNIG + "/");
                    } else {
                        map.put("user-agent", Util.getUserAgent(context, context.getPackageName()));
                    }

                }else {
                    map.put("user-agent", Util.getUserAgent(context, context.getPackageName()));
                }

                Uri.Builder cleanUriBuilder = u.buildUpon().clearQuery();

                for (String paramName : u.getQueryParameterNames()) {
                    if (!"__ref".equals(paramName)) {
                        String paramValue = u.getQueryParameter(paramName);
                        cleanUriBuilder.appendQueryParameter(paramName, paramValue);
                    }
                }

                Uri cleanUri = cleanUriBuilder.build();
                return dataSpec.withUri(cleanUri).withRequestHeaders(map);
            });
        }
        return resolvingUpstreamFactory;
    }

    @UnstableApi
    private static DatabaseProvider getDatabaseProvider() {
        return App.getInstance().getDatabaseProvider();

    }

    public static Uri buildAnnotatedUri(String fileUrl, String urlBook) {
        return Uri.parse(fileUrl)
                .buildUpon()
                .appendQueryParameter("__ref", Uri.encode(urlBook))
                .build();
    }

}
