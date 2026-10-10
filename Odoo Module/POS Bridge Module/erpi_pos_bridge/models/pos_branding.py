from odoo import fields, models


class PosBranding(models.Model):
    _name = "pos.branding"
    _description = "POS Customer Facing Display Branding"

    company_id = fields.Many2one(
        "res.company",
        string="Company",
        required=True,
        default=lambda self: self.env.company,
        index=True,
    )
    brand_name = fields.Char(string="Brand Name", required=True)
    primary_color = fields.Char(string="Primary Color", default="#059669")
    secondary_color = fields.Char(string="Secondary Color", default="#0f172a")
    logo_image = fields.Binary(string="Logo Image")
    banner_ids = fields.One2many(
        "pos.branding.banner",
        "branding_id",
        string="Banners",
    )

    _sql_constraints = [
        (
            "pos_branding_company_unique",
            "unique(company_id)",
            "Only one branding configuration allowed per company.",
        ),
    ]


class PosBrandingBanner(models.Model):
    _name = "pos.branding.banner"
    _description = "POS CFD Promotional Banner"
    _order = "sequence asc, id asc"

    branding_id = fields.Many2one(
        "pos.branding",
        string="Branding",
        required=True,
        ondelete="cascade",
    )
    name = fields.Char(string="Banner Title", required=True)
    image = fields.Binary(string="Banner Image", required=True)
    sequence = fields.Integer(string="Sequence", default=10)
    display_duration_seconds = fields.Integer(
        string="Display Duration (Seconds)",
        default=5,
    )
