package com.qureon.locara.core;

import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.HealResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies Locara v1.9.0 capabilities for "Naughty Developer" UI anti-patterns:
 * 1. ViewGroup without resource-id acting as clickable button or custom checkbox (wrapping text or placed to the left of label).
 * 2. CheckBox without resource-id and without text placed to the left of a label or sole CheckBox on screen.
 * 3. EditText without resource-id and without placeholder wrapped inside a ViewGroup with identical bounds below a label.
 */
public class AnonymousWidgetHealingTest {

    private LocaraEngine engine;

    @BeforeEach
    void setUp() {
        HealConfig config = new HealConfig();
        config.setAutoGenerateRs(false);
        config.setAuditLedgerEnabled(false);
        config.setMinConfidenceThreshold(0.80);
        engine = new LocaraEngine(config);
    }

    @Test
    @DisplayName("1A. Clickable ViewGroup without ID wrapping child TextView should inherit text and heal cleanly")
    void testClickableViewGroupWrappingTextWithoutId() {
        String xml = """
                <hierarchy>
                    <android.widget.FrameLayout bounds="[0,0][1080,2400]" displayed="true">
                        <android.view.ViewGroup bounds="[60,1800][1020,1940]" clickable="true" enabled="true" displayed="true" resource-id="" text="">
                            <android.widget.TextView bounds="[300,1840][780,1900]" clickable="false" enabled="true" displayed="true" text="Bayar Tagihan Sekarang" resource-id=""/>
                        </android.view.ViewGroup>
                    </android.widget.FrameLayout>
                </hierarchy>
                """;

        HealContext ctx = new HealContext.Builder()
                .targetHint("01. ViewGroup - Bayar Tagihan Sekarang")
                .action("TAP")
                .expectedTag("ViewGroup")
                .build();

        HealResult res = engine.heal(xml, ctx);
        assertTrue(res.isSuccess(), "Should heal clickable ViewGroup wrapping child TextView");
        assertNotNull(res.getCandidate());
        assertTrue(res.getCandidate().getSuggestedXPath().contains("android.view.ViewGroup[@clickable='true'"),
                "XPath should target the clickable ViewGroup: " + res.getCandidate().getSuggestedXPath());
        assertTrue(res.getCandidate().getSuggestedXPath().contains("Bayar Tagihan Sekarang"));
    }

    @Test
    @DisplayName("1B. Naughty Developer: Empty clickable square ViewGroup acting as Checkbox to the LEFT of label without ID")
    void testCustomViewGroupCheckboxLeftOfLabelWithoutId() {
        String xml = """
                <hierarchy>
                    <android.widget.FrameLayout bounds="[0,0][1080,2400]" displayed="true">
                        <android.view.ViewGroup bounds="[40,1500][1040,1600]" clickable="false" displayed="true">
                            <android.view.ViewGroup bounds="[60,1520][120,1580]" clickable="true" enabled="true" displayed="true" resource-id="" text="" content-desc=""/>
                            <android.widget.TextView bounds="[150,1520][980,1580]" clickable="false" enabled="true" displayed="true" text="Saya menyetujui Syarat &amp; Ketentuan" resource-id=""/>
                        </android.view.ViewGroup>
                    </android.widget.FrameLayout>
                </hierarchy>
                """;

        HealContext ctx = new HealContext.Builder()
                .targetHint("05. ViewGroup - Setuju Syarat dan Ketentuan")
                .action("TAP")
                .expectedTag("ViewGroup")
                .build();

        HealResult res = engine.heal(xml, ctx);
        assertTrue(res.isSuccess(), "Should heal custom square ViewGroup checkbox to the left of label");
        assertEquals("android.view.ViewGroup", res.getCandidate().getNode().getTag());
        assertTrue(res.getCandidate().getNode().isClickable());
        assertEquals(60, res.getCandidate().getNode().getBounds().x, "Should pick the small left checkbox ViewGroup at x=60");
        assertTrue(res.getCandidate().getSuggestedXPath().contains("following-sibling::android.widget.TextView"),
                "Should synthesize sibling-anchored XPath: " + res.getCandidate().getSuggestedXPath());
    }

