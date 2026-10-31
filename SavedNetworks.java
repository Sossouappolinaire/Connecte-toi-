package com.wifimanager.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Réseaux Wi-Fi déjà utilisés via l'application (nom + mot de passe). */
public class SavedNetworks {

    private final SharedPreferences sp;

    public SavedNetworks(Context c) {
        sp = c.getSharedPreferences("wifi_saved", Context.MODE_PRIVATE);
    }

    public List<String> list() {
        return new ArrayList<>(new TreeSet<>(sp.getStringSet("ssids", new HashSet<String>())));
    }

    public boolean has(String ssid) {
        return sp.getStringSet("ssids", new HashSet<String>()).contains(ssid);
    }

    public String getPassword(String ssid) {
        return sp.getString("pwd_" + ssid, "");
    }

    public void save(String ssid, String password) {
        Set<String> s = new HashSet<>(sp.getStringSet("ssids", new HashSet<String>()));
        s.add(ssid);
        sp.edit().putStringSet("ssids", s).putString("pwd_" + ssid, password).apply();
    }

    public void remove(String ssid) {
        Set<String> s = new HashSet<>(sp.getStringSet("ssids", new HashSet<String>()));
        s.remove(ssid);
        sp.edit().putStringSet("ssids", s).remove("pwd_" + ssid).apply();
    }

    public Map<String, String> all() {
        Map<String, String> m = new LinkedHashMap<>();
        for (String ssid : list()) {
            m.put(ssid, getPassword(ssid));
        }
        return m;
    }
}
