# 📊 Mission Control Dashboard

Locara includes a lightweight, real-time telemetry dashboard server built into `dashboard/` for monitoring healing performance, latency, and recovery audits.

---

## 🚀 Running the Dashboard Server

The dashboard server is written in standard Python 3 with zero external package dependencies:

```bash
cd /path/to/Locara/dashboard
python3 server.py --port 8088
```

Once started, open your browser at:
👉 **`http://localhost:8088`**

---

## 📈 Dashboard Features

1. **Offline-First Security**: Zero external CDN dependencies, strictly bound to `127.0.0.1` (`ThreadingHTTPServer`), loopback origin/host guards, and static-asset allowlist blocking backend Python source, tests, and dot-paths.
2. **Live Telemetry Ledger**: Inspects persisted `locara_audit.json` / `healing_audit.json` events with filtering, sorting, and diff views (never fabricates confidence, latency, or score vectors when absent).
3. **Spatial & Form Inspector**: Extracts 2D Left-to-Right and Top-to-Bottom key-value pairs via server or browser fallback while filtering hidden and zero-height nodes.
4. **Recon Lab & XPath Synthesizer**: Generates Katalon 11.7.0+ ranked multi-XPath collections and `.rs` XML previews (`MobileElementEntity` for native XML, `WebElementEntity` for HTML/WebView).
5. **Target Profile & Weight Tuning**: Reads and persists `locara.properties` overrides atomically (temp-file replace under lock) cleanly across `AUTO`, `UMUM`, `REVAMP`, and `KMP`.
6. **REST API Endpoints** (all `GET` endpoints also support RFC 9110 `HEAD` with empty body and matching `Content-Length`):
   - `GET /api/health` — Dashboard server health & active target status.
   - `GET /api/audit` — Audit ledger events JSON array.
   - `GET /api/config` / `POST /api/config` — Read and persist `locara.properties` tuning parameters.
   - `GET /api/report` — Serve generated standalone HTML UAT report (`Include/evidence/locara_report.html` with legacy `healing_report.html` fallback; returns HTTP `404` until `python3 dashboard/generate_report.py` is run).
   - `POST /api/extract-fields` — Extract 2D spatial label-value field pairs from XXE-checked XML.
   - `POST /api/synthesize-xpath` — Generate ranked XPath 1.0 expressions.
   - `POST /api/classify-keyword` — Classify Katalon keywords into `INTERACTION`, `CHECKPOINT`, or `CONDITIONAL_PROBE`.
