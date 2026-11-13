/**
 * ==============================================================================
 * LOCARA MISSION CONTROL — SHARED ESCAPING & XPATH UTILITIES
 * ==============================================================================
 */

/**
 * Escape untrusted text before inserting into HTML element bodies or quoted attributes.
 */
export function escapeHtml(str) {
    if (str === null || str === undefined) return "";
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

/**
 * Escape untrusted text before embedding into XML text nodes or attribute values.
 */
export function escapeXml(str) {
    if (str === null || str === undefined) return "";
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&apos;");
}

/**
 * Quote an arbitrary runtime value into a safe XPath 1.0 string literal.
 */
export function toXPathLiteral(val) {
    const s = val === null || val === undefined ? "" : String(val);
    if (!s.includes("'")) {
        return `'${s}'`;
    }
    if (!s.includes('"')) {
        return `"${s}"`;
    }
    const parts = s.split("'").map(p => `'${p}'`);
    return `concat(${parts.join(', "\'", ')})`;
}

/**
 * Escape a string for safe embedding inside a Groovy/Java double-quoted string literal.
 */
export function escapeCodeString(str) {
    if (str === null || str === undefined) return "";
    return String(str)
        .replace(/\\/g, "\\\\")
        .replace(/"/g, '\\"')
        .replace(/\$/g, "\\$")
        .replace(/\r/g, "\\r")
        .replace(/\n/g, "\\n");
}

