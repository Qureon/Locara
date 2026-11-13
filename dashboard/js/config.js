/**
 * ==============================================================================
 * LOCARA MISSION CONTROL — CONFIGURATION & CONSTANTS
 * ==============================================================================
 */

export const APP_CONFIG = {
    name: "Locara Mission Control",
    byline: "Powered by Qureon",
    version: "1.15.0",
    semver: "v1.15.0",
    organization: "Qureon Autonomous QA Platform",
    targetProject: "Mobile & Web Automation Suite",
    defaultPageSize: 6,
    defaultThresholds: {
        minSimilarity: 0.70,
        textWeight: 0.35,
        idWeight: 0.25,
        descWeight: 0.20,
        classWeight: 0.10,
        proximityWeight: 0.10
    },
    dangerKeywords: [
        "batal", "cancel", "hapus", "delete", "blokir", "block", "tutup", "keluar"
    ],
    slaLatencyMs: 50
};
