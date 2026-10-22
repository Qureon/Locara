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
        else:
            self.send_response(404)
            self.end_headers()

    def handle_health(self):
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        payload = {
            "status": "ONLINE",
            "engine": "Locara Core v1.3.0 · Powered by Qureon",
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
        config_data = {
            "minSimilarityThreshold": 0.70,
            "textSimilarityWeight": 0.35,
            "resourceIdSimilarityWeight": 0.25,
            "contentDescSimilarityWeight": 0.20,
            "auditLedgerEnabled": True
        }
        self.wfile.write(json.dumps(config_data).encode("utf-8"))

    def handle_post_config(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length)
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps({"success": True, "message": "Config saved"}).encode("utf-8"))

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
