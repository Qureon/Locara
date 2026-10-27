package com.zerohealing.core.model;

import java.io.File;

/**
 * Result of a healing operation containing execution status and output references.
 */
public class HealResult {
    private final boolean success;
    private final HealCandidate candidate;
    private final File generatedRsFile;
    private final File annotatedScreenshotFile;
    private final com.zerohealing.core.framework.FrameworkFingerprint frameworkFingerprint;
    private final String message;

    public HealResult(boolean success, HealCandidate candidate, File generatedRsFile, String message) {
        this(success, candidate, generatedRsFile, null, null, message);
    }

    public HealResult(boolean success, HealCandidate candidate, File generatedRsFile, File annotatedScreenshotFile, String message) {
        this(success, candidate, generatedRsFile, annotatedScreenshotFile, null, message);
    }

    public HealResult(boolean success, HealCandidate candidate, File generatedRsFile, File annotatedScreenshotFile,
                      com.zerohealing.core.framework.FrameworkFingerprint frameworkFingerprint, String message) {
        this.success = success;
        this.candidate = candidate;
        this.generatedRsFile = generatedRsFile;
        this.annotatedScreenshotFile = annotatedScreenshotFile;
        this.frameworkFingerprint = frameworkFingerprint;
        this.message = message != null ? message : "";
    }

    public boolean isSuccess() { return success; }
    public HealCandidate getCandidate() { return candidate; }
    public File getGeneratedRsFile() { return generatedRsFile; }
    public File getAnnotatedScreenshotFile() { return annotatedScreenshotFile; }
    public com.zerohealing.core.framework.FrameworkFingerprint getFrameworkFingerprint() { return frameworkFingerprint; }
    public String getMessage() { return message; }

    @Override
    public String toString() {
        return "HealResult{" +
                "success=" + success +
                ", candidate=" + candidate +
                ", rsFile=" + (generatedRsFile != null ? generatedRsFile.getName() : "none") +
                ", message='" + message + '\'' +
                '}';
    }
}
