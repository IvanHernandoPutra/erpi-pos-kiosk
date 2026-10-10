{
    "name": "ERPI POS Enterprise Suite",
    "version": "17.0.1.0.0",
    "category": "Point of Sale",
    "summary": "Enterprise Touch UI & Cash Reconciliation for POS ERPI Commercial (SKU 2 Turnkey)",
    "author": "ERP Indonesia",
    "website": "https://erpindonesia.net",
    "license": "LGPL-3",
    "depends": [
        "point_of_sale",
        "erpi_pos_bridge",
        "account",
    ],
    "data": [],
    "assets": {
        "point_of_sale._assets_pos": [
            "erpi_pos/static/src/scss/touch_pos.scss",
            "erpi_pos/static/src/scss/customer_display_split.scss",
            "erpi_pos/static/src/xml/product_screen_override.xml",
            "erpi_pos/static/src/xml/payment_screen_override.xml",
            "erpi_pos/static/src/xml/customer_display_override.xml",
            "erpi_pos/static/src/js/quick_payment_patch.js",
            "erpi_pos/static/src/js/order_cfd_sync.js",
        ],
    },
    "installable": True,
    "application": False,
    "auto_install": False,
}
