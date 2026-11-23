#!/usr/bin/env python3
"""Configuration storage, validation, and CLI port/audit path discovery for Locara Dashboard."""

import math
import os
import tempfile
from pathlib import Path

ENGINE_VERSION = "v1.16.1"
DEFAULT_PORT = 8088
ALLOWED_TARGETS = frozenset({"AUTO", "UMUM", "REVAMP", "KMP", "LEGACY_XML", "LEGACY", "WEB", "GENERIC"})
FLOAT_CONFIG_KEYS = frozenset({
    "minSimilarityThreshold",
    "textSimilarityWeight",
    "resourceIdSimilarityWeight",
    "contentDescSimilarityWeight",
})
PROPERTIES_KEY_MAP = {
    "appTarget": "locara.target",
    "minSimilarityThreshold": "locara.threshold",
    "textSimilarityWeight": "locara.weight.text",
    "resourceIdSimilarityWeight": "locara.weight.id",
    "contentDescSimilarityWeight": "locara.weight.desc",
    "auditLedgerEnabled": "locara.audit.enabled",
}
DEFAULT_CONFIG = {
    "appTarget": "AUTO",
    "minSimilarityThreshold": 0.70,
    "textSimilarityWeight": 0.35,
    "resourceIdSimilarityWeight": 0.25,
    "contentDescSimilarityWeight": 0.20,
    "auditLedgerEnabled": True,
}


class BadRequestError(ValueError):
    """Raised when an incoming HTTP payload violates schema or security constraints."""


class PayloadTooLargeError(ValueError):
    """Raised when an incoming HTTP payload exceeds MAX_REQUEST_BYTES."""


def _parse_valid_port(raw_value):
    try:
        port = int(raw_value)
    except (TypeError, ValueError):
        return None
    return port if 1 <= port <= 65535 else None


def parse_port(argv):
    """Parse HTTP port (1..65535) from --port, -p, or positional argument; fallback to DEFAULT_PORT."""
    for i, arg in enumerate(argv[1:], start=1):
        if arg in ("--port", "-p") and i + 1 < len(argv):
            port = _parse_valid_port(argv[i + 1])
            return port if port is not None else DEFAULT_PORT
        if arg.startswith("--port="):
            port = _parse_valid_port(arg.split("=", 1)[1])
            return port if port is not None else DEFAULT_PORT
        if not arg.startswith("-"):
            port = _parse_valid_port(arg)
            if port is not None:
                return port
    return DEFAULT_PORT


def build_audit_candidates(dashboard_dir):
    """Build prioritized list of candidate audit log paths from env and workspace roots."""
    candidates = []
    env_log = os.environ.get("LOCARA_AUDIT_LOG")
    if env_log:
        candidates.append(Path(env_log).expanduser())

    search_roots = [dashboard_dir.parent, Path.cwd()]
    env_dirs = os.environ.get("LOCARA_AUDIT_DIRS", "")
    for raw_dir in env_dirs.split(os.pathsep):
        cleaned = raw_dir.strip()
        if cleaned:
            search_roots.append(Path(cleaned).expanduser())

    seen = set()
    rel_paths = (
        Path("Include") / "evidence" / "locara_audit.json",
        Path("Include") / "evidence" / "healing_audit.json",
        Path("locara_audit.json"),
        Path("healing_audit.json"),
    )
    for root in search_roots:
        for rel in rel_paths:
            cand = (root / rel).resolve()
            if str(cand) not in seen:
                seen.add(str(cand))
                candidates.append(cand)
    return candidates


