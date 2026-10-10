package com.erpindonesia.pos;

import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * Customer Presentation untuk Layar Kedua Fisik (Display ID > 0).
 */
public class CustomerPresentation extends Presentation {

    private static final String TAG = "CustomerPresentation";
    private final String posOrigin;
    private WebView webView;

    public CustomerPresentation(Context outerContext, Display display, String posOrigin) {
        super(outerContext, display);
        this.posOrigin = (posOrigin != null && !posOrigin.isEmpty()) ? posOrigin : "http://10.0.2.2:8076";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(getContext());
        setContentView(webView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        try (InputStream is = getContext().getAssets().open("customer_shell.html");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String html = reader.lines().collect(Collectors.joining("\n"));
            webView.loadDataWithBaseURL(posOrigin, html, "text/html", "utf-8", null);
        } catch (Exception e) {
            Log.e(TAG, "Gagal memuat customer_shell.html", e);
        }
    }

    public void render(String html) {
        if (webView != null) {
            String js = "window.renderCustomer(" + JSONObject.quote(html) + ");";
            webView.evaluateJavascript(js, null);
        }
    }
}
