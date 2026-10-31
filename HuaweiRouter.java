package com.wifimanager.app;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Détection et pilotage des routeurs Huawei.
 *
 * - HILINK : routeurs/clés 4G et 5G Huawei (B310, B315, B525, B618, H122...). Changement automatique.
 * - ONT / HUAWEI / INCONNU : box fibre (HG8145, EG8145...) -> pas d'automatisation, diagnostic uniquement.
 */
public class HuaweiRouter {

    public static class Probe {
        public String type = "INCONNU"; // HILINK, ONT, HUAWEI, INCONNU, INJOIGNABLE
        public int code;
        public String title = "";
        public String server = "";
        public String snippet = "";
        public String error = "";
    }

    private static class Resp {
        int code;
        String body = "";
        Map<String, List<String>> headers = new HashMap<>();
    }

    // ---------- HTTP ----------

    private static Resp request(String method, String url, String body, Map<String, String> headers) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(8000);
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod(method);
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                c.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        if (body != null) {
            byte[] b = body.getBytes("UTF-8");
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(b.length);
            OutputStream o = c.getOutputStream();
            o.write(b);
            o.close();
        }
        Resp r = new Resp();
        r.code = c.getResponseCode();
        InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in != null) {
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[2048];
            int n;
            while ((n = br.read(buf)) > 0 && sb.length() < 200000) {
                sb.append(buf, 0, n);
            }
            br.close();
            r.body = sb.toString();
        }
        r.headers = new HashMap<>(c.getHeaderFields());
        c.disconnect();
        return r;
    }

    private static String header(Resp r, String name) {
        for (Map.Entry<String, List<String>> e : r.headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name) && !e.getValue().isEmpty()) {
                return e.getValue().get(0);
            }
        }
        return null;
    }

    private static String xml(String body, String tag) {
        if (body == null) return null;
        Matcher m = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">", Pattern.DOTALL).matcher(body);
        return m.find() ? m.group(1) : null;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String sha256Hex(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte x : d) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static String b64(String s) throws Exception {
        return Base64.encodeToString(s.getBytes("UTF-8"), Base64.NO_WRAP);
    }

    // ---------- Détection ----------

    public static Probe probe(String gateway) {
        Probe p = new Probe();
        String base = "http://" + gateway;

        try {
            Resp r = request("GET", base + "/api/webserver/SesTokInfo", null, null);
            if (r.code == 200 && r.body.contains("<SesInfo>") && r.body.contains("<TokInfo>")) {
                p.type = "HILINK";
                p.code = r.code;
                p.snippet = "API HiLink détectée";
                return p;
            }
        } catch (Exception ignored) {
        }

        try {
            Resp r = request("GET", base + "/", null, null);
            p.code = r.code;
            String srv = header(r, "Server");
            p.server = srv == null ? "" : srv;
            String title = xml(r.body.replaceAll("(?i)<title>", "<title>").replaceAll("(?i)</title>", "</title>"), "title");
            p.title = title == null ? "" : title.trim();
            String loc = header(r, "Location");
            String one = r.body.replaceAll("\\s+", " ");
            p.snippet = (loc != null ? "Redirection vers : " + loc + " | " : "")
                    + one.substring(0, Math.min(400, one.length()));

            String low = (r.body + " " + p.title + " " + p.server).toLowerCase();
            if (low.contains("hg8") || low.contains("eg8") || low.contains("hn8") || low.contains("xn8")
                    || low.contains("optixstar") || low.contains("echolife") || low.contains("x_hw_token")
                    || low.contains("txt_username")) {
                p.type = "ONT";
            } else if (low.contains("huawei")) {
                p.type = "HUAWEI";
            }
        } catch (Exception e) {
            p.type = "INJOIGNABLE";
            p.error = String.valueOf(e.getMessage());
        }
        return p;
    }

    public static String label(Probe p) {
        switch (p.type) {
            case "HILINK": return "routeur 4G/5G Huawei (HiLink)";
            case "ONT": return "box fibre Huawei (ONT)";
            case "HUAWEI": return "équipement Huawei";
            case "INJOIGNABLE": return "routeur injoignable";
            default: return "modèle non reconnu";
        }
    }

    public static String diagnostic(Probe p, String gateway) {
        return "Adresse : " + gateway
                + "\nType détecté : " + p.type
                + "\nCode HTTP : " + p.code
                + "\nTitre de la page : " + p.title
                + "\nServeur : " + p.server
                + (p.error.isEmpty() ? "" : "\nErreur : " + p.error)
                + "\nDébut de la page : " + p.snippet;
    }

    // ---------- Changement du mot de passe : routeurs 4G/5G HiLink ----------

    private static String loginError(String code) {
        if (code == null) return "Connexion refusée par le routeur";
        switch (code) {
            case "108001":
            case "108002":
            case "108006":
                return "Identifiant ou mot de passe administrateur incorrect";
            case "108003":
                return "Un autre appareil est déjà connecté à l'administration du routeur";
            case "108007":
                return "Trop d'essais : attendez quelques minutes";
            default:
                return "Connexion refusée par le routeur (code " + code + ")";
        }
    }

    private static String freshToken(String base, String cookie) throws Exception {
        Map<String, String> h = new HashMap<>();
        h.put("Cookie", cookie);
        Resp r = request("GET", base + "/api/webserver/token", null, h);
        String t = xml(r.body, "token");
        if (t == null) throw new Exception("Jeton de sécurité introuvable");
        t = t.trim();
        return t.length() > 32 ? t.substring(t.length() - 32) : t;
    }

    /**
     * Se connecte à l'administration, relit les réglages Wi-Fi actuels, ne remplace QUE la clé WPA,
     * puis renvoie le tout. Au moindre doute, rien n'est modifié.
     */
    public static String changeHiLink(String gateway, String user, String adminPass, String newPsk) throws Exception {
        String base = "http://" + gateway;

        Resp t = request("GET", base + "/api/webserver/SesTokInfo", null, null);
        String ses = xml(t.body, "SesInfo");
        String tok = xml(t.body, "TokInfo");
        if (ses == null || tok == null) {
            throw new Exception("Ce routeur ne répond pas comme un Huawei 4G/5G (HiLink)");
        }

        String hashed = b64(sha256Hex(user + b64(sha256Hex(adminPass)) + tok));
        String loginXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><Username>" + esc(user)
                + "</Username><Password>" + hashed + "</Password><password_type>4</password_type></request>";
        Map<String, String> h = new HashMap<>();
        h.put("Cookie", ses);
        h.put("__RequestVerificationToken", tok);
        h.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        Resp login = request("POST", base + "/api/user/login", loginXml, h);
        if (!login.body.contains("<response>OK</response>")) {
            throw new Exception(loginError(xml(login.body, "code")));
        }

        String cookie = ses;
        List<String> cookies = login.headers.get("Set-Cookie");
        if (cookies == null) cookies = login.headers.get("set-cookie");
        if (cookies != null) {
            for (String c : cookies) {
                if (c.startsWith("SessionID=")) {
                    int end = c.indexOf(';');
                    cookie = end > 0 ? c.substring(0, end) : c;
                }
            }
        }

        try {
            Map<String, String> hc = new HashMap<>();
            hc.put("Cookie", cookie);
            Resp cur = request("GET", base + "/api/wlan/security-settings", null, hc);
            if (cur.body.contains("<error>")) {
                throw new Exception("Lecture des réglages Wi-Fi refusée (code " + xml(cur.body, "code") + ")");
            }
            if (!cur.body.contains("<WifiWpapsk>")) {
                throw new Exception("Réglages Wi-Fi inattendus : aucun changement effectué");
            }
            String mode = xml(cur.body, "WifiAuthmode");
            if (mode == null || !mode.toUpperCase().contains("PSK")) {
                throw new Exception("Mode de sécurité non géré (" + mode + ") : aucun changement effectué");
            }

            String reqXml = cur.body
                    .replace("<response>", "<request>")
                    .replace("</response>", "</request>")
                    .replaceFirst("(?s)<WifiWpapsk>.*?</WifiWpapsk>",
                            Matcher.quoteReplacement("<WifiWpapsk>" + esc(newPsk) + "</WifiWpapsk>"));
            if (!reqXml.startsWith("<?xml")) {
                reqXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + reqXml;
            }

            Map<String, String> hp = new HashMap<>();
            hp.put("Cookie", cookie);
            hp.put("__RequestVerificationToken", freshToken(base, cookie));
            hp.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            Resp set = request("POST", base + "/api/wlan/security-settings", reqXml, hp);
            if (!set.body.contains("<response>OK</response>")) {
                throw new Exception("Le routeur a refusé le changement (code " + xml(set.body, "code") + ")");
            }
            return "Mot de passe Wi-Fi du routeur modifié";
        } finally {
            try {
                Map<String, String> hl = new HashMap<>();
                hl.put("Cookie", cookie);
                hl.put("__RequestVerificationToken", freshToken(base, cookie));
                hl.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                request("POST", base + "/api/user/logout",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><Logout>1</Logout></request>", hl);
            } catch (Exception ignored) {
            }
        }
    }
}
