package com.oti.posdisplay;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * GPS gate: kumpulkan fix lokasi, cek provider nyala, cek mock.
 * Callback di main thread.
 */
public class LocationGate implements LocationListener {

    public interface Callback {
        void onFix(Location loc, boolean isMock);
        void onProviderStateChanged(boolean gpsOn);
    }

    private final Activity activity;
    private final Callback callback;
    private LocationManager lm;
    private BroadcastReceiver providerReceiver;
    private Location lastFix;
    private boolean receiverRegistered = false;

    public LocationGate(Activity activity, Callback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    public boolean hasPermission() {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void requestPermission() {
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, 1001);
    }

    public boolean isGpsOn() {
        lm = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                || lm.isProviderEnabled(LocationManager.FUSED_PROVIDER);
        // FUSED_PROVIDER hidden API — fallback try/catch tidak perlu, isProviderEnabled aman utk nama tak dikenal (return false)
    }

    public void start() {
        lm = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 0, this, Looper.getMainLooper());
        } catch (SecurityException e) {
            callback.onProviderStateChanged(false);
            return;
        }
        try {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, 0, this, Looper.getMainLooper());
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
        // Seed fix awal dari last known (cepat, indoor kadang hanya ini yang ada)
        for (String prov : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
            try {
                Location last = lm.getLastKnownLocation(prov);
                if (last != null && (lastFix == null || last.getTime() > lastFix.getTime())) {
                    lastFix = last;
                }
            } catch (SecurityException | IllegalArgumentException ignored) {
            }
        }
        // Instan lock saat user matikan GPS dari control panel
        providerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                callback.onProviderStateChanged(isGpsOn());
            }
        };
        if (!receiverRegistered) {
            IntentFilter f = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
            f.addAction(LocationManager.MODE_CHANGED_ACTION);
            if (Build.VERSION.SDK_INT >= 33) {
                activity.registerReceiver(providerReceiver, f, Context.RECEIVER_EXPORTED);
            } else {
                activity.registerReceiver(providerReceiver, f);
            }
            receiverRegistered = true;
        }
    }

    public void stop() {
        try {
            if (lm != null) lm.removeUpdates(this);
        } catch (Exception ignored) {
        }
        if (providerReceiver != null && receiverRegistered) {
            try {
                activity.unregisterReceiver(providerReceiver);
            } catch (Exception ignored) {
            }
            receiverRegistered = false;
        }
    }

    public Location lastFix() {
        return lastFix;
    }

    /** Fix terakhir yang masih segar (maxAgeMs). Null kalau basi. */
    public Location freshFix(long maxAgeMs) {
        if (lastFix == null) return null;
        return (System.currentTimeMillis() - lastFix.getTime()) <= maxAgeMs ? lastFix : null;
    }

    public boolean isMock(Location loc) {
        return loc != null && loc.isFromMockProvider();
    }

    @Override
    public void onLocationChanged(Location loc) {
        lastFix = loc;
        callback.onFix(loc, isMock(loc));
    }

    @Override
    public void onProviderDisabled(String provider) {
        callback.onProviderStateChanged(isGpsOn());
    }

    @Override
    public void onProviderEnabled(String provider) {
        callback.onProviderStateChanged(isGpsOn());
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }
}
