package com.qureon.locara.core.framework;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class KeywordIntentionClassifierTest {

    @Test
    @DisplayName("Should classify interaction keywords correctly")
    void testClassifyInteractions() {
        assertEquals(KeywordIntentionClassifier.IntentionTier.INTERACTION, KeywordIntentionClassifier.classify("tap"));
        assertEquals(KeywordIntentionClassifier.IntentionTier.INTERACTION, KeywordIntentionClassifier.classify("TAP"));
        assertEquals(KeywordIntentionClassifier.IntentionTier.INTERACTION, KeywordIntentionClassifier.classify("setText"));
        assertEquals(KeywordIntentionClassifier.IntentionTier.INTERACTION, KeywordIntentionClassifier.classify("click"));
    }

    @Test
    @DisplayName("Should classify assertion and checkpoint keywords correctly")
    void testClassifyCheckpoints() {
        assertEquals(KeywordIntentionClassifier.IntentionTier.CHECKPOINT, KeywordIntentionClassifier.classify("verifyElementPresent"));
        assertEquals(KeywordIntentionClassifier.IntentionTier.CHECKPOINT, KeywordIntentionClassifier.classify("verifyElementText"));
        assertEquals(KeywordIntentionClassifier.IntentionTier.CHECKPOINT, KeywordIntentionClassifier.classify("assertElementPresent"));
        assertEquals(KeywordIntentionClassifier.IntentionTier.CHECKPOINT, KeywordIntentionClassifier.classify("assertElementVisible"));
        assertTrue(KeywordIntentionClassifier.isCheckpoint("assertElementText"));
    }

    @Test
    @DisplayName("Should classify conditional probes correctly (Katalon 11.7 isElement*)")
    void testClassifyProbes() {
        assertEquals(KeywordIntentionClassifier.IntentionTier.CONDITIONAL_PROBE, KeywordIntentionClassifier.classify("isElementPresent"));
        assertEquals(KeywordIntentionClassifier.IntentionTier.CONDITIONAL_PROBE, KeywordIntentionClassifier.classify("isElementVisible"));
        assertEquals(KeywordIntentionClassifier.IntentionTier.CONDITIONAL_PROBE, KeywordIntentionClassifier.classify("findFirstElement"));
        assertTrue(KeywordIntentionClassifier.isConditionalProbe("isElementClickable"));
    }
}
