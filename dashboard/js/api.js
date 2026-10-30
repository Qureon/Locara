/**
 * ==============================================================================
 * LOCARA MISSION CONTROL — API CLIENT MODULE (POWERED BY QUREON)
 * ==============================================================================
 */

export async function fetchAuditEvents() {
    try {
        const res = await fetch("/api/audit");
        if (!res.ok) throw new Error(`HTTP error ${res.status}`);
        const data = await res.json();
        return Array.isArray(data) ? data : [];
    } catch (err) {
        console.warn("Using baseline mock telemetry (backend offline or empty):", err.message);
        return null;
    }
}

export async function fetchEngineHealth() {
    try {
        const res = await fetch("/api/health");
        if (!res.ok) throw new Error(`HTTP error ${res.status}`);
        return await res.json();
    } catch (err) {
        return { status: "STANDALONE", engine: "Locara Core v1.5.0", target: "Qureon_Autonomous_QA" };
    }
}

export async function fetchEngineConfig() {
    try {
        const res = await fetch("/api/config");
        if (!res.ok) throw new Error(`HTTP error ${res.status}`);
        return await res.json();
    } catch (err) {
        return null;
    }
}

export async function saveEngineConfig(config) {
    try {
        const res = await fetch("/api/config", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(config)
        });
        return res.ok;
    } catch (err) {
        return false;
    }
}

export async function extractFields(xml) {
    try {
        const res = await fetch("/api/extract-fields", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ xml })
        });
        if (!res.ok) throw new Error(`HTTP error ${res.status}`);
        return await res.json();
    } catch (err) {
        console.error("Field extraction failed:", err);
        return { success: false, error: err.message, pairs: [] };
    }
}
