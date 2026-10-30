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

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8088
CURRENT_CONFIG = {
    "appTarget": "AUTO",
    "minSimilarityThreshold": 0.70,
    "textSimilarityWeight": 0.35,
    "resourceIdSimilarityWeight": 0.25,
    "contentDescSimilarityWeight": 0.20,
    "auditLedgerEnabled": True
}
DASHBOARD_DIR = Path(__file__).resolve().parent

# File paths
AUDIT_CANDIDATES = [
    DASHBOARD_DIR.parent / "Include" / "evidence" / "locara_audit.json",
    DASHBOARD_DIR.parent / "Include" / "evidence" / "healing_audit.json",
    DASHBOARD_DIR.parent.parent / "Revamp_M-Smile" / "Include" / "evidence" / "locara_audit.json",
    DASHBOARD_DIR.parent.parent / "Revamp_M-Smile" / "Include" / "evidence" / "healing_audit.json"
]
REPORT_FILE = DASHBOARD_DIR.parent / "Include" / "evidence" / "healing_report.html"
CONFIG_FILE = DASHBOARD_DIR.parent / "locara.properties" if (DASHBOARD_DIR.parent / "locara.properties").exists() else DASHBOARD_DIR.parent / "zero-healing.properties"

class ZeroHealingHandler(http.server.SimpleHTTPRequestHandler):
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
        else:
            self.send_response(404)
            self.end_headers()

    def handle_health(self):
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        payload = {
            "status": "ONLINE",
            "engine": "Locara Core v1.5.0 · Powered by Qureon",
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
            labels = [n for n in nodes if n["text"] and n["w"] > 0]
            claimed = set()

            for lbl in labels:
                best_val = None
                best_score = 0.0
                for cand in nodes:
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

def run():
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("127.0.0.1", PORT), ZeroHealingHandler) as httpd:
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
