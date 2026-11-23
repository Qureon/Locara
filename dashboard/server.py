#!/usr/bin/env python3
"""
==============================================================================
🛡️ LOCARA MISSION CONTROL — MODULAR TELEMETRY SERVER (POWERED BY QUREON)
Qureon Autonomous QA Engineering · Qureon Engineering
==============================================================================
"""

import http.server
import json
import os
import sys
import threading
from datetime import datetime
from pathlib import Path
from urllib.parse import urlparse
from xml.etree.ElementTree import ParseError

from config_store import (
    ALLOWED_TARGETS,
    BadRequestError,
    DEFAULT_CONFIG,
    DEFAULT_PORT,
    ENGINE_VERSION,
    FLOAT_CONFIG_KEYS,
    PROPERTIES_KEY_MAP,
    PayloadTooLargeError,
    build_audit_candidates,
    load_persisted_config as _load_persisted_config_into,
    parse_port,
    save_persisted_config as _save_persisted_config_to,
    validate_config_patch,
)
from http_utils import (
    LOOPBACK_HOSTS,
    drain_small_body,
    is_allowed_host_header,
    is_allowed_loopback_origin,
    is_allowed_static_path,
    load_audit_events,
    read_json_body,
)
from recon_service import (
    BOUNDS_PATTERN,
    SAFE_TAG_PATTERN,
    XML_FORBIDDEN_DECL_PATTERN,
    classify_keyword_tier,
    extract_label_value_pairs,
    synthesize_ranked_xpaths,
    xp_literal,
)

ENGINE_BANNER = f"Locara Core {ENGINE_VERSION} · Powered by Qureon (Katalon 11.7.0+ Ready)"
MAX_REQUEST_BYTES = 2 * 1024 * 1024  # 2 MiB max JSON/XML payload

# Retained for backward compatibility with pre-modularization external helper imports
_BadRequestError = BadRequestError
_PayloadTooLargeError = PayloadTooLargeError
_validate_config_patch = validate_config_patch
_build_audit_candidates = build_audit_candidates
_is_allowed_loopback_origin = is_allowed_loopback_origin
_is_allowed_host_header = is_allowed_host_header
_xp_literal = xp_literal

PORT = parse_port(sys.argv)
CONFIG_LOCK = threading.Lock()
CURRENT_CONFIG = dict(DEFAULT_CONFIG)
DASHBOARD_DIR = Path(__file__).resolve().parent
AUDIT_CANDIDATES = build_audit_candidates(DASHBOARD_DIR)
REPORT_FILE = DASHBOARD_DIR.parent / "Include" / "evidence" / "locara_report.html"
LEGACY_REPORT_FILE = DASHBOARD_DIR.parent / "Include" / "evidence" / "healing_report.html"
CONFIG_FILE = (
    Path(os.environ["LOCARA_CONFIG_FILE"]).expanduser()
    if os.environ.get("LOCARA_CONFIG_FILE")
    else DASHBOARD_DIR.parent / "locara.properties"
)


def load_persisted_config(config_path=None):
    """Load persisted locara.* configuration properties into CURRENT_CONFIG."""
    target_path = Path(config_path) if config_path is not None else CONFIG_FILE
    with CONFIG_LOCK:
        return _load_persisted_config_into(CURRENT_CONFIG, target_path)


def save_persisted_config(config_data, config_path=None):
    """Persist core locara.* keys into locara.properties while preserving unrelated entries."""
    target_path = Path(config_path) if config_path is not None else CONFIG_FILE
    _save_persisted_config_to(config_data, target_path)


load_persisted_config()


class LocaraHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(DASHBOARD_DIR), **kwargs)

    def end_headers(self):
        origin = self.headers.get("Origin")
        if is_allowed_loopback_origin(origin):
            self.send_header("Access-Control-Allow-Origin", origin)
            self.send_header("Vary", "Origin")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
        super().end_headers()

    def _send_bytes(self, status_code, body, content_type):
        self.send_response(status_code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def _send_json(self, status_code, payload):
        self._send_bytes(status_code, json.dumps(payload).encode("utf-8"), "application/json; charset=utf-8")

    def _validate_request_origin(self):
        if not is_allowed_host_header(self.headers.get("Host")):
            return False
        origin = self.headers.get("Origin")
        if origin is not None:
            return is_allowed_loopback_origin(origin)
        referer = self.headers.get("Referer")
        if referer is not None:
            return is_allowed_loopback_origin(referer)
        return True

    def do_OPTIONS(self):
        origin = self.headers.get("Origin")
        if origin is not None and not is_allowed_loopback_origin(origin):
            self._send_json(403, {"error": "Forbidden origin"})
            return
        self.send_response(204)
        self.end_headers()

    def list_directory(self, path):
        self._send_json(403, {"error": "Directory listing forbidden"})
        return None

    def _dispatch_read(self, static_handler):
        path = urlparse(self.path).path
        handler = {
            "/api/health": self.handle_health,
            "/api/audit": self.handle_audit,
            "/api/config": self.handle_get_config,
            "/api/report": self.handle_get_report,
        }.get(path)
        if handler:
            handler()
        elif not is_allowed_static_path(path):
            self._send_json(403, {"error": "Forbidden static resource"})
        else:
            static_handler()

    def do_HEAD(self):
        self._dispatch_read(super().do_HEAD)

    def do_GET(self):
        self._dispatch_read(super().do_GET)

    def do_POST(self):
        if not self._validate_request_origin():
            drain_small_body(self.rfile, self.headers)
            self._send_json(403, {"error": "Cross-origin POST rejected"})
            return
        routes = {
            "/api/config": self.handle_post_config,
            "/api/extract-fields": self.handle_extract_fields,
            "/api/synthesize-xpath": self.handle_synthesize_xpath,
            "/api/classify-keyword": self.handle_classify_keyword,
        }
        handler = routes.get(urlparse(self.path).path)
        if handler:
            handler()
        else:
            self._send_json(404, {"error": "Not found"})

    def _execute_json_post(self, op_name, handler_fn, allow_empty=False, handle_xml_parse=False):
        try:
            body = read_json_body(self.rfile, self.headers, MAX_REQUEST_BYTES, allow_empty=allow_empty)
            self._send_json(200, handler_fn(body))
        except PayloadTooLargeError as exc:
            self._send_json(413, {"error": str(exc)})
        except BadRequestError as exc:
            self._send_json(400, {"error": str(exc)})
        except ParseError:
            if handle_xml_parse:
                self._send_json(400, {"error": "Malformed XML payload"})
            else:
                raise
        except Exception as exc:
            self.log_error("%s failed: %r", op_name, exc)
            self._send_json(500, {"error": "Internal server error"})

    def handle_health(self):
        with CONFIG_LOCK:
            target = CURRENT_CONFIG.get("appTarget", "AUTO")
        self._send_json(200, {
            "status": "ONLINE",
            "engine": ENGINE_BANNER,
            "version": ENGINE_VERSION,
            "target": target,
            "timestamp": datetime.now().isoformat(),
            "server_port": PORT,
        })

    def handle_audit(self):
        self._send_json(200, load_audit_events(AUDIT_CANDIDATES))

    def handle_get_config(self):
        with CONFIG_LOCK:
            snapshot = dict(CURRENT_CONFIG)
        self._send_json(200, snapshot)

    def handle_post_config(self):
        def _apply(data):
            validated = validate_config_patch(data)
            with CONFIG_LOCK:
                candidate = dict(CURRENT_CONFIG)
                candidate.update(validated)
                save_persisted_config(candidate)
                CURRENT_CONFIG.clear()
                CURRENT_CONFIG.update(candidate)
                snapshot = dict(CURRENT_CONFIG)
            return {"success": True, "message": "Config saved", "config": snapshot}

        self._execute_json_post("handle_post_config", _apply, allow_empty=False)

    def handle_get_report(self):
        report_path = REPORT_FILE if REPORT_FILE.exists() else (
            LEGACY_REPORT_FILE if LEGACY_REPORT_FILE.exists() else None
        )
        if report_path is not None:
            self._send_bytes(200, report_path.read_bytes(), "text/html; charset=utf-8")
        else:
            self._send_bytes(
                404,
                b"Report not yet generated. Run dashboard/generate_report.py first.",
                "text/plain; charset=utf-8",
            )

    def handle_extract_fields(self):
        def _extract(body):
            pairs = extract_label_value_pairs(body.get("xml", ""))
            return {"success": True, "count": len(pairs), "pairs": pairs}

        self._execute_json_post("handle_extract_fields", _extract, allow_empty=False, handle_xml_parse=True)

    def handle_synthesize_xpath(self):
        def _synth(body):
            return {
                "success": True,
                "engine": f"Locara Katalon 11.7.0+ Object XPaths Synthesizer ({ENGINE_VERSION})",
                "rankedXPaths": synthesize_ranked_xpaths(body),
            }

        self._execute_json_post("handle_synthesize_xpath", _synth, allow_empty=True)

    def handle_classify_keyword(self):
        def _classify(body):
            raw_keyword = body.get("keyword")
            return {
                "success": True,
                "keyword": raw_keyword,
                "tier": classify_keyword_tier(raw_keyword),
                "note": "Aligns with Katalon 11.2 - 11.7+ Element State Specification",
            }

        self._execute_json_post("handle_classify_keyword", _classify, allow_empty=True)


def run():
    http.server.ThreadingHTTPServer.allow_reuse_address = True
    with http.server.ThreadingHTTPServer(("127.0.0.1", PORT), LocaraHandler) as httpd:
        target_name = os.environ.get("LOCARA_TARGET_NAME", "Active Automation Suite")
        print("\n========================================================")
        print("🛡️  Locara Mission Control Dashboard · Powered by Qureon")
        print(f"🌐  URL: http://127.0.0.1:{PORT}")
        print(f"📁  Serving: {DASHBOARD_DIR}")
        print(f"⚡  Target Project: {target_name}")
        print("🔒  Security: Bound strictly to localhost (127.0.0.1)")
        print("========================================================\n")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nShutting down server cleanly...")


if __name__ == "__main__":
    run()
