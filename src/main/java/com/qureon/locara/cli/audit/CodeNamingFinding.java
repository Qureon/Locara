package com.qureon.locara.cli.audit;

/**
 * Domain entity representing a code naming defect or convention finding.
 * Strictly adheres to Section 10 of the Master Code Naming Intelligence specification.
 */
public class CodeNamingFinding {

    private final String file;
    private final String location;
    private final String identifier;
    private final String category;
    private final String currentConvention;
    private final String expectedConvention;
    private final String issue;
    private final String proposedName;
    private final String reason;
    private final String risk; // LOW, MEDIUM, HIGH

    public CodeNamingFinding(String file,
                             String location,
                             String identifier,
                             String category,
                             String currentConvention,
                             String expectedConvention,
                             String issue,
                             String proposedName,
                             String reason,
                             String risk) {
        this.file = file;
        this.location = location;
        this.identifier = identifier;
        this.category = category;
        this.currentConvention = currentConvention;
        this.expectedConvention = expectedConvention;
        this.issue = issue;
        this.proposedName = proposedName;
        this.reason = reason;
        this.risk = risk;
    }

    public String getFile() { return file; }
    public String getLocation() { return location; }
    public String getIdentifier() { return identifier; }
    public String getCategory() { return category; }
    public String getCurrentConvention() { return currentConvention; }
    public String getExpectedConvention() { return expectedConvention; }
    public String getIssue() { return issue; }
    public String getProposedName() { return proposedName; }
    public String getReason() { return reason; }
    public String getRisk() { return risk; }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s) in %s:%s -> %s (Proposed: %s)",
                risk, identifier, category, file, location, issue, proposedName);
    }
}
