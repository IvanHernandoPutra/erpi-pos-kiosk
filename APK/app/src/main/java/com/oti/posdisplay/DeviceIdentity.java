package com.oti.posdisplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Identitas device: SHA-256(ANDROID_ID|MANUFACTURER|MODEL), hex 64 lowercase.
 * Cache di SharedPreferences — ANDROID_ID stabil per install + factory reset.
 */
public final class DeviceIdentity {

    private static final String PREFS = "oti_pos_prefs";
    private static final String PREF_KEY = "device_uid";

    private DeviceIdentity() {
    }

    /** Ambil UID device (cache SharedPreferences, hitung sekali). */
    public static String getUid(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String cached = sp.getString(PREF_KEY, "");
        if (cached != null && cached.length() == 64) return cached;
        String uid = computeUid(
                Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID),
                Build.MANUFACTURER, Build.MODEL);
        sp.edit().putString(PREF_KEY, uid).apply();
        return uid;
    }

    /** Pure: SHA-256 hex 64 lowercase dari "androidId|manufacturer|model". Testable tanpa Android. */
    static String computeUid(String androidId, String manufacturer, String model) {
        String raw = safe(androidId) + "|" + safe(manufacturer) + "|" + safe(model);
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : d) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            // SHA-256 wajib ada di setiap JVM/Android — tidak akan terjadi
            throw new IllegalStateException("SHA-256 tidak tersedia", e);
        }
    }

    /** 6 char pertama uid — kode singkat device utk admin. */
    static String shortCode(String uid) {
        return uid == null || uid.length() < 6 ? "" : uid.substring(0, 6);
    }

    private static String safe(String s) {
        // ANDROID_ID bisa null (jarang) — deterministik: pakai "unknown"
        return s == null || s.isEmpty() ? "unknown" : s;
    }
}
