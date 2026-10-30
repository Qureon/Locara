package com.qureon.locara.core.spatial;

import com.qureon.locara.core.model.UIElementNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class SpatialAlignmentEngineTest {

    @Test
    @DisplayName("Should pair left-to-right label and value on same horizontal row")
    void testLeftToRightPairing() {
        UIElementNode labelNode = new UIElementNode(
                "android.widget.TextView", "lbl_acc", "Nomor Rekening", "",
                "android.widget.TextView", "//TextView[1]",
                new Rectangle(50, 400, 250, 50),
                false, true, true, false, false, 3
        );

        UIElementNode valueNode = new UIElementNode(
                "android.widget.TextView", "val_acc", "1234-5678-90", "",
                "android.widget.TextView", "//TextView[2]",
                new Rectangle(550, 400, 400, 50),
                false, true, true, false, false, 3
        );

        List<UIElementNode> nodes = Arrays.asList(labelNode, valueNode);

        SpatialAlignmentEngine engine = new SpatialAlignmentEngine();
        Optional<UIElementNode> aligned = engine.findAlignedNode(labelNode, nodes, SpatialAlignmentEngine.AlignmentMode.LEFT_TO_RIGHT);

        assertTrue(aligned.isPresent());
        assertEquals("1234-5678-90", aligned.get().getText());
    }

    @Test
    @DisplayName("Should pair top-to-bottom label and input field")
    void testTopToBottomInputPairing() {
        UIElementNode labelNode = new UIElementNode(
                "android.widget.TextView", "lbl_phone", "Nomor Handphone", "",
                "android.widget.TextView", "//TextView[1]",
                new Rectangle(60, 300, 300, 40),
                false, true, true, false, false, 3
        );

        UIElementNode inputNode = new UIElementNode(
                "android.widget.EditText", "input_phone", "", "Masukkan nomor",
                "android.widget.EditText", "//EditText[1]",
                new Rectangle(60, 360, 800, 90),
                true, true, true, false, true, 3
        );

        List<UIElementNode> nodes = Arrays.asList(labelNode, inputNode);

        SpatialAlignmentEngine engine = new SpatialAlignmentEngine();
        Optional<UIElementNode> target = engine.findTargetByLabel("Nomor Handphone", nodes);

        assertTrue(target.isPresent());
        assertEquals("android.widget.EditText", target.get().getTag());
        assertEquals("input_phone", target.get().getResourceId());
    }
}
