#!/usr/bin/env python3
"""
==============================================================================
🛡️ LOCARA MISSION CONTROL — MODULAR TELEMETRY SERVER (POWERED BY QUREON)
Qureon Autonomous QA Engineering · Qureon Engineering
==============================================================================
"""

import sys
import os
import json
import http.server
import socketserver
from pathlib import Path
from datetime import datetime

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
CURRENT_CONFIG = {
    "appTarget": "AUTO",
    "minSimilarityThreshold": 0.70,
    "textSimilarityWeight": 0.35,
    "resourceIdSimilarityWeight": 0.25,
    "contentDescSimilarityWeight": 0.20,
    "auditLedgerEnabled": True
}
DASHBOARD_DIR = Path(__file__).resolve().parent

# Universal Audit Path Resolution (Environment Variable, Project Root, or Sibling Repositories)
AUDIT_CANDIDATES = [
    Path(os.environ.get("LOCARA_AUDIT_LOG")) if os.environ.get("LOCARA_AUDIT_LOG") else None,
    DASHBOARD_DIR.parent / "Include" / "evidence" / "locara_audit.json",
    DASHBOARD_DIR.parent / "Include" / "evidence" / "healing_audit.json",
]
if DASHBOARD_DIR.parent.parent.exists():
    for sibling in DASHBOARD_DIR.parent.parent.iterdir():
        if sibling.is_dir() and sibling != DASHBOARD_DIR.parent:
            cand = sibling / "Include" / "evidence" / "locara_audit.json"
            if cand.exists():
                AUDIT_CANDIDATES.append(cand)
AUDIT_CANDIDATES = [p for p in AUDIT_CANDIDATES if p is not None]

REPORT_FILE = DASHBOARD_DIR.parent / "Include" / "evidence" / "healing_report.html"
CONFIG_FILE = DASHBOARD_DIR.parent / "locara.properties"

class LocaraHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(DASHBOARD_DIR), **kwargs)

    def end_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
        super().end_headers()

    def do_OPTIONS(self):
        self.send_response(200)
        self.end_headers()

    def do_HEAD(self):
        if self.path in ("/api/health", "/api/audit", "/api/config", "/api/report"):
            self.do_GET()
        else:
            super().do_HEAD()

    def do_GET(self):
        if self.path == "/api/health":
            self.handle_health()
        elif self.path == "/api/audit":
            self.handle_audit()
        elif self.path == "/api/config":
            self.handle_get_config()
        elif self.path == "/api/report":
            self.handle_get_report()
        else:
            super().do_GET()

    def do_POST(self):
        if self.path == "/api/config":
            self.handle_post_config()
        elif self.path == "/api/extract-fields":
            self.handle_extract_fields()
        elif self.path == "/api/synthesize-xpath":
            self.handle_synthesize_xpath()
        elif self.path == "/api/classify-keyword":
            self.handle_classify_keyword()
        else:
            self.send_response(404)
            self.end_headers()

    def handle_health(self):
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        payload = {
            "status": "ONLINE",
            "engine": "Locara Core v1.9.2 · Powered by Qureon (Katalon 11.7.0+ Ready)",
            "target": "Qureon Autonomous QA Platform",
            "timestamp": datetime.now().isoformat(),
            "server_port": PORT
        }
        self.wfile.write(json.dumps(payload).encode("utf-8"))

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
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(CURRENT_CONFIG).encode("utf-8"))

    def handle_post_config(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length)
        try:
            data = json.loads(body.decode("utf-8"))
            CURRENT_CONFIG.update(data)
        except Exception:
            pass
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps({"success": True, "message": "Config saved", "config": CURRENT_CONFIG}).encode("utf-8"))

    def handle_get_report(self):
        if REPORT_FILE.exists():
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            with open(REPORT_FILE, "rb") as f:
                self.wfile.write(f.read())
        else:
            self.send_response(404)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"Report not yet generated. Run dashboard/generate_report.py first.")

    def handle_extract_fields(self):
        content_length = int(self.headers.get("Content-Length", 0))
        post_data = self.rfile.read(content_length).decode("utf-8")
        try:
            body = json.loads(post_data)
            xml_text = body.get("xml", "")
            if not xml_text:
                self.send_response(400)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({"error": "Empty XML content"}).encode("utf-8"))
                return

            import xml.etree.ElementTree as ET
            import re

            root = ET.fromstring(xml_text)
            nodes = []
            bounds_pattern = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")

            for el in root.iter():
                text = el.get("text", "") or ""
                desc = el.get("content-desc", "") or ""
                b_str = el.get("bounds", "")
                m = bounds_pattern.match(b_str)
                if m:
                    x1, y1, x2, y2 = map(int, m.groups())
                    nodes.append({
                        "tag": el.tag,
                        "text": text.strip(),
                        "desc": desc.strip(),
                        "x1": x1, "y1": y1, "x2": x2, "y2": y2,
                        "w": x2 - x1, "h": y2 - y1,
                        "clickable": el.get("clickable", "false") == "true"
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
                            "bounds": f"[{lbl['x1']},{lbl['y1']}][{best_val['x2']},{best_val['y2']}]"
                        })

            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"success": True, "count": len(pairs), "pairs": pairs}).encode("utf-8"))
        except Exception as e:
            self.send_response(500)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"error": str(e)}).encode("utf-8"))

    def handle_synthesize_xpath(self):
        length = int(self.headers.get("Content-Length", 0))
        post_data = self.rfile.read(length).decode("utf-8")
        try:
            body = json.loads(post_data) if post_data else {}
            tag = body.get("tag", "*")
            text = body.get("text", "")
            res_id = body.get("resourceId", body.get("id", ""))
            desc = body.get("contentDesc", body.get("desc", ""))
            cls = body.get("className", body.get("class", ""))

            def _xp_lit(val):
                if "'" not in val:
                    return f"'{val}'"
                if '"' not in val:
                    return f'"{val}"'
                parts = val.split("'")
                return "concat(" + ", \"'\", ".join(f"'{p}'" for p in parts) + ")"

            ranked = {}
            if res_id and text:
                ranked["xpath:attributes"] = f"//{tag}[@resource-id={_xp_lit(res_id)} and @text={_xp_lit(text)}]"
            elif res_id:
                ranked["xpath:attributes"] = f"//{tag}[@resource-id={_xp_lit(res_id)}]"
            elif desc:
                ranked["xpath:attributes"] = f"//{tag}[@content-desc={_xp_lit(desc)}]"
            elif text:
                ranked["xpath:attributes"] = f"//{tag}[@text={_xp_lit(text)}]"
            else:
                ranked["xpath:attributes"] = f"//{tag}"

            if res_id:
                ranked["xpath:idRelative"] = f"//*[@id={_xp_lit(res_id)}]"
            if text:
                ranked["xpath:custom"] = f"//{tag}[text()={_xp_lit(text)} or .={_xp_lit(text)}]"
            if cls:
                ranked["xpath:position"] = f"//*[@class={_xp_lit(cls)}]"

            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({
                "success": True,
                "engine": "Locara Katalon 11.6+ Object XPaths Synthesizer",
                "rankedXPaths": ranked
            }).encode("utf-8"))
        except Exception as e:
            self.send_response(500)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"error": str(e)}).encode("utf-8"))

    def handle_classify_keyword(self):
        length = int(self.headers.get("Content-Length", 0))
        post_data = self.rfile.read(length).decode("utf-8")
        try:
            body = json.loads(post_data) if post_data else {}
            kw = (body.get("keyword") or "").strip().lower()
            if kw.startswith("webui.") or kw.startswith("mobile.") or kw.startswith("windows."):
                kw = kw.split(".", 1)[1]

            tier = "INTERACTION"
            if (kw.startswith("iselement") or kw.startswith("is_element") or
                    "findfirst" in kw or "probe" in kw or "optional" in kw):
                tier = "CONDITIONAL_PROBE"
            elif kw.startswith("verify") or kw.startswith("assert") or "checkpoint" in kw:
                tier = "CHECKPOINT"

            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({
                "success": True,
                "keyword": body.get("keyword"),
                "tier": tier,
                "note": "Aligns with Katalon 11.2 - 11.7+ Element State Specification"
            }).encode("utf-8"))
        except Exception as e:
            self.send_response(500)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"error": str(e)}).encode("utf-8"))

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
