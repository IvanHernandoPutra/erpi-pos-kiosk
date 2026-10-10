package com.oti.posdisplay;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Flow: register device -> login PIN -> resolve outlet via device binding
 * (GPS opsional — data forensik) -> POS WebView -> heartbeat + violation log.
 * Lock hanya untuk mock / server reject. Kiosk: startLockTask saat POS terbuka.
 */
public class MainActivity extends Activity implements LocationGate.Callback {

    private static final String TAG = "OtiPosDisplay";
    private static final String PREFS = "oti_pos_prefs";

    private final String baseUrl = BuildConfig.POS_URL;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private WebView posWebView;
    private LinearLayout lockOverlay;
    private TextView lockTitle, lockMessage, gateStatus;
    private LinearLayout loginView;
    private EditText etPin;

    private LocationGate gate;
    private final OtiApi api = new OtiApi();
    private String savedPin;

    private int configId = 0;
    private String configName = "";
    private int heartbeatInterval = 120;
    private boolean posOpened = false;
    private boolean locked = false;
    private boolean loginDone = false;
    private boolean resolving = false;
    private String deviceUid = "";
    private int tapCount = 0;          // detektor 10x tap utk wizard konfigurasi
    private long tapWindowStart = 0L;  // awal window 4 dtk

/** Tiket akses POS dari resolve — wajib utk buka /pos/ui (server guard). */
private String accessTicket = "";
    private String deviceRegState = "";
    private boolean registerSent = false;
    private Runnable pendingRetry;
    private CustomerPresentation customerPresentation;
    private String injectionScript;
    private String pendingHtml;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        setContentView(root);

        buildLockOverlay();
        buildLoginView();
        buildWebView();
        buildExitButton();

        gate = new LocationGate(this, this);

