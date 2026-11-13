package com.qureon.locara.core.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ContextualEntityResolverTest {

    private final ContextualEntityResolver resolver = new ContextualEntityResolver();

    @Test
    @DisplayName("Should autonomously resolve active bank account card and holder from Android dashboard XML")
    public void testResolveActiveAccountFromDashboard() {
        String dashboardXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <hierarchy rotation="0">
                  <android.widget.FrameLayout bounds="[0,0][1080,2400]">
                    <android.widget.LinearLayout bounds="[0,0][1080,2400]">
                      <android.widget.TextView text="Halo, Rio Griya" bounds="[48,120][600,180]" />
                      <android.widget.HorizontalScrollView bounds="[48,320][1032,800]">
                        <android.view.ViewGroup bounds="[48,320][1032,800]">
                          <android.widget.TextView text="Tabungan Mega Dana" bounds="[80,360][500,420]" />
                          <android.widget.TextView text="010740021025853" bounds="[600,360][1000,420]" />
                          <android.widget.TextView text="••••••••" bounds="[80,480][300,540]" />
                          <android.view.ViewGroup bounds="[320,480][380,540]" clickable="true" content-desc="Lihat Saldo" />
                        </android.view.ViewGroup>
                      </android.widget.HorizontalScrollView>
                    </android.widget.LinearLayout>
                  </android.widget.FrameLayout>
                </hierarchy>
                """;

        ResolvedAccount account = resolver.resolveActiveAccount(dashboardXml);

        assertNotNull(account, "Resolved account should not be null");
        assertTrue(account.isValid(), "Account should be marked valid (10-16 digits)");
        assertEquals("010740021025853", account.getAccountNumber());
        assertEquals("Rio Griya", account.getHolderName());
        assertTrue(account.getAccountType().contains("Tabungan Mega Dana"));
        assertTrue(account.isMaskedBalancePresent(), "Masked balance should be detected");
    }

    @Test
    @DisplayName("Should extract spaced account numbers and sanitize them to pure digits")
    public void testResolveSpacedAccountNumber() {
        String xml = """
                <hierarchy>
                  <android.widget.TextView text="0107 4002 1025 853" bounds="[100,200][500,260]" />
                  <android.widget.TextView text="Mega Maxi" bounds="[100,120][400,180]" />
                </hierarchy>
                """;

        ResolvedAccount account = resolver.resolveActiveAccount(xml);

        assertNotNull(account);
        assertEquals("010740021025853", account.getAccountNumber());
        assertEquals("Mega Maxi", account.getAccountType());
    }

    @Test
    @DisplayName("Should return null safely on empty or invalid XML")
    public void testInvalidXmlReturnsNull() {
        assertNull(resolver.resolveActiveAccount((String) null));
        assertNull(resolver.resolveActiveAccount("   "));
        assertNull(resolver.resolveActiveAccount("<empty></empty>"));
    }

    @Test
    @DisplayName("Should resolve specific account card matching targetAccountNumber in multi-account dashboard")
    public void testResolveSpecificTargetAccount() {
        String multiCardXml = """
                <hierarchy>
                  <android.widget.TextView text="Halo, Rio Griya" />
                  <android.view.ViewGroup resource-id="my_account_savings_0">
                    <android.widget.TextView resource-id="my_account_savings_0_title" text="Mega Dana" />
                    <android.widget.TextView resource-id="my_account_savings_0_number" text="010740021025853" />
                  </android.view.ViewGroup>
                  <android.view.ViewGroup resource-id="my_account_savings_1">
                    <android.widget.TextView resource-id="my_account_savings_1_title" text="Mega Berbagi" />
                    <android.widget.TextView resource-id="my_account_savings_1_number" text="010740099999999" />
                  </android.view.ViewGroup>
                </hierarchy>
                """;

        ResolvedAccount acc0 = resolver.resolveAccountByNumber(multiCardXml, "010740021025853");
        assertNotNull(acc0);
        assertEquals("010740021025853", acc0.getAccountNumber());
        assertTrue(acc0.getAccountType().contains("Mega Dana"));

        ResolvedAccount acc1 = resolver.resolveAccountByNumber(multiCardXml, "010740099999999");
        assertNotNull(acc1);
        assertEquals("010740099999999", acc1.getAccountNumber());
        assertTrue(acc1.getAccountType().contains("Mega Berbagi"));
    }

    @Test
    @DisplayName("Should synthesize safe card tap XPath avoiding copy button for KMP and React Native")
    public void testResolveSafeCardTapXPath() {
        String kmpXml = """
                <hierarchy>
                  <android.view.ViewGroup resource-id="my_account_savings_0">
                    <android.view.ViewGroup resource-id="my_account_savings_0_art" />
                    <android.widget.TextView resource-id="my_account_savings_0_title" text="Mega Dana" />
                    <android.widget.TextView resource-id="my_account_savings_0_number" text="010740021025853" />
                    <android.view.ViewGroup resource-id="my_account_savings_0_copy_button" />
                  </android.view.ViewGroup>
                </hierarchy>
                """;

        String kmpXpath = resolver.resolveSafeCardTapXPath(kmpXml, "010740021025853");
        assertNotNull(kmpXpath);
        assertTrue(kmpXpath.contains("my_account_savings_0_art"));
        assertFalse(kmpXpath.contains("copy_button"));

        String rnXml = """
                <hierarchy>
                  <android.view.ViewGroup clickable="true" enabled="true">
                    <android.widget.ImageView />
                    <android.widget.TextView text="0107 4002 1025 853" />
                  </android.view.ViewGroup>
                </hierarchy>
                """;

        String rnXpath = resolver.resolveSafeCardTapXPath(rnXml, "010740021025853");
        assertNotNull(rnXpath);
        assertTrue(rnXpath.contains("010740021025853"));
        assertTrue(rnXpath.contains("ImageView"));
    }
}
