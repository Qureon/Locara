import sys
import tempfile
import unittest
from pathlib import Path

DASHBOARD_DIR = Path(__file__).resolve().parent.parent
if str(DASHBOARD_DIR) not in sys.path:
    sys.path.insert(0, str(DASHBOARD_DIR))

import config_store  # noqa: E402
import http_utils  # noqa: E402
import recon_service  # noqa: E402


class ConfigStoreTest(unittest.TestCase):
    def test_parse_port_variants(self):
        self.assertEqual(config_store.parse_port(["server.py"]), config_store.DEFAULT_PORT)
        self.assertEqual(config_store.parse_port(["server.py", "--port", "9090"]), 9090)
        self.assertEqual(config_store.parse_port(["server.py", "-p", "8123"]), 8123)
        self.assertEqual(config_store.parse_port(["server.py", "--port=8765"]), 8765)
        self.assertEqual(config_store.parse_port(["server.py", "8999"]), 8999)
        self.assertEqual(config_store.parse_port(["server.py", "--port", "notanint"]), config_store.DEFAULT_PORT)

    def test_validate_config_patch_rejects_invalid_and_normalizes_valid(self):
        with self.assertRaises(config_store.BadRequestError):
            config_store.validate_config_patch(["not", "a", "dict"])
        with self.assertRaises(config_store.BadRequestError):
            config_store.validate_config_patch({"appTarget": "INVALID_TARGET"})
        with self.assertRaises(config_store.BadRequestError):
            config_store.validate_config_patch({"auditLedgerEnabled": "true"})

        normalized = config_store.validate_config_patch({
            "appTarget": " kmp ",
            "minSimilarityThreshold": 0.72559,
            "auditLedgerEnabled": True,
        })
        self.assertEqual(normalized["appTarget"], "KMP")
        self.assertAlmostEqual(normalized["minSimilarityThreshold"], 0.7256)
        self.assertTrue(normalized["auditLedgerEnabled"])


class HttpUtilsTest(unittest.TestCase):
    def test_origin_and_host_allowlist(self):
        self.assertTrue(http_utils.is_allowed_loopback_origin("http://127.0.0.1:8088"))
        self.assertTrue(http_utils.is_allowed_loopback_origin("http://localhost:3000"))
        self.assertFalse(http_utils.is_allowed_loopback_origin("https://attacker.example"))
        self.assertFalse(http_utils.is_allowed_loopback_origin("javascript:alert(1)"))

        self.assertTrue(http_utils.is_allowed_host_header("127.0.0.1:8088"))
        self.assertFalse(http_utils.is_allowed_host_header("evil.example:8088"))

    def test_load_audit_events_handles_jsonl_and_ignores_corrupt_lines(self):
        with tempfile.TemporaryDirectory() as tmp:
            jsonl = Path(tmp) / "locara_audit.json"
            jsonl.write_text('{"status":"SUCCESS"}\n{corrupt-json\n{"status":"SCROLLED"}\n', encoding="utf-8")
            events = http_utils.load_audit_events([Path(tmp) / "missing.json", jsonl])
            self.assertEqual([e["status"] for e in events], ["SUCCESS", "SCROLLED"])


class ReconServiceTest(unittest.TestCase):
    def test_xp_literal_quotes_mixed_apostrophe_and_double_quote(self):
        self.assertEqual(recon_service.xp_literal("Pay"), "'Pay'")
        self.assertEqual(recon_service.xp_literal("User's"), '"User\'s"')
        mixed = recon_service.xp_literal("Owner's \"Account\"")
        self.assertTrue(mixed.startswith("concat("))
        self.assertIn("Account", mixed)

    def test_classify_keyword_tier_handles_none_and_invalid_type(self):
        self.assertEqual(recon_service.classify_keyword_tier(None), "INTERACTION")
        self.assertEqual(recon_service.classify_keyword_tier("Mobile.isElementExist"), "CONDITIONAL_PROBE")
        self.assertEqual(recon_service.classify_keyword_tier("WebUI.verifyMatch"), "CHECKPOINT")
        with self.assertRaises(config_store.BadRequestError):
            recon_service.classify_keyword_tier(12345)


if __name__ == "__main__":
    unittest.main()
