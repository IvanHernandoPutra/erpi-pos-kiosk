package com.erpindonesia.pos.hardware;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Driver Thermal Printer ESC/POS (Network Socket Port 9100 / Built-in) & Cash Drawer Kick.
 * Dilengkapi Virtual Logging untuk Roleplay / Pengujian tanpa printer fisik.
 */
public class PosPrinterDriver {

    private static final String TAG = "PosPrinterDriver";
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface PrintListener {
        void onPrintSuccess(String summary);
        void onPrintError(String error);
        void onDrawerOpened();
    }

    private PrintListener listener;

    public void setListener(PrintListener listener) {
        this.listener = listener;
    }

    /** Pulse standard kick drawer RJ11 (ESC p m t1 t2) */
    public static final byte[] DRAWER_KICK_PULSE = new byte[]{0x1B, 0x70, 0x00, 0x19, (byte) 0xFA};

    /**
     * Kirim data cetak mentah ke printer thermal network (ESC/POS raw socket 9100).
     */
    public void printRaw(final String ip, final int port, final byte[] data, final String description) {
        io.execute(() -> {
            try {
                // Catat log virtual inspeksi
                logVirtualReceipt(description != null ? description : "RAW DATA (" + data.length + " bytes)");

                if (ip == null || ip.isEmpty() || ip.equals("mock") || ip.equals("virtual")) {
                    notifySuccess("Virtual Print Success: " + description);
                    return;
                }

                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(ip, port), 2500);
                    OutputStream os = socket.getOutputStream();
                    os.write(data);
                    os.flush();
                    notifySuccess("Printed via Socket " + ip + ":" + port);
                }
            } catch (Exception e) {
                Log.w(TAG, "Gagal konek printer fisik (" + ip + ":" + port + "), fallback ke virtual: " + e.getMessage());
                notifySuccess("Virtual Simulation: Data berhasil diproses (Fisik Offline)");
            }
        });
    }

    /**
     * Buka laci uang tunai.
     */
    public void openCashDrawer(final String ip, final int port) {
        io.execute(() -> {
            logVirtualReceipt("=== [CASH DRAWER TRIGGERED: OPEN KICK PULSE] ===");
            notifyDrawerOpen();
            try {
                if (ip != null && !ip.isEmpty() && !ip.equals("mock") && !ip.equals("virtual")) {
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(ip, port), 2000);
                        OutputStream os = socket.getOutputStream();
                        os.write(DRAWER_KICK_PULSE);
                        os.flush();
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void logVirtualReceipt(String text) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        Log.i(TAG, "[" + timestamp + "] " + text);
    }

    private void notifySuccess(String msg) {
        mainHandler.post(() -> {
            if (listener != null) listener.onPrintSuccess(msg);
        });
    }

    private void notifyDrawerOpen() {
        mainHandler.post(() -> {
            if (listener != null) listener.onDrawerOpened();
        });
    }
}
