import http.client
import json
import os
import sys
import tempfile
import threading
import unittest
from http.server import ThreadingHTTPServer
from pathlib import Path

DASHBOARD_DIR = Path(__file__).resolve().parent.parent
if str(DASHBOARD_DIR) not in sys.path:
    sys.path.insert(0, str(DASHBOARD_DIR))

import generate_report  # noqa: E402
import server  # noqa: E402


class QuietLocaraHandler(server.LocaraHandler):
    def log_message(self, format, *args):
        # Silence HTTP request logging during automated unittest runs
        pass


class DashboardServerSecurityAndApiTest(unittest.TestCase):
    def setUp(self):
        self.tmpdir = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmpdir.cleanup)

        self.orig_config_file = server.CONFIG_FILE
        self.orig_current_config = dict(server.CURRENT_CONFIG)
        self.orig_audit_candidates = list(server.AUDIT_CANDIDATES)
        self.addCleanup(self._restore_server_globals)

        server.CONFIG_FILE = Path(self.tmpdir.name) / "locara.properties"
        server.CURRENT_CONFIG.clear()
        server.CURRENT_CONFIG.update(self.orig_current_config)

        self.httpd = ThreadingHTTPServer(("127.0.0.1", 0), QuietLocaraHandler)
        self.port = self.httpd.server_address[1]
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()
        self.addCleanup(self._stop_server)

    def _restore_server_globals(self):
        server.CONFIG_FILE = self.orig_config_file
        server.CURRENT_CONFIG.clear()
        server.CURRENT_CONFIG.update(self.orig_current_config)
        server.AUDIT_CANDIDATES = self.orig_audit_candidates

    def _stop_server(self):
        self.httpd.shutdown()
        self.httpd.server_close()
        self.thread.join(timeout=2)

    def _request(self, method, path, body=None, headers=None):
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=5)
        try:
            req_headers = dict(headers or {})
            if body is not None:
                payload = body.encode("utf-8") if isinstance(body, str) else body
                conn.request(method, path, body=payload, headers=req_headers)
            else:
                conn.request(method, path, headers=req_headers)
            resp = conn.getresponse()
            raw = resp.read().decode("utf-8")
            return resp.status, dict(resp.getheaders()), raw
        finally:
            conn.close()

    def test_health_and_loopback_cors_policy(self):
        status, hdrs, raw = self._request(
            "GET",
            "/api/health",
            headers={"Origin": "https://evil.example"},
        )
        self.assertEqual(status, 200)
        data = json.loads(raw)
        self.assertEqual(data["version"], "v1.15.1")
        lower_hdrs = {k.lower(): v for k, v in hdrs.items()}
        self.assertNotIn("access-control-allow-origin", lower_hdrs)

        status2, hdrs2, _ = self._request(
            "GET",
            "/api/health",
            headers={"Origin": "http://localhost:8088"},
        )
        self.assertEqual(status2, 200)
        lower_hdrs2 = {k.lower(): v for k, v in hdrs2.items()}
        self.assertEqual(lower_hdrs2.get("access-control-allow-origin"), "http://localhost:8088")
        self.assertEqual(lower_hdrs2.get("vary"), "Origin")

    def test_cross_origin_post_rejected(self):
        status, _, raw = self._request(
            "POST",
            "/api/config",
            body=json.dumps({"appTarget": "KMP"}),
            headers={
                "Content-Type": "application/json",
                "Origin": "https://evil.example",
            },
        )
        self.assertEqual(status, 403)
        self.assertIn("error", json.loads(raw))

    def test_payload_size_and_json_validation(self):
        # Oversized Content-Length -> HTTP 413
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=5)
        try:
            conn.putrequest("POST", "/api/config")
            conn.putheader("Host", f"127.0.0.1:{self.port}")
            conn.putheader("Content-Type", "application/json")
            conn.putheader("Content-Length", str(server.MAX_REQUEST_BYTES + 1))
            conn.endheaders()
            resp = conn.getresponse()
            _ = resp.read()
            self.assertEqual(resp.status, 413)
        finally:
            conn.close()

        # Negative Content-Length -> HTTP 400
        conn2 = http.client.HTTPConnection("127.0.0.1", self.port, timeout=5)
        try:
            conn2.putrequest("POST", "/api/config")
            conn2.putheader("Host", f"127.0.0.1:{self.port}")
            conn2.putheader("Content-Type", "application/json")
            conn2.putheader("Content-Length", "-5")
            conn2.endheaders()
            resp2 = conn2.getresponse()
            _ = resp2.read()
            self.assertEqual(resp2.status, 400)
        finally:
            conn2.close()

        # Malformed JSON -> HTTP 400
        status, _, _ = self._request(
            "POST",
            "/api/config",
            body="{invalid-json",
            headers={"Content-Type": "application/json"},
        )
        self.assertEqual(status, 400)

    def test_config_allowlist_and_properties_roundtrip(self):
        server.CONFIG_FILE.write_text("# user comment\ncustom.keep=yes\n", encoding="utf-8")

        # Unknown key rejected with HTTP 400
        bad_status, _, _ = self._request(
            "POST",
            "/api/config",
            body=json.dumps({"arbitraryExec": "rm -rf /"}),
            headers={"Content-Type": "application/json"},
        )
        self.assertEqual(bad_status, 400)

        # Out-of-range weight rejected with HTTP 400
        bad_weight, _, _ = self._request(
            "POST",
            "/api/config",
            body=json.dumps({"minSimilarityThreshold": 1.5}),
            headers={"Content-Type": "application/json"},
        )
        self.assertEqual(bad_weight, 400)

        ok_status, _, raw = self._request(
            "POST",
            "/api/config",
            body=json.dumps({
                "appTarget": "KMP",
                "minSimilarityThreshold": 0.78,
                "auditLedgerEnabled": False,
            }),
            headers={"Content-Type": "application/json"},
        )
        self.assertEqual(ok_status, 200)
        self.assertTrue(json.loads(raw)["success"])

        persisted_text = server.CONFIG_FILE.read_text(encoding="utf-8")
        self.assertIn("custom.keep=yes", persisted_text)
        self.assertIn("locara.target=KMP", persisted_text)
        self.assertIn("locara.threshold=0.78", persisted_text)
        self.assertIn("locara.audit.enabled=false", persisted_text)

        # Reset CURRENT_CONFIG and reload from locara.properties
        server.CURRENT_CONFIG["appTarget"] = "AUTO"
        server.CURRENT_CONFIG["minSimilarityThreshold"] = 0.70
        server.CURRENT_CONFIG["auditLedgerEnabled"] = True
        reloaded = server.load_persisted_config(server.CONFIG_FILE)
        self.assertEqual(reloaded["appTarget"], "KMP")
        self.assertAlmostEqual(reloaded["minSimilarityThreshold"], 0.78)
        self.assertFalse(reloaded["auditLedgerEnabled"])

    def test_extract_fields_xxe_blocked_and_spatial_pairs(self):
        xxe = "<?xml version='1.0'?><!DOCTYPE foo [<!ENTITY e 'x'>]><hierarchy/>"
        status_xxe, _, _ = self._request(
            "POST",
            "/api/extract-fields",
            body=json.dumps({"xml": xxe}),
            headers={"Content-Type": "application/json"},
        )
        self.assertEqual(status_xxe, 400)

        mobile_xml = (
            "<hierarchy>"
            "<android.widget.TextView text=\"Nominal\" bounds=\"[40,100][220,140]\"/>"
            "<android.widget.TextView text=\"Rp 150.000\" bounds=\"[260,100][640,140]\"/>"
            "<android.widget.TextView text=\"Nomor Tujuan\" bounds=\"[40,220][300,260]\"/>"
            "<android.widget.TextView text=\"9000123456\" bounds=\"[40,275][400,315]\"/>"
            "</hierarchy>"
        )
        status_ok, _, raw = self._request(
            "POST",
            "/api/extract-fields",
            body=json.dumps({"xml": mobile_xml}),
            headers={"Content-Type": "application/json"},
        )
        self.assertEqual(status_ok, 200)
        payload = json.loads(raw)
        pair_map = {item["label"]: item["value"] for item in payload["pairs"]}
        self.assertEqual(pair_map.get("Nominal"), "Rp 150.000")
        self.assertEqual(pair_map.get("Nomor Tujuan"), "9000123456")

    def test_synthesize_xpath_validation_and_escaping(self):
        bad_status, _, _ = self._request(
            "POST",
            "/api/synthesize-xpath",
            body=json.dumps({"tag": "button[@id='x']", "text": "Pay"}),
            headers={"Content-Type": "application/json"},
        )
        self.assertEqual(bad_status, 400)

        ok_status, _, raw = self._request(
            "POST",
            "/api/synthesize-xpath",
            body=json.dumps({
                "tag": "button",
                "resourceId": "com.example.app:id/btn_pay",
                "text": "Owner's \"Pay\"",
            }),
            headers={"Content-Type": "application/json"},
        )
        self.assertEqual(ok_status, 200)
        ranked = json.loads(raw)["rankedXPaths"]
        self.assertIn("xpath:attributes", ranked)
        self.assertIn("concat(", ranked["xpath:attributes"])
        self.assertIn("@resource-id=", ranked["xpath:attributes"])

    def test_classify_keyword_tiers(self):
        for kw, expected in (
            ("Mobile.tap", "INTERACTION"),
            ("WebUI.isElementVisible", "CONDITIONAL_PROBE"),
            ("WebUI.verifyElementText", "CHECKPOINT"),
        ):
            status, _, raw = self._request(
                "POST",
                "/api/classify-keyword",
                body=json.dumps({"keyword": kw}),
                headers={"Content-Type": "application/json"},
            )
            self.assertEqual(status, 200)
            self.assertEqual(json.loads(raw)["tier"], expected)

    def test_generate_report_canonical_keys_and_xss_escaping(self):
        audit_file = Path(self.tmpdir.name) / "locara_audit.json"
        output_file = Path(self.tmpdir.name) / "healing_report.html"
        events = [
            {
                "timestamp": "2026-11-13T10:00:00",
                "screen": "TransferScreen<script>alert(1)</script>",
                "action": "TAP",
                "targetHint": "Pay Button<img src=x onerror=alert(2)>",
                "origLocator": "//*[@id='broken']",
                "healedLocator": "//*[@text='Pay Now']",
                "generatedRs": "Object Repository/btn_pay.rs",
                "status": "HEALED",
                "confidence": "invalid-float",
                "framework": "Compose",
            }
        ]
        audit_file.write_text("\n".join(json.dumps(e) for e in events), encoding="utf-8")

        result_path = generate_report.generate_report(output_path=output_file, audit_path=audit_file)
        self.assertTrue(result_path.exists())
        html = result_path.read_text(encoding="utf-8")
        self.assertIn("v1.15.1", html)
        self.assertIn("//*[@text=&#x27;Pay Now&#x27;]", html)
        self.assertNotIn("<script>alert(1)</script>", html)
        self.assertIn("&lt;script&gt;alert(1)&lt;/script&gt;", html)
        self.assertNotIn("<img src=x onerror=alert(2)>", html)
        self.assertEqual(generate_report._safe_confidence_pct("invalid-float"), "0.0%")


if __name__ == "__main__":
    unittest.main()
