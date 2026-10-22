package com.zerohealing.core.framework;

import com.zerohealing.core.model.UIElementNode;
import com.zerohealing.core.parser.PageSourceParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FrameworkDetectorTest {
    private FrameworkDetector detector;
    private PageSourceParser parser;

    @BeforeEach
    public void setUp() {
        detector = new FrameworkDetector();
        parser = new PageSourceParser();
    }

    @Test
    public void testDetectReactNativeByViewGroupDensity() throws Exception {
        String rnXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<hierarchy rotation=\"0\">\n" +
                "  <android.widget.FrameLayout bounds=\"[0,0][1080,2400]\">\n" +
                "    <android.view.ViewGroup bounds=\"[0,0][1080,2400]\">\n" +
                "      <android.view.ViewGroup bounds=\"[0,0][1080,200]\">\n" +
                "        <android.widget.TextView text=\"M-Smile React Native\" bounds=\"[60,60][400,120]\" />\n" +
                "      </android.view.ViewGroup>\n" +
                "      <android.view.ViewGroup clickable=\"true\" content-desc=\"btn_transfer\" bounds=\"[60,300][1020,450]\">\n" +
                "        <android.widget.TextView text=\"Transfer Sekarang\" bounds=\"[100,320][800,400]\" />\n" +
                "      </android.view.ViewGroup>\n" +
                "      <android.view.ViewGroup bounds=\"[60,500][1020,700]\">\n" +
                "        <android.view.ViewGroup bounds=\"[80,520][1000,680]\" />\n" +
                "      </android.view.ViewGroup>\n" +
                "    </android.view.ViewGroup>\n" +
                "  </android.widget.FrameLayout>\n" +
                "</hierarchy>";

        List<UIElementNode> nodes = parser.parse(rnXml);
        FrameworkFingerprint fp = detector.detect(nodes, rnXml);

        assertEquals(AppFramework.REACT_NATIVE, fp.getFramework());
        assertTrue(fp.getConfidence() >= 0.90);
        assertTrue(fp.getViewGroupRatio() > 0.35);
    }

    @Test
    public void testDetectJetpackCompose() throws Exception {
        String composeXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<hierarchy rotation=\"0\">\n" +
                "  <android.widget.FrameLayout bounds=\"[0,0][1080,2400]\">\n" +
                "    <androidx.compose.ui.platform.ComposeView bounds=\"[0,0][1080,2400]\">\n" +
                "      <android.view.View content-desc=\"Tombol Konfirmasi\" bounds=\"[60,1800][1020,1950]\" />\n" +
                "    </androidx.compose.ui.platform.ComposeView>\n" +
                "  </android.widget.FrameLayout>\n" +
                "</hierarchy>";

        List<UIElementNode> nodes = parser.parse(composeXml);
        FrameworkFingerprint fp = detector.detect(nodes, composeXml);

        assertEquals(AppFramework.JETPACK_COMPOSE, fp.getFramework());
        assertTrue(fp.getConfidence() >= 0.95);
    }

    @Test
    public void testDetectFlutter() throws Exception {
        String flutterXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<hierarchy rotation=\"0\">\n" +
                "  <io.flutter.embedding.android.FlutterView bounds=\"[0,0][1080,2400]\">\n" +
                "    <android.view.View content-desc=\"Bayar QRIS Mega\" bounds=\"[60,1800][1020,1950]\" />\n" +
                "  </io.flutter.embedding.android.FlutterView>\n" +
                "</hierarchy>";

        List<UIElementNode> nodes = parser.parse(flutterXml);
        FrameworkFingerprint fp = detector.detect(nodes, flutterXml);

        assertEquals(AppFramework.FLUTTER, fp.getFramework());
        assertTrue(fp.getConfidence() >= 0.95);
    }

    @Test
    public void testDetectNativeAndroidXml() throws Exception {
        String nativeXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<hierarchy rotation=\"0\">\n" +
                "  <android.widget.FrameLayout bounds=\"[0,0][1080,2400]\">\n" +
                "    <android.widget.LinearLayout bounds=\"[0,0][1080,2400]\">\n" +
                "      <android.widget.TextView resource-id=\"com.bankmega.msmile:id/tv_title\" text=\"Login\" bounds=\"[60,60][400,120]\" />\n" +
                "      <android.widget.EditText resource-id=\"com.bankmega.msmile:id/et_user\" bounds=\"[60,200][1000,320]\" />\n" +
                "      <android.widget.Button resource-id=\"com.bankmega.msmile:id/btn_submit\" text=\"Masuk\" bounds=\"[60,500][1000,640]\" />\n" +
                "    </android.widget.LinearLayout>\n" +
                "  </android.widget.FrameLayout>\n" +
                "</hierarchy>";

        List<UIElementNode> nodes = parser.parse(nativeXml);
        FrameworkFingerprint fp = detector.detect(nodes, nativeXml);

        assertEquals(AppFramework.NATIVE_ANDROID, fp.getFramework());
        assertTrue(fp.getConfidence() >= 0.85);
    }

    @Test
    public void testDetectIosXcuITest() throws Exception {
        String iosXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<AppiumAUT>\n" +
                "  <XCUIElementTypeApplication name=\"M-Smile\" bounds=\"[0,0][390,844]\">\n" +
                "    <XCUIElementTypeButton name=\"Lanjut\" label=\"Lanjut\" bounds=\"[20,700][370,760]\" />\n" +
                "  </XCUIElementTypeApplication>\n" +
                "</AppiumAUT>";

        List<UIElementNode> nodes = parser.parse(iosXml);
        FrameworkFingerprint fp = detector.detect(nodes, iosXml);

        assertEquals(AppFramework.IOS_XCUITEST, fp.getFramework());
        assertEquals(1.0, fp.getConfidence(), 0.001);
    }
}
