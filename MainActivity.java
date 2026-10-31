package com.wifimanager.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String APP_NAME = "AppConstant";

    private interface TextCb { void on(String text); }

    private WifiHelper wifi;
    private SavedNetworks saved;

    // Carte d'état
    private View stateDot;
    private TextView stateText;
    private TextView ssidText;
    private TextView ipText;
    private TextView gwText;
    private TextView devText;
    private TextView speedText;
    private Ui.Gauge gauge;
    private Ui.Bar speedBar;
    private ProgressBar busyBar;

    private LinearLayout scanBox;
    private LinearLayout savedBox;

    private final Set<String> shown = new HashSet<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private int devices = -1;
    private boolean autoTried = false;
    private boolean receiverOn = false;
    private boolean scanning = false;
    private boolean counting = false;

    private final Runnable stopScan = new Runnable() {
        @Override
        public void run() {
            scanning = false;
            updateBusy();
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshInfo();
            ui.postDelayed(this, 3000);
        }
    };

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            ui.removeCallbacks(stopScan);
            scanning = false;
            updateBusy();
            renderScan();
            autoConnectIfNeeded();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG1);
        getWindow().setNavigationBarColor(Ui.BG2);
        wifi = new WifiHelper(this);
        saved = new SavedNetworks(this);
        buildUi();
        if (!hasPerms()) {
            requestPermissions(perms(), 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(scanReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(scanReceiver, f);
        }
        receiverOn = true;
        ui.post(ticker);
        startScan();
        renderSaved();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (receiverOn) {
            unregisterReceiver(scanReceiver);
            receiverOn = false;
        }
        ui.removeCallbacks(ticker);
        ui.removeCallbacks(stopScan);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        startScan();
    }

    // ---------- Permissions ----------

    private String[] perms() {
        if (Build.VERSION.SDK_INT >= 33) {
            return new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES};
        }
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    private boolean hasPerms() {
        for (String p : perms()) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    // ---------- Interface ----------

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private LinearLayout.LayoutParams lp(float top, float bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(top);
        p.bottomMargin = dp(bottom);
        return p;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private void section(LinearLayout root, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View bar = new View(this);
        bar.setBackground(Ui.gradient(this, Ui.CYAN, Ui.PRIMARY2, 3));
        row.addView(bar, new LinearLayout.LayoutParams(dp(4), dp(20)));
        TextView t = Ui.text(this, label, 18, Ui.TEXT, true);
        t.setPadding(dp(10), 0, 0, 0);
        row.addView(t);
        root.addView(row, lp(8, 10));
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.BG1, Ui.BG2}));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(32));
        scroll.addView(root);

        // En-tête
        TextView title = Ui.text(this, APP_NAME, 30, Ui.TEXT, true);
        title.getPaint().setShader(new LinearGradient(0, 0, dp(190), 0,
                new int[]{Ui.CYAN, Ui.PRIMARY2}, null, Shader.TileMode.CLAMP));
        root.addView(title);
        root.addView(Ui.text(this, "Gestionnaire Wi-Fi intelligent", 13, Ui.MUTED, false), lp(2, 16));

        // Carte d'état
        LinearLayout status = Ui.card(this);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        stateDot = Ui.dot(this, Ui.RED);
        Ui.pulse(stateDot);
        top.addView(stateDot);
        stateText = Ui.text(this, "Non connecté", 13, Ui.RED, true);
        stateText.setPadding(dp(8), 0, 0, 0);
        top.addView(stateText);
        status.addView(top);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.HORIZONTAL);
        mid.setGravity(Gravity.CENTER_VERTICAL);
        gauge = new Ui.Gauge(this);
        mid.addView(gauge);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(16), 0, 0, 0);
        ssidText = Ui.text(this, "—", 20, Ui.TEXT, true);
        ipText = Ui.text(this, "", 13, Ui.MUTED, false);
        gwText = Ui.text(this, "", 13, Ui.MUTED, false);
        devText = Ui.text(this, "", 14, Ui.CYAN, true);
        info.addView(ssidText);
        info.addView(ipText);
        info.addView(gwText);
        info.addView(devText, lp(6, 0));
        mid.addView(info, weight());
        status.addView(mid, lp(8, 8));

        LinearLayout speedRow = new LinearLayout(this);
        speedRow.setOrientation(LinearLayout.HORIZONTAL);
        speedRow.addView(Ui.text(this, "Débit", 13, Ui.MUTED, false), weight());
        speedText = Ui.text(this, "—", 14, Ui.TEXT, true);
        speedRow.addView(speedText);
        status.addView(speedRow);

        speedBar = new Ui.Bar(this);
        status.addView(speedBar, lp(6, 0));

        busyBar = Ui.progress(this, Ui.CYAN);
        status.addView(busyBar, lp(12, 0));

        root.addView(status, lp(0, 12));
        Ui.reveal(status, 0);

        // Boutons principaux
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        Button scanBtn = Ui.bigButton(this, "⟳  Scanner", Ui.PRIMARY1, Ui.PRIMARY2, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autoTried = false;
                startScan();
            }
        });
        Button cntBtn = Ui.bigButton(this, "👥  Compter", Ui.CYAN, Ui.BLUE, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                countDevices();
            }
        });
        LinearLayout.LayoutParams a = weight();
        a.rightMargin = dp(6);
        LinearLayout.LayoutParams b = weight();
        b.leftMargin = dp(6);
        btns.addView(scanBtn, a);
        btns.addView(cntBtn, b);
        root.addView(btns, lp(0, 10));
        Ui.reveal(btns, 1);

        Button router = Ui.bigButton(this, "📡  Routeur Huawei : changer le mot de passe Wi-Fi",
                Ui.PINK, Ui.PRIMARY2, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        routerDialog();
                    }
                });
        root.addView(router, lp(0, 14));
        Ui.reveal(router, 2);

        // Réseaux disponibles
        section(root, "Réseaux disponibles");
        scanBox = new LinearLayout(this);
        scanBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(scanBox);

        // Réseaux enregistrés
        section(root, "Réseaux enregistrés");
        TextView hint = Ui.text(this, "« Modifier » change le mot de passe utilisé par ce téléphone. "
                + "Le mot de passe du routeur lui-même ne change pas.", 12, Ui.MUTED, false);
        root.addView(hint, lp(0, 10));
        savedBox = new LinearLayout(this);
        savedBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(savedBox);

        setContentView(scroll);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private void updateBusy() {
        busyBar.setVisibility(scanning || counting ? View.VISIBLE : View.INVISIBLE);
    }

    private void setState(int color, String label) {
        Ui.setDotColor(stateDot, color);
        stateText.setTextColor(color);
        stateText.setText(label);
    }

    private void refreshInfo() {
        if (!wifi.isEnabled()) {
            setState(Ui.AMBER, "Wi-Fi désactivé");
            ssidText.setText("—");
            ipText.setText("Activez le Wi-Fi du téléphone");
            gwText.setText("");
            devText.setText("");
            speedText.setText("—");
            gauge.setPercent(0);
            speedBar.setFraction(0);
        } else if (!wifi.isConnected()) {
            setState(Ui.RED, "Non connecté");
            ssidText.setText("Aucun réseau");
            ipText.setText("Touchez un réseau pour vous connecter");
            gwText.setText("");
            devText.setText("");
            speedText.setText("—");
            gauge.setPercent(0);
            speedBar.setFraction(0);
        } else {
            setState(Ui.GREEN, "Connecté");
            ssidText.setText(wifi.ssid());
            ipText.setText("IP " + wifi.ip());
            gwText.setText("Passerelle " + wifi.gateway());
            devText.setText(devices < 0
                    ? "👥 Appareils : touchez « Compter »"
                    : "👥 " + devices + " appareil" + (devices > 1 ? "s" : "") + " connecté" + (devices > 1 ? "s" : ""));
            int speed = Math.max(0, wifi.linkSpeed());
            speedText.setText(speed + " Mbps");
            speedBar.setFraction(Math.min(1f, speed / 866f));
            gauge.setPercent((wifi.rssi() + 100) * 2);
        }
    }

    private void startScan() {
        if (!hasPerms()) {
            renderScan();
            return;
        }
        scanning = true;
        updateBusy();
        ui.removeCallbacks(stopScan);
        ui.postDelayed(stopScan, 8000);
        wifi.startScan();
        renderScan();
    }

    private void renderScan() {
        scanBox.removeAllViews();
        if (!hasPerms()) {
            scanBox.addView(Ui.text(this, "Autorisez la localisation pour voir les réseaux.", 14, Ui.MUTED, false));
            return;
        }
        List<ScanResult> res = new ArrayList<>(wifi.results());
        Collections.sort(res, new Comparator<ScanResult>() {
            @Override
            public int compare(ScanResult a, ScanResult b) {
                return b.level - a.level;
            }
        });
        boolean connected = wifi.isConnected();
        String current = connected ? wifi.ssid() : "";
        Set<String> seen = new HashSet<>();
        int idx = 0;
        for (ScanResult r : res) {
            if (r.SSID == null || r.SSID.isEmpty() || !seen.add(r.SSID)) continue;
            final String ssid = r.SSID;
            final boolean secured = WifiHelper.isSecured(r.capabilities);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackground(Ui.ripple(this, Ui.shape(this, Ui.CARD, 16, Ui.STROKE, 1), 16));
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onNetworkTap(ssid, secured);
                }
            });
            Ui.pressable(row);

            Ui.SignalBars bars = new Ui.SignalBars(this);
            bars.setLevel(WifiHelper.level(r.level));
            row.addView(bars);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(14), 0, 0, 0);
            col.addView(Ui.text(this, ssid, 16, Ui.TEXT, true));
            col.addView(Ui.text(this, (secured ? "🔒 Sécurisé" : "Ouvert") + " · " + r.level + " dBm",
                    12, Ui.MUTED, false));
            row.addView(col, weight());

            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            pl.leftMargin = dp(8);
            if (connected && ssid.equals(current)) {
                row.addView(Ui.pill(this, "Connecté", Ui.GREEN), pl);
            } else if (saved.has(ssid)) {
                row.addView(Ui.pill(this, "Enregistré", Ui.LAVENDER), pl);
            }

            TextView chevron = Ui.text(this, "›", 24, Ui.MUTED, false);
            chevron.setPadding(dp(10), 0, 0, 0);
            row.addView(chevron);

            scanBox.addView(row, lp(0, 8));
            Ui.reveal(row, idx++);
        }
        if (seen.isEmpty()) {
            scanBox.addView(Ui.text(this, "Aucun réseau trouvé. Touchez « Scanner ».", 14, Ui.MUTED, false));
        }
    }

    private void renderSaved() {
        savedBox.removeAllViews();
        List<String> list = saved.list();
        if (list.isEmpty()) {
            savedBox.addView(Ui.text(this, "Aucun réseau enregistré pour l'instant.", 14, Ui.MUTED, false));
            return;
        }
        int idx = 0;
        for (final String ssid : list) {
            LinearLayout card = Ui.card(this);

            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(Ui.text(this, ssid, 17, Ui.TEXT, true), weight());
            head.addView(Ui.pill(this, "Enregistré", Ui.LAVENDER));
            card.addView(head);

            String pwd = saved.getPassword(ssid);
            String shownPwd;
            if (pwd.isEmpty()) {
                shownPwd = "(réseau ouvert)";
            } else if (shown.contains(ssid)) {
                shownPwd = pwd;
            } else {
                shownPwd = "••••••••";
            }
            card.addView(Ui.text(this, "Mot de passe", 12, Ui.MUTED, false), lp(10, 0));
            TextView pw = Ui.text(this, shownPwd, 17, Ui.CYAN, true);
            pw.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            card.addView(pw, lp(2, 12));

            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.rightMargin = dp(8);

            chips.addView(Ui.chip(this, shown.contains(ssid) ? "🙈 Masquer" : "👁 Afficher", Ui.CYAN,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (!shown.remove(ssid)) shown.add(ssid);
                            renderSaved();
                        }
                    }), cp);
            chips.addView(Ui.chip(this, "✏️ Modifier", Ui.LAVENDER, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editPassword(ssid);
                }
            }), cp);
            chips.addView(Ui.chip(this, "🗑 Oublier", Ui.RED, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    saved.remove(ssid);
                    shown.remove(ssid);
                    wifi.syncSuggestions(saved.all());
                    renderSaved();
                    renderScan();
                    toast(ssid + " oublié");
                }
            }), cp);
            card.addView(chips);

            savedBox.addView(card, lp(0, 10));
            Ui.reveal(card, idx++);
        }
    }

    // ---------- Actions Wi-Fi ----------

    private void onNetworkTap(final String ssid, boolean secured) {
        if (saved.has(ssid)) {
            connectAndSave(ssid, saved.getPassword(ssid));
        } else if (secured) {
            askPassword("Mot de passe de " + ssid, "", new TextCb() {
                @Override
                public void on(String t) {
                    connectAndSave(ssid, t);
                }
            });
        } else {
            connectAndSave(ssid, "");
        }
    }

    private void askPassword(String title, String initial, final TextCb cb) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setText(initial);
        box.addView(input);

        CheckBox show = new CheckBox(this);
        show.setText("Afficher le mot de passe");
        show.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                input.setTransformationMethod(checked
                        ? HideReturnsTransformationMethod.getInstance()
                        : PasswordTransformationMethod.getInstance());
                input.setSelection(input.getText().length());
            }
        });
        box.addView(show);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        cb.on(input.getText().toString());
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void editPassword(final String ssid) {
        askPassword("Nouveau mot de passe pour " + ssid, saved.getPassword(ssid), new TextCb() {
            @Override
            public void on(String t) {
                saved.save(ssid, t);
                wifi.syncSuggestions(saved.all());
                renderSaved();
                toast("Mot de passe modifié dans l'application");
                wifi.connect(ssid, t, new WifiHelper.Result() {
                    @Override
                    public void done(boolean ok, String message) {
                        toast(message);
                        refreshInfo();
                    }
                });
            }
        });
    }

    private void connectAndSave(final String ssid, final String password) {
        toast("Connexion à " + ssid + "…");
        scanning = true;
        updateBusy();
        ui.removeCallbacks(stopScan);
        ui.postDelayed(stopScan, 30000);
        wifi.connect(ssid, password, new WifiHelper.Result() {
            @Override
            public void done(boolean ok, String message) {
                ui.removeCallbacks(stopScan);
                scanning = false;
                updateBusy();
                if (ok) {
                    saved.save(ssid, password);
                    wifi.syncSuggestions(saved.all());
                    renderSaved();
                    renderScan();
                }
                toast(message);
                refreshInfo();
            }
        });
    }

    private void autoConnectIfNeeded() {
        if (autoTried || !hasPerms() || !wifi.isEnabled() || wifi.isConnected()) return;
        for (ScanResult r : wifi.results()) {
            if (r.SSID != null && saved.has(r.SSID)) {
                autoTried = true;
                connectAndSave(r.SSID, saved.getPassword(r.SSID));
                return;
            }
        }
    }

    private void countDevices() {
        if (!wifi.isConnected()) {
            toast("Connectez-vous d'abord à un Wi-Fi");
            return;
        }
        counting = true;
        updateBusy();
        wifi.countDevices(new WifiHelper.Count() {
            @Override
            public void done(int n) {
                counting = false;
                updateBusy();
                devices = n;
                refreshInfo();
            }
        });
    }

    // ---------- Routeur Huawei ----------

    private EditText field(String hint, String initial, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(initial);
        e.setInputType(inputType);
        return e;
    }

    private void copyText(String label, String value) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, value));
        toast("Copié");
    }

    private void routerDialog() {
        if (!wifi.isConnected()) {
            toast("Connectez-vous d'abord au Wi-Fi du routeur");
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        final EditText u = field("Identifiant administrateur", "admin", InputType.TYPE_CLASS_TEXT);
        final EditText p = field("Mot de passe administrateur (étiquette du routeur)", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText n = field("Nouveau mot de passe Wi-Fi (8 caractères min.)", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        box.addView(u);
        box.addView(p);
        box.addView(n);

        new AlertDialog.Builder(this)
                .setTitle("Routeur Huawei")
                .setView(box)
                .setPositiveButton("Continuer", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String np = n.getText().toString();
                        if (np.length() < 8) {
                            toast("Le nouveau mot de passe doit avoir au moins 8 caractères");
                            return;
                        }
                        runRouter(u.getText().toString().trim(), p.getText().toString(), np);
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void runRouter(final String user, final String pass, final String newPass) {
        final String gw = wifi.gateway();
        toast("Analyse du routeur " + gw + "…");
        counting = true;
        updateBusy();
        new Thread(new Runnable() {
            @Override
            public void run() {
                wifi.bindToWifi();
                final HuaweiRouter.Probe pr = HuaweiRouter.probe(gw);
                if ("HILINK".equals(pr.type)) {
                    String msg;
                    boolean ok = false;
                    try {
                        msg = HuaweiRouter.changeHiLink(gw, user, pass, newPass);
                        ok = true;
                    } catch (Exception e) {
                        msg = "Échec : " + e.getMessage();
                    }
                    final String fMsg = msg;
                    final boolean fOk = ok;
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            counting = false;
                            updateBusy();
                            toast(fMsg);
                            if (fOk) onRouterPasswordChanged(newPass);
                        }
                    });
                } else {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            counting = false;
                            updateBusy();
                            showDiagnostic(pr, gw, user, pass, newPass);
                        }
                    });
                }
            }
        }).start();
    }

    private void onRouterPasswordChanged(String newPass) {
        String current = wifi.ssid();
        if (saved.has(current)) {
            saved.save(current, newPass);
            wifi.syncSuggestions(saved.all());
            renderSaved();
        }
        toast("Le téléphone va se déconnecter puis se reconnecter avec le nouveau mot de passe");
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                autoTried = false;
                startScan();
            }
        }, 10000);
    }

    private void showDiagnostic(final HuaweiRouter.Probe pr, final String gw,
                                final String user, final String pass, final String newPass) {
        final String diag = HuaweiRouter.diagnostic(pr, gw);
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Détecté : " + HuaweiRouter.label(pr));
        if ("INJOIGNABLE".equals(pr.type)) {
            b.setMessage("Impossible de joindre le routeur à l'adresse " + gw + ".\n\n" + diag);
        } else {
            b.setMessage("Le changement automatique n'est pas disponible pour ce modèle. "
                    + "L'app peut ouvrir la page du routeur avec la connexion pré-remplie.\n\n" + diag);
            b.setPositiveButton("Ouvrir la page", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    Intent i = new Intent(MainActivity.this, WebActivity.class);
                    i.putExtra("url", "http://" + gw);
                    i.putExtra("user", user);
                    i.putExtra("pass", pass);
                    i.putExtra("newpass", newPass);
                    startActivity(i);
                }
            });
        }
        b.setNeutralButton("Copier le diagnostic", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                copyText("diagnostic", diag);
            }
        });
        b.setNegativeButton("Fermer", null);
        b.show();
    }
}
