package com.wifimanager.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.WifiNetworkSuggestion;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Toute la logique Wi-Fi : scan, connexion, infos, comptage des appareils. */
@SuppressWarnings("deprecation")
public class WifiHelper {

    public interface Result { void done(boolean ok, String message); }
    public interface Count { void done(int n); }

    private final WifiManager wm;
    private final ConnectivityManager cm;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ConnectivityManager.NetworkCallback current;

    public WifiHelper(Context ctx) {
        Context app = ctx.getApplicationContext();
        wm = (WifiManager) app.getSystemService(Context.WIFI_SERVICE);
        cm = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    // ---------- État ----------

    public boolean isEnabled() {
        return wm.isWifiEnabled();
    }

    public boolean isConnected() {
        try {
            Network n = cm.getActiveNetwork();
            if (n == null) return false;
            NetworkCapabilities c = cm.getNetworkCapabilities(n);
            return c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Exception e) {
            return false;
        }
    }

    /** Force le trafic de l'app à passer par le Wi-Fi (même s'il n'a pas Internet). */
    public boolean bindToWifi() {
        try {
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities c = cm.getNetworkCapabilities(n);
                if (c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return cm.bindProcessToNetwork(n);
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private WifiInfo info() {
        return wm.getConnectionInfo();
    }

    public String ssid() {
        try {
            String s = info().getSSID();
            if (s == null || s.equals("<unknown ssid>")) return "(masqué : autorisez la localisation)";
            if (s.length() > 1 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
            return s;
        } catch (Exception e) {
            return "?";
        }
    }

    public int linkSpeed() {
        return info().getLinkSpeed();
    }

    public int rssi() {
        return info().getRssi();
    }

    public int level() {
        return level(info().getRssi());
    }

    public static int level(int rssi) {
        return WifiManager.calculateSignalLevel(rssi, 5);
    }

    public String ip() {
        return formatIp(info().getIpAddress());
    }

    public String gateway() {
        DhcpInfo d = wm.getDhcpInfo();
        return d == null ? "?" : formatIp(d.gateway);
    }

    private static String formatIp(int i) {
        return (i & 0xff) + "." + ((i >> 8) & 0xff) + "." + ((i >> 16) & 0xff) + "." + ((i >> 24) & 0xff);
    }

    // ---------- Scan ----------

    public void startScan() {
        try {
            wm.startScan();
        } catch (Exception ignored) {
        }
    }

    public List<ScanResult> results() {
        try {
            return wm.getScanResults();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static boolean isSecured(String caps) {
        return caps != null && (caps.contains("WPA") || caps.contains("WEP")
                || caps.contains("SAE") || caps.contains("PSK"));
    }

    // ---------- Connexion ----------

    public void connect(final String ssid, final String password, final Result cb) {
        release();
        if (Build.VERSION.SDK_INT >= 29) {
            connectApi29(ssid, password, cb);
        } else {
            connectLegacy(ssid, password, cb);
        }
    }

    private void release() {
        if (current != null) {
            try {
                cm.unregisterNetworkCallback(current);
            } catch (Exception ignored) {
            }
            current = null;
        }
    }

    private void postResult(final Result cb, final boolean ok, final String msg) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                cb.done(ok, msg);
            }
        });
    }

    private void connectApi29(final String ssid, String password, final Result cb) {
        WifiNetworkSpecifier.Builder b = new WifiNetworkSpecifier.Builder().setSsid(ssid);
        if (password != null && !password.isEmpty()) {
            b.setWpa2Passphrase(password);
        }
        NetworkRequest req = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(b.build())
                .build();

        current = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                cm.bindProcessToNetwork(network);
                postResult(cb, true, "Connecté à " + ssid);
            }

            @Override
            public void onUnavailable() {
                postResult(cb, false, "Échec : mot de passe incorrect ou réseau hors de portée");
            }
        };
        try {
            cm.requestNetwork(req, current, 30000);
        } catch (Exception e) {
            postResult(cb, false, "Erreur : " + e.getMessage());
        }
    }

    private void connectLegacy(String ssid, String password, Result cb) {
        try {
            WifiConfiguration c = new WifiConfiguration();
            c.SSID = "\"" + ssid + "\"";
            if (password == null || password.isEmpty()) {
                c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            } else {
                c.preSharedKey = "\"" + password + "\"";
            }
            int id = wm.addNetwork(c);
            if (id == -1) {
                postResult(cb, false, "Impossible d'ajouter le réseau");
                return;
            }
            wm.disconnect();
            wm.enableNetwork(id, true);
            wm.reconnect();
            postResult(cb, true, "Connexion à " + ssid + " en cours…");
        } catch (Exception e) {
            postResult(cb, false, "Erreur : " + e.getMessage());
        }
    }

    /**
     * Android 10+ : donne au système la liste des réseaux enregistrés
     * pour qu'il s'y reconnecte tout seul.
     */
    public void syncSuggestions(Map<String, String> all) {
        if (Build.VERSION.SDK_INT < 29) return;
        try {
            wm.removeNetworkSuggestions(new ArrayList<WifiNetworkSuggestion>());
        } catch (Exception ignored) {
        }
        List<WifiNetworkSuggestion> list = new ArrayList<>();
        for (Map.Entry<String, String> e : all.entrySet()) {
            try {
                WifiNetworkSuggestion.Builder b = new WifiNetworkSuggestion.Builder().setSsid(e.getKey());
                if (e.getValue() != null && !e.getValue().isEmpty()) {
                    b.setWpa2Passphrase(e.getValue());
                }
                list.add(b.build());
            } catch (Exception ignored) {
            }
        }
        if (!list.isEmpty()) {
            try {
                wm.addNetworkSuggestions(list);
            } catch (Exception ignored) {
            }
        }
    }

    // ---------- Appareils connectés ----------

    /** Balaye le réseau local (x.x.x.1 à x.x.x.254) et compte les appareils qui répondent. */
    public void countDevices(final Count cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int ip = info().getIpAddress();
                if (ip == 0) {
                    postCount(cb, -1);
                    return;
                }
                final String prefix = (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "." + ((ip >> 16) & 0xff) + ".";
                final Set<String> found = Collections.synchronizedSet(new HashSet<String>());
                ExecutorService ex = Executors.newFixedThreadPool(48);
                for (int i = 1; i < 255; i++) {
                    final String host = prefix + i;
                    ex.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (InetAddress.getByName(host).isReachable(400)) {
                                    found.add(host);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    });
                }
                ex.shutdown();
                try {
                    ex.awaitTermination(40, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
                found.addAll(readArp(prefix));
                postCount(cb, found.size());
            }
        }).start();
    }

    private void postCount(final Count cb, final int n) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                cb.done(n);
            }
        });
    }

    private Set<String> readArp(String prefix) {
        Set<String> s = new HashSet<>();
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/net/arp"))) {
            String line = r.readLine(); // en-tête
            while ((line = r.readLine()) != null) {
                String[] p = line.trim().split("\\s+");
                if (p.length >= 4 && p[0].startsWith(prefix) && "0x2".equals(p[2])
                        && !"00:00:00:00:00:00".equals(p[3])) {
                    s.add(p[0]);
                }
            }
        } catch (Exception ignored) {
        }
        return s;
    }
}
