#!/usr/bin/env python3
"""
==============================================================================
🛡️ LOCARA BY QUREON — STANDALONE EXECUTIVE UAT REPORT GENERATOR
Qureon Autonomous Quality Assurance · Qureon Engineering
==============================================================================
Generates an offline-ready, single-file HTML audit report suitable for
attaching to Jira tickets, Jenkins artifacts, or UAT sign-off documentation.
"""

import json
import math
import os
import sys
from datetime import datetime
from pathlib import Path

from report_template import REPORT_TEMPLATE, render_table_row

ENGINE_VERSION = "v1.16.0"


def _safe_confidence_pct(raw_value):
    try:
        val = float(raw_value if raw_value is not None else 0.0)
        if not math.isfinite(val):
            return "0.0%"
        return f"{val * 100:.1f}%"
    except (TypeError, ValueError):
        return "0.0%"


def _resolve_audit_candidates(base_dir):
    candidates = []
    env_log = os.environ.get("LOCARA_AUDIT_LOG")
    if env_log:
        candidates.append(Path(env_log).expanduser())

    roots = [base_dir, Path.cwd()]
    env_dirs = os.environ.get("LOCARA_AUDIT_DIRS", "")
    for raw_dir in env_dirs.split(os.pathsep):
        cleaned = raw_dir.strip()
        if cleaned:
            roots.append(Path(cleaned).expanduser())

    for root in roots:
        candidates.append(root / "Include" / "evidence" / "locara_audit.json")
        candidates.append(root / "Include" / "evidence" / "healing_audit.json")
    return candidates


SUCCESS_STATUSES = frozenset({"SUCCESS", "HEALED", "SCROLLED"})


def _load_audit_events(target_file):
    if not target_file or not target_file.exists():
        return []
    try:
        content = target_file.read_text(encoding="utf-8").strip()
        if not content:
            return []
        if content.startswith("["):
            parsed = json.loads(content)
            return parsed if isinstance(parsed, list) else []
        events = []
        for idx, line in enumerate(content.splitlines(), start=1):
            stripped = line.strip()
            if not stripped:
                continue
            try:
                item = json.loads(stripped)
                if isinstance(item, dict):
                    events.append(item)
            except json.JSONDecodeError as exc:
                print(f"Warning: skipping malformed JSONL line {idx} in {target_file}: {exc}")
        return events
    except Exception as exc:
        print(f"Error reading audit file {target_file}: {exc}")
        return []


def generate_report(output_path=None, audit_path=None):
    base_dir = Path(__file__).resolve().parent.parent
    candidates = ([Path(audit_path)] if audit_path else []) + _resolve_audit_candidates(base_dir)
    target_file = next((f for f in candidates if f.exists() and f.is_file()), None)
    events = _load_audit_events(target_file)

    total = len(events)
    success = sum(1 for e in events if str(e.get("status", "")).upper() in SUCCESS_STATUSES)
    scrolled = sum(1 for e in events if str(e.get("status", "")).upper() == "SCROLLED")
    lockouts = sum(1 for e in events if str(e.get("status", "")).upper() == "LOCKOUT")
    rate = f"{(success / total * 100):.1f}" if total > 0 else "0.0"

    if events:
        rows = [render_table_row(e, _safe_confidence_pct) for e in events]
    else:
        rows = [
            '<tr><td colspan="8" style="text-align:center; padding:24px; color:var(--text-secondary);">'
            'No audit telemetry events recorded. Run Locara tests with audit ledger enabled first.'
            '</td></tr>'
        ]
    html = REPORT_TEMPLATE.format(
        generated_time=datetime.now().strftime("%d %B %Y, %H:%M:%S WIB"),
        engine_version=ENGINE_VERSION,
        total_heals=total,
        success_rate=rate,
        scrolled_count=scrolled,
        lockouts_count=lockouts,
        table_rows="".join(rows),
    )

    if not output_path:
        out_dir = base_dir / "Include" / "evidence"
        out_dir.mkdir(parents=True, exist_ok=True)
        output_path = out_dir / "locara_report.html"
        (out_dir / "healing_report.html").write_text(html, encoding="utf-8")
    else:
        output_path = Path(output_path)

    output_path.write_text(html, encoding="utf-8")
    print(f"✅ Locara Audit Report successfully written to: {output_path}")
    return output_path


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else None
    generate_report(out)
