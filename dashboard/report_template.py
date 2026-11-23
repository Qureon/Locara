#!/usr/bin/env python3
"""HTML template and row rendering helpers for Locara standalone UAT reports."""

import html as html_lib
from pathlib import Path

REPORT_TEMPLATE = """<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Locara by Qureon — Official UAT Regression Report</title>
    <style>
        :root {{
            --bg-primary: #070913;
            --bg-card: #101626;
            --card-border: rgba(255, 255, 255, 0.08);
            --text-primary: #F8FAFC;
            --text-secondary: #94A3B8;
            --cyan: #00E5FF;
            --emerald: #10B981;
            --violet: #8B5CF6;
            --danger: #EF4444;
            --font-sans: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            --font-mono: "JetBrains Mono", Consolas, monospace;
        }}
        * {{ margin: 0; padding: 0; box-sizing: border-box; }}
        body {{
            background: var(--bg-primary);
            color: var(--text-primary);
            font-family: var(--font-sans);
            padding: 40px 20px;
            line-height: 1.6;
        }}
        .container {{
            max-width: 1100px;
            margin: 0 auto;
        }}
        .header {{
            background: var(--bg-card);
            border: 1px solid var(--card-border);
            border-radius: 16px;
            padding: 32px;
            margin-bottom: 24px;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }}
        .brand-badge {{
            display: inline-block;
            background: rgba(0, 229, 255, 0.15);
            color: var(--cyan);
            border: 1px solid rgba(0, 229, 255, 0.3);
            padding: 4px 12px;
            border-radius: 9999px;
            font-size: 11px;
            font-weight: 700;
            letter-spacing: 1px;
            margin-bottom: 8px;
        }}
        .title {{
            font-size: 24px;
            font-weight: 700;
            margin-bottom: 6px;
        }}
        .subtitle {{
            color: var(--text-secondary);
            font-size: 14px;
        }}
        .meta-right {{
            text-align: right;
            font-size: 13px;
            color: var(--text-secondary);
        }}
        .meta-right strong {{
            color: var(--text-primary);
        }}
        .kpi-grid {{
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: 16px;
            margin-bottom: 24px;
        }}
        .kpi-card {{
            background: var(--bg-card);
            border: 1px solid var(--card-border);
            border-radius: 12px;
            padding: 20px;
        }}
        .kpi-label {{
            font-size: 11px;
            color: var(--text-secondary);
            text-transform: uppercase;
            letter-spacing: 0.5px;
            margin-bottom: 8px;
        }}
        .kpi-value {{
            font-size: 28px;
            font-weight: 700;
            font-family: var(--font-mono);
        }}
        .text-cyan {{ color: var(--cyan); }}
        .text-emerald {{ color: var(--emerald); }}
        .text-violet {{ color: var(--violet); }}
        .text-danger {{ color: var(--danger); }}
        .card {{
            background: var(--bg-card);
            border: 1px solid var(--card-border);
            border-radius: 16px;
            padding: 24px;
            margin-bottom: 24px;
        }}
        .card-title {{
            font-size: 18px;
            margin-bottom: 16px;
            font-weight: 600;
            display: flex;
            align-items: center;
            gap: 8px;
        }}
        table {{
            width: 100%;
            border-collapse: collapse;
            font-size: 13px;
        }}
        th {{
            text-align: left;
            padding: 12px 14px;
            background: rgba(255, 255, 255, 0.03);
            color: var(--text-secondary);
            font-weight: 600;
            border-bottom: 1px solid var(--card-border);
        }}
        td {{
            padding: 12px 14px;
            border-bottom: 1px solid rgba(255, 255, 255, 0.04);
        }}
        tr:hover td {{
            background: rgba(255, 255, 255, 0.02);
        }}
        .badge {{
            display: inline-block;
            padding: 3px 8px;
            border-radius: 6px;
            font-size: 11px;
            font-weight: 700;
            font-family: var(--font-mono);
        }}
        .badge-success {{ background: rgba(16, 185, 129, 0.15); color: var(--emerald); border: 1px solid rgba(16, 185, 129, 0.3); }}
        .badge-scrolled {{ background: rgba(0, 229, 255, 0.15); color: var(--cyan); border: 1px solid rgba(0, 229, 255, 0.3); }}
        .badge-lockout {{ background: rgba(239, 68, 68, 0.15); color: var(--danger); border: 1px solid rgba(239, 68, 68, 0.3); }}
        code {{
            font-family: var(--font-mono);
            background: rgba(0, 0, 0, 0.3);
            padding: 2px 6px;
            border-radius: 4px;
            font-size: 12px;
            color: var(--cyan);
        }}
        .footer {{
            text-align: center;
            color: var(--text-secondary);
            font-size: 12px;
            margin-top: 40px;
            padding-top: 20px;
            border-top: 1px solid var(--card-border);
        }}
    </style>
</head>
<body>
    <div class="container">
        <!-- Header -->
        <header class="header">
            <div>
                <span class="brand-badge">QUREON QA · SDET PLATFORM</span>
                <h1 class="title">Locara by Qureon UAT Report</h1>
                <p class="subtitle">Autonomous Locator Self-Healing & Object Repository Verification</p>
            </div>
            <div class="meta-right">
                <p><strong>Target App:</strong> Mobile Application (Android/iOS) &amp; Web</p>
                <p><strong>Generated:</strong> {generated_time}</p>
                <p><strong>Engine:</strong> Locara Core {engine_version} (Katalon 11.7.0+ Ready)</p>
            </div>
        </header>

        <!-- KPI Metrics -->
        <section class="kpi-grid">
            <div class="kpi-card">
                <div class="kpi-label">Total Heal Interceptions</div>
                <div class="kpi-value text-cyan">{total_heals}</div>
            </div>
            <div class="kpi-card">
                <div class="kpi-label">In-Flight Recovery Rate</div>
                <div class="kpi-value text-emerald">{success_rate}%</div>
            </div>
            <div class="kpi-card">
                <div class="kpi-label">Auto-Scrolled Viewports</div>
                <div class="kpi-value text-violet">{scrolled_count}</div>
            </div>
            <div class="kpi-card">
                <div class="kpi-label">Safety Lockouts (Danger Guard)</div>
                <div class="kpi-value text-danger">{lockouts_count}</div>
            </div>
        </section>

        <!-- Event Audit Table -->
        <section class="card">
            <h2 class="card-title">📋 Executed Locator Healing Trace Log</h2>
            <table>
                <thead>
                    <tr>
                        <th>Timestamp</th>
                        <th>Screen Context</th>
                        <th>Target Hint</th>
                        <th>Tier</th>
                        <th>Status</th>
                        <th>Confidence</th>
                        <th>Healed XPath</th>
                        <th>Generated .rs File</th>
                    </tr>
                </thead>
                <tbody>
                    {table_rows}
                </tbody>
            </table>
        </section>

        <!-- Footer -->
        <footer class="footer">
            <p>🛡️ Locara Autonomous Testing Platform · Powered by Qureon</p>
            <p>Architect: Qureon Engineering · Confirmed 100% In-Flight Zero-Interruption Standard</p>
        </footer>
    </div>
</body>
</html>
"""

