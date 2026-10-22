/**
 * ==============================================================================
 * ZERO-HEALING DASHBOARD — ENGINE SETTINGS MODULE
 * Configuration tuning for heuristic weights, safety thresholds, and audit rules.
 * ==============================================================================
 */

import { APP_CONFIG } from "../config.js";
import { saveEngineConfig } from "../api.js";

export function initSettingsModule() {
    initSliders();
    initSaveButton();
}

function initSliders() {
    const sliders = [
        { id: "slider-min-sim", valId: "val-min-sim", mult: 100, suffix: "%" },
        { id: "slider-text-weight", valId: "val-text-weight", mult: 100, suffix: "%" },
        { id: "slider-id-weight", valId: "val-id-weight", mult: 100, suffix: "%" },
        { id: "slider-desc-weight", valId: "val-desc-weight", mult: 100, suffix: "%" }
    ];

    sliders.forEach(s => {
        const input = document.getElementById(s.id);
        const label = document.getElementById(s.valId);
        if (input && label) {
            input.addEventListener("input", (e) => {
                const val = parseFloat(e.target.value);
                label.textContent = `${(val * s.mult).toFixed(0)}${s.suffix}`;
            });
        }
    });
}

function initSaveButton() {
    const btnSave = document.getElementById("btn-save-settings");
    const saveNotice = document.getElementById("save-settings-notice");

    if (btnSave) {
        btnSave.addEventListener("click", async () => {
            const minSim = parseFloat(document.getElementById("slider-min-sim")?.value || 0.70);
            const textWeight = parseFloat(document.getElementById("slider-text-weight")?.value || 0.35);
            const idWeight = parseFloat(document.getElementById("slider-id-weight")?.value || 0.25);
            const descWeight = parseFloat(document.getElementById("slider-desc-weight")?.value || 0.20);

            const payload = {
                minSimilarityThreshold: minSim,
                textSimilarityWeight: textWeight,
                resourceIdSimilarityWeight: idWeight,
                contentDescSimilarityWeight: descWeight,
                updatedAt: new Date().toISOString()
            };

            btnSave.disabled = true;
            btnSave.textContent = "Menyimpan...";

            const success = await saveEngineConfig(payload);

            btnSave.disabled = false;
            btnSave.textContent = "Simpan Konfigurasi";

            if (saveNotice) {
                saveNotice.textContent = success ? "Konfigurasi berhasil disimpan!" : "Tersimpan di memori sesi aktif.";
                saveNotice.classList.remove("hidden");
                setTimeout(() => {
                    saveNotice.classList.add("hidden");
                }, 3000);
            }
        });
    }
}