    @Test
    @DisplayName("2. CheckBox without ID and without text placed to the LEFT of label should be detected via RIGHT_TO_LEFT spatial alignment")
    void testAnonymousCheckBoxLeftOfLabel() {
        String xml = """
                <hierarchy>
                    <android.widget.FrameLayout bounds="[0,0][1080,2400]" displayed="true">
                        <android.view.ViewGroup bounds="[40,1600][1040,1700]" clickable="false" displayed="true">
                            <android.widget.CheckBox bounds="[55,1620][115,1680]" checkable="true" checked="false" clickable="true" enabled="true" displayed="true" resource-id="" text="" content-desc=""/>
                            <android.widget.TextView bounds="[140,1620][950,1680]" clickable="false" enabled="true" displayed="true" text="Ingat Saya di Perangkat Ini" resource-id=""/>
                        </android.view.ViewGroup>
                    </android.widget.FrameLayout>
                </hierarchy>
                """;

        HealContext ctx = new HealContext.Builder()
                .targetHint("02. CheckBox - Ingat Saya")
                .action("TAP")
                .expectedTag("CheckBox")
                .build();

        HealResult res = engine.heal(xml, ctx);
        assertTrue(res.isSuccess(), "Should heal anonymous CheckBox to the left of 'Ingat Saya' label");
        assertEquals("android.widget.CheckBox", res.getCandidate().getNode().getTag());
        assertTrue(res.getCandidate().getSuggestedXPath().contains("android.widget.CheckBox[following-sibling::android.widget.TextView[@text='Ingat Saya di Perangkat Ini']]"),
                "Should generate clean relational XPath: " + res.getCandidate().getSuggestedXPath());
    }

    @Test
    @DisplayName("3. EditText without ID and without placeholder inside identical-bounds ViewGroup below label should select EditText (not ViewGroup)")
    void testAnonymousEditTextInsideIdenticalBoundsViewGroupBelowLabel() {
        String xml = """
                <hierarchy>
                    <android.widget.FrameLayout bounds="[0,0][1080,2400]" displayed="true">
                        <android.view.ViewGroup bounds="[50,400][1030,620]" clickable="false" displayed="true">
                            <android.widget.TextView bounds="[50,410][400,460]" clickable="false" displayed="true" text="Nama Zaku" resource-id=""/>
                            <android.view.ViewGroup bounds="[50,480][1030,590]" clickable="true" enabled="true" displayed="true" resource-id="" text="">
                                <android.widget.EditText bounds="[50,480][1030,590]" clickable="true" enabled="true" displayed="true" focusable="true" resource-id="" text="" content-desc=""/>
                            </android.view.ViewGroup>
                        </android.view.ViewGroup>
                        <android.view.ViewGroup bounds="[50,650][1030,870]" clickable="false" displayed="true">
                            <android.widget.TextView bounds="[50,660][450,710]" clickable="false" displayed="true" text="Nominal Setoran" resource-id=""/>
                            <android.view.ViewGroup bounds="[50,730][1030,840]" clickable="true" enabled="true" displayed="true" resource-id="" text="">
                                <android.widget.EditText bounds="[50,730][1030,840]" clickable="true" enabled="true" displayed="true" focusable="true" resource-id="" text="" content-desc=""/>
                            </android.view.ViewGroup>
                        </android.view.ViewGroup>
                    </android.widget.FrameLayout>
                </hierarchy>
                """;

        HealContext ctxNama = new HealContext.Builder()
                .targetHint("01. EditText - Nama Zaku")
                .action("SET_TEXT")
                .expectedTag("EditText")
                .build();

        HealResult resNama = engine.heal(xml, ctxNama);
        assertTrue(resNama.isSuccess(), "Should heal anonymous EditText below 'Nama Zaku'");
        assertEquals("android.widget.EditText", resNama.getCandidate().getNode().getTag(),
                "Must select EditText directly, NOT the parent ViewGroup wrapper!");
        assertEquals(480, resNama.getCandidate().getNode().getBounds().y);
        assertEquals("//android.view.ViewGroup[./android.widget.TextView[@text='Nama Zaku']]//android.widget.EditText",
                resNama.getCandidate().getSuggestedXPath());

        HealContext ctxNominal = new HealContext.Builder()
                .targetHint("02. EditText - Nominal Setoran")
                .action("SET_TEXT")
                .expectedTag("EditText")
                .build();

        HealResult resNominal = engine.heal(xml, ctxNominal);
        assertTrue(resNominal.isSuccess(), "Should heal anonymous EditText below 'Nominal Setoran'");
        assertEquals("android.widget.EditText", resNominal.getCandidate().getNode().getTag());
        assertEquals(730, resNominal.getCandidate().getNode().getBounds().y);
        assertEquals("//android.view.ViewGroup[./android.widget.TextView[@text='Nominal Setoran']]//android.widget.EditText",
                resNominal.getCandidate().getSuggestedXPath());
    }

