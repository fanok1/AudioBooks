package com.fanok.audiobooks;

import static com.fanok.audiobooks.Consts.PROXY_PASSWORD;
import static com.fanok.audiobooks.Consts.PROXY_USERNAME;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import com.fanok.audiobooks.pojo.StorageUtil;
import com.fanok.audiobooks.presenter.BookPresenter;
import com.google.android.gms.ads.MobileAds;
import java.net.Authenticator;
import java.net.PasswordAuthentication;

public class App extends Application {

    public static boolean useProxy;

    @Override
    protected void attachBaseContext(Context base) {
        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(base);

        String lang = pref.getString("pref_lang", "ru");
        super.attachBaseContext(LocaleManager.onAttach(base, lang));
    }

    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences pref = PreferenceManager
                .getDefaultSharedPreferences(this);

        String themeName = pref.getString("pref_theme", getString(R.string.theme_dark_value));
        if (themeName.equals(getString(R.string.theme_dark_value))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if (themeName.equals(getString(R.string.theme_light_value))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (themeName.equals(getString(R.string.theme_black_value))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }

        Consts.setBazaKnigCookies(pref.getString("cookes_baza_knig", ""));

        MobileAds.initialize(this, "ca-app-pub-3595775191373219~2371571769");

        BookPresenter.setSpeedWithoutBroadcast(new StorageUtil(getBaseContext()).loadSpeed());

        String source = pref.getString("sorce_books", getString(R.string.abook_value));

        Consts.setSOURCE(this, source);

        useProxy = pref.getBoolean("pref_proxy", false);
        if(useProxy){
            Authenticator authenticator = new Authenticator() {
                public PasswordAuthentication getPasswordAuthentication() {
                    return (new PasswordAuthentication(PROXY_USERNAME,
                            PROXY_PASSWORD.toCharArray()));
                }
            };
            Authenticator.setDefault(authenticator);
        }

        /*String vpn = pref.getString("vpn", getString(R.string.vpn_no_value));
        if (!vpn.equals(getString(R.string.vpn_no_value))) {
            String file = "";
            String name = "";
            if (vpn.equals(getString(R.string.vpn_antizapret_value))) {
                file = "antizapret-tcp.ovpn";
                name = "Антизапрет";
            } else if (vpn.equals(getString(R.string.vpn_zaborona_value))) {
                file = "srv0.zaborona-help_maxroutes.ovpn";
                name = "Заборона";
            } else if (vpn.equals(getString(R.string.vpn_zaborona_europe_value))) {
                file = "srv0.zaborona-help-UDP-no-encryption_maxroutes.ovpn";
                name = "Заборона Европа";
            } else if (vpn.equals(getString(R.string.vpn_ukrane_value))) {
                file = "ukrane.ovpn";
                name = "VPN Украина";
            }
            if (!file.isEmpty()) {
                Intent intent = VpnService.prepare(getApplicationContext());
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getApplicationContext().startActivity(intent);
                }
                startVpn(file, name);
            }
        }*/

        //Billing.initBilding(getBaseContext());
    }

    /*private void startVpn(String file, String name) {
        try {
            // .ovpn file
            InputStream conf = getAssets().open(file);
            InputStreamReader isr = new InputStreamReader(conf);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder config = new StringBuilder();
            String line;

            while (true) {
                line = br.readLine();
                if (line == null) {
                    break;
                }
                config.append(line).append("\n");
            }

            br.readLine();
            OpenVpnApi.startVpn(getApplicationContext(), config.toString(), name, null, null);

        } catch (IOException | RemoteException e) {
            e.printStackTrace();
        }
    }*/
}
