package com.oti.posdisplay;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Mode Konfigurasi Device — wizard 3 layar utk teknisi:
 * 1) PIN teknisi (divalidasi server lewat config_list + header X-OTI-Tech-Pin),
 * 2) pilih outlet (pos.config aktif dari server),
 * 3) konfirmasi + daftar kasir unit + buat kasir baru + bind device.
 * Dibuka dari MainActivity: 10x tap dalam 4 detik di layar login.
 * Sukses bind: popup, delay 2 dtk, stopLockTask + finishAffinity (pola exit admin).
 */
public class ConfigWizard extends Dialog {

    private final Activity activity;
    private final String baseUrl;
    private final String deviceUid;
    private final OtiApi api;
    private final String shortCode;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String techPin = "";
    private JSONArray outlets = new JSONArray();
    private JSONObject outlet = null; // outlet terpilih (pos.config)
    private JSONArray kasirRows = new JSONArray();

    private LinearLayout content; // container layar aktif (swap)
    private final List<TextView> outletRowViews = new ArrayList<>();
    private LinearLayout kasirListbox; // container baris kasir layar 3
    private AlertDialog successDialog = null;

    public ConfigWizard(Activity activity, String baseUrl, String deviceUid, OtiApi api) {
        super(activity);
        this.activity = activity;
        this.baseUrl = baseUrl;
        this.deviceUid = deviceUid == null ? "" : deviceUid;
        this.api = api;
        this.shortCode = DeviceIdentity.shortCode(this.deviceUid);

        setCancelable(true);
        setCanceledOnTouchOutside(false);

        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(48, 48, 48, 48);
        setContentView(content);

        showScreen(buildScreenPin());
    }

