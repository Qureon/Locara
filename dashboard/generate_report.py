#!/usr/bin/env python3
"""
==============================================================================
🛡️ LOCARA BY QUREON — STANDALONE EXECUTIVE UAT REPORT GENERATOR
Qureon Autonomous Quality Assurance · Qureon Engineering
==============================================================================
Generates an offline-ready, single-file HTML audit report suitable for
attaching to Jira tickets, Jenkins artifacts, or UAT sign-off documentation.
"""

import math
import sys
from datetime import datetime
from pathlib import Path

try:
    from .config_store import ENGINE_VERSION, build_audit_candidates
    from .http_utils import load_audit_events
    from .report_template import REPORT_TEMPLATE, render_table_row
except ImportError:
    from config_store import ENGINE_VERSION, build_audit_candidates
    from http_utils import load_audit_events
    from report_template import REPORT_TEMPLATE, render_table_row

SUCCESS_STATUSES = frozenset({"SUCCESS", "HEALED", "SCROLLED"})


def _safe_confidence_pct(raw_value):
    try:
        val = float(raw_value if raw_value is not None else 0.0)
        if not math.isfinite(val):
            return "0.0%"
        return f"{val * 100:.1f}%"
    except (TypeError, ValueError):
        return "0.0%"


def generate_report(output_path=None, audit_path=None):
    dashboard_dir = Path(__file__).resolve().parent
    base_dir = dashboard_dir.parent
    candidates = ([Path(audit_path)] if audit_path else []) + build_audit_candidates(dashboard_dir)
    events = [e for e in load_audit_events(candidates) if isinstance(e, dict)]

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
    generated_ts = datetime.now().astimezone().strftime("%d %B %Y, %H:%M:%S %Z").strip()
    html = REPORT_TEMPLATE.format(
        generated_time=generated_ts,
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
        output_path.parent.mkdir(parents=True, exist_ok=True)

    output_path.write_text(html, encoding="utf-8")
    print(f"✅ Locara Audit Report successfully written to: {output_path}")
    return output_path


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else None
    generate_report(out)
