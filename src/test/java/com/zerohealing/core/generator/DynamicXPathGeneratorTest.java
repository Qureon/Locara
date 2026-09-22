package com.zerohealing.core.generator;

import com.zerohealing.core.model.UIElementNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DynamicXPathGeneratorTest {

    private DynamicXPathGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new DynamicXPathGenerator();
    }

    @Test
    @DisplayName("Should generate React Native parent container ascension XPath for TAP action on non-clickable TextView")
    void testReactNativeParentContainerAscensionForTap() {
        // Simulating CButton.tsx:
        // TouchableOpacity (ViewGroup, clickable=true)
        //   -> View (ViewGroup, clickable=false)
        //     -> Text (TextView, text="Lanjut", clickable=false)

        UIElementNode touchable = new UIElementNode(
                "android.view.ViewGroup", "", "", "", "android.view.ViewGroup",
                "//hierarchy/android.widget.FrameLayout/android.view.ViewGroup[1]",
                new Rectangle(48, 1500, 984, 150),
                true, true, true, false, false, 2
        );

        UIElementNode innerView = new UIElementNode(
                "android.view.ViewGroup", "", "", "", "android.view.ViewGroup",
                "//hierarchy/android.widget.FrameLayout/android.view.ViewGroup[1]/android.view.ViewGroup[1]",
                new Rectangle(48, 1500, 984, 150),
                false, true, true, false, false, 3
        );
        touchable.addChild(innerView);

        UIElementNode textNode = new UIElementNode(
                "android.widget.TextView", "", "Lanjut", "", "android.widget.TextView",
                "//hierarchy/android.widget.FrameLayout/android.view.ViewGroup[1]/android.view.ViewGroup[1]/android.widget.TextView[1]",
                new Rectangle(450, 1550, 180, 50),
                false, true, true, false, false, 4
        );
        innerView.addChild(textNode);

        List<UIElementNode> allNodes = new ArrayList<>();
        allNodes.add(touchable);
        allNodes.add(innerView);
        allNodes.add(textNode);

        // For TAP action: should ascend to clickable ViewGroup container
        String tapXPath = generator.generateOptimalXPath(textNode, allNodes, "TAP");
        assertEquals("//android.view.ViewGroup[@clickable='true' and .//android.widget.TextView[@text='Lanjut']]", tapXPath);

        // For GET_TEXT or null action: should target the TextView directly
        String textXPath = generator.generateOptimalXPath(textNode, allNodes, "GET_TEXT");
        assertEquals("//android.widget.TextView[@text='Lanjut']", textXPath);
    }
}
