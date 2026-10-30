/**
 * ==============================================================================
 * ZERO-HEALING DASHBOARD — ENGINE SETTINGS MODULE
 * Configuration tuning for heuristic weights, target presets (UMUM, REVAMP, KMP),
 * safety thresholds, and audit rules.
 * ==============================================================================
 */

import { APP_CONFIG } from "../config.js";
import { saveEngineConfig } from "../api.js";

const TARGET_PRESETS = {
    "AUTO": {
        name: "AUTO (Adaptif)",
        pillText: "TARGET: AUTO (Umum / Revamp / KMP)",
        desc: "Pendeteksian runtime otomatis berdasarkan tanda tangan hierarki XML aplikasi aktif.",
        minSim: 0.70,
        textWeight: 0.35,
        idWeight: 0.25,
        descWeight: 0.20
    },
    "UMUM": {
        name: "UMUM (Universal App-Agnostic)",
        pillText: "TARGET: UMUM (Universal App-Agnostic)",
        desc: "Mode universal untuk aplikasi apa pun (fintech, e-commerce, native) berbasis irisan spasial 2D & multi-vektor seimbang.",
        minSim: 0.70,
        textWeight: 0.35,
        idWeight: 0.25,
        descWeight: 0.20
    },
    "REVAMP": {
        name: "REVAMP (React Native M-Smile)",
        pillText: "TARGET: REVAMP (React Native M-Smile)",
        desc: "Dioptimalkan untuk pohon ViewGroup bersarang dalam, resolusi parent-container click, dan prioritas teks/deskripsi.",
        minSim: 0.70,
        textWeight: 0.55,
        idWeight: 0.08,
        descWeight: 0.07
    },
    "KMP": {
        name: "KMP (Kotlin / Compose Multiplatform)",
        pillText: "TARGET: KMP (Compose Multiplatform)",
        desc: "Dioptimalkan untuk taksonomi testID <screen>_<element>, dynamic tokens (<key>), dan resolusi scrambled PIN pad.",
        minSim: 0.70,
        textWeight: 0.30,
        idWeight: 0.45,
        descWeight: 0.15
    }
};

let currentTargetKey = "AUTO";

export function initSettingsModule() {
    initSliders();
    initProfileSwitcher();
    initSaveButton();
}

function initProfileSwitcher() {
    const profileBtns = document.querySelectorAll(".profile-btn");
    const pill = document.getElementById("target-profile-pill");
    const desc = document.getElementById("target-preset-desc");

    profileBtns.forEach(btn => {
        btn.addEventListener("click", () => {
            const key = btn.dataset.target || "AUTO";
            currentTargetKey = key;

            profileBtns.forEach(b => b.classList.remove("active"));
            btn.classList.add("active");

            const preset = TARGET_PRESETS[key] || TARGET_PRESETS["AUTO"];

            if (pill) {
                pill.innerHTML = `<span style="color: var(--cyan);">●</span> ${preset.pillText}`;
            }

            if (desc) {
                desc.textContent = preset.desc;
            }

            // Apply preset slider values
            setSliderValue("slider-min-sim", "val-min-sim", preset.minSim, "%");
            setSliderValue("slider-text-weight", "val-text-weight", preset.textWeight, "%");
            setSliderValue("slider-id-weight", "val-id-weight", preset.idWeight, "%");
            setSliderValue("slider-desc-weight", "val-desc-weight", preset.descWeight, "%");
        });
    });
}

function setSliderValue(sliderId, labelId, val, suffix) {
    const slider = document.getElementById(sliderId);
    const label = document.getElementById(labelId);
    if (slider) slider.value = val;
    if (label) label.textContent = `${(val * 100).toFixed(0)}${suffix}`;
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
                appTarget: currentTargetKey,
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
