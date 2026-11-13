#!/usr/bin/env python3
"""
==============================================================================
🛡️ LOCARA MISSION CONTROL — MODULAR TELEMETRY SERVER (POWERED BY QUREON)
Qureon Autonomous QA Engineering · Qureon Engineering
==============================================================================
"""

import sys
import os
import re
import json
import math
import http.server
import socketserver
import xml.etree.ElementTree as ET
from pathlib import Path
from datetime import datetime
from urllib.parse import urlparse
from xml.etree.ElementTree import ParseError

ENGINE_VERSION = "v1.15.1"
ENGINE_BANNER = f"Locara Core {ENGINE_VERSION} · Powered by Qureon (Katalon 11.7.0+ Ready)"
MAX_REQUEST_BYTES = 2 * 1024 * 1024  # 2 MiB max JSON/XML payload
LOOPBACK_HOSTS = frozenset({"127.0.0.1", "localhost", "::1"})
BOUNDS_PATTERN = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")
XML_FORBIDDEN_DECL_PATTERN = re.compile(r"<!\s*(DOCTYPE|ENTITY)\b", re.IGNORECASE)
SAFE_TAG_PATTERN = re.compile(r"^(\*|[A-Za-z_][A-Za-z0-9_.:-]*)$")
ALLOWED_TARGETS = frozenset({"AUTO", "UMUM", "REVAMP", "KMP", "LEGACY_XML", "LEGACY", "WEB", "GENERIC"})
FLOAT_CONFIG_KEYS = frozenset({
    "minSimilarityThreshold",
    "textSimilarityWeight",
    "resourceIdSimilarityWeight",
    "contentDescSimilarityWeight",
})
PROPERTIES_KEY_MAP = {
    "appTarget": "locara.target",
    "minSimilarityThreshold": "locara.threshold",
    "textSimilarityWeight": "locara.weight.text",
    "resourceIdSimilarityWeight": "locara.weight.id",
    "contentDescSimilarityWeight": "locara.weight.desc",
    "auditLedgerEnabled": "locara.audit.enabled",
}


class _BadRequestError(ValueError):
    """Raised when an incoming HTTP payload violates schema or security constraints."""


class _PayloadTooLargeError(ValueError):
    """Raised when an incoming HTTP payload exceeds MAX_REQUEST_BYTES."""


def _is_allowed_loopback_origin(origin_value):
    if not origin_value:
        return False
    try:
        parsed = urlparse(origin_value)
    except Exception:
        return False
    return parsed.scheme in ("http", "https") and (parsed.hostname or "").lower() in LOOPBACK_HOSTS


def _is_allowed_host_header(host_header):
    if not host_header:
        return True
    try:
        parsed = urlparse(f"http://{host_header}")
        return (parsed.hostname or "").lower() in LOOPBACK_HOSTS
    except Exception:
        return False


def _xp_literal(val):
    if "'" not in val:
        return f"'{val}'"
    if '"' not in val:
        return f'"{val}"'
    parts = val.split("'")
    return "concat(" + ", \"'\", ".join(f"'{p}'" for p in parts) + ")"


def _parse_port(argv):
    for i, arg in enumerate(argv[1:], start=1):
        if arg in ("--port", "-p") and i + 1 < len(argv):
            try:
                return int(argv[i + 1])
            except ValueError:
                return 8088
        if arg.startswith("--port="):
            try:
                return int(arg.split("=", 1)[1])
            except ValueError:
                return 8088
        if not arg.startswith("-"):
            try:
                return int(arg)
            except ValueError:
                pass
    return 8088

PORT = _parse_port(sys.argv)
DEFAULT_CONFIG = {
    "appTarget": "AUTO",
    "minSimilarityThreshold": 0.70,
    "textSimilarityWeight": 0.35,
    "resourceIdSimilarityWeight": 0.25,
    "contentDescSimilarityWeight": 0.20,
    "auditLedgerEnabled": True,
}
CURRENT_CONFIG = dict(DEFAULT_CONFIG)
DASHBOARD_DIR = Path(__file__).resolve().parent


