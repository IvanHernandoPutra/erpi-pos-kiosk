import secrets
from datetime import timedelta
from odoo import api, fields, models


class PosDevice(models.Model):
    _name = "pos.device"
    _description = "POS Hardware Device"
    _order = "name asc, id desc"

    name = fields.Char(string="Device Name", required=True)
    device_uuid = fields.Char(string="Device UUID", required=True, index=True)
    device_token = fields.Char(string="Device Token", readonly=True, index=True)
    pairing_code = fields.Char(string="Pairing Code", index=True, copy=False)
    pos_config_id = fields.Many2one(
        "pos.config",
        string="POS Register",
        required=True,
        ondelete="restrict",
    )
    company_id = fields.Many2one(
        "res.company",
        string="Company",
        required=True,
        default=lambda self: self.env.company,
    )
    status = fields.Selection(
        [
            ("pending", "Pending"),
            ("active", "Active"),
            ("suspended", "Suspended"),
            ("revoked", "Revoked"),
        ],
        string="Status",
        default="pending",
        required=True,
        index=True,
    )
    app_version = fields.Char(string="App Version")
    last_heartbeat = fields.Datetime(string="Last Heartbeat", readonly=True)
    printer_ip = fields.Char(
        string="Printer IP Address",
        help="IP address for receipt printer LAN socket",
    )
    emergency_otp = fields.Char(string="Emergency OTP", readonly=True, copy=False)
    emergency_otp_expired = fields.Datetime(
        string="Emergency OTP Expired",
        readonly=True,
        copy=False,
    )

    _sql_constraints = [
        (
            "device_uuid_unique",
            "unique(device_uuid)",
            "Device UUID must be unique per device.",
        ),
    ]

    def action_generate_emergency_otp(self):
        self.ensure_one()
        otp = f"{secrets.randbelow(900000) + 100000:06d}"
        expires_at = fields.Datetime.now() + timedelta(minutes=15)
        self.write({
            "emergency_otp": otp,
            "emergency_otp_expired": expires_at,
        })
        return True

    def action_activate_device(self):
        for record in self:
            vals = {"status": "active"}
            if not record.device_token:
                vals["device_token"] = secrets.token_hex(24)
            record.write(vals)

    def action_suspend_device(self):
        self.write({"status": "suspended"})

    def action_revoke_device(self):
        self.write({"status": "revoked"})
