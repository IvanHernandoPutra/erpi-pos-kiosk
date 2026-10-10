import json
import logging
import secrets
from odoo import fields, http
from odoo.http import request

_logger = logging.getLogger(__name__)


class ErpiPosBridgeController(http.Controller):

    @http.route("/api/pos/device/claim", type="http", auth="public", methods=["POST"], csrf=False)
    def claim_device(self, **kwargs):
        try:
            body = json.loads(request.httprequest.data.decode("utf-8")) if request.httprequest.data else {}
        except Exception:
            body = {}

        pairing_code = body.get("pairing_code") or kwargs.get("pairing_code")
        device_uuid = body.get("device_uuid") or kwargs.get("device_uuid")
        pos_config_id = body.get("pos_config_id") or kwargs.get("pos_config_id")
        company_id = body.get("company_id") or kwargs.get("company_id")
        name = body.get("name") or kwargs.get("name") or f"Tablet-{device_uuid[:8] if device_uuid else 'unnamed'}"

        if not pairing_code or not device_uuid:
            return request.make_json_response(
                {
                    "status": "error",
                    "message": "Missing required fields: pairing_code and device_uuid.",
                },
                status=400,
            )

        pos_device_model = request.env["pos.device"].sudo()

        device = pos_device_model.search([("device_uuid", "=", device_uuid)], limit=1)

        domain_code = [("pairing_code", "=", pairing_code), ("status", "=", "pending")]
        pending_by_code = pos_device_model.search(domain_code, limit=1)

        target_device = device or pending_by_code

        if not target_device:
            if not pos_config_id:
                default_config = request.env["pos.config"].sudo().search([], limit=1)
                pos_config_id = default_config.id if default_config else False
            if not pos_config_id:
                return request.make_json_response(
                    {
                        "status": "error",
                        "message": "No POS configuration available for pairing.",
                    },
                    status=400,
                )

            token = secrets.token_hex(24)
            target_device = pos_device_model.create({
                "name": name,
                "device_uuid": device_uuid,
                "device_token": token,
                "pairing_code": pairing_code,
                "pos_config_id": int(pos_config_id),
                "company_id": int(company_id) if company_id else request.env.company.id,
                "status": "active",
                "last_heartbeat": fields.Datetime.now(),
            })
        else:
            token = target_device.device_token or secrets.token_hex(24)
            vals = {
                "device_uuid": device_uuid,
                "device_token": token,
                "status": "active",
                "last_heartbeat": fields.Datetime.now(),
            }
            if pos_config_id:
                vals["pos_config_id"] = int(pos_config_id)
            if company_id:
                vals["company_id"] = int(company_id)
            target_device.write(vals)

        base_url = request.httprequest.host_url.rstrip("/")
        pos_url = f"{base_url}/pos/ui?config_id={target_device.pos_config_id.id}"

        return request.make_json_response({
            "status": "success",
            "device_token": target_device.device_token,
            "device_id": target_device.id,
            "pos_config_id": target_device.pos_config_id.id,
            "pos_url": pos_url,
        })

    @http.route("/api/pos/device/open_session", type="http", auth="public", methods=["POST"], csrf=False)
    def open_device_session(self, **kwargs):
        """Buka sesi POS dan login otomatis untuk tablet Kiosk tanpa form login Odoo."""
        device_token = request.httprequest.headers.get("X-Device-Token")
        if not device_token:
            try:
                body = json.loads(request.httprequest.data.decode("utf-8")) if request.httprequest.data else {}
            except Exception:
                body = {}
            device_token = body.get("device_token") or kwargs.get("device_token")

        if not device_token:
            return request.make_json_response(
                {"status": "unauthorized", "message": "Missing device token."},
                status=401,
            )

        pos_device = request.env["pos.device"].sudo().search(
            [("device_token", "=", device_token)],
            limit=1,
        )
        if not pos_device or pos_device.status != "active":
            return request.make_json_response(
                {"status": "unauthorized", "message": "Device not active or not found."},
                status=401,
            )

        pos_config = pos_device.pos_config_id
        if not pos_config:
            return request.make_json_response(
                {"status": "error", "message": "No POS configuration assigned."},
                status=400,
            )

        # Cari user kasir / admin
        cashier_user = request.env.ref("base.user_admin").sudo()
        db_name = request.session.db or request.env.cr.dbname

        # Autentikasi sesi Odoo
        request.session.uid = cashier_user.id
        request.session.login = cashier_user.login
        request.session.db = db_name
        request.session.context = dict(cashier_user.context_get())
        http.root.session_store.rotate(request.session, request.env)
        http.root.session_store.save(request.session)

        # Pastikan ada active session untuk pos.config ini
        active_sess = request.env["pos.session"].sudo().search([
            ("config_id", "=", pos_config.id),
            ("state", "in", ["opening_control", "opened"]),
        ], limit=1)

        if not active_sess:
            active_sess = request.env["pos.session"].sudo().create({
                "config_id": pos_config.id,
                "user_id": cashier_user.id,
            })

        base_url = request.httprequest.host_url.rstrip("/")
        pos_ui_url = f"{base_url}/pos/ui?config_id={pos_config.id}"

        response = request.make_json_response({
            "status": "success",
            "session_id": request.session.sid,
            "pos_session_id": active_sess.id,
            "pos_session_name": active_sess.name,
            "config_id": pos_config.id,
            "pos_url": pos_ui_url,
        })

        # Set cookie di HTTP response header
        response.set_cookie(
            "session_id",
            request.session.sid,
            max_age=86400 * 30,
            httponly=True,
            path="/",
        )
        return response

    @http.route("/pos/demo_login", type="http", auth="public", methods=["GET"], csrf=False)
    def demo_login(self, config_id=1, **kwargs):
        """Auto-login instan untuk demo browser kasir tanpa perlu ketik username/password."""
        import werkzeug.utils
        cashier_user = request.env.ref("base.user_admin").sudo()
        db_name = request.session.db or request.env.cr.dbname

        request.session.uid = cashier_user.id
        request.session.login = cashier_user.login
        request.session.db = db_name
        request.session.context = dict(cashier_user.context_get())
        http.root.session_store.rotate(request.session, request.env)
        http.root.session_store.save(request.session)

        # Pastikan sesi POS terbuka
        try:
            cfg_id = int(config_id)
        except Exception:
            cfg_id = 1

        active_sess = request.env["pos.session"].sudo().search([
            ("config_id", "=", cfg_id),
            ("state", "in", ["opening_control", "opened"]),
        ], limit=1)

        if not active_sess:
            active_sess = request.env["pos.session"].sudo().create({
                "config_id": cfg_id,
                "user_id": cashier_user.id,
            })

        resp = werkzeug.utils.redirect(f"/pos/ui?config_id={cfg_id}")
        resp.set_cookie(
            "session_id",
            request.session.sid,
            max_age=86400 * 30,
            httponly=True,
            path="/",
        )
        return resp

    @http.route("/api/pos/device/heartbeat", type="http", auth="public", methods=["POST"], csrf=False)
    def heartbeat_device(self, **kwargs):
        device_token = request.httprequest.headers.get("X-Device-Token")
        if not device_token:
            return request.make_json_response(
                {
                    "status": "unauthorized",
                    "message": "Missing X-Device-Token header.",
                },
                status=401,
            )

        pos_device = request.env["pos.device"].sudo().search(
            [("device_token", "=", device_token)],
            limit=1,
        )
        if not pos_device:
            return request.make_json_response(
                {
                    "status": "unauthorized",
                    "message": "Invalid device token.",
                },
                status=401,
            )

        try:
            body = json.loads(request.httprequest.data.decode("utf-8")) if request.httprequest.data else {}
        except Exception:
            body = {}

        app_version = body.get("app_version")
        vals = {"last_heartbeat": fields.Datetime.now()}
        if app_version:
            vals["app_version"] = app_version
        pos_device.write(vals)

        command = "none"
        if pos_device.status in ("suspended", "revoked"):
            command = "lock_screen"

        return request.make_json_response({
            "status": pos_device.status,
            "command": command,
            "emergency_otp": pos_device.emergency_otp if (
                pos_device.emergency_otp_expired
                and pos_device.emergency_otp_expired > fields.Datetime.now()
            ) else None,
        })

    @http.route("/api/pos/config/sync", type="http", auth="public", methods=["GET"], csrf=False)
    def config_sync(self, **kwargs):
        device_token = request.httprequest.headers.get("X-Device-Token")
        if not device_token:
            return request.make_json_response(
                {
                    "status": "unauthorized",
                    "message": "Missing X-Device-Token header.",
                },
                status=401,
            )

        pos_device = request.env["pos.device"].sudo().search(
            [("device_token", "=", device_token)],
            limit=1,
        )
        if not pos_device:
            return request.make_json_response(
                {
                    "status": "unauthorized",
                    "message": "Invalid device token.",
                },
                status=401,
            )

        company = pos_device.company_id
        branding = request.env["pos.branding"].sudo().search(
            [("company_id", "=", company.id)],
            limit=1,
        )

        banners_payload = []
        if branding and branding.banner_ids:
            for banner in branding.banner_ids:
                banners_payload.append({
                    "id": banner.id,
                    "name": banner.name,
                    "sequence": banner.sequence,
                    "display_duration_seconds": banner.display_duration_seconds,
                    "has_image": bool(banner.image),
                })

        branding_payload = {
            "brand_name": branding.brand_name if branding else company.name,
            "primary_color": branding.primary_color if branding else "#059669",
            "secondary_color": branding.secondary_color if branding else "#0f172a",
            "has_logo": bool(branding.logo_image) if branding else False,
            "banners": banners_payload,
        }

        return request.make_json_response({
            "status": "success",
            "device": {
                "id": pos_device.id,
                "name": pos_device.name,
                "status": pos_device.status,
                "printer_ip": pos_device.printer_ip or "",
                "pos_config_id": pos_device.pos_config_id.id,
            },
            "branding": branding_payload,
        })
