/**
 * ==============================================================================
 * LOCARA MISSION CONTROL — ENGINE SETTINGS MODULE
 * Configuration tuning for heuristic weights, target presets (UMUM, REVAMP, KMP),
 * safety thresholds, and audit rules.
 * ==============================================================================
 */

import { fetchEngineConfig, saveEngineConfig } from "../api.js";
import { escapeHtml } from "../utils.js";

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
        name: "REVAMP (React Native Hybrid)",
        pillText: "TARGET: REVAMP (React Native Hybrid)",
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
    hydrateEngineConfig();
}

async function hydrateEngineConfig() {
    const cfg = await fetchEngineConfig();
    if (!cfg || typeof cfg !== "object") return;
    const key = (typeof cfg.appTarget === "string" && TARGET_PRESETS[cfg.appTarget.toUpperCase()])
        ? cfg.appTarget.toUpperCase()
        : "AUTO";
    applyTargetSelection(key, {
        minSim: typeof cfg.minSimilarityThreshold === "number" ? cfg.minSimilarityThreshold : undefined,
        textWeight: typeof cfg.textSimilarityWeight === "number" ? cfg.textSimilarityWeight : undefined,
        idWeight: typeof cfg.resourceIdSimilarityWeight === "number" ? cfg.resourceIdSimilarityWeight : undefined,
        descWeight: typeof cfg.contentDescSimilarityWeight === "number" ? cfg.contentDescSimilarityWeight : undefined
    });
}

function applyTargetSelection(key, overrides = {}) {
    const profileBtns = document.querySelectorAll(".profile-btn");
    const pill = document.getElementById("target-profile-pill");
    const desc = document.getElementById("target-preset-desc");
    const normalizedKey = TARGET_PRESETS[key] ? key : "AUTO";
    currentTargetKey = normalizedKey;

    profileBtns.forEach(b => {
        b.classList.toggle("active", (b.dataset.target || "AUTO") === normalizedKey);
    });

    const preset = TARGET_PRESETS[normalizedKey];
    if (pill) {
        pill.innerHTML = `<span style="color: var(--cyan);">●</span> ${escapeHtml(preset.pillText)}`;
    }
    if (desc) {
        desc.textContent = preset.desc;
    }

    setSliderValue("slider-min-sim", "val-min-sim", overrides.minSim ?? preset.minSim, "%");
    setSliderValue("slider-text-weight", "val-text-weight", overrides.textWeight ?? preset.textWeight, "%");
    setSliderValue("slider-id-weight", "val-id-weight", overrides.idWeight ?? preset.idWeight, "%");
    setSliderValue("slider-desc-weight", "val-desc-weight", overrides.descWeight ?? preset.descWeight, "%");
}

function initProfileSwitcher() {
    const profileBtns = document.querySelectorAll(".profile-btn");
    profileBtns.forEach(btn => {
        btn.addEventListener("click", () => {
            applyTargetSelection(btn.dataset.target || "AUTO");
        });
    });
}

function setSliderValue(sliderId, labelId, val, suffix) {
    const slider = document.getElementById(sliderId);
    const label = document.getElementById(labelId);
    let effective = val;
    if (slider) {
        slider.value = String(val);
        const parsed = parseFloat(slider.value);
        if (Number.isFinite(parsed)) {
            effective = parsed;
        }
    }
    if (label) label.textContent = `${(effective * 100).toFixed(0)}${suffix}`;
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
                saveNotice.classList.toggle("text-emerald", success);
                saveNotice.classList.toggle("text-danger", !success);
                saveNotice.textContent = success
                    ? "Konfigurasi berhasil disimpan!"
                    : "Gagal menyimpan konfigurasi ke server (cek koneksi atau rentang nilai).";
                saveNotice.classList.remove("hidden");
                setTimeout(() => {
                    saveNotice.classList.add("hidden");
                }, 3000);
            }
        });
    }
}
