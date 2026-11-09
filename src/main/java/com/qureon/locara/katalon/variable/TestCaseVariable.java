package com.qureon.locara.katalon.variable;

import java.util.Objects;
import java.util.UUID;

/**
 * Domain representation of a Katalon Studio Test Case variable entity in XML format.
 */
public class TestCaseVariable {
    private final String name;
    private final String defaultValue;
    private final String description;
    private final String variableId;
    private final boolean masked;

    public TestCaseVariable(String name, String defaultValue, String description) {
        this(name, defaultValue, description, UUID.randomUUID().toString(), false);
    }

    public TestCaseVariable(String name, String defaultValue, String description, String variableId, boolean masked) {
        this.name = name != null ? name.trim() : "";
        this.defaultValue = defaultValue != null ? defaultValue : "''";
        this.description = description != null ? description.trim() : "";
        this.variableId = (variableId != null && !variableId.trim().isEmpty()) ? variableId.trim() : UUID.randomUUID().toString();
        this.masked = masked;
    }

    public String getName() {
        return name;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getDescription() {
        return description;
    }

    public String getVariableId() {
        return variableId;
    }

    public boolean isMasked() {
        return masked;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestCaseVariable)) return false;
        TestCaseVariable that = (TestCaseVariable) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "TestCaseVariable{" +
                "name='" + name + '\'' +
                ", defaultValue='" + defaultValue + '\'' +
                ", description='" + description + '\'' +
                ", variableId='" + variableId + '\'' +
                '}';
    }
}
