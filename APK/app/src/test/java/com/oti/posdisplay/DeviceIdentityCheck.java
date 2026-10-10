package com.oti.posdisplay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Self-check DeviceIdentity — plain JVM, tanpa framework (junit tidak ada di cache offline).
 * Jalankan ( oleh controller ):
 *   cd android/oti_pos_display
 *   mkdir -p /tmp/oticheck && JAVA_HOME=/home/ivanputra/tools/jdk17 $JAVA_HOME/bin/javac \
 *     -cp /home/ivanputra/tools/android-sdk/platforms/android-34/android.jar \
 *     -d /tmp/oticheck \
 *     app/src/main/java/com/oti/posdisplay/DeviceIdentity.java \
 *     app/src/test/java/com/oti/posdisplay/DeviceIdentityCheck.java
 *   $JAVA_HOME/bin/java -ea -cp /tmp/oticheck:/home/ivanputra/tools/android-sdk/platforms/android-34/android.jar \
 *     com.oti.posdisplay.DeviceIdentityCheck
 * Oracle SHA-256 dihitung INDEPENDEN di sini (concat + format %02x) — bukan memanggil computeUid.
 */
public final class DeviceIdentityCheck {

    public static void main(String[] args) throws Exception {
        String uid = DeviceIdentity.computeUid("a1b2c3d4e5f6", "Samsung", "SM-C8G");

        // format kontrak: hex 64 lowercase
        assert uid.length() == 64 : "panjang=" + uid.length();
        assert uid.matches("[0-9a-f]{64}") : "bukan hex lowercase: " + uid;

        // oracle independen: sha256("androidId|manufacturer|model")
        assert uid.equals(sha256("a1b2c3d4e5f6|Samsung|SM-C8G")) : "beda dari oracle: " + uid;

        // deterministik
        assert uid.equals(DeviceIdentity.computeUid("a1b2c3d4e5f6", "Samsung", "SM-C8G"));

        // input beda -> hash beda
        assert !uid.equals(DeviceIdentity.computeUid("a1b2c3d4e5f6", "Samsung", "SM-C8Gx"));

        // null/empty aman (ANDROID_ID bisa null)
        String uid2 = DeviceIdentity.computeUid(null, "", "C8G");
        assert uid2.length() == 64 && uid2.equals(sha256("unknown|unknown|C8G")) : "fallback null/empty salah";

        // shortCode: 6 char pertama, guard input pendek/null
        assert DeviceIdentity.shortCode(uid).equals(uid.substring(0, 6));
        assert DeviceIdentity.shortCode(null).isEmpty();
        assert DeviceIdentity.shortCode("abc").isEmpty();

        System.out.println("DeviceIdentityCheck OK — semua assert lolos");
    }

    private static String sha256(String raw) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(64);
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