def _build_audit_candidates(dashboard_dir=DASHBOARD_DIR):
    """Build prioritized list of candidate audit log paths from explicit env & workspace roots."""
    candidates = []
    env_log = os.environ.get("LOCARA_AUDIT_LOG")
    if env_log:
        candidates.append(Path(env_log).expanduser())

    search_roots = [dashboard_dir.parent, Path.cwd()]
    env_dirs = os.environ.get("LOCARA_AUDIT_DIRS", "")
    for raw_dir in env_dirs.split(os.pathsep):
        cleaned = raw_dir.strip()
        if cleaned:
            search_roots.append(Path(cleaned).expanduser())

    seen = set()
    for root in search_roots:
        for rel in (
            Path("Include") / "evidence" / "locara_audit.json",
            Path("Include") / "evidence" / "healing_audit.json",
            Path("locara_audit.json"),
            Path("healing_audit.json"),
        ):
            cand = (root / rel).resolve()
            if str(cand) not in seen:
                seen.add(str(cand))
                candidates.append(cand)
    return candidates


AUDIT_CANDIDATES = _build_audit_candidates()

REPORT_FILE = DASHBOARD_DIR.parent / "Include" / "evidence" / "healing_report.html"
CONFIG_FILE = (
    Path(os.environ["LOCARA_CONFIG_FILE"]).expanduser()
    if os.environ.get("LOCARA_CONFIG_FILE")
    else DASHBOARD_DIR.parent / "locara.properties"
)


def _validate_config_patch(data):
    if not isinstance(data, dict):
        raise _BadRequestError("Config payload must be a JSON object")

    allowed_keys = set(PROPERTIES_KEY_MAP.keys()) | {"updatedAt"}
    unknown = set(data.keys()) - allowed_keys
    if unknown:
        raise _BadRequestError(f"Unsupported configuration key(s): {', '.join(sorted(unknown))}")

    validated = {}
    if "appTarget" in data:
        raw_target = data["appTarget"]
        if not isinstance(raw_target, str):
            raise _BadRequestError("appTarget must be a string")
        normalized_target = raw_target.strip().upper()
        if normalized_target not in ALLOWED_TARGETS:
            raise _BadRequestError("Unsupported appTarget profile")
        validated["appTarget"] = normalized_target

    for key in FLOAT_CONFIG_KEYS:
        if key in data:
            val = data[key]
            if isinstance(val, bool) or not isinstance(val, (int, float)):
                raise _BadRequestError(f"{key} must be a numeric value")
            fval = float(val)
            if not math.isfinite(fval) or fval < 0.0 or fval > 1.0:
                raise _BadRequestError(f"{key} must be within [0.0, 1.0]")
            validated[key] = round(fval, 4)

    if "auditLedgerEnabled" in data:
        val = data["auditLedgerEnabled"]
        if not isinstance(val, bool):
            raise _BadRequestError("auditLedgerEnabled must be a boolean")
        validated["auditLedgerEnabled"] = val

    if "updatedAt" in data:
        updated_at = data["updatedAt"]
        if not isinstance(updated_at, str) or len(updated_at) > 64:
            raise _BadRequestError("updatedAt must be a short ISO timestamp string")

    return validated


def load_persisted_config(config_path=None):
    """Load persisted locara.* configuration properties into CURRENT_CONFIG."""
    target_path = Path(config_path) if config_path is not None else CONFIG_FILE
    if not target_path.exists() or not target_path.is_file():
        return dict(CURRENT_CONFIG)

    reverse_map = {prop_key: cfg_key for cfg_key, prop_key in PROPERTIES_KEY_MAP.items()}
    patch = {}
    try:
        for raw_line in target_path.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or line.startswith("!") or "=" not in line:
                continue
            prop_key, prop_val = line.split("=", 1)
            prop_key = prop_key.strip()
            prop_val = prop_val.strip()
            cfg_key = reverse_map.get(prop_key)
            if not cfg_key:
                continue
            if cfg_key == "appTarget":
                patch[cfg_key] = prop_val
            elif cfg_key in FLOAT_CONFIG_KEYS:
                try:
                    patch[cfg_key] = float(prop_val)
                except ValueError:
                    pass
            elif cfg_key == "auditLedgerEnabled":
                low = prop_val.lower()
                if low in ("true", "false"):
                    patch[cfg_key] = low == "true"
        if patch:
            CURRENT_CONFIG.update(_validate_config_patch(patch))
    except Exception:
        pass
    return dict(CURRENT_CONFIG)


