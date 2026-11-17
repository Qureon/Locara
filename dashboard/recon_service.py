#!/usr/bin/env python3
"""XML recon field extraction, XPath synthesis, and Katalon keyword tier classification."""

import re
import xml.etree.ElementTree as ET

from config_store import BadRequestError

BOUNDS_PATTERN = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")
XML_FORBIDDEN_DECL_PATTERN = re.compile(r"<!\s*(DOCTYPE|ENTITY)\b", re.IGNORECASE)
SAFE_TAG_PATTERN = re.compile(r"^(\*|[A-Za-z_][A-Za-z0-9_.:-]*)$")


def xp_literal(val):
    """Quote an arbitrary string value into a safe XPath 1.0 literal expression."""
    if "'" not in val:
        return f"'{val}'"
    if '"' not in val:
        return f'"{val}"'
    parts = val.split("'")
    return "concat(" + ", \"'\", ".join(f"'{p}'" for p in parts) + ")"


def extract_label_value_pairs(xml_text):
    """Extract spatial label-value pairs from Android/iOS/HTML XML hierarchy safely."""
    if not isinstance(xml_text, str) or not xml_text.strip():
        raise BadRequestError("Empty XML content")
    if XML_FORBIDDEN_DECL_PATTERN.search(xml_text):
        raise BadRequestError("DTD and entity declarations are not permitted")

    root = ET.fromstring(xml_text)
    nodes = []
    for el in root.iter():
        match = BOUNDS_PATTERN.match(el.get("bounds", ""))
        if not match:
            continue
        x1, y1, x2, y2 = map(int, match.groups())
        nodes.append({
            "tag": el.tag,
            "text": (el.get("text", "") or "").strip(),
            "desc": (el.get("content-desc", "") or "").strip(),
            "x1": x1, "y1": y1, "x2": x2, "y2": y2,
            "w": x2 - x1, "h": y2 - y1,
            "clickable": el.get("clickable", "false") == "true",
        })

    non_buttons = [n for n in nodes if "button" not in n["tag"].lower() and n["w"] > 0]
    labels = [n for n in non_buttons if n["text"]]
    claimed = set()
    pairs = []

    for lbl in labels:
        if id(lbl) in claimed:
            continue
        best_val, best_score = _find_best_value_candidate(lbl, non_buttons, claimed)
        if best_val and (best_val["text"] or best_val["desc"]):
            val_text = best_val["text"] or best_val["desc"]
            if val_text != lbl["text"]:
                claimed.add(id(best_val))
                pairs.append({
                    "label": lbl["text"],
                    "value": val_text,
                    "score": round(best_score, 2),
                    "bounds": f"[{lbl['x1']},{lbl['y1']}][{best_val['x2']},{best_val['y2']}]",
                })
    return pairs


def _find_best_value_candidate(lbl, non_buttons, claimed):
    best_val = None
    best_score = 0.0
    for cand in non_buttons:
        if cand is lbl or id(cand) in claimed:
            continue
        if cand["x1"] >= lbl["x1"] and cand["x2"] > lbl["x2"]:
            v_overlap = max(0, min(lbl["y2"], cand["y2"]) - max(lbl["y1"], cand["y1"]))
            min_h = max(1, min(lbl["h"], cand["h"]))
            if (v_overlap / min_h) >= 0.40 and (cand["text"] or cand["desc"] or cand["clickable"]):
                h_dist = max(0, cand["x1"] - lbl["x2"])
                score = (v_overlap / min_h) * 0.6 + (1.0 / (1.0 + h_dist / 100.0)) * 0.4
                if score > best_score:
                    best_score = score
                    best_val = cand

    if not best_val:
        for cand in non_buttons:
            if cand is lbl or id(cand) in claimed or not cand["text"]:
                continue
            v_dist = cand["y1"] - lbl["y2"]
            if 0 <= v_dist <= 40 and abs(cand["x1"] - lbl["x1"]) <= 40:
                return cand, 0.90
    return best_val, best_score


def synthesize_ranked_xpaths(body):
    """Synthesize safe XPath 1.0 candidates using quoted XPath literals."""
    if not isinstance(body, dict):
        raise BadRequestError("Payload must be a JSON object")

    raw_tag = body.get("tag", "*")
    tag = "*" if raw_tag is None else str(raw_tag).strip() or "*"
    if not SAFE_TAG_PATTERN.match(tag):
        raise BadRequestError("Invalid XML tag name")

    def _opt_str(*keys):
        for k in keys:
            if k in body and body[k] is not None:
                if not isinstance(body[k], str):
                    raise BadRequestError(f"Field '{k}' must be a string")
                return body[k]
        return ""

    text = _opt_str("text")
    res_id = _opt_str("resourceId", "id")
    desc = _opt_str("contentDesc", "desc")
    cls = _opt_str("className", "class")

    ranked = {}
    if res_id and text:
        ranked["xpath:attributes"] = f"//{tag}[@resource-id={xp_literal(res_id)} and @text={xp_literal(text)}]"
    elif res_id:
        ranked["xpath:attributes"] = f"//{tag}[@resource-id={xp_literal(res_id)}]"
    elif desc:
        ranked["xpath:attributes"] = f"//{tag}[@content-desc={xp_literal(desc)}]"
    elif text:
        ranked["xpath:attributes"] = f"//{tag}[@text={xp_literal(text)}]"
    else:
        ranked["xpath:attributes"] = f"//{tag}"

    if res_id:
        ranked["xpath:idRelative"] = f"//*[@resource-id={xp_literal(res_id)} or @id={xp_literal(res_id)}]"
    if text:
        ranked["xpath:custom"] = f"//{tag}[text()={xp_literal(text)} or .={xp_literal(text)}]"
    if cls:
        ranked["xpath:position"] = f"//*[@class={xp_literal(cls)}]"
    return ranked


def classify_keyword_tier(keyword):
    """Classify WebUI/Mobile/Windows keyword into Locara healing risk tiers."""
    if keyword is None:
        raw_kw = ""
    elif isinstance(keyword, str):
        raw_kw = keyword
    else:
        raise BadRequestError("Field 'keyword' must be a string")

    kw = raw_kw.strip().lower()
    if kw.startswith("webui.") or kw.startswith("mobile.") or kw.startswith("windows."):
        kw = kw.split(".", 1)[1]

    if (
        kw.startswith("iselement")
        or kw.startswith("is_element")
        or "findfirst" in kw
        or "probe" in kw
        or "optional" in kw
    ):
        return "CONDITIONAL_PROBE"
    if kw.startswith("verify") or kw.startswith("assert") or "checkpoint" in kw:
        return "CHECKPOINT"
    return "INTERACTION"
