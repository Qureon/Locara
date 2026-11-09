package com.qureon.locara.core.context;

import com.qureon.locara.core.model.UIElementNode;
import com.qureon.locara.core.parser.PageSourceParser;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contextual Entity Resolver for Locara by Qureon.
 * Dynamically infers and resolves financial entities (active account numbers, card types,
 * account holders) directly from live UI dumps to prevent test crashes caused by data drift.
 */
public class ContextualEntityResolver {

    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("\\b(\\d{10,16})\\b");
    private static final Pattern SPACED_ACCOUNT_PATTERN = Pattern.compile("\\b(\\d{4}\\s+\\d{4}\\s+\\d{4,8})\\b");
    private static final Pattern GREETING_PATTERN = Pattern.compile("(?i)^Halo,?\\s*([^\\n\\r]+)");
    private static final Pattern MASKED_BALANCE_PATTERN = Pattern.compile("[*•]{3,}");

    private final PageSourceParser parser;

    public ContextualEntityResolver() {
        this.parser = new PageSourceParser();
    }

    /**
     * Resolves the active bank account card from a raw XML page source string.
     */
    public ResolvedAccount resolveActiveAccount(String pageSource) {
        if (pageSource == null || pageSource.trim().isEmpty()) {
            return null;
        }
        try {
            List<UIElementNode> nodes = parser.parse(pageSource);
            return resolveActiveAccount(nodes);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolves the active bank account card from a flattened list of parsed UIElementNodes.
     */
    public ResolvedAccount resolveActiveAccount(List<UIElementNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }

        UIElementNode accountNode = null;
        String rawAccountNumber = null;

        // 1. Scan for account number in text and content-desc
        for (UIElementNode node : nodes) {
            String text = node.getText();
            String desc = node.getContentDesc();

            String extracted = extractAccountNumber(text);
            if (extracted == null) {
                extracted = extractAccountNumber(desc);
            }

            if (extracted != null) {
                accountNode = node;
                rawAccountNumber = extracted;
                break;
            }
        }

        if (accountNode == null || rawAccountNumber == null) {
            return null;
        }

        // 2. Scan for Holder Name (e.g. "Halo, Rio Griya")
        String holderName = null;
        for (UIElementNode node : nodes) {
            String text = node.getText();
            if (text != null) {
                Matcher m = GREETING_PATTERN.matcher(text.trim());
                if (m.find()) {
                    holderName = m.group(1).trim();
                    break;
                }
            }
            String desc = node.getContentDesc();
            if (desc != null) {
                Matcher m = GREETING_PATTERN.matcher(desc.trim());
                if (m.find()) {
                    holderName = m.group(1).trim();
                    break;
                }
            }
        }

        // 3. Scan for Account Type / Bank Name near the account node
        String accountType = findAccountTypeNearNode(accountNode, nodes);

        // 4. Check if masked balance is present near account node or on screen
        boolean maskedBalance = isMaskedBalanceNearby(accountNode, nodes);

        return new ResolvedAccount(rawAccountNumber, accountType, holderName, maskedBalance, accountNode);
    }

    private String extractAccountNumber(String val) {
        if (val == null || val.trim().isEmpty()) {
            return null;
        }
        String cleanSpaced = val.trim();
        // If string consists purely of digits and whitespace (e.g. "0107 4002 1025 853")
        String numericOnly = cleanSpaced.replaceAll("[^0-9]", "");
        if (numericOnly.length() >= 10 && numericOnly.length() <= 16) {
            if (cleanSpaced.replaceAll("[0-9\\s]", "").isEmpty()) {
                return numericOnly;
            }
        }
        Matcher m = ACCOUNT_NUMBER_PATTERN.matcher(cleanSpaced);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String findAccountTypeNearNode(UIElementNode accountNode, List<UIElementNode> allNodes) {
        // Check siblings in the same parent container
        UIElementNode parent = accountNode.getParent();
        if (parent != null) {
            for (UIElementNode sibling : parent.getChildren()) {
                if (sibling != accountNode) {
                    String text = sibling.getText();
                    if (isLikelyAccountType(text)) {
                        return text.trim();
                    }
                }
            }
            // Check grandparent's other children
            UIElementNode grandParent = parent.getParent();
            if (grandParent != null) {
                for (UIElementNode uncle : grandParent.getChildren()) {
                    if (uncle != parent) {
                        String text = uncle.getText();
                        if (isLikelyAccountType(text)) {
                            return text.trim();
                        }
                    }
                }
            }
        }

        // Fallback: search nodes located right above account node geographically
        if (accountNode.getBounds() != null) {
            int accTop = accountNode.getBounds().y;
            UIElementNode bestAbove = null;
            int minDistance = Integer.MAX_VALUE;

            for (UIElementNode node : allNodes) {
                if (node != accountNode && node.getBounds() != null) {
                    int nodeBottom = node.getBounds().y + node.getBounds().height;
                    int diff = accTop - nodeBottom;
                    if (diff >= 0 && diff < 200 && isLikelyAccountType(node.getText())) {
                        if (diff < minDistance) {
                            minDistance = diff;
                            bestAbove = node;
                        }
                    }
                }
            }
            if (bestAbove != null) {
                return bestAbove.getText().trim();
            }
        }

        return "Rekening Tabungan";
    }

    private boolean isLikelyAccountType(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase().trim();
        return lower.contains("tabungan") ||
                lower.contains("giro") ||
                lower.contains("deposito") ||
                lower.contains("mega dana") ||
                lower.contains("mega maxi") ||
                lower.contains("mega berbagi") ||
                lower.contains("estatement");
    }

    private boolean isMaskedBalanceNearby(UIElementNode accountNode, List<UIElementNode> allNodes) {
        UIElementNode parent = accountNode.getParent();
        if (parent != null) {
            for (UIElementNode child : parent.getChildren()) {
                String text = child.getText();
                if (text != null && MASKED_BALANCE_PATTERN.matcher(text).find()) {
                    return true;
                }
            }
        }
        // Fallback: search all nodes
        for (UIElementNode node : allNodes) {
            String text = node.getText();
            if (text != null && MASKED_BALANCE_PATTERN.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