def save_persisted_config(config_data, config_path=None):
    """Persist core locara.* keys into locara.properties while preserving unrelated entries."""
    target_path = Path(config_path) if config_path is not None else CONFIG_FILE
    updates = {}
    for cfg_key, prop_key in PROPERTIES_KEY_MAP.items():
        if cfg_key in config_data:
            val = config_data[cfg_key]
            if isinstance(val, bool):
                updates[prop_key] = "true" if val else "false"
            else:
                updates[prop_key] = str(val)

    existing_lines = []
    if target_path.exists() and target_path.is_file():
        existing_lines = target_path.read_text(encoding="utf-8").splitlines()

    written_keys = set()
    new_lines = []
    for line in existing_lines:
        stripped = line.strip()
        if stripped and not stripped.startswith("#") and not stripped.startswith("!") and "=" in line:
            k = line.split("=", 1)[0].strip()
            if k in updates:
                new_lines.append(f"{k}={updates[k]}")
                written_keys.add(k)
                continue
        new_lines.append(line)

    for prop_key, prop_val in updates.items():
        if prop_key not in written_keys:
            new_lines.append(f"{prop_key}={prop_val}")

    target_path.parent.mkdir(parents=True, exist_ok=True)
    target_path.write_text("\n".join(new_lines) + "\n", encoding="utf-8")


load_persisted_config()


def extract_label_value_pairs(xml_text):
    """Extract spatial label-value pairs from Android/iOS/HTML XML hierarchy safely."""
    if not isinstance(xml_text, str) or not xml_text.strip():
        raise _BadRequestError("Empty XML content")
    if XML_FORBIDDEN_DECL_PATTERN.search(xml_text):
        raise _BadRequestError("DTD and entity declarations are not permitted")

    root = ET.fromstring(xml_text)
    nodes = []
    for el in root.iter():
        text = el.get("text", "") or ""
        desc = el.get("content-desc", "") or ""
        b_str = el.get("bounds", "")
        m = BOUNDS_PATTERN.match(b_str)
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            nodes.append({
                "tag": el.tag,
                "text": text.strip(),
                "desc": desc.strip(),
                "x1": x1, "y1": y1, "x2": x2, "y2": y2,
                "w": x2 - x1, "h": y2 - y1,
                "clickable": el.get("clickable", "false") == "true",
            })

    pairs = []
    non_buttons = [n for n in nodes if "button" not in n["tag"].lower() and n["w"] > 0]
    labels = [n for n in non_buttons if n["text"]]
    claimed = set()

    for lbl in labels:
        if id(lbl) in claimed:
            continue
        best_val = None
        best_score = 0.0
        for cand in non_buttons:
            if cand is lbl or id(cand) in claimed:
                continue
            if cand["x1"] >= lbl["x1"] and cand["x2"] > lbl["x2"]:
                v_overlap = max(0, min(lbl["y2"], cand["y2"]) - max(lbl["y1"], cand["y1"]))
                min_h = max(1, min(lbl["h"], cand["h"]))
                if (v_overlap / min_h) >= 0.40 and (cand["text"] or cand["desc"] or cand["clickable"]):
                    h_dist = max(0, cand["x1"] - lbl["x2"])
                    score = (v_overlap / min_h) * 0.6 + (1.0 / (1.0 + h_dist / 100.0)) * 0.4
                    if score > best_score:
                        best_score = score
                        best_val = cand

        if not best_val:
            for cand in non_buttons:
                if cand is lbl or id(cand) in claimed or not cand["text"]:
                    continue
                v_dist = cand["y1"] - lbl["y2"]
                if 0 <= v_dist <= 40 and abs(cand["x1"] - lbl["x1"]) <= 40:
                    best_val = cand
                    best_score = 0.90
                    break

        if best_val and (best_val["text"] or best_val["desc"]):
            val_text = best_val["text"] or best_val["desc"]
            if val_text != lbl["text"]:
                claimed.add(id(best_val))
                pairs.append({
                    "label": lbl["text"],
                    "value": val_text,
                    "score": round(best_score, 2),
                    "bounds": f"[{lbl['x1']},{lbl['y1']}][{best_val['x2']},{best_val['y2']}]",
                })
    return pairs


