# OTI POS Display (Smartlogic C8 dual-screen)

Cashier screen (Display 0, 1920x1080): WebView with Odoo POS.
Customer screen (Display 2, 800x1280 portrait): android.app.Presentation with WebView shell.

## How it works
1. Enable Customer Display in Odoo POS config (iface_customer_facing_display_local).
2. Odoo LocalDisplay service calls window.open("", "Customer Display").
3. inject_customer_display.js stubs window.open -> hidden iframe, MutationObserver pushes HTML to OtiPosBridge.
4. Native relays to CustomerPresentation WebView which renders it (customer_shell.html logic).

## Build (no Android Studio)
    sudo apt install openjdk-17-jdk unzip
    # install Android cmdline-tools to ~/android-sdk, then:
    ~/android-sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
    # generate wrapper jar once (or copy from any gradle project):
    gradle wrapper --gradle-version 8.7   # if gradle installed; else download wrapper jar
    ./gradlew assembleDebug
    adb connect <device-ip>:<port>
    adb install app/build/outputs/apk/debug/app-debug.apk

## Configure POS URL
Edit POS_ORIGIN in CustomerPresentation.java and default URL in MainActivity.java,
or build a settings screen later (Fase 3).
