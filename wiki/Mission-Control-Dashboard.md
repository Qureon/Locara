# 📊 Mission Control Dashboard

Locara includes a lightweight, real-time telemetry dashboard server built into `dashboard/` for monitoring healing performance, latency, and recovery audits.

---

## 🚀 Running the Dashboard Server

The dashboard server is written in standard Python 3 with zero external package dependencies:

```bash
cd /path/to/Locara/dashboard
python3 server.py --port 8085
```

Once started, open your browser at:
👉 **`http://localhost:8085`**

---

## 📈 Dashboard Features

1. **Live Recovery Stream**: Real-time websocket/SSE stream of broken selectors and the winning heuristics that healed them.
2. **Success Rate Analytics**: Overall percentage of prevented test failures across your test execution run.
3. **Latency Gauge**: Monitors execution overhead in milliseconds to ensure sub-15ms performance.
4. **Target Profile Switcher**: Toggle between `AUTO`, `UMUM`, `REVAMP`, and `KMP` directly from the UI.
5. **REST API Endpoints**:
   - `GET /api/health` — Dashboard server health status.
   - `GET /api/metrics` — Aggregate healing statistics (JSON).
   - `POST /api/heal-event` — Ingest telemetry events from running test suites.
