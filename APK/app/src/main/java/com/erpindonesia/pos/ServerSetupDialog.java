package com.erpindonesia.pos;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Dialog Konfigurasi URL Server Odoo Dinamis (Bebas Hardcode).
 */
public class ServerSetupDialog {

    public interface OnSetupCompleteListener {
        void onSetupSaved(String serverUrl, String printerIp);
    }

    public static void show(Context context, PosPreferences prefs, OnSetupCompleteListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(false);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#0F172A"));
        layout.setPadding(48, 48, 48, 48);

        TextView tvTitle = new TextView(context);
        tvTitle.setText("Konfigurasi Kasir POS ERPI");
        tvTitle.setTextSize(20);
        tvTitle.setTextColor(Color.parseColor("#38BDF8"));
        tvTitle.setPadding(0, 0, 0, 16);
        layout.addView(tvTitle);

        TextView tvDesc = new TextView(context);
        tvDesc.setText("Masukkan alamat server Odoo Anda (IP Laptop / Domain):");
        tvDesc.setTextColor(Color.parseColor("#94A3B8"));
        tvDesc.setTextSize(13);
        tvDesc.setPadding(0, 0, 0, 24);
        layout.addView(tvDesc);

        final EditText etUrl = new EditText(context);
        etUrl.setHint("http://10.0.2.2:8069 atau http://192.168.1.x:8069");
        etUrl.setText(prefs.getServerUrl());
        etUrl.setTextColor(Color.WHITE);
        etUrl.setHintTextColor(Color.parseColor("#64748B"));
        etUrl.setBackgroundColor(Color.parseColor("#1E293B"));
        etUrl.setPadding(24, 20, 24, 20);
        layout.addView(etUrl);

        TextView tvPrinter = new TextView(context);
        tvPrinter.setText("IP Printer Thermal (Ketik 'virtual' untuk mode testing roleplay):");
        tvPrinter.setTextColor(Color.parseColor("#94A3B8"));
        tvPrinter.setTextSize(13);
        tvPrinter.setPadding(0, 24, 0, 12);
        layout.addView(tvPrinter);

        final EditText etPrinter = new EditText(context);
        etPrinter.setHint("virtual atau 192.168.1.200");
        etPrinter.setText(prefs.getPrinterIp());
        etPrinter.setTextColor(Color.WHITE);
        etPrinter.setHintTextColor(Color.parseColor("#64748B"));
        etPrinter.setBackgroundColor(Color.parseColor("#1E293B"));
        etPrinter.setPadding(24, 20, 24, 20);
        layout.addView(etPrinter);

        Button btnSave = new Button(context);
        btnSave.setText("SIMPAN & MULAI KASIR");
        btnSave.setBackgroundColor(Color.parseColor("#2563EB"));
        btnSave.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        btnParams.topMargin = 36;
        layout.addView(btnSave, btnParams);

        builder.setView(layout);
        AlertDialog dialog = builder.create();

        btnSave.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            String printerIp = etPrinter.getText().toString().trim();

            if (url.isEmpty()) {
                Toast.makeText(context, "URL Server tidak boleh kosong!", Toast.LENGTH_SHORT).show();
                return;
            }

            prefs.setServerUrl(url);
            prefs.setPrinterIp(printerIp.isEmpty() ? "virtual" : printerIp);
            dialog.dismiss();

            if (listener != null) {
                listener.onSetupSaved(url, printerIp);
            }
        });

        dialog.show();
    }
}
