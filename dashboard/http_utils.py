#!/usr/bin/env python3
"""HTTP security checks, bounded JSON body reader, and audit file loader for Locara Dashboard."""

import json
from urllib.parse import urlparse

from config_store import BadRequestError, PayloadTooLargeError

LOOPBACK_HOSTS = frozenset({"127.0.0.1", "localhost", "::1"})


def is_allowed_loopback_origin(origin_value):
    """Return True only if Origin/Referer resolves to an HTTP(S) loopback host."""
    if not origin_value:
        return False
    try:
        parsed = urlparse(origin_value)
    except Exception:
        return False
    return parsed.scheme in ("http", "https") and (parsed.hostname or "").lower() in LOOPBACK_HOSTS


def is_allowed_host_header(host_header):
    """Return True if Host header is empty or targets a loopback address."""
    if not host_header:
        return True
    try:
        parsed = urlparse(f"http://{host_header}")
        return (parsed.hostname or "").lower() in LOOPBACK_HOSTS
    except Exception:
        return False


def read_json_body(rfile, headers, max_request_bytes, allow_empty=False):
    """Validate Content-Length, decode UTF-8 safely, and parse a JSON object payload."""
    raw_len = headers.get("Content-Length")
    if raw_len is None:
        length = 0
    else:
        try:
            length = int(raw_len)
        except ValueError as exc:
            raise BadRequestError("Invalid Content-Length header") from exc
    if length < 0:
        raise BadRequestError("Negative Content-Length header")
    if length > max_request_bytes:
        raise PayloadTooLargeError(f"Request body exceeds {max_request_bytes} byte limit")
    if length == 0:
        if allow_empty:
            return {}
        raise BadRequestError("Request body is empty")

    try:
        text = rfile.read(length).decode("utf-8")
    except UnicodeDecodeError as exc:
        raise BadRequestError("Request body must be valid UTF-8") from exc
    if not text.strip():
        if allow_empty:
            return {}
        raise BadRequestError("Request body is empty")

    try:
        parsed = json.loads(text)
    except json.JSONDecodeError as exc:
        raise BadRequestError("Malformed JSON payload") from exc
    if not isinstance(parsed, dict):
        raise BadRequestError("JSON root must be an object")
    return parsed


def drain_small_body(rfile, headers, limit=65536):
    """Drain small request bodies on rejected POSTs so persistent HTTP connections stay clean."""
    raw_len = headers.get("Content-Length")
    if not raw_len:
        return
    try:
        length = int(raw_len)
    except ValueError:
        return
    if 0 < length <= limit:
        rfile.read(length)


def load_audit_events(audit_candidates):
    """Load the first readable audit file (JSON array, single object, or JSONL) from candidates."""
    for path in audit_candidates:
        if not path.exists() or not path.is_file():
            continue
        try:
            content = path.read_text(encoding="utf-8").strip()
            if not content:
                return []
            try:
                parsed = json.loads(content)
                return parsed if isinstance(parsed, list) else [parsed]
            except json.JSONDecodeError:
                events = []
                for line in content.splitlines():
                    line_str = line.strip()
                    if line_str:
                        try:
                            events.append(json.loads(line_str))
                        except Exception:
                            pass
                return events
        except Exception:
            pass
    return []
