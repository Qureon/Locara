package com.qureon.locara.core;

import com.qureon.locara.core.LocaraEngine;
import com.qureon.locara.core.model.HealConfig;
import com.qureon.locara.core.model.HealContext;
import com.qureon.locara.core.model.HealResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ParallelExecutionStressTest {

    @TempDir
    Path tempProjectDir;

    @Test
    @DisplayName("Should withstand 10 concurrent threads healing simultaneously without ledger lock contention or corruption")
    void testParallelHealingStress() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        String sampleXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<hierarchy rotation=\"0\">"
                + "  <android.widget.FrameLayout bounds=\"[0,0][1080,2400]\">"
                + "    <android.widget.Button bounds=\"[100,500][980,620]\" "
                + "                           resource-id=\"com.example:id/btn_action\" "
                + "                           text=\"Konfirmasi\" "
                + "                           clickable=\"true\" "
                + "                           enabled=\"true\" />"
                + "  </android.widget.FrameLayout>"
                + "</hierarchy>";

        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int workerId = i;
            futures.add(executor.submit(() -> {
                try {
                    startGate.await(); // wait for simultaneous release
                    HealConfig config = new HealConfig();
                    config.setKatalonProjectPath(tempProjectDir.toString());
                    config.setAuditLedgerEnabled(true);
                    config.setAutoGenerateRs(false);

                    LocaraEngine engine = new LocaraEngine(config);
                    HealContext context = HealContext.builder()
                            .targetHint("Konfirmasi")
                            .action("TAP")
                            .screenName("ParallelScreen_" + workerId)
                            .originalLocator("//android.widget.Button[@id='old_btn']")
                            .build();

                    HealResult result = engine.heal(sampleXml, context);
                    if (result.isSuccess()) {
                        successCount.incrementAndGet();
                        return true;
                    }
                    return false;
                } catch (Exception e) {
                    return false;
                } finally {
                    endGate.countDown();
                }
            }));
        }

        // Fire all threads simultaneously
        startGate.countDown();
        boolean finished = endGate.await(10, TimeUnit.SECONDS);
        assertTrue(finished, "All 10 worker threads must finish within timeout");
        assertEquals(threadCount, successCount.get(), "All 10 parallel healing executions must succeed");

        executor.shutdown();

        // Verify audit log has exactly 10 intact JSON lines
        File auditJson = new File(tempProjectDir.toFile(), "Include/evidence/locara_audit.json");
        assertTrue(auditJson.exists(), "locara_audit.json must exist");
        List<String> lines = Files.readAllLines(auditJson.toPath());
        assertEquals(threadCount, lines.size(), "Must contain exactly 10 lines for 10 threads");

        for (String line : lines) {
            assertTrue(line.startsWith("{\"id\":"), "Each line must be well-formed JSON: " + line);
            assertTrue(line.contains("\"status\":\"SUCCESS\""));
            assertTrue(line.contains("\"thread\":"));
        }
    }
}