def synthesize_ranked_xpaths(body):
    """Synthesize safe XPath 1.0 candidates using quoted XPath literals."""
    if not isinstance(body, dict):
        raise _BadRequestError("Payload must be a JSON object")

    raw_tag = body.get("tag", "*")
    tag = "*" if raw_tag is None else str(raw_tag).strip() or "*"
    if not SAFE_TAG_PATTERN.match(tag):
        raise _BadRequestError("Invalid XML tag name")

    def _opt_str(*keys):
        for k in keys:
            if k in body and body[k] is not None:
                if not isinstance(body[k], str):
                    raise _BadRequestError(f"Field '{k}' must be a string")
                return body[k]
        return ""

    text = _opt_str("text")
    res_id = _opt_str("resourceId", "id")
    desc = _opt_str("contentDesc", "desc")
    cls = _opt_str("className", "class")

    ranked = {}
    if res_id and text:
        ranked["xpath:attributes"] = f"//{tag}[@resource-id={_xp_literal(res_id)} and @text={_xp_literal(text)}]"
    elif res_id:
        ranked["xpath:attributes"] = f"//{tag}[@resource-id={_xp_literal(res_id)}]"
    elif desc:
        ranked["xpath:attributes"] = f"//{tag}[@content-desc={_xp_literal(desc)}]"
    elif text:
        ranked["xpath:attributes"] = f"//{tag}[@text={_xp_literal(text)}]"
    else:
        ranked["xpath:attributes"] = f"//{tag}"

    if res_id:
        ranked["xpath:idRelative"] = f"//*[@resource-id={_xp_literal(res_id)} or @id={_xp_literal(res_id)}]"
    if text:
        ranked["xpath:custom"] = f"//{tag}[text()={_xp_literal(text)} or .={_xp_literal(text)}]"
    if cls:
        ranked["xpath:position"] = f"//*[@class={_xp_literal(cls)}]"
    return ranked


def classify_keyword_tier(keyword):
    """Classify WebUI/Mobile/Windows keyword into Locara healing risk tiers."""
    if keyword is None:
        raw_kw = ""
    elif isinstance(keyword, str):
        raw_kw = keyword
    else:
        raise _BadRequestError("Field 'keyword' must be a string")

    kw = raw_kw.strip().lower()
    if kw.startswith("webui.") or kw.startswith("mobile.") or kw.startswith("windows."):
        kw = kw.split(".", 1)[1]

    tier = "INTERACTION"
    if (
        kw.startswith("iselement")
        or kw.startswith("is_element")
        or "findfirst" in kw
        or "probe" in kw
        or "optional" in kw
    ):
        tier = "CONDITIONAL_PROBE"
    elif kw.startswith("verify") or kw.startswith("assert") or "checkpoint" in kw:
        tier = "CHECKPOINT"
    return tier

class LocaraHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(DASHBOARD_DIR), **kwargs)

    def end_headers(self):
        origin = self.headers.get("Origin")
        if _is_allowed_loopback_origin(origin):
            self.send_header("Access-Control-Allow-Origin", origin)
            self.send_header("Vary", "Origin")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
        super().end_headers()

    def _send_json(self, status_code, payload):
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.end_headers()
        self.wfile.write(json.dumps(payload).encode("utf-8"))

    def _read_json_body(self, allow_empty=False):
        raw_len = self.headers.get("Content-Length")
        if raw_len is None:
            length = 0
        else:
            try:
                length = int(raw_len)
            except ValueError as exc:
                raise _BadRequestError("Invalid Content-Length header") from exc
        if length < 0:
            raise _BadRequestError("Negative Content-Length header")
        if length > MAX_REQUEST_BYTES:
            raise _PayloadTooLargeError(f"Request body exceeds {MAX_REQUEST_BYTES} byte limit")

        if length == 0:
            if allow_empty:
                return {}
            raise _BadRequestError("Request body is empty")

        raw_bytes = self.rfile.read(length)
        try:
            text = raw_bytes.decode("utf-8")
        except UnicodeDecodeError as exc:
            raise _BadRequestError("Request body must be valid UTF-8") from exc

        if not text.strip():
            if allow_empty:
                return {}
            raise _BadRequestError("Request body is empty")

        try:
            parsed = json.loads(text)
        except json.JSONDecodeError as exc:
            raise _BadRequestError("Malformed JSON payload") from exc

        if not isinstance(parsed, dict):
            raise _BadRequestError("JSON root must be an object")
        return parsed

    def _validate_request_origin(self):
        if not _is_allowed_host_header(self.headers.get("Host")):
            return False
        origin = self.headers.get("Origin")
        if origin is not None:
            return _is_allowed_loopback_origin(origin)
        referer = self.headers.get("Referer")
        if referer is not None:
            return _is_allowed_loopback_origin(referer)
        return True

    def do_OPTIONS(self):
        origin = self.headers.get("Origin")
        if origin is not None and not _is_allowed_loopback_origin(origin):
            self._send_json(403, {"error": "Forbidden origin"})
            return
        self.send_response(204)
        self.end_headers()

    def do_HEAD(self):
        path = urlparse(self.path).path
        if path in ("/api/health", "/api/audit", "/api/config", "/api/report"):
            self.do_GET()
        else:
            super().do_HEAD()

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/api/health":
            self.handle_health()
        elif path == "/api/audit":
            self.handle_audit()
        elif path == "/api/config":
            self.handle_get_config()
        elif path == "/api/report":
            self.handle_get_report()
        else:
            super().do_GET()

    def _drain_small_body(self, limit=65536):
        raw_len = self.headers.get("Content-Length")
        if not raw_len:
            return
        try:
            length = int(raw_len)
        except ValueError:
            return
        if 0 < length <= limit:
            self.rfile.read(length)

    def do_POST(self):
        if not self._validate_request_origin():
            self._drain_small_body()
            self._send_json(403, {"error": "Cross-origin POST rejected"})
            return

        path = urlparse(self.path).path
        if path == "/api/config":
            self.handle_post_config()
        elif path == "/api/extract-fields":
            self.handle_extract_fields()
        elif path == "/api/synthesize-xpath":
            self.handle_synthesize_xpath()
        elif path == "/api/classify-keyword":
            self.handle_classify_keyword()
        else:
            self._send_json(404, {"error": "Not found"})

    def handle_health(self):
        payload = {
            "status": "ONLINE",
            "engine": ENGINE_BANNER,
            "version": ENGINE_VERSION,
            "target": CURRENT_CONFIG.get("appTarget", "AUTO"),
            "timestamp": datetime.now().isoformat(),
            "server_port": PORT,
        }
        self._send_json(200, payload)

    def handle_audit(self):
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()

        audit_data = None
        for p in AUDIT_CANDIDATES:
            if p.exists() and p.is_file():
                try:
                    with open(p, "r", encoding="utf-8") as f:
                        content = f.read().strip()
                        if not content:
                            audit_data = []
                            break
                        try:
                            parsed = json.loads(content)
                            if isinstance(parsed, list):
                                audit_data = parsed
                            else:
                                audit_data = [parsed]
                            break
                        except json.JSONDecodeError:
                            # Handle JSON Lines format (.jsonl)
                            lines_data = []
                            for line in content.splitlines():
                                line_str = line.strip()
                                if line_str:
                                    try:
                                        lines_data.append(json.loads(line_str))
                                    except Exception:
                                        pass
                            audit_data = lines_data
                            break
                except Exception:
                    pass

        if audit_data is None:
            audit_data = []

        self.wfile.write(json.dumps(audit_data).encode("utf-8"))

    def handle_get_config(self):
        self._send_json(200, CURRENT_CONFIG)

    def handle_post_config(self):
        try:
            data = self._read_json_body(allow_empty=False)
            validated = _validate_config_patch(data)
            CURRENT_CONFIG.update(validated)
            save_persisted_config(CURRENT_CONFIG)
            self._send_json(200, {"success": True, "message": "Config saved", "config": CURRENT_CONFIG})
        except _PayloadTooLargeError as exc:
            self._send_json(413, {"error": str(exc)})
        except _BadRequestError as exc:
            self._send_json(400, {"error": str(exc)})
        except Exception as exc:
            self.log_error("handle_post_config failed: %r", exc)
            self._send_json(500, {"error": "Internal server error"})

    def handle_get_report(self):
        if REPORT_FILE.exists():
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            with open(REPORT_FILE, "rb") as f:
                self.wfile.write(f.read())
        else:
            self.send_response(404)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.end_headers()
            self.wfile.write(b"Report not yet generated. Run dashboard/generate_report.py first.")

    def handle_extract_fields(self):
        try:
            body = self._read_json_body(allow_empty=False)
            xml_text = body.get("xml", "")
            pairs = extract_label_value_pairs(xml_text)
            self._send_json(200, {"success": True, "count": len(pairs), "pairs": pairs})
        except _PayloadTooLargeError as exc:
            self._send_json(413, {"error": str(exc)})
        except _BadRequestError as exc:
            self._send_json(400, {"error": str(exc)})
        except ParseError:
            self._send_json(400, {"error": "Malformed XML payload"})
        except Exception as exc:
            self.log_error("handle_extract_fields failed: %r", exc)
            self._send_json(500, {"error": "Internal server error"})

    def handle_synthesize_xpath(self):
        try:
            body = self._read_json_body(allow_empty=True)
            ranked = synthesize_ranked_xpaths(body)
            self._send_json(200, {
                "success": True,
                "engine": f"Locara Katalon 11.7.0+ Object XPaths Synthesizer ({ENGINE_VERSION})",
                "rankedXPaths": ranked,
            })
        except _PayloadTooLargeError as exc:
            self._send_json(413, {"error": str(exc)})
        except _BadRequestError as exc:
            self._send_json(400, {"error": str(exc)})
        except Exception as exc:
            self.log_error("handle_synthesize_xpath failed: %r", exc)
            self._send_json(500, {"error": "Internal server error"})

    def handle_classify_keyword(self):
        try:
            body = self._read_json_body(allow_empty=True)
            raw_keyword = body.get("keyword")
            tier = classify_keyword_tier(raw_keyword)
            self._send_json(200, {
                "success": True,
                "keyword": raw_keyword,
                "tier": tier,
                "note": "Aligns with Katalon 11.2 - 11.7+ Element State Specification",
            })
        except _PayloadTooLargeError as exc:
            self._send_json(413, {"error": str(exc)})
        except _BadRequestError as exc:
            self._send_json(400, {"error": str(exc)})
        except Exception as exc:
            self.log_error("handle_classify_keyword failed: %r", exc)
            self._send_json(500, {"error": "Internal server error"})

def run():
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("127.0.0.1", PORT), LocaraHandler) as httpd:
        target_name = os.environ.get("LOCARA_TARGET_NAME", "Active Automation Suite")
        print(f"\n========================================================")
        print(f"🛡️  Locara Mission Control Dashboard · Powered by Qureon")
        print(f"🌐  URL: http://127.0.0.1:{PORT}")
        print(f"📁  Serving: {DASHBOARD_DIR}")
        print(f"⚡  Target Project: {target_name}")
        print(f"🔒  Security: Bound strictly to localhost (127.0.0.1)")
        print(f"========================================================\n")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nShutting down server cleanly...")
            httpd.shutdown()

if __name__ == "__main__":
    run()
