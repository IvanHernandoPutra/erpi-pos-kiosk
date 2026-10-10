package com.erpindonesia.pos;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * Overlay In-App Picture-in-Picture untuk Layar Pelanggan.
 * Digunakan untuk Roleplay / Pengujian Single Screen (Laptop / Emulator / Tablet Biasa).
 * Menampilkan customer view berorientasi portrait (800x1280 rasio) dengan tombol Minimize/Expand dan Draggable.
 */
@SuppressLint("SetJavaScriptEnabled")
public class CustomerPipView extends FrameLayout {

    private final Context context;
    private final WebView webView;
    private final LinearLayout header;
    private boolean isMinimized = false;

    private int dX, dY;
    private int lastAction;

    public CustomerPipView(Context context, String posOrigin) {
        super(context);
        this.context = context;

        // Container styling: Card shadow border dengan radius
        setBackgroundColor(Color.parseColor("#0F172A"));
        setPadding(6, 6, 6, 6);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        // Header bar (Title + Minimize toggle)
        header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Color.parseColor("#1E293B"));
        header.setPadding(16, 10, 16, 10);

        TextView title = new TextView(context);
        title.setText("Customer Screen (800x1280)");
        title.setTextColor(Color.parseColor("#38BDF8"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(title, titleParams);

        Button btnMin = new Button(context);
        btnMin.setText("-");
        btnMin.setTextColor(Color.WHITE);
        btnMin.setBackgroundColor(Color.parseColor("#334155"));
        btnMin.setPadding(12, 0, 12, 0);
        btnMin.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnMin.setOnClickListener(v -> toggleMinimize());
        header.addView(btnMin);

        layout.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // WebView customer shell
        webView = new WebView(context);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        loadCustomerShell(posOrigin);

        // Dimensi rasio portrait 800x1280 (diskalakan ke lebar 260dp, tinggi 416dp)
        int widthPx = dpToPx(260);
        int heightPx = dpToPx(416);
        LinearLayout.LayoutParams webParams = new LinearLayout.LayoutParams(widthPx, heightPx);
        layout.addView(webView, webParams);

        addView(layout);

        // Touch listener agar bisa di-drag di layar jika menutupi tombol kasir
        setupDraggable();
    }

    private void loadCustomerShell(String posOrigin) {
        try (InputStream is = context.getAssets().open("customer_shell.html");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String html = reader.lines().collect(Collectors.joining("\n"));
            webView.loadDataWithBaseURL(posOrigin, html, "text/html", "utf-8", null);
        } catch (Exception ignored) {
        }
    }

    public void render(String html) {
        if (webView != null) {
            String js = "window.renderCustomer(" + JSONObject.quote(html) + ");";
            webView.evaluateJavascript(js, null);
        }
    }

    private void toggleMinimize() {
        isMinimized = !isMinimized;
        webView.setVisibility(isMinimized ? View.GONE : View.VISIBLE);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDraggable() {
        header.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dX = (int) (getX() - event.getRawX());
                    dY = (int) (getY() - event.getRawY());
                    lastAction = MotionEvent.ACTION_DOWN;
                    break;
                case MotionEvent.ACTION_MOVE:
                    setY(event.getRawY() + dY);
                    setX(event.getRawX() + dX);
                    lastAction = MotionEvent.ACTION_MOVE;
                    break;
                default:
                    return false;
            }
            return true;
        });
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()
        );
    }
}
