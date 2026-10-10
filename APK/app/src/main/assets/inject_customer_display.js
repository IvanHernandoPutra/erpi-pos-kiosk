/**
 * Injected into cashier POS WebView before Odoo bundle executes.
 * Stubs window.open(name=\"Customer Display\") to return a hidden iframe window,
 * so Odoo 17 LocalDisplay writes its customer display HTML into that iframe.
 * MutationObserver forwards document HTML to native bridge (OtiPosBridge.push).
 * Fallback: if bridge absent (desktop browser), behaves like normal window.open.
 */
(function () {
    if (window.__otiDisplayStubInstalled) { return; }
    window.__otiDisplayStubInstalled = true;

    // Kiosk always wants the customer display: pre-seed Odoo's flag so
    // LocalDisplay.connect() runs on every load, even after data/cache clear.
    try {
        window.localStorage.setItem("pos-customerdisplay-local-open", "true");
    } catch (e) { /* storage unavailable; ignore */ }

    var nativeOpen = window.open.bind(window);

    function hasBridge() {
        return typeof window.OtiPosBridge !== "undefined" && window.OtiPosBridge && (typeof window.OtiPosBridge.pushCustomerHtml === "function" || typeof window.OtiPosBridge.push === "function");
    }

    window.open = function (url, name, features) {
        if (name === "Customer Display" && hasBridge()) {
            var existing = document.getElementById("__oti_customer_display_frame");
            if (existing) {
                return existing.contentWindow;
            }
            var frame = document.createElement("iframe");
            frame.id = "__oti_customer_display_frame";
            frame.style.display = "none";
            document.body.appendChild(frame);
            observeFrame(frame);
            return frame.contentWindow;
        }
        return nativeOpen(url, name, features);
    };

    function observeFrame(frame) {
        var pending = false;
        var observer = new MutationObserver(function () {
            if (pending) { return; }
            pending = true;
            setTimeout(function () {
                pending = false;
                try {
                    var doc = frame.contentDocument;
                    if (!doc || !doc.documentElement) { return; }
                    var html = doc.documentElement.outerHTML;
                    if (html && html.length > 50) {
                        if (typeof window.OtiPosBridge.pushCustomerHtml === "function") {
                            window.OtiPosBridge.pushCustomerHtml(html);
                        } else if (typeof window.OtiPosBridge.push === "function") {
                            window.OtiPosBridge.push(html);
                        }
                    }
                } catch (e) {
                    // cross-origin or detached; ignore
                }
            }, 120);
        });
        var attach = function () {
            try {
                observer.observe(frame.contentDocument, { childList: true, subtree: true, characterData: true });
            } catch (e) {
                setTimeout(attach, 500);
            }
        };
        attach();
    }
})();
