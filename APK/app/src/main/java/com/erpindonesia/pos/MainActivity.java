package com.erpindonesia.pos;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.erpindonesia.pos.hardware.PosPrinterDriver;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * Main Activity untuk ERPI POS Kiosk.
 * Mengelola WebView Kasir, Dual Screen (Presentation atau In-App PiP), Driver Thermal Printer, dan Settings.
 */
public class MainActivity extends Activity {

    private static final String TAG = "ErpiPosKiosk";

    private FrameLayout root;
    private WebView posWebView;
    private DualScreenManager dualScreenManager;
    private PosPreferences prefs;
    private final PosPrinterDriver printerDriver = new PosPrinterDriver();

    private LinearLayout inspectorBar;
    private TextView tvInspectorStatus;
    private String injectionScript;
    private String pendingCustomerHtml;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        prefs = new PosPreferences(this);
        injectionScript = readAsset("inject_customer_display.js");

        root = new FrameLayout(this);
        setContentView(root);

        // 1. Setup WebView Kasir
        initPosWebView();

        // 2. Setup Dual-Screen Dispatcher (Presentation / PiP)
        dualScreenManager = new DualScreenManager(this, root, prefs.getServerUrl());

        // 3. Setup Hardware Status Bar / Inspector (Bawah layar)
        setupInspectorBar();

        // 4. Cek First-Run Setup: Jika belum pernah setting, buka SetupDialog
        if (!prefs.isConfigured()) {
            ServerSetupDialog.show(this, prefs, (serverUrl, printerIp) -> loadPosUrl(serverUrl));
        } else {
            loadPosUrl(prefs.getServerUrl());
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initPosWebView() {
        posWebView = new WebView(this);
        WebSettings ws = posWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setMediaPlaybackRequiresUserGesture(false);

        // Bridge JS ke Java untuk Layar Customer & Hardware Printer
        posWebView.addJavascriptInterface(new PosWebBridge(), "OtiPosBridge");

        posWebView.setWebChromeClient(new WebChromeClient());
        posWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectCustomerBridge();
                updateInspector("Terkoneksi ke " + url);
            }
        });

        root.addView(posWebView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private void loadPosUrl(String baseUrl) {
        String fullUrl = baseUrl.endsWith("/pos/web") || baseUrl.endsWith("/pos/ui") ? baseUrl : baseUrl + "/pos/ui";
        posWebView.loadUrl(fullUrl);
        updateInspector("Memuat: " + fullUrl);
    }

    private void injectCustomerBridge() {
        if (injectionScript != null && !injectionScript.isEmpty()) {
            posWebView.evaluateJavascript(injectionScript, null);
        }
    }

    private void setupInspectorBar() {
        inspectorBar = new LinearLayout(this);
        inspectorBar.setOrientation(LinearLayout.HORIZONTAL);
        inspectorBar.setBackgroundColor(Color.parseColor("#E60F172A"));
        inspectorBar.setPadding(24, 12, 24, 12);
        inspectorBar.setGravity(Gravity.CENTER_VERTICAL);

        tvInspectorStatus = new TextView(this);
        tvInspectorStatus.setText("Status: Memulai Kasir...");
        tvInspectorStatus.setTextColor(Color.parseColor("#38BDF8"));
        tvInspectorStatus.setTextSize(12);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        inspectorBar.addView(tvInspectorStatus, statusParams);

        // Tombol Tes Buka Laci Tunai
        Button btnDrawer = new Button(this);
        btnDrawer.setText("Tes Laci");
        btnDrawer.setTextSize(11);
        btnDrawer.setBackgroundColor(Color.parseColor("#334155"));
        btnDrawer.setTextColor(Color.WHITE);
        btnDrawer.setOnClickListener(v -> printerDriver.openCashDrawer(prefs.getPrinterIp(), prefs.getPrinterPort()));
        inspectorBar.addView(btnDrawer);

        // Tombol Pengaturan Server / IP
        Button btnConfig = new Button(this);
        btnConfig.setText("Konfigurasi");
        btnConfig.setTextSize(11);
        btnConfig.setBackgroundColor(Color.parseColor("#2563EB"));
        btnConfig.setTextColor(Color.WHITE);
        btnConfig.setOnClickListener(v -> ServerSetupDialog.show(this, prefs, (serverUrl, printerIp) -> loadPosUrl(serverUrl)));
        inspectorBar.addView(btnConfig);

        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        barParams.gravity = Gravity.BOTTOM;
        root.addView(inspectorBar, barParams);

        // Pasang listener status printer
        printerDriver.setListener(new PosPrinterDriver.PrintListener() {
            @Override
            public void onPrintSuccess(String summary) {
                updateInspector(summary);
            }

            @Override
            public void onPrintError(String error) {
                updateInspector("Print Error: " + error);
            }

            @Override
            public void onDrawerOpened() {
                updateInspector("Cash Drawer: KICK Terbuka!");
                Toast.makeText(MainActivity.this, "Laci Kasir Berhasil Terbuka!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateInspector(String text) {
        runOnUiThread(() -> {
            if (tvInspectorStatus != null) {
                tvInspectorStatus.setText("Inspector: " + text);
            }
        });
    }

    private String readAsset(String name) {
        try (InputStream is = getAssets().open(name);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            Log.e(TAG, "Gagal membaca asset " + name, e);
            return null;
        }
    }

    /**
     * Interface Bridge JavaScript Odoo POS -> Android Native.
     */
    public class PosWebBridge {

        @JavascriptInterface
        public void pushCustomerHtml(String html) {
            runOnUiThread(() -> {
                pendingCustomerHtml = html;
                if (dualScreenManager != null) {
                    dualScreenManager.render(html);
                }
            });
        }

        @JavascriptInterface
        public void printReceipt(String receiptData) {
            byte[] bytes = receiptData.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            printerDriver.printRaw(prefs.getPrinterIp(), prefs.getPrinterPort(), bytes, "Struk Penjualan Kasir");
        }

        @JavascriptInterface
        public void openCashDrawer() {
            printerDriver.openCashDrawer(prefs.getPrinterIp(), prefs.getPrinterPort());
        }
    }
}