def validate_config_patch(data):
    """Validate and normalize an incoming configuration dictionary against the allowlist."""
    if not isinstance(data, dict):
        raise BadRequestError("Config payload must be a JSON object")

    allowed_keys = set(PROPERTIES_KEY_MAP.keys()) | {"updatedAt"}
    unknown = set(data.keys()) - allowed_keys
    if unknown:
        raise BadRequestError(f"Unsupported configuration key(s): {', '.join(sorted(unknown))}")

    validated = {}
    if "appTarget" in data:
        raw_target = data["appTarget"]
        if not isinstance(raw_target, str):
            raise BadRequestError("appTarget must be a string")
        normalized_target = raw_target.strip().upper()
        if normalized_target not in ALLOWED_TARGETS:
            raise BadRequestError("Unsupported appTarget profile")
        validated["appTarget"] = normalized_target

    for key in FLOAT_CONFIG_KEYS:
        if key in data:
            val = data[key]
            if isinstance(val, bool) or not isinstance(val, (int, float)):
                raise BadRequestError(f"{key} must be a numeric value")
            fval = float(val)
            if not math.isfinite(fval) or fval < 0.0 or fval > 1.0:
                raise BadRequestError(f"{key} must be within [0.0, 1.0]")
            validated[key] = round(fval, 4)

    if "auditLedgerEnabled" in data:
        val = data["auditLedgerEnabled"]
        if not isinstance(val, bool):
            raise BadRequestError("auditLedgerEnabled must be a boolean")
        validated["auditLedgerEnabled"] = val

    if "updatedAt" in data:
        updated_at = data["updatedAt"]
        if not isinstance(updated_at, str) or len(updated_at) > 64:
            raise BadRequestError("updatedAt must be a short ISO timestamp string")

    return validated


def load_persisted_config(target_config, config_path):
    """Load persisted locara.* configuration properties into target_config."""
    target = Path(config_path)
    if not target.exists() or not target.is_file():
        return dict(target_config)

    reverse_map = {prop_key: cfg_key for cfg_key, prop_key in PROPERTIES_KEY_MAP.items()}
    patch = {}
    try:
        for raw_line in target.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or line.startswith("!") or "=" not in line:
                continue
            prop_key, prop_val = line.split("=", 1)
            cfg_key = reverse_map.get(prop_key.strip())
            if not cfg_key:
                continue
            val_str = prop_val.strip()
            if cfg_key == "appTarget":
                patch[cfg_key] = val_str
            elif cfg_key in FLOAT_CONFIG_KEYS:
                try:
                    patch[cfg_key] = float(val_str)
                except ValueError:
                    pass
            elif cfg_key == "auditLedgerEnabled" and val_str.lower() in ("true", "false"):
                patch[cfg_key] = val_str.lower() == "true"
        for cfg_key, raw_val in patch.items():
            try:
                target_config.update(validate_config_patch({cfg_key: raw_val}))
            except BadRequestError:
                pass
    except OSError:
        pass
    return dict(target_config)


def save_persisted_config(config_data, config_path):
    """Persist core locara.* keys into locara.properties while preserving unrelated entries."""
    target = Path(config_path)
    updates = {
        prop_key: ("true" if config_data[cfg_key] else "false")
        if isinstance(config_data[cfg_key], bool)
        else str(config_data[cfg_key])
        for cfg_key, prop_key in PROPERTIES_KEY_MAP.items()
        if cfg_key in config_data
    }

    existing_lines = (
        target.read_text(encoding="utf-8").splitlines()
        if target.exists() and target.is_file()
        else []
    )
    written_keys = set()
    new_lines = []
    for line in existing_lines:
        stripped = line.strip()
        if stripped and not stripped.startswith("#") and not stripped.startswith("!") and "=" in line:
            k = line.split("=", 1)[0].strip()
            if k in updates:
                new_lines.append(f"{k}={updates[k]}")
                written_keys.add(k)
                continue
        new_lines.append(line)

    for prop_key, prop_val in updates.items():
        if prop_key not in written_keys:
            new_lines.append(f"{prop_key}={prop_val}")

    target.parent.mkdir(parents=True, exist_ok=True)
    payload = "\n".join(new_lines) + "\n"
    tmp_path = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w", encoding="utf-8", dir=str(target.parent), prefix=".locara_cfg_", delete=False
        ) as tmp_file:
            tmp_path = Path(tmp_file.name)
            tmp_file.write(payload)
            tmp_file.flush()
        os.replace(str(tmp_path), str(target))
    except Exception:
        if tmp_path is not None and tmp_path.exists():
            try:
                tmp_path.unlink()
            except OSError:
                pass
        raise
