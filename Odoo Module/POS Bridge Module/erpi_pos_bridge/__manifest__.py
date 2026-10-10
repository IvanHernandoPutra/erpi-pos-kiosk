{
    "name": "ERPI POS Bridge",
    "version": "17.0.1.0.0",
    "category": "Point of Sale",
    "summary": "Bridge module for POS ERPI Android Kiosk and hardware relay",
    "author": "ERP Indonesia",
    "website": "https://erpindonesia.net",
    "license": "LGPL-3",
    "depends": ["point_of_sale"],
    "data": [
        "security/ir.model.access.csv",
        "views/pos_device_views.xml",
    ],
    "installable": True,
    "application": False,
    "auto_install": False,
}