TIER_STYLE_MAP = {
    "INTERACTION": "background:rgba(0,229,255,0.15); color:var(--cyan);",
    "CHECKPOINT": "background:rgba(16,185,129,0.15); color:var(--emerald);",
}
DEFAULT_TIER_STYLE = "background:rgba(139,92,246,0.15); color:var(--violet);"
STATUS_BADGE_MAP = {
    "SUCCESS": "badge-success",
    "HEALED": "badge-success",
    "SCROLLED": "badge-scrolled",
    "LOCKOUT": "badge-lockout",
}


def render_table_row(event, confidence_pct_fn):
    """Render one HTML table row from a Locara audit event dictionary with HTML escaping."""
    status = str(event.get("status", "UNKNOWN")).upper()
    badge_cls = STATUS_BADGE_MAP.get(status, "badge-lockout")
    tier = str(event.get("intentionTier", "INTERACTION"))
    tier_style = TIER_STYLE_MAP.get(tier, DEFAULT_TIER_STYLE)
    conf = confidence_pct_fn(event.get("confidence", 0.0))
    xpath = html_lib.escape(str(event.get("healedLocator") or event.get("healedXPath") or "-"))
    rs_raw = event.get("generatedRs") or event.get("rsFile") or "-"
    rs = html_lib.escape(Path(str(rs_raw)).name if rs_raw != "-" else "-")
    ts = html_lib.escape(str(event.get("timestamp", "-")))
    screen = html_lib.escape(str(event.get("screen", "-")))
    hint = html_lib.escape(str(event.get("targetHint") or event.get("hint") or "-"))

    return f"""
            <tr>
                <td style="font-family: var(--font-mono); color: var(--text-secondary);">{ts}</td>
                <td><strong>{screen}</strong></td>
                <td>{hint}</td>
                <td><span class="badge" style="{tier_style} font-size:10px;">{html_lib.escape(tier)}</span></td>
                <td><span class="badge {badge_cls}">{html_lib.escape(status)}</span></td>
                <td><strong>{conf}</strong></td>
                <td><code>{xpath}</code></td>
                <td style="color: var(--cyan);">{rs}</td>
            </tr>
        """
