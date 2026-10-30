package com.qureon.locara.core.parser;

import com.qureon.locara.core.model.UIElementNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FieldRelationshipParserTest {

    @Test
    @DisplayName("Should extract structured key-value pairs and terminal total from UI nodes")
    void testFieldRelationshipExtraction() {
        UIElementNode lbl1 = new UIElementNode("android.widget.TextView", "", "No Pelanggan", "", "android.widget.TextView", "", new Rectangle(50, 200, 250, 40), false, true, true, false, false, 2);
        UIElementNode val1 = new UIElementNode("android.widget.TextView", "", "081234567890", "", "android.widget.TextView", "", new Rectangle(550, 200, 400, 40), false, true, true, false, false, 2);

        UIElementNode lbl2 = new UIElementNode("android.widget.TextView", "", "Nama Pelanggan", "", "android.widget.TextView", "", new Rectangle(50, 260, 250, 40), false, true, true, false, false, 2);
        UIElementNode val2 = new UIElementNode("android.widget.TextView", "", "RIO GRIYAPUTRA", "", "android.widget.TextView", "", new Rectangle(550, 260, 400, 40), false, true, true, false, false, 2);

        UIElementNode lbl3 = new UIElementNode("android.widget.TextView", "", "Total Pembayaran", "", "android.widget.TextView", "", new Rectangle(50, 320, 250, 40), false, true, true, false, false, 2);
        UIElementNode val3 = new UIElementNode("android.widget.TextView", "", "Rp 50.000", "", "android.widget.TextView", "", new Rectangle(550, 320, 400, 40), false, true, true, false, false, 2);

        List<UIElementNode> nodes = Arrays.asList(lbl1, val1, lbl2, val2, lbl3, val3);

        FieldRelationshipParser parser = new FieldRelationshipParser();
        FieldRelationshipParser.ParsedDocument doc = parser.parse(nodes);

        assertEquals(3, doc.getEntries().size());
        assertEquals("081234567890", doc.get("no_pelanggan"));
        assertEquals("RIO GRIYAPUTRA", doc.get("nama_pelanggan"));
        assertTrue(doc.getTotalEntry().isPresent());
        assertEquals("Rp 50.000", doc.getTotalEntry().get().getValue());
    }
}