    @Test
    @DisplayName("4. Sole anonymous EditText on screen without label and without ID should resolve via Single-Widget Fallback")
    void testSoleAnonymousEditTextFallback() {
        String xml = """
                <hierarchy>
                    <android.widget.FrameLayout bounds="[0,0][1080,2400]" displayed="true">
                        <android.view.ViewGroup bounds="[60,900][1020,1020]" displayed="true">
                            <android.widget.EditText bounds="[60,900][1020,1020]" clickable="true" enabled="true" displayed="true" resource-id="" text="" content-desc=""/>
                        </android.view.ViewGroup>
                    </android.widget.FrameLayout>
                </hierarchy>
                """;

        HealContext ctx = new HealContext.Builder()
                .targetHint("01. EditText - Input Kode Voucher")
                .action("SET_TEXT")
                .expectedTag("EditText")
                .build();

        HealResult res = engine.heal(xml, ctx);
        assertTrue(res.isSuccess(), "Should heal sole anonymous EditText on screen");
        assertEquals("android.widget.EditText", res.getCandidate().getNode().getTag());
        assertEquals("//android.widget.EditText", res.getCandidate().getSuggestedXPath());
        assertEquals("SINGLE_INPUT_WIDGET_ON_SCREEN", res.getCandidate().getHealReason());
    }

    @Test
    @DisplayName("5. Multiple anonymous EditTexts inside a flat parent ViewGroup should generate adjacent label sibling XPath")
    void testFlatFormMultipleAnonymousEditTexts() {
        String xml = """
                <hierarchy>
                    <android.widget.FrameLayout bounds="[0,0][1080,2400]" displayed="true">
                        <android.view.ViewGroup bounds="[50,300][1030,1200]" clickable="false" displayed="true">
                            <android.widget.TextView bounds="[50,410][400,460]" clickable="false" displayed="true" text="Nama Zaku" resource-id=""/>
                            <android.widget.EditText bounds="[50,480][1030,590]" clickable="true" enabled="true" displayed="true" resource-id="" text="" content-desc=""/>
                            <android.widget.TextView bounds="[50,660][450,710]" clickable="false" displayed="true" text="Target Dana" resource-id=""/>
                            <android.widget.EditText bounds="[50,730][1030,840]" clickable="true" enabled="true" displayed="true" resource-id="" text="" content-desc=""/>
                        </android.view.ViewGroup>
                    </android.widget.FrameLayout>
                </hierarchy>
                """;

        HealContext ctxTarget = new HealContext.Builder()
                .targetHint("02. EditText - Target Dana")
                .action("SET_TEXT")
                .expectedTag("EditText")
                .build();

        HealResult res = engine.heal(xml, ctxTarget);
        assertTrue(res.isSuccess(), "Should heal second anonymous EditText in flat form container");
        assertEquals(730, res.getCandidate().getNode().getBounds().y);
        assertEquals("//android.widget.TextView[@text='Target Dana']/following-sibling::android.widget.EditText[1]",
                res.getCandidate().getSuggestedXPath());
    }
}
