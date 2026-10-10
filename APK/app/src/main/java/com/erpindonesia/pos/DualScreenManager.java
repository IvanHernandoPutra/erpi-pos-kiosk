package com.erpindonesia.pos;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.widget.FrameLayout;

/**
 * Pengelola Layar Ganda Otomatis (Dual Screen Dispatcher).
 * Jika ada layar kedua fisik -> Tampilkan via android.app.Presentation.
 * Jika hanya ada satu layar (laptop / emulator / tablet standar) -> Pasang In-App PiP Overlay.
 */
public class DualScreenManager {

    private static final String TAG = "DualScreenManager";
    private final Context context;
    private final FrameLayout rootContainer;
    private final String posOrigin;

    private CustomerPresentation customerPresentation;
    private CustomerPipView pipView;
    private boolean isPhysicalDualScreen = false;

    public DualScreenManager(Context context, FrameLayout rootContainer, String posOrigin) {
        this.context = context;
        this.rootContainer = rootContainer;
        this.posOrigin = posOrigin;
        initDisplay();
    }

    private void initDisplay() {
        DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        Display secondaryDisplay = null;

        if (dm != null) {
            for (Display d : dm.getDisplays()) {
                if (d.getDisplayId() != Display.DEFAULT_DISPLAY) {
                    secondaryDisplay = d;
                    break;
                }
            }
        }

        if (secondaryDisplay != null) {
            // Hardware dual-screen terdeteksi (C8G / Sunmi)
            isPhysicalDualScreen = true;
            customerPresentation = new CustomerPresentation(context, secondaryDisplay, posOrigin);
            try {
                customerPresentation.show();
                Log.i(TAG, "Secondary Physical Display aktif pada Display ID: " + secondaryDisplay.getDisplayId());
            } catch (Exception e) {
                Log.e(TAG, "Gagal meluncurkan CustomerPresentation: " + e.getMessage());
                fallbackToPip();
            }
        } else {
            // Single-screen mode -> Pasang PiP overlay untuk roleplay di laptop/emulator
            fallbackToPip();
        }
    }

    private void fallbackToPip() {
        isPhysicalDualScreen = false;
        pipView = new CustomerPipView(context, posOrigin);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.topMargin = 30;
        params.rightMargin = 30;
        rootContainer.addView(pipView, params);
        Log.i(TAG, "In-App Customer PiP Preview aktif untuk roleplay single-screen.");
    }

    /**
     * Kirim update HTML dokumen belanja dari kasir ke layar pelanggan.
     */
    public void render(String html) {
        if (isPhysicalDualScreen && customerPresentation != null) {
            customerPresentation.render(html);
        } else if (pipView != null) {
            pipView.render(html);
        }
    }

    public void setPipVisible(boolean visible) {
        if (pipView != null) {
            pipView.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    public boolean isPhysicalDualScreen() {
        return isPhysicalDualScreen;
    }
}
