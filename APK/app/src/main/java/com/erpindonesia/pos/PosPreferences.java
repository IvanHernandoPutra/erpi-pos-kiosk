package com.erpindonesia.pos;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Pengelola konfigurasi persisten lokal ERPI POS Kiosk.
 * Tidak memerlukan hardcode di build.gradle; mendukung konfigurasi dinamis saat runtime.
 */
public class PosPreferences {

    private static final String PREF_NAME = "erpi_pos_config";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_DEVICE_TOKEN = "device_token";
    private static final String KEY_ADMIN_PIN = "admin_pin";
    private static final String KEY_PRINTER_IP = "printer_ip";
    private static final String KEY_PRINTER_PORT = "printer_port";
    private static final String KEY_SHOW_PIP = "show_pip_preview";

    // Default development fallback (10.0.2.2 = host laptop dari Android Emulator)
    public static final String DEFAULT_SERVER_URL = "http://10.0.2.2:8076";
    public static final String DEFAULT_ADMIN_PIN = "996699";
    public static final String DEFAULT_PRINTER_IP = "10.0.2.2";
    public static final int DEFAULT_PRINTER_PORT = 9100;

    private final SharedPreferences prefs;

    public PosPreferences(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public String getServerUrl() {
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
    }

    public void setServerUrl(String url) {
        if (url != null && url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        prefs.edit().putString(KEY_SERVER_URL, url).apply();
    }

    public String getDeviceToken() {
        return prefs.getString(KEY_DEVICE_TOKEN, "");
    }

    public void setDeviceToken(String token) {
        prefs.edit().putString(KEY_DEVICE_TOKEN, token).apply();
    }

    public String getAdminPin() {
        return prefs.getString(KEY_ADMIN_PIN, DEFAULT_ADMIN_PIN);
    }

    public void setAdminPin(String pin) {
        prefs.edit().putString(KEY_ADMIN_PIN, pin).apply();
    }

    public String getPrinterIp() {
        return prefs.getString(KEY_PRINTER_IP, DEFAULT_PRINTER_IP);
    }

    public void setPrinterIp(String ip) {
        prefs.edit().putString(KEY_PRINTER_IP, ip).apply();
    }

    public int getPrinterPort() {
        return prefs.getInt(KEY_PRINTER_PORT, DEFAULT_PRINTER_PORT);
    }

    public void setPrinterPort(int port) {
        prefs.edit().putInt(KEY_PRINTER_PORT, port).apply();
    }

    public boolean isShowPipPreview() {
        return prefs.getBoolean(KEY_SHOW_PIP, true);
    }

    public void setShowPipPreview(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_PIP, show).apply();
    }

    public boolean isConfigured() {
        return prefs.contains(KEY_SERVER_URL);
    }
}
