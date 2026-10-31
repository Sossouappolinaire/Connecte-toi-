package com.wifimanager.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.View;
import android.webkit.SslErrorHandler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.net.URI;

/** Ouvre la page d'administration du routeur dans l'app, identifiants pré-remplis (sans valider). */
public class WebActivity extends Activity {

    private WebView web;
    private String host = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final String url = getIntent().getStringExtra("url");
        final String user = getIntent().getStringExtra("user");
        final String pass = getIntent().getStringExtra("pass");
        final String newPass = getIntent().getStringExtra("newpass");
        try {
            host = new URI(url).getHost();
        } catch (Exception ignored) {
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView hint = new TextView(this);
        hint.setText("Connexion pré-remplie : appuyez sur « Connexion » dans la page.\n"
                + "Ensuite : WLAN (ou icône antenne) → clé WPA / mot de passe → Enregistrer.");
        hint.setTextSize(13);
        int pad = (int) (10 * getResources().getDisplayMetrics().density);
        hint.setPadding(pad, pad, pad, pad);
        root.addView(hint);

        Button copy = new Button(this);
        copy.setAllCaps(false);
        copy.setText("Copier le nouveau mot de passe Wi-Fi");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("wifi", newPass == null ? "" : newPass));
                Toast.makeText(WebActivity.this, "Copié", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(copy);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) {
                // Certificat auto-signé du routeur : accepté uniquement pour l'adresse du routeur.
                String h2 = "";
                try {
                    h2 = new URI(e.getUrl()).getHost();
                } catch (Exception ignored) {
                }
                if (h2 != null && h2.equals(host)) h.proceed();
                else h.cancel();
            }

            @Override
            public void onPageFinished(WebView v, String u) {
                String js = "(function(){"
                        + "var u=document.getElementById('txt_Username')||document.querySelector('input[name=UserName],input[name=username],input[type=text]');"
                        + "var p=document.getElementById('txt_Password')||document.querySelector('input[type=password]');"
                        + "if(u&&p){u.value=" + JSONObject.quote(user == null ? "" : user) + ";"
                        + "p.value=" + JSONObject.quote(pass == null ? "" : pass) + ";}"
                        + "})();";
                v.evaluateJavascript(js, null);
            }

            @Override
            public void onPageStarted(WebView v, String u, Bitmap f) {
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        web.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
