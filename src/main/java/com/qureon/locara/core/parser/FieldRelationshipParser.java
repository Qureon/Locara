package com.qureon.locara.core.parser;

import com.qureon.locara.core.model.UIElementNode;
import com.qureon.locara.core.spatial.SpatialAlignmentEngine;

import java.util.*;

/**
 * 🧾 FieldRelationshipParser — Universal Key-Value & Form Section Extractor.
 *
 * Driver-agnostic extractor that autonomously discovers label-value pairs,
 * form sections, and terminal totals from mobile page source hierarchies.
 */
public class FieldRelationshipParser {

    private final SpatialAlignmentEngine spatialEngine = new SpatialAlignmentEngine();

    public static class FieldEntry {
        private final String key;
        private final String rawLabel;
        private final String value;
        private final UIElementNode labelNode;
        private final UIElementNode valueNode;

        public FieldEntry(String key, String rawLabel, String value, UIElementNode labelNode, UIElementNode valueNode) {
            this.key = key;
            this.rawLabel = rawLabel;
            this.value = value;
            this.labelNode = labelNode;
            this.valueNode = valueNode;
        }

        public String getKey() { return key; }
        public String getRawLabel() { return rawLabel; }
        public String getValue() { return value; }
        public UIElementNode getLabelNode() { return labelNode; }
        public UIElementNode getValueNode() { return valueNode; }

        @Override
        public String toString() {
            return String.format("%s: %s", rawLabel, value);
        }
    }

    public static class ParsedDocument {
        private final Map<String, String> fieldMap = new LinkedHashMap<>();
        private final List<FieldEntry> entries = new ArrayList<>();
        private FieldEntry totalEntry;

        public void addEntry(FieldEntry entry) {
            entries.add(entry);
            fieldMap.put(entry.getKey(), entry.getValue());
            if (isTerminalTotal(entry.getRawLabel())) {
                this.totalEntry = entry;
            }
        }

        public Map<String, String> getFieldMap() { return Collections.unmodifiableMap(fieldMap); }
        public List<FieldEntry> getEntries() { return Collections.unmodifiableList(entries); }
        public Optional<FieldEntry> getTotalEntry() { return Optional.ofNullable(totalEntry); }

        public String get(String key) {
            if (key == null) return null;
            String norm = normalizeKey(key);
            for (Map.Entry<String, String> e : fieldMap.entrySet()) {
                if (e.getKey().equalsIgnoreCase(norm) || e.getKey().contains(norm)) {
                    return e.getValue();
                }
            }
            return null;
        }

        public String toSummaryTable() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%-32s | %s\n", "FIELD LABEL", "VALUE"));
            sb.append("---------------------------------+-----------------------------------\n");
            for (FieldEntry e : entries) {
                sb.append(String.format("%-32s | %s\n", truncate(e.getRawLabel(), 32), e.getValue()));
            }
            return sb.toString();
        }

        private static boolean isTerminalTotal(String label) {
            if (label == null) return false;
            String l = label.toLowerCase();
            return (l.contains("total") || l.contains("grand total") || l.contains("subtotal")) &&
                    !l.contains("peserta") && !l.contains("tagihan");
        }

        private static String truncate(String s, int max) {
            if (s == null) return "";
            return s.length() > max ? s.substring(0, max - 3) + "..." : s;
        }
    }

    /**
     * Parses all nodes into a structured key-value document.
     */
    public ParsedDocument parse(List<UIElementNode> nodes) {
        ParsedDocument doc = new ParsedDocument();
        if (nodes == null || nodes.isEmpty()) return doc;

        List<SpatialAlignmentEngine.SpatialPair> pairs = spatialEngine.extractAllPairs(nodes);

        for (SpatialAlignmentEngine.SpatialPair pair : pairs) {
            UIElementNode lbl = pair.getLabelNode();
            UIElementNode val = pair.getValueNode();

            String rawLabel = lbl.hasText() ? lbl.getText().trim() : (lbl.hasContentDesc() ? lbl.getContentDesc().trim() : "");
            String rawVal = val.hasText() ? val.getText().trim() : (val.hasContentDesc() ? val.getContentDesc().trim() : "");

            if (!rawLabel.isEmpty() && !rawVal.isEmpty() && !rawLabel.equalsIgnoreCase(rawVal)) {
                String key = normalizeKey(rawLabel);
                doc.addEntry(new FieldEntry(key, rawLabel, rawVal, lbl, val));
            }
        }

        return doc;
    }

    private static String normalizeKey(String label) {
        if (label == null) return "";
        return label.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", "_")
                .trim();
    }
}
