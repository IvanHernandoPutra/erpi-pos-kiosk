package com.oti.posdisplay;

import android.annotation.SuppressLint;
import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.webkit.WebSettings;
import android.webkit.WebView;

/**
 * Customer-facing screen (Display 2, 800x1280 portrait).
 * Loads shell HTML with base URL = POS origin so relative asset URLs
 * (/web/assets/..., product images) resolve. render(html) mirrors Odoo
 * LocalDisplay.update(): moves .resources into head once, replaces body content.
 */
public class CustomerPresentation extends Presentation {

    private static final String TAG = "CustomerPresentation";
    private final String posOrigin;
    private WebView webView;

    public CustomerPresentation(Context outerContext, Display display, String posOrigin) {
        super(outerContext, display);
        this.posOrigin = (posOrigin != null && !posOrigin.isEmpty()) ? posOrigin : "https://devoti.erpindonesia.net";
    }

    @SuppressLint({"SetJavaScriptEnabled"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(getContext());
        setContentView(webView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Load the shell that defines window.renderCustomer (customer_shell.html asset).
        // Base URL = POS origin so relative URLs (/web/assets/..., product images) resolve.
        try (java.io.InputStream is = getContext().getAssets().open("customer_shell.html");
             java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            webView.loadDataWithBaseURL(posOrigin, sb.toString(), "text/html", "utf-8", null);
        } catch (java.io.IOException e) {
            Log.e(TAG, "Cannot read customer_shell.html", e);
        }
    }

    /** Called from MainActivity bridge with full document HTML from cashier iframe. */
    public void render(String html) {
        if (webView == null) {
            return;
        }
        // JSONObject.quote produces a valid JS string literal (JSON string == JS string)
        String js = "window.renderCustomer(" + org.json.JSONObject.quote(html) + ");";
        webView.evaluateJavascript(js, null);
        Log.d(TAG, "render called, html length=" + html.length());
    }

    public WebView getWebView() {
        return webView;
    }
}
