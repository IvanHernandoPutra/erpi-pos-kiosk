# ERPI POS Kiosk Ecosystem

Sistem Kasir Komersial Layar Ganda (Dual-Screen) & Jembatan Odoo POS tanpa IoT Box. Dikembangkan oleh ERP Indonesia.

## Struktur Direktori

```text
erpi-pos-kiosk/
├── APK/                         # Source code Android Kiosk (Kotlin + Jetpack Compose)
│   ├── app/                     # Modul aplikasi Android
│   ├── build.gradle.kts         # Root build script
│   └── settings.gradle.kts      # Gradle settings
│
└── Odoo Module/                 # Addons Odoo untuk backend POS
    ├── POS Module/              # Addon kustomisasi POS Odoo (erpi_pos)
    └── POS Bridge Module/       # Addon jembatan perangkat & API lisensi (erpi_pos_bridge)
```

## Komponen Produk

1. **APK (`com.erpindonesia.pos`)**:
   - Android Kiosk launcher dengan penguncian `LockTaskMode`.
   - Setup wizard dinamis (Input Odoo URL & Pairing Code 6-digit tanpa hardcode IP).
   - Pengendali Layar Kedua (`Presentation API`): Rotasi Banner Promosi -> Keranjang Kasir -> QRIS Dinamis.
   - Driver thermal printer (ESC/POS LAN port 9100 / Built-in hardware C8G) dan pemicu laci uang (RJ11 drawer-kick pulse).

2. **Odoo Module / POS Bridge (`erpi_pos_bridge`)**:
   - Mengelola data `pos.device` (UUID, token, status pairing, heartbeat).
   - Endpoint `/api/pos/device/claim` dan `/api/pos/device/heartbeat`.
   - Konfigurasi branding (`pos.branding`) & banner promosi untuk customer display.

3. **Odoo Module / POS Core (`erpi_pos`)**:
   - Fitur bisnis POS: modal awal kasir, verifikasi PIN supervisor untuk diskon/void, dan laporan Z-report.
