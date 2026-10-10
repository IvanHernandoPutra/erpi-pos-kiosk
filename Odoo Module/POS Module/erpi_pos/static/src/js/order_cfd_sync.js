/** @odoo-module **/

import { Order } from "@point_of_sale/app/store/models";
import { patch } from "@web/core/utils/patch";

// BroadcastChannel untuk sinkronisasi instan antar tab/window browser lokal (Simulator CFD)
let cfdBroadcastChannel = null;
try {
    cfdBroadcastChannel = new BroadcastChannel("erpi_pos_cfd_channel");
} catch (e) {
    console.warn("BroadcastChannel not supported", e);
}

function broadcastCart(order) {
    if (!order) return;
    const lines = order.get_orderlines().map((l) => ({
        product_name: l.get_full_product_name ? l.get_full_product_name() : l.get_product().display_name,
        qty: l.get_quantity(),
        price_unit: l.get_unit_price(),
        price_subtotal: l.get_price_with_tax ? l.get_price_with_tax() : l.get_display_price(),
    }));

    const cartData = {
        action: "cart_update",
        lines: lines,
        item_count: lines.length,
        subtotal: order.get_total_without_tax ? order.get_total_without_tax() : order.get_total_with_tax(),
        tax: order.get_total_tax ? order.get_total_tax() : 0,
        discount: order.get_total_discount ? order.get_total_discount() : 0,
        total: order.get_total_with_tax(),
    };

    // 1. Kirim ke simulator browser via BroadcastChannel & localStorage
    if (cfdBroadcastChannel) {
        cfdBroadcastChannel.postMessage(cartData);
    }
    try {
        localStorage.setItem("erpi_cfd_event", JSON.stringify({ ...cartData, timestamp: Date.now() }));
    } catch (_) {}

    // 2. Kirim ke Android Presentation API jika running di dalam Android Kiosk WebView
    if (window.AndroidPosBridge && typeof window.AndroidPosBridge.onCartUpdated === "function") {
        window.AndroidPosBridge.onCartUpdated(JSON.stringify(cartData));
    }
}

// Patch Order untuk mendeteksi perubahan keranjang belanja
patch(Order.prototype, {
    add_product() {
        const res = super.add_product(...arguments);
        broadcastCart(this);
        return res;
    },
    remove_orderline() {
        const res = super.remove_orderline(...arguments);
        broadcastCart(this);
        return res;
    },
    set_orderline_options() {
        const res = super.set_orderline_options(...arguments);
        broadcastCart(this);
        return res;
    },
});