    @Override
    protected void onStart() {
        super.onStart();
        Window w = getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#0F172A")));
        }
    }

    private void showScreen(View screen) {
        content.removeAllViews();
        content.addView(screen, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ================= Layar 1 — PIN teknisi =================

    private View buildScreenPin() {
        LinearLayout box = vBox();
        box.addView(text("Mode Konfigurasi Device", 22, true));
        box.addView(dimText("Masukkan PIN teknisi", 14));

        final EditText etTech = new EditText(activity);
        etTech.setHint("PIN Teknisi");
        etTech.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etTech.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        etTech.setTextSize(22);
        etTech.setGravity(Gravity.CENTER);
        etTech.setTextColor(Color.parseColor("#F8FAFC"));
        etTech.setHintTextColor(Color.parseColor("#94A3B8"));
        etTech.setBackgroundColor(Color.parseColor("#1E293B"));
        etTech.setPadding(24, 24, 24, 24);
        box.addView(etTech, linParams(600));

        final Button btnNext = button("Lanjut", "#0284C7");
        btnNext.setOnClickListener(v -> {
            final String pin = etTech.getText().toString().trim();
            if (pin.isEmpty()) {
                toast("Masukkan PIN teknisi");
                return;
            }
            btnNext.setEnabled(false);
            // config_list sekaligus validasi PIN teknisi di server (header X-OTI-Tech-Pin)
            api.configList(baseUrl, pin, new OtiApi.CallBack() {
                @Override
                public void onResult(JSONObject r) {
                    btnNext.setEnabled(true);
                    if (r != null && r.optBoolean("ok")) {
                        techPin = pin;
                        JSONArray arr = r.optJSONArray("outlets");
                        outlets = arr != null ? arr : new JSONArray();
                        showScreen(buildScreenOutlet());
                    } else {
                        String msg = r != null ? r.optString("message", "") : "";
                        toast(msg.isEmpty() ? "PIN teknisi salah" : msg);
                    }
                }

                @Override
                public void onError(String message) {
                    btnNext.setEnabled(true);
                    toast("Koneksi gagal: " + message);
                }
            });
        });
        box.addView(btnNext, linParams(600));

        Button btnClose = button("Tutup", "#334155");
        btnClose.setOnClickListener(v -> dismiss());
        box.addView(btnClose, linParams(600));
        return box;
    }

    // ================= Layar 2 — Pilih outlet =================

    private View buildScreenOutlet() {
        LinearLayout box = vBox();
        box.addView(text("Mode Konfigurasi Device", 22, true));
        box.addView(dimText("Device " + shortCode + " — " + Build.MANUFACTURER + " " + Build.MODEL, 13));

        TextView lbl = dimText("Pilih outlet:", 15);
        lbl.setPadding(0, 24, 0, 8);
        box.addView(lbl);

        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        outletRowViews.clear();
        if (outlets.length() == 0) {
            rows.addView(dimText("Tidak ada outlet aktif di server", 14));
        }
        for (int i = 0; i < outlets.length(); i++) {
            final JSONObject o = outlets.optJSONObject(i);
            if (o == null) continue;
            String unit = o.optString("unit", "");
            String label = o.optString("name", "?") + (unit.isEmpty() ? "" : " — " + unit);
            final TextView row = new TextView(activity);
            row.setText(label);
            row.setTextSize(16);
            row.setTextColor(Color.parseColor("#F8FAFC"));
            row.setPadding(20, 18, 20, 18);
            row.setBackgroundColor(Color.parseColor("#1E293B"));
            row.setOnClickListener(v -> selectOutlet(o, row));
            outletRowViews.add(row);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = 6;
            rows.addView(row, lp);
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(rows);
        box.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)); // isi sisa tinggi

        LinearLayout navRow = new LinearLayout(activity);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setGravity(Gravity.CENTER_HORIZONTAL);
        Button btnBack = button("← Kembali", "#334155");
        btnBack.setOnClickListener(v -> showScreen(buildScreenPin()));
        LinearLayout.LayoutParams bpl = new LinearLayout.LayoutParams(280,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bpl.rightMargin = 12;
        navRow.addView(btnBack, bpl);

        final Button btnPick = button("Pilih", "#0284C7");
        navRow.addView(btnPick, new LinearLayout.LayoutParams(280,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        btnPick.setOnClickListener(v -> {
            if (outlet == null) {
                toast("Pilih outlet dulu");
                return;
            }
            showScreen(buildScreenConfirm());
            fetchKasir();
        });
        box.addView(navRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private void selectOutlet(JSONObject o, TextView row) {
        outlet = o;
        for (TextView t : outletRowViews) {
            t.setBackgroundColor(Color.parseColor("#1E293B"));
        }
        row.setBackgroundColor(Color.parseColor("#0284C7"));
    }

    // ================= Layar 3 — Konfirmasi + kasir + bind =================

    private View buildScreenConfirm() {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout box = vBox();
        scroll.addView(box);

        TextView name = text(outlet.optString("name", "?"), 24, true);
        name.setPadding(0, 8, 0, 8);
        box.addView(name);
        box.addView(dimText("Device " + shortCode + " akan di-bind ke outlet ini", 13));

        TextView h1 = text("Kasir Outlet Ini", 16, true);
        h1.setPadding(0, 24, 0, 8);
        box.addView(h1);
        kasirListbox = new LinearLayout(activity);
        kasirListbox.setOrientation(LinearLayout.VERTICAL);
        box.addView(kasirListbox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView h2 = text("Kasir Baru", 16, true);
        h2.setPadding(0, 24, 0, 8);
        box.addView(h2);

        final EditText etName = new EditText(activity);
        etName.setHint("Nama Kasir");
        etName.setTextSize(16);
        etName.setTextColor(Color.parseColor("#F8FAFC"));
        etName.setHintTextColor(Color.parseColor("#94A3B8"));
        etName.setBackgroundColor(Color.parseColor("#1E293B"));
        etName.setPadding(20, 16, 20, 16);
        box.addView(etName, linParams(ViewGroup.LayoutParams.MATCH_PARENT));

        final EditText etEmail = new EditText(activity);
        etEmail.setHint("Email (wajib)");
        etEmail.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        etEmail.setTextSize(16);
        etEmail.setTextColor(Color.parseColor("#F8FAFC"));
        etEmail.setHintTextColor(Color.parseColor("#94A3B8"));
        etEmail.setBackgroundColor(Color.parseColor("#1E293B"));
        etEmail.setPadding(20, 16, 20, 16);
        box.addView(etEmail, linParams(ViewGroup.LayoutParams.MATCH_PARENT));

        final EditText etPinNew = new EditText(activity);
        etPinNew.setHint("PIN (6 Angka)");
        etPinNew.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etPinNew.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        etPinNew.setTextSize(16);
        etPinNew.setTextColor(Color.parseColor("#F8FAFC"));
        etPinNew.setHintTextColor(Color.parseColor("#94A3B8"));
        etPinNew.setBackgroundColor(Color.parseColor("#1E293B"));
        etPinNew.setPadding(20, 16, 20, 16);
        box.addView(etPinNew, linParams(ViewGroup.LayoutParams.MATCH_PARENT));

        final Button btnCreate = button("Buat Kasir", "#334155");
        btnCreate.setOnClickListener(v -> {
            String nm = etName.getText().toString().trim();
            String em = etEmail.getText().toString().trim();
            String pw = etPinNew.getText().toString().trim();
            if (nm.isEmpty()) {
                toast("Nama tidak boleh kosong");
                return;
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(em).matches()) {
                toast("Email wajib dan harus valid");
                return;
            }
            if (!pw.matches("[0-9]{6}")) {
                toast("PIN harus 6 angka");
                return;
            }
            btnCreate.setEnabled(false);
            api.createKasir(baseUrl, techPin, outlet.optInt("id"), nm, em, pw, new OtiApi.CallBack() {
                @Override
                public void onResult(JSONObject r) {
                    btnCreate.setEnabled(true);
                    if (r != null && r.optBoolean("ok")) {
                        JSONObject u = r.optJSONObject("user");
                        toast("Kasir dibuat: " + (u != null ? u.optString("name", nm) : nm));
                        etName.setText("");
                        etEmail.setText("");
                        etPinNew.setText("");
                        fetchKasir(); // refresh daftar kasir
                    } else {
                        String code = r != null ? r.optString("code", "") : "";
                        if ("pin_dup".equals(code)) {
                            toast("PIN sudah dipakai kasir lain");
                        } else if ("bad_pin".equals(code)) {
                            toast("PIN tidak valid");
                        } else if ("bad_name".equals(code)) {
                            toast("Nama tidak valid");
                        } else if ("bad_email".equals(code)) {
                            toast("Email wajib dan harus valid");
                        } else if ("email_dup".equals(code)) {
                            toast("Email sudah dipakai user lain");
                        } else {
                            String msg = r != null ? r.optString("message", "") : "";
                            toast(msg.isEmpty() ? "Gagal membuat kasir" : msg);
                        }
                    }
                }

                @Override
                public void onError(String message) {
                    btnCreate.setEnabled(true);
                    toast("Koneksi gagal: " + message);
                }
            });
        });
        box.addView(btnCreate, linParams(ViewGroup.LayoutParams.MATCH_PARENT));

        Button btnChange = button("← Ganti Outlet", "#334155");
        btnChange.setOnClickListener(v -> showScreen(buildScreenOutlet()));
        box.addView(btnChange, linParams(ViewGroup.LayoutParams.MATCH_PARENT));

        Button btnBind = button("BIND DEVICE KE OUTLET INI", "#0284C7");
        btnBind.setTextSize(18);
        btnBind.setOnClickListener(v -> confirmBind());
        box.addView(btnBind, linParams(ViewGroup.LayoutParams.MATCH_PARENT));
        return scroll;
    }

    private void fetchKasir() {
        if (outlet == null) return;
        api.outletUsers(baseUrl, techPin, outlet.optInt("id"), new OtiApi.CallBack() {
            @Override
            public void onResult(JSONObject r) {
                if (r != null && r.optBoolean("ok")) {
                    JSONArray arr = r.optJSONArray("users");
                    kasirRows = arr != null ? arr : new JSONArray();
                    renderKasir();
                } else {
                    String msg = r != null ? r.optString("message", "") : "";
                    toast(msg.isEmpty() ? "Gagal memuat kasir" : msg);
                }
            }

            @Override
            public void onError(String message) {
                toast("Koneksi gagal: " + message);
            }
        });
    }

    private void renderKasir() {
        kasirListbox.removeAllViews();
        if (kasirRows.length() == 0) {
            kasirListbox.addView(dimText("Belum ada kasir di unit outlet ini", 14));
            return;
        }
        for (int i = 0; i < kasirRows.length(); i++) {
            JSONObject u = kasirRows.optJSONObject(i);
            if (u == null) continue;
            String pin = u.optString("pin", "");
            TextView row = text(u.optString("name", "?") + " — " + u.optString("login", "")
                    + " — " + (pin.isEmpty() ? "-" : pin), 14, false);
            row.setPadding(16, 12, 16, 12);
            kasirListbox.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void confirmBind() {
        if (outlet == null) return;
        final String outletName = outlet.optString("name", "?");
        new AlertDialog.Builder(activity)
                .setTitle("Bind Device?")
                .setMessage("Device " + shortCode + " akan di-bind ke outlet " + outletName + ". Lanjut?")
                .setPositiveButton("Bind", (d, w) -> doBind(outletName))
                .setNegativeButton("Batal", null)
                .show();
    }

    private void doBind(String outletName) {
        api.bindOutlet(baseUrl, techPin, deviceUid, outlet.optInt("id"), new OtiApi.CallBack() {
            @Override
            public void onResult(JSONObject r) {
                if (r != null && r.optBoolean("ok")) {
                    showBindSuccess(outletName);
                } else {
                    String msg = r != null ? r.optString("message", "") : "";
                    toast(msg.isEmpty() ? "Gagal bind device" : msg);
                }
            }

            @Override
            public void onError(String message) {
                toast("Koneksi gagal: " + message);
            }
        });
    }

    private void showBindSuccess(String outletName) {
        successDialog = new AlertDialog.Builder(activity)
                .setTitle("Bind Berhasil")
                .setMessage("Device " + shortCode + " berhasil di-bind ke " + outletName
                        + ". Silakan login kasir.")
                .setCancelable(false)
                .show();
        // tutup popup + wizard saja — app tetap jalan, kembali ke layar login POS OTI
        handler.postDelayed(() -> {
            if (successDialog != null && successDialog.isShowing()) successDialog.dismiss();
            dismiss(); // tutup wizard
        }, 2000L);
    }

    // ================= Helper UI (palet ikut login screen existing) =================

    private LinearLayout vBox() {
        LinearLayout b = new LinearLayout(activity);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        return b;
    }

    private TextView text(String s, float size, boolean bold) {
        TextView t = new TextView(activity);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(Color.parseColor("#F8FAFC"));
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private TextView dimText(String s, float size) {
        TextView t = text(s, size, false);
        t.setTextColor(Color.parseColor("#94A3B8"));
        return t;
    }

    private Button button(String label, String hex) {
        Button b = new Button(activity);
        b.setText(label);
        b.setTextSize(16);
        b.setBackgroundColor(Color.parseColor(hex));
        b.setTextColor(Color.WHITE);
        return b;
    }

    private LinearLayout.LayoutParams linParams(int width) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                width, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 12;
        return lp;
    }

    private void toast(String s) {
        Toast.makeText(activity, s, Toast.LENGTH_SHORT).show();
    }
}
