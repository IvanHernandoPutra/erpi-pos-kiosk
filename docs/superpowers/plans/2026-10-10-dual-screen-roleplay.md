# ERPI POS Kiosk Dual-Screen & Roleplay Architecture Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement clean Dual-Screen (Hardware Presentation + In-App PiP Preview), dynamic server configuration, and hardware simulation into ERPI POS Kiosk APK for complete roleplay testing on laptop/emulator.

**Architecture:** 
- Hybrid Display Controller routing customer view to `android.app.Presentation` when external display exists, or docking an In-App PiP (800x1280 portrait aspect) floating/split preview when single display.
- Dynamic Setup Dialog on first boot allowing custom Odoo URL (e.g. `http://10.0.2.2:8069` or LAN IP) with offline persistent storage.
- Mock & Raw Network ESC/POS Printer driver with in-app virtual receipt inspection.

**Tech Stack:** Android SDK 34, Java/Kotlin, WebView Bridge, ESC/POS Raw Socket.

---

## Global Constraints
- Target Android SDK 34, minSdk 26+
- Package name: `com.erpindonesia.pos`
- No hardcoded IP/credentials in production code; use SharedPreferences with fallback.
- Dual-Screen support: Native Presentation API + In-App PiP Overlay.

---

### Task 1: Package Refactoring & Manifest Alignment
**Files:**
- Modify: `APK/app/build.gradle`
- Modify: `APK/app/src/main/AndroidManifest.xml`
- Rename package: `com.oti.posdisplay` -> `com.erpindonesia.pos`

- [ ] **Step 1: Update app build.gradle with new applicationId & dependencies**
- [ ] **Step 2: Update AndroidManifest.xml with new package & permissions**
- [ ] **Step 3: Verify gradle compile check**

### Task 2: Dynamic Server Setup & Preferences
**Files:**
- Create: `APK/app/src/main/java/com/erpindonesia/pos/PosPreferences.java`
- Create: `APK/app/src/main/java/com/erpindonesia/pos/ServerSetupDialog.java`

- [ ] **Step 1: Write PosPreferences unit test & class**
- [ ] **Step 2: Implement ServerSetupDialog for dynamic URL configuration**
- [ ] **Step 3: Test and verify offline persistence**

### Task 3: Dual-Screen Controller (Presentation + In-App PiP Overlay)
**Files:**
- Create: `APK/app/src/main/java/com/erpindonesia/pos/DualScreenManager.java`
- Modify: `APK/app/src/main/java/com/erpindonesia/pos/CustomerPresentation.java`
- Create: `APK/app/src/main/java/com/erpindonesia/pos/CustomerPipView.java`
- Update: `APK/app/src/main/assets/customer_shell.html`

- [ ] **Step 1: Create CustomerPipView with toggle minimize/expand**
- [ ] **Step 2: Implement DualScreenManager dispatching HTML/State to Presentation or PiP**
- [ ] **Step 3: Verify customer state transitions (Idle, Cart, Payment, Success)**

### Task 4: Virtual Printer & Cash Drawer Hardware Simulation
**Files:**
- Create: `APK/app/src/main/java/com/erpindonesia/pos/hardware/PosPrinterDriver.java`
- Create: `APK/app/src/main/java/com/erpindonesia/pos/hardware/VirtualPrinterInspector.java`

- [ ] **Step 1: Implement ESC/POS Network Socket & Virtual Logger**
- [ ] **Step 2: Implement Cash Drawer Kick pulse parser**
- [ ] **Step 3: Connect JS bridge to printer driver**

### Task 5: MainActivity Assembly & Roleplay Kiosk UI
**Files:**
- Refactor: `APK/app/src/main/java/com/erpindonesia/pos/MainActivity.java`
- Modify: `APK/app/src/main/assets/inject_customer_display.js`

- [ ] **Step 1: Clean MainActivity from legacy UI slop, integrate SetupDialog and DualScreenManager**
- [ ] **Step 2: Add on-screen toggle for Hardware Inspector & PiP Customer View**
- [ ] **Step 3: Compile debug APK and verify build**