        if (!gate.hasPermission()) {
            gate.requestPermission();
        } else {
            startGate();
        }
    }

    private void buildExitButton() {
        // Tombol keluar kecil pojok kanan atas — minta PIN admin (device C8G tanpa launcher)
        Button btnExit = new Button(this);
        btnExit.setText("✕");
        btnExit.setTextSize(14);
        btnExit.setBackgroundColor(Color.TRANSPARENT);
        btnExit.setTextColor(Color.parseColor("#94A3B8"));
        btnExit.setPadding(8, 4, 8, 4);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        lp.topMargin = 12;
        lp.rightMargin = 12;
        btnExit.setOnClickListener(v -> showExitDialog());
        root.addView(btnExit, lp);
    }

    private void showExitDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 32, 48, 16);
        final EditText etAdminPin = new EditText(this);
        etAdminPin.setHint("PIN Admin");
        etAdminPin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etAdminPin.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
        box.addView(etAdminPin);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Keluar Aplikasi?")
                .setMessage("Masukkan PIN admin untuk keluar.")
                .setView(box)
                .setPositiveButton("Keluar", (d, w) -> {
                    String pin = etAdminPin.getText().toString().trim();
                    if (BuildConfig.DEBUG) Log.i(TAG, "exit pin len=" + pin.length()
                            + " match=" + pin.equals(BuildConfig.ADMIN_EXIT_PIN));
                    if (pin.equals(BuildConfig.ADMIN_EXIT_PIN)) {
                        stopLockTask();
                        finishAffinity();
                    } else {
                        Toast.makeText(this, "PIN admin salah", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void startGate() {
        if (!gate.isGpsOn()) {
            // GPS kini data forensik saja (device binding) — tidak mengunci, cukup log
            Log.w(TAG, "startGate: GPS nonaktif — lanjut tanpa fix");
        }
        gate.start();
        // Login tidak butuh fix GPS — resolve outlet via device binding.
        showLogin();
        registerDevice();
    }

    // ================= LocationGate.Callback =================

    @Override
    public void onFix(Location loc, boolean isMock) {
        if (isMock) {
            lock("LOKASI PALSU TERDETEKSI", "Aplikasi lokasi palsu tidak diizinkan. Akses ditolak.", true);
            return;
        }
        if (loginDone && !posOpened && !resolving) {
            resolveOutlet();
        }
    }

    @Override
    public void onProviderStateChanged(boolean gpsOn) {
        // GPS kini data forensik — off/on TIDAK lagi mengunci POS, cukup log + violation
        if (!gpsOn) {
            Log.w(TAG, "onProviderStateChanged: GPS dimatikan — catat violation, POS tetap jalan");
            if (posOpened) {
                sendViolationHeartbeat(false, null, null);
            }
        } else {
            Log.i(TAG, "onProviderStateChanged: GPS nyala kembali");
        }
    }

    // ================= UI builders =================

    private void buildLockOverlay() {
        lockOverlay = new LinearLayout(this);
        lockOverlay.setOrientation(LinearLayout.VERTICAL);
        lockOverlay.setGravity(Gravity.CENTER);
        lockOverlay.setBackgroundColor(Color.parseColor("#E6180C0C"));
        lockOverlay.setPadding(48, 48, 48, 48);

        lockTitle = new TextView(this);
        lockTitle.setTextSize(26);
        lockTitle.setTypeface(null, Typeface.BOLD);
        lockTitle.setTextColor(Color.WHITE);
        lockTitle.setGravity(Gravity.CENTER);
        lockOverlay.addView(lockTitle);

        lockMessage = new TextView(this);
        lockMessage.setTextSize(16);
        lockMessage.setTextColor(Color.parseColor("#FFD6D6"));
        lockMessage.setGravity(Gravity.CENTER);
        lockMessage.setPadding(0, 24, 0, 32);
        lockOverlay.addView(lockMessage);

        Button btnSettings = new Button(this);
        btnSettings.setText("Buka Pengaturan Lokasi");
        btnSettings.setTextSize(16);
        btnSettings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        lockOverlay.addView(btnSettings);

        lockOverlay.setVisibility(View.GONE);
        root.addView(lockOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildLoginView() {
        loginView = new LinearLayout(this);
        loginView.setOrientation(LinearLayout.VERTICAL);
        loginView.setGravity(Gravity.CENTER);
        loginView.setBackgroundColor(Color.parseColor("#F8FAFC"));
        loginView.setPadding(64, 64, 64, 64);

        TextView title = new TextView(this);
        title.setText("Masuk POS OTI");
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Color.parseColor("#0F172A"));
        loginView.addView(title);

        gateStatus = new TextView(this);
        gateStatus.setTextSize(14);
        gateStatus.setGravity(Gravity.CENTER);
        gateStatus.setTextColor(Color.parseColor("#475569"));
        gateStatus.setPadding(0, 12, 0, 32);
        loginView.addView(gateStatus);

        etPin = new EditText(this);
        etPin.setHint("PIN Kasir (6 Angka)");
        etPin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etPin.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
        etPin.setTextSize(26);
        etPin.setGravity(Gravity.CENTER);
        etPin.setTextColor(Color.parseColor("#0F172A"));
        etPin.setBackgroundColor(Color.parseColor("#E2E8F0"));
        etPin.setPadding(24, 24, 24, 24);
        LinearLayout.LayoutParams pinParams = new LinearLayout.LayoutParams(
                600, ViewGroup.LayoutParams.WRAP_CONTENT);
        pinParams.gravity = Gravity.CENTER;
        pinParams.bottomMargin = 32;
        loginView.addView(etPin, pinParams);

        Button btnLogin = new Button(this);
        btnLogin.setText("Masuk");
        btnLogin.setTextSize(18);
        btnLogin.setBackgroundColor(Color.parseColor("#0284C7"));
        btnLogin.setTextColor(Color.WHITE);
        btnLogin.setOnClickListener(v -> doLogin());
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                600, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.gravity = Gravity.CENTER;
        loginView.addView(btnLogin, btnParams);

        loginView.setVisibility(View.GONE);
        root.addView(loginView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @SuppressLint({"SetJavaScriptEnabled"})
    private void buildWebView() {
        posWebView = new WebView(this);
        posWebView.setVisibility(View.GONE);

        WebSettings ws = posWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        // Desktop UA: Odoo menyembunyikan tombol CustomerFacingDisplay di mobile UA
        ws.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");

        posWebView.setWebChromeClient(new WebChromeClient());
        posWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                String u = req.getUrl().toString();
                String path = req.getUrl().getPath();
                // Hanya rute guard POS yang perlu intercept & inject token header
                boolean isGuardRoute = path != null && (
                        path.equals("/pos/ui") || path.startsWith("/pos/ui/") ||
                        path.equals("/pos/web") || path.startsWith("/pos/web/") ||
                        path.equals("/pos/customer_display") || path.startsWith("/pos/customer_display/"));

                if (!isGuardRoute) {
                    return super.shouldInterceptRequest(view, req);
                }

                Map<String, String> headers = new HashMap<>(req.getRequestHeaders());
                headers.put("X-OTI-App-Token", BuildConfig.APP_TOKEN);
                if (!accessTicket.isEmpty()) headers.put("X-OTI-Access-Ticket", accessTicket);
                try {
                    java.net.URL url = new java.net.URL(u);
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) url.openConnection();
                    c.setInstanceFollowRedirects(true);
                    for (Map.Entry<String, String> e : headers.entrySet()) {
                        if (e.getValue() != null) c.setRequestProperty(e.getKey(), e.getValue());
                    }
                    try {
                        String ck = CookieManager.getInstance().getCookie(u);
                        if (ck != null && !ck.isEmpty()) c.setRequestProperty("Cookie", ck);
                    } catch (Exception ignored) {
                    }
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(15000);
                    int code = c.getResponseCode();
                    Log.d(TAG, "intercept " + u + " -> " + code);

                    String rawContentType = c.getContentType();
                    String mimeType = "text/html";
                    String encoding = "utf-8";
                    if (rawContentType != null) {
                        String[] parts = rawContentType.split(";");
                        mimeType = parts[0].trim();
                        for (int i = 1; i < parts.length; i++) {
                            String p = parts[i].trim().toLowerCase();
                            if (p.startsWith("charset=")) {
                                encoding = p.substring("charset=".length()).trim();
                            }
                        }
                    }

                    InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    String enc = c.getContentEncoding();
                    if ("gzip".equalsIgnoreCase(enc) && is != null) {
                        is = new java.util.zip.GZIPInputStream(is);
                    }
                    if (is == null) return super.shouldInterceptRequest(view, req);
                    return new WebResourceResponse(mimeType, encoding, is);
                } catch (IOException e) {
                    Log.e(TAG, "intercept fail " + u + ": " + e);
                    return super.shouldInterceptRequest(view, req);
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (injectionScript != null) view.evaluateJavascript(injectionScript, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (injectionScript != null) view.evaluateJavascript(injectionScript, null);
                if (url.contains("/pos/") && !locked) {
                    try {
                        startLockTask(); // kiosk
                    } catch (Exception e) {
                        Log.w(TAG, "startLockTask gagal: " + e.getMessage());
                    }
                }
                // Odoo redirect ke backend dashboard setelah close session — balik ke login POS.
                // URL POS asli selalu punya /pos/ui atau /pos/web; dashboard backend pakai /odoo/ atau /web.
                if (posOpened && !url.contains("/pos/ui") && !url.contains("/pos/web")
                        && (url.contains("/odoo/") || url.contains("/web"))) {
                    Log.i(TAG, "session closed (redirect ke " + url + ") — kembali ke login");
                    resetToLogin();
                }
            }
        });

        posWebView.addJavascriptInterface(new Bridge(), "OtiPosBridge");
        injectionScript = readAsset("inject_customer_display.js");

        String origin = baseUrl;
        try {
            java.net.URI uri = new java.net.URI(baseUrl);
            origin = uri.getScheme() + "://" + uri.getAuthority();
        } catch (Exception ignored) {
        }
        setupCustomerPresentation(origin);

        root.addView(posWebView, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ================= Login + resolve =================

    private void showLogin() {
        if (loginView.getVisibility() == View.VISIBLE) return;
        loginView.setVisibility(View.VISIBLE);
        gateStatus.setText("Menunggu login kasir");
    }

    /** Reset app ke layar login — dipanggil saat Odoo redirect ke backend (session closed). */
    private void resetToLogin() {
        handler.removeCallbacks(heartbeatTask);
        posOpened = false;
        loginDone = false;
        resolving = false;
        savedPin = null;
        etPin.setText("");
        posWebView.stopLoading();
        posWebView.setVisibility(View.GONE);
        loginView.setVisibility(View.VISIBLE);
        gateStatus.setText("Session ditutup. Menunggu login kasir");
        Log.i(TAG, "resetToLogin: kembali ke layar login");
    }

    // ================= Mode Konfigurasi Device (wizard teknisi) =================

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 10x tap (window 4 dtk) di layar login — buka wizard. Event tetap diteruskan,
        // jadi input PIN + tombol Masuk tidak terganggu.
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN
                && !posOpened
                && loginView.getVisibility() == View.VISIBLE) {
            long now = SystemClock.elapsedRealtime();
            if (tapWindowStart == 0L || now - tapWindowStart > 4000L) {
                tapWindowStart = now; // window baru
                tapCount = 1;
            } else {
                tapCount++;
            }
            if (tapCount >= 10) {
                tapCount = 0;
                tapWindowStart = 0L;
                openConfigWizard();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    /** Buka Mode Konfigurasi Device — dipicu 10x tap cepat di layar login. */
    private void openConfigWizard() {
        if (deviceUid.isEmpty()) deviceUid = DeviceIdentity.getUid(this); // guard: register bisa belum balas
        if (BuildConfig.DEBUG) Log.i(TAG, "10x tap terdeteksi — buka ConfigWizard");
        new ConfigWizard(this, baseUrl, deviceUid, api).show();
    }

    private void doLogin() {
        if (resolving || loginDone) return; // anti double-tap "Masuk"
        String pin = etPin.getText().toString().trim();
        if (pin.length() < 4) {
            Toast.makeText(this, "Masukkan PIN minimal 4 angka", Toast.LENGTH_SHORT).show();
            return;
        }
        gateStatus.setText("Memverifikasi PIN...");
        if (BuildConfig.DEBUG) Log.i(TAG, "doLogin: pin length=" + pin.length() + " url=" + baseUrl);
        savedPin = pin;
        api.loginPin(baseUrl, BuildConfig.DB_NAME, pin, new OtiApi.CallBack() {
            @Override
            public void onResult(JSONObject result) {
                if (result != null && result.optBoolean("ok")) {
                    loginDone = true;
                    injectSessionCookie();
                    gateStatus.setText("PIN valid — mencari outlet...");
                    resolveOutlet();
                } else {
                    String msg = result != null ? result.optString("message", "PIN salah") : "PIN salah";
                    gateStatus.setText(msg);
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "loginPin error: " + message);
                gateStatus.setText("Koneksi gagal: " + message);
            }
        });
    }

    private void injectSessionCookie() {
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            String c = api.sessionCookie();
            if (!c.isEmpty()) {
                cm.setCookie(baseUrl, c + "; Path=/");
                cm.flush();
            }
        } catch (Exception e) {
            Log.w(TAG, "injectSessionCookie: " + e.getMessage());
        }
    }

    /** Registrasi device ke server (sekali per launch). Hasil diingat di field utk status login. */
    private void registerDevice() {
        if (registerSent) return;
        registerSent = true;
        deviceUid = DeviceIdentity.getUid(this);
        String label = Build.MODEL;
        String model = Build.MANUFACTURER + " " + Build.MODEL;
        api.register(baseUrl, deviceUid, label, model, new OtiApi.CallBack() {
            @Override
            public void onResult(JSONObject r) {
                if (r != null && r.optBoolean("ok")) {
                    deviceRegState = r.optString("state", "");
                    Log.i(TAG, "register device ok state=" + deviceRegState);
                    if ("pending".equals(deviceRegState) && !posOpened) {
                        showGateStatus("Device menunggu approval admin — kode device: "
                                + DeviceIdentity.shortCode(deviceUid));
                    }
                } else {
                    deviceRegState = "error";
                    Log.w(TAG, "register device gagal: " + (r != null ? r.optString("code", "?") : "null"));
                }
            }

            @Override
            public void onError(String message) {
                deviceRegState = "error";
                registerSent = false; // izinkan retry — device belum terdaftar
                Log.e(TAG, "register device error: " + message);
                showGateStatus("Registrasi device gagal — akan dicoba ulang");
                handler.postDelayed(MainActivity.this::registerDevice, 10000);
            }
        });
    }

    private void resolveOutlet() {
        if (posOpened) return; // POS sudah buka — jangan resolve (anti reload mid-transaksi)
        Log.i(TAG, "resolveOutlet called, loginDone=" + loginDone);
        Location f = gate.freshFix(60000);
        boolean testFix = false;
        if (f == null && BuildConfig.DEBUG) {
            // ponytail: dev-only fallback indoor (ceiling: tak ada di release build)
            f = new Location("test");
            f.setLatitude(BuildConfig.TEST_LAT);
            f.setLongitude(BuildConfig.TEST_LON);
            f.setAccuracy(15);
            f.setTime(System.currentTimeMillis());
            testFix = true;
            Log.w(TAG, "resolveOutlet: pakai TEST coords (debug fallback, indoor tanpa fix)");
        }
        resolving = true;
        if (f != null) {
            // GPS forensik ikut dikirim bila ada; outlet tetap dari device binding
            Log.i(TAG, "resolveOutlet: device_uid + GPS " + f.getLatitude() + "," + f.getLongitude()
                    + " acc=" + f.getAccuracy() + " test=" + testFix);
            api.resolve(baseUrl, deviceUid, f.getLatitude(), f.getLongitude(), f.getAccuracy(), false, resolveCb());
        } else {
            Log.i(TAG, "resolveOutlet: tanpa fix GPS — resolve via device binding saja");
            api.resolve(baseUrl, deviceUid, resolveCb());
        }
    }

    /** Callback resolve bersama (dengan/ tanpa GPS). Status device tampil di layar login, bukan lock merah. */
    private OtiApi.CallBack resolveCb() {
        return new OtiApi.CallBack() {
            @Override
            public void onResult(JSONObject r) {
                resolving = false;
                if (r != null && r.optBoolean("ok")) {
                    configId = r.optInt("config_id");
                    configName = r.optString("config_name");
                    heartbeatInterval = r.optInt("heartbeat_interval", 120);
                    accessTicket = r.optString("access_ticket", "");
                    openPos();
                    return;
                }
                String code = r != null ? r.optString("code", "?") : "?";
                String msg = r != null ? r.optString("message", "") : "";
                String kode = DeviceIdentity.shortCode(deviceUid);
                if ("pending_approval".equals(code)) {
                    showGateStatus("Device menunggu approval admin — hubungi admin. Kode device: " + kode);
                } else if ("unknown_device".equals(code)) {
                    showGateStatus("Device belum dikenal server — hubungi admin. Kode device: " + kode);
                } else if ("blocked".equals(code)) {
                    showGateStatus("DITOLAK: device diblokir admin. Kode device: " + kode);
                } else {
                    lock("AKSES DITOLAK", msg.isEmpty() ? ("Kode: " + code) : msg, true);
                }
            }

            @Override
            public void onError(String message) {
                resolving = false;
                showGateStatus("Resolve gagal: " + message + " — retry 10 dtk");
                handler.removeCallbacks(pendingRetry); // anti duplikat retry chain
                pendingRetry = MainActivity.this::resolveOutlet;
                handler.postDelayed(pendingRetry, 10000);
            }
        };
    }

    private void openPos() {
        if (posOpened) return; // sudah buka — jangan loadUrl ulang (anti reload mid-transaksi)
        handler.removeCallbacks(pendingRetry); // sukses — batalkan retry stale
        posOpened = true;
        locked = false;
        loginView.setVisibility(View.GONE);
        lockOverlay.setVisibility(View.GONE);
        posWebView.setVisibility(View.VISIBLE);
        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put("X-OTI-App-Token", BuildConfig.APP_TOKEN);
        if (!accessTicket.isEmpty()) extraHeaders.put("X-OTI-Access-Ticket", accessTicket);
        posWebView.loadUrl(baseUrl + "/pos/ui?config_id=" + configId, extraHeaders);
        startHeartbeat();
        Toast.makeText(this, "Outlet: " + configName, Toast.LENGTH_LONG).show();
    }

    // ================= Heartbeat + lock =================

    private final Runnable heartbeatTask = new Runnable() {
        @Override
        public void run() {
            if (!posOpened) return;
            if (locked) return;
            boolean gpsOn = gate.isGpsOn();
            Location f = gate.lastFix();
            boolean mock = f != null && gate.isMock(f);
            if (mock || !gpsOn) {
                // Violation GPS: tercatat via heartbeat, TIDAK lock (spec device binding)
                sendViolationHeartbeat(mock, f, gpsOn);
                handler.postDelayed(this, heartbeatInterval * 1000L);
                return;
            }
            if (f == null) {
                // Indoor tanpa fix: jangan hukum, tunggu fix berikutnya
                handler.postDelayed(this, heartbeatInterval * 1000L);
                return;
            }
            api.heartbeat(baseUrl, deviceUid, 0, configId, f.getLatitude(), f.getLongitude(),
                    f.getAccuracy(), false, true, new OtiApi.CallBack() {
                        @Override
                        public void onResult(JSONObject r) {
                            if (r != null && !r.optBoolean("ok")) {
                                // Server eksplisit menolak device (mis. blocked) — kunci
                                lock("AKSES DITOLAK", "Server menolak device ini.\nHubungi admin. Kode device: "
                                        + DeviceIdentity.shortCode(deviceUid), false);
                            }
                        }

                        @Override
                        public void onError(String message) {
                            // session expired -> login ulang senyap dengan PIN
                            if (savedPin != null && message != null && message.toLowerCase().contains("session")) {
                                api.loginPin(baseUrl, BuildConfig.DB_NAME, savedPin, new OtiApi.CallBack() {
                                    @Override
                                    public void onResult(JSONObject result) {
                                        injectSessionCookie();
                                    }

                                    @Override
                                    public void onError(String m) {
                                    }
                                });
                            }
                        }
                    });
            handler.postDelayed(this, heartbeatInterval * 1000L);
        }
    };

    private void startHeartbeat() {
        handler.removeCallbacks(heartbeatTask);
        handler.postDelayed(heartbeatTask, heartbeatInterval * 1000L);
    }

    private void sendViolationHeartbeat(boolean isMock, Location f, Boolean gpsOn) {
        double lat = f != null ? f.getLatitude() : 0;
        double lon = f != null ? f.getLongitude() : 0;
        float acc = f != null ? f.getAccuracy() : 0;
        api.heartbeat(baseUrl, deviceUid, 0, configId, lat, lon, acc, isMock, gpsOn != null && gpsOn, new OtiApi.CallBack() {
            @Override
            public void onResult(JSONObject r) {
            }

            @Override
            public void onError(String m) {
            }
        });
    }

    private void lock(String title, String msg, boolean gpsRelated) {
        locked = true;
        handler.removeCallbacks(heartbeatTask);
        lockTitle.setText(title);
        lockMessage.setText(msg);
        lockOverlay.setVisibility(View.VISIBLE);
        lockOverlay.bringChildToFront(lockOverlay);
        // reset resolve state supaya onProviderStateChanged bisa re-resolve
        posOpened = false;
    }

    private void unlockAttempt() {
        // dipanggil dari onProviderStateChanged -> resolveOutlet -> openPos (locked=false)
    }

    private void showGateStatus(String s) {
        gateStatus.setText(s);
    }

    // ================= Customer display =================

    private void setupCustomerPresentation(String origin) {
        android.hardware.display.DisplayManager dm =
                (android.hardware.display.DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        android.view.Display secondary = null;
        for (android.view.Display d : dm.getDisplays()) {
            if (d.getDisplayId() != android.view.Display.DEFAULT_DISPLAY) {
                secondary = d;
                break;
            }
        }
        if (secondary != null) {
            customerPresentation = new CustomerPresentation(this, secondary, origin);
            customerPresentation.show();
            Log.i(TAG, "Customer presentation shown on display " + secondary.getDisplayId());
        } else {
            Log.w(TAG, "No secondary display found");
        }
    }

    private String readAsset(String name) {
        try (InputStream is = getAssets().open(name);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            Log.e(TAG, "Cannot read asset " + name, e);
            return null;
        }
    }

    /** JS bridge: cashier WebView -> native -> presentation WebView */
    private class Bridge {
        @JavascriptInterface
        public void push(final String html) {
            runOnUiThread(() -> {
                if (customerPresentation != null) {
                    customerPresentation.render(html);
                } else {
                    pendingHtml = html;
                }
            });
        }
    }

    // ================= Lifecycle =================

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == 1001) {
            boolean granted = false;
            for (int r : results) if (r == android.content.pm.PackageManager.PERMISSION_GRANTED) granted = true;
            if (granted) startGate();
            else {
                // Izin lokasi ditolak — GPS forensik saja, device binding tetap jalan
                Log.w(TAG, "Izin lokasi ditolak — lanjut tanpa GPS");
                startGate();
            }
        }
    }

    @Override
    public void onBackPressed() {
        // Kiosk: tidak keluar saat POS aktif
        if (posOpened && !locked) return;
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (gate != null) gate.stop();
        if (customerPresentation != null) customerPresentation.dismiss();
        super.onDestroy();
    }
}
