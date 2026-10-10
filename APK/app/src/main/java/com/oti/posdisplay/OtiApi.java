package com.oti.posdisplay;

import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Klien HTTP untuk /oti/pos/register + /oti/pos/resolve + /oti/pos/heartbeat. Session cookie Odoo dipakai bersama. */
public class OtiApi {

    public interface CallBack {
        void onResult(JSONObject result);
        void onError(String message);
    }

    // ponytail: 2 thread cukup utk fleet 28 device — heartbeat timeout tak lagi blokir antrean (upgrade: pool dinamis bila traffic naik)
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private volatile String sessionCookie = "";

    public void loginPin(String baseUrl, String db, String pin, CallBack cb) {
        JSONObject params = new JSONObject();
        try {
            params.put("db", db);
            params.put("pin", pin);
        } catch (Exception ignored) {
        }
        post(baseUrl + "/oti/pos/login_pin", BuildConfig.APP_TOKEN, params, cb);
    }

    /** Login Odoo (jsonrpc). Simpan cookie session (opsional — POS login biasanya via WebView). */
    public void login(String baseUrl, String db, String login, String password, CallBack cb) {
        JSONObject params = new JSONObject();
        try {
            params.put("db", db);
            params.put("login", login);
            params.put("password", password);
        } catch (Exception ignored) {
        }
        post(baseUrl + "/web/session/authenticate", null, params, cb);
    }

    /** Registrasi device (auth none, app token). UID baru = pending, UID active = update last_seen. */
    public void register(String baseUrl, String deviceUid, String deviceLabel, String model, CallBack cb) {
        JSONObject params = new JSONObject();
        try {
            params.put("device_uid", deviceUid);
            if (deviceLabel != null) params.put("device_label", deviceLabel);
            if (model != null) params.put("model", model);
        } catch (Exception ignored) {
        }
        post(baseUrl + "/oti/pos/register", BuildConfig.APP_TOKEN, params, cb);
    }

    /** Resolve outlet via device binding + GPS (bila ada fix — opsional, tidak blokir). */
    public void resolve(String baseUrl, String deviceUid, double lat, double lon, float accuracy, boolean isMock, CallBack cb) {
        JSONObject params = resolveParams(deviceUid);
        try {
            params.put("lat", lat);
            params.put("lon", lon);
            params.put("accuracy", accuracy);
            params.put("is_mock", isMock);
        } catch (Exception ignored) {
        }
        post(baseUrl + "/oti/pos/resolve", BuildConfig.APP_TOKEN, params, cb);
    }

    /** Resolve outlet via device binding saja (tanpa fix GPS). */
    public void resolve(String baseUrl, String deviceUid, CallBack cb) {
        post(baseUrl + "/oti/pos/resolve", BuildConfig.APP_TOKEN, resolveParams(deviceUid), cb);
    }

    private JSONObject resolveParams(String deviceUid) {
        JSONObject params = new JSONObject();
        try {
            params.put("device_uid", deviceUid);
        } catch (Exception ignored) {
        }
        return params;
    }

    public void heartbeat(String baseUrl, String deviceUid, int sessionId, int configId, double lat, double lon,
                          float accuracy, boolean isMock, boolean gpsOn, CallBack cb) {
        JSONObject params = new JSONObject();
        try {
            params.put("device_uid", deviceUid);
            params.put("session_id", sessionId);
            params.put("config_id", configId);
            params.put("lat", lat);
            params.put("lon", lon);
            params.put("accuracy", accuracy);
            params.put("is_mock", isMock);
            params.put("gps_on", gpsOn);
        } catch (Exception ignored) {
        }
        post(baseUrl + "/oti/pos/heartbeat", BuildConfig.APP_TOKEN, params, cb);
    }

    // ================= Mode Konfigurasi Device (wizard) =================

    /** Daftar pos.config aktif — gate: X-OTI-App-Token + X-OTI-Tech-Pin (PIN teknisi divalidasi server). */
    public void configList(String baseUrl, String techPin, CallBack cb) {
        post(baseUrl + "/oti/pos/config_list", BuildConfig.APP_TOKEN, techPin, new JSONObject(), cb);
    }

    /** Daftar kasir unit outlet (res.users + PIN dari hr.employee). */
    public void outletUsers(String baseUrl, String techPin, int configId, CallBack cb) {
        JSONObject params = new JSONObject();
        try {
            params.put("config_id", configId);
        } catch (Exception ignored) {
        }
        post(baseUrl + "/oti/pos/outlet_users", BuildConfig.APP_TOKEN, techPin, params, cb);
    }

    /** Buat kasir baru (res.users + hr.employee) di unit outlet terpilih. Email wajib. */
    public void createKasir(String baseUrl, String techPin, int configId, String name, String email, String pin, CallBack cb) {
        JSONObject params = new JSONObject();
        try {
            params.put("config_id", configId);
            params.put("name", name);
            params.put("email", email);
            params.put("pin", pin);
        } catch (Exception ignored) {
        }
        post(baseUrl + "/oti/pos/create_kasir", BuildConfig.APP_TOKEN, techPin, params, cb);
    }

    /** Bind device ke outlet (device_uid + config_id). */
    public void bindOutlet(String baseUrl, String techPin, String deviceUid, int configId, CallBack cb) {
        JSONObject params = new JSONObject();
        try {
            params.put("device_uid", deviceUid);
            params.put("config_id", configId);
        } catch (Exception ignored) {
        }
        post(baseUrl + "/oti/pos/bind_outlet", BuildConfig.APP_TOKEN, techPin, params, cb);
    }

    public String sessionCookie() {
        return sessionCookie;
    }

    public void setSessionCookie(String cookie) {
        this.sessionCookie = cookie == null ? "" : cookie;
    }

    private void post(String urlStr, String token, JSONObject params, CallBack cb) {
        post(urlStr, token, null, params, cb);
    }

    /** POST jsonrpc {"jsonrpc","params"}; header X-OTI-App-Token + opsional X-OTI-Tech-Pin (mode konfigurasi). */
    private void post(String urlStr, String token, String techPin, JSONObject params, CallBack cb) {
        io.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");
                if (token != null) {
                    conn.setRequestProperty("X-OTI-App-Token", token);
                }
                if (techPin != null) {
                    conn.setRequestProperty("X-OTI-Tech-Pin", techPin);
                }
                if (!sessionCookie.isEmpty()) {
                    conn.setRequestProperty("Cookie", sessionCookie);
                }
                JSONObject body = new JSONObject();
                body.put("jsonrpc", "2.0");
                body.put("params", params);
                byte[] out = body.toString().getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(out);
                }
                String setC = conn.getHeaderField("Set-Cookie");
                if (setC != null && setC.contains("session_id")) {
                    int i = setC.indexOf("session_id");
                    int j = setC.indexOf(";", i);
                    sessionCookie = "session_id=" + setC.substring(i + "session_id=".length(), j > 0 ? j : setC.length());
                }
                String resp = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JSONObject root = new JSONObject(resp);
                if (root.has("error")) {
                    final String msg = root.getJSONObject("error").getJSONObject("data").optString("message", "server error");
                    postCb(cb, null, msg);
                    return;
                }
                postCb(cb, root.getJSONObject("result"), null);
            } catch (Exception e) {
                postCb(cb, null, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void postCb(CallBack cb, JSONObject result, String err) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (err != null) cb.onError(err);
            else cb.onResult(result);
        });
    }
}
