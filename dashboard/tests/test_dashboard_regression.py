import http.client
import importlib
import json
import sys
import tempfile
import threading
import unittest
from http.server import ThreadingHTTPServer
from pathlib import Path

DASHBOARD_DIR = Path(__file__).resolve().parent.parent
REPO_ROOT = DASHBOARD_DIR.parent
for entry in (str(DASHBOARD_DIR), str(REPO_ROOT)):
    if entry not in sys.path:
        sys.path.insert(0, entry)

import config_store  # noqa: E402
import generate_report  # noqa: E402
import recon_service  # noqa: E402
import server  # noqa: E402


class QuietHandler(server.LocaraHandler):
    def log_message(self, format, *args):
        pass


class DashboardRegressionTest(unittest.TestCase):
    def setUp(self):
        self.tmpdir = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmpdir.cleanup)

        self.orig_config_file = server.CONFIG_FILE
        self.orig_current_config = dict(server.CURRENT_CONFIG)
        self.orig_audit_candidates = list(server.AUDIT_CANDIDATES)
        self.orig_report_file = server.REPORT_FILE
        self.orig_legacy_report_file = server.LEGACY_REPORT_FILE
        self.addCleanup(self._restore_globals)

        tmp_root = Path(self.tmpdir.name)
        server.CONFIG_FILE = tmp_root / "locara.properties"
        server.REPORT_FILE = tmp_root / "locara_report.html"
        server.LEGACY_REPORT_FILE = tmp_root / "healing_report.html"
        server.AUDIT_CANDIDATES = [tmp_root / "locara_audit.json"]
        server.CURRENT_CONFIG.clear()
        server.CURRENT_CONFIG.update(config_store.DEFAULT_CONFIG)

        self.httpd = ThreadingHTTPServer(("127.0.0.1", 0), QuietHandler)
        self.port = self.httpd.server_address[1]
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()
        self.addCleanup(self._stop_server)

    def _restore_globals(self):
        server.CONFIG_FILE = self.orig_config_file
        server.CURRENT_CONFIG.clear()
        server.CURRENT_CONFIG.update(self.orig_current_config)
        server.AUDIT_CANDIDATES = self.orig_audit_candidates
        server.REPORT_FILE = self.orig_report_file
        server.LEGACY_REPORT_FILE = self.orig_legacy_report_file

    def _stop_server(self):
        self.httpd.shutdown()
        self.httpd.server_close()
        self.thread.join(timeout=2)

    def _raw_request(self, method, path, headers=None):
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=5)
        try:
            conn.request(method, path, headers=headers or {})
            resp = conn.getresponse()
            body = resp.read()
            hdrs = {k.lower(): v for k, v in resp.getheaders()}
            return resp.status, hdrs, body
        finally:
            conn.close()

    def test_head_requests_return_zero_body_and_matching_content_length(self):
        for path in ("/api/health", "/api/audit", "/api/config", "/api/report", "/server.py"):
            get_status, get_hdrs, get_body = self._raw_request("GET", path)
            head_status, head_hdrs, head_body = self._raw_request("HEAD", path)
            self.assertEqual(head_status, get_status, f"HEAD/GET status mismatch for {path}")
            self.assertEqual(head_body, b"", f"RFC 9110: HEAD must return empty body for {path}")
            self.assertEqual(
                head_hdrs.get("content-length"),
                str(len(get_body)),
                f"HEAD Content-Length must equal GET byte length for {path}",
            )
            self.assertEqual(head_hdrs.get("content-length"), get_hdrs.get("content-length"))

    def test_static_asset_allowlist_blocks_backend_and_allows_frontend(self):
        for forbidden in ("/server.py", "/config_store.py", "/tests/test_dashboard_server.py", "/..%2FREADME.md"):
            status, _, _ = self._raw_request("GET", forbidden)
            self.assertEqual(status, 403, f"Expected 403 for {forbidden}")

        for allowed in ("/", "/index.html", "/css/main.css", "/js/app.js", "/assets/logo.svg", "/assets/favicon.svg"):
            status, _, body = self._raw_request("GET", allowed)
            self.assertEqual(status, 200, f"Expected 200 for {allowed}")
            self.assertGreater(len(body), 0)

    def test_report_endpoint_returns_404_when_missing_and_prefers_canonical(self):
        status_missing, _, _ = self._raw_request("GET", "/api/report")
        self.assertEqual(status_missing, 404)

        server.LEGACY_REPORT_FILE.write_text("<html>legacy-report</html>", encoding="utf-8")
        status_legacy, _, body_legacy = self._raw_request("GET", "/api/report")
        self.assertEqual(status_legacy, 200)
        self.assertIn(b"legacy-report", body_legacy)

        server.REPORT_FILE.write_text("<html>canonical-report</html>", encoding="utf-8")
        status_canon, _, body_canon = self._raw_request("GET", "/api/report")
        self.assertEqual(status_canon, 200)
        self.assertIn(b"canonical-report", body_canon)

    def test_parse_port_rejects_out_of_range_ports(self):
        for bad in ("0", "-1", "65536", "999999"):
            self.assertEqual(config_store.parse_port(["server.py", "--port", bad]), config_store.DEFAULT_PORT)
            self.assertEqual(config_store.parse_port(["server.py", bad]), config_store.DEFAULT_PORT)
        self.assertEqual(config_store.parse_port(["server.py", "--port", "1"]), 1)
        self.assertEqual(config_store.parse_port(["server.py", "--port=65535"]), 65535)

    def test_load_persisted_config_per_key_resilience_and_atomic_save(self):
        cfg_file = Path(self.tmpdir.name) / "locara.properties"
        cfg_file.write_text(
            "locara.threshold=99.9\n"
            "locara.target=KMP\n"
            "locara.audit.enabled=false\n",
            encoding="utf-8",
        )
        target = dict(config_store.DEFAULT_CONFIG)
        loaded = config_store.load_persisted_config(target, cfg_file)
        self.assertEqual(loaded["appTarget"], "KMP")
        self.assertFalse(loaded["auditLedgerEnabled"])
        self.assertAlmostEqual(loaded["minSimilarityThreshold"], config_store.DEFAULT_CONFIG["minSimilarityThreshold"])

        config_store.save_persisted_config(loaded, cfg_file)
        leftover_temps = list(cfg_file.parent.glob(".locara_cfg_*"))
        self.assertEqual(leftover_temps, [], "Atomic save must leave no stray temp files")

    def test_recon_filters_hidden_or_zero_height_nodes_and_keyword_aliases(self):
        xml = (
            "<hierarchy>"
            "<android.widget.TextView text=\"Tersembunyi\" bounds=\"[40,100][240,140]\" displayed=\"false\"/>"
            "<android.widget.TextView text=\"Rp 999\" bounds=\"[260,100][600,140]\" displayed=\"false\"/>"
            "<android.widget.TextView text=\"ZeroHeight\" bounds=\"[40,200][240,200]\"/>"
            "<android.widget.TextView text=\"Rp 0\" bounds=\"[260,200][600,200]\"/>"
            "<android.widget.TextView text=\"Total\" bounds=\"[40,300][240,340]\" displayed=\"true\"/>"
            "<android.widget.TextView text=\"Rp 50.000\" bounds=\"[260,300][600,340]\" displayed=\"true\"/>"
            "</hierarchy>"
        )
        pairs = recon_service.extract_label_value_pairs(xml)
        pair_map = {item["label"]: item["value"] for item in pairs}
        self.assertEqual(pair_map, {"Total": "Rp 50.000"})
        self.assertEqual(recon_service.classify_keyword_tier("Mobile.find_first_element"), "CONDITIONAL_PROBE")
        self.assertEqual(recon_service.classify_keyword_tier("WebUI.check_point_status"), "CHECKPOINT")

    def test_package_import_and_generate_report_creates_nested_parent_dirs(self):
        pkg_mod = importlib.import_module("dashboard.generate_report")
        self.assertTrue(hasattr(pkg_mod, "generate_report"))

        nested_out = Path(self.tmpdir.name) / "deep" / "nested" / "locara_report.html"
        audit_file = Path(self.tmpdir.name) / "empty_audit.json"
        audit_file.write_text("[]", encoding="utf-8")
        written = generate_report.generate_report(output_path=nested_out, audit_path=audit_file)
        self.assertTrue(written.exists())
        self.assertEqual(written, nested_out)


if __name__ == "__main__":
    unittest.main()
