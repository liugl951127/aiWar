package com.openclaw.wargame.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BenchTest {

    @Test
    void runMeasuresSimpleAction() {
        Bench.Results r = Bench.run("noop", 100, 10, () -> {
            // noop，但 JIT 不能完全消除
            int x = 0;
            for (int i = 0; i < 100; i++) x += i;
            assert x >= 0;
        });
        assertEquals("noop", r.label());
        assertEquals(100, r.iterations());
        assertTrue(r.meanNs() >= 0, "mean must be non-negative");
        assertTrue(r.minNs() <= r.meanNs());
        assertTrue(r.maxNs() >= r.minNs());
        assertTrue(r.medianNs() >= r.minNs());
    }

    @Test
    void benchStringBuilder() {
        Bench.Results r = Bench.run("stringbuilder", 50, 5, () -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) sb.append(i).append(",");
            assert sb.length() > 0;
        });
        assertTrue(r.meanNs() > 0);
    }

    @Test
    void benchFastActionShouldBeQuick() {
        // 简单算术 应该 < 1ms（10K 次）
        Bench.Results r = Bench.run("arith", 10000, 100, () -> {
            int x = 0;
            for (int i = 0; i < 10; i++) x += i;
            assert x >= 0;
        });
        // 应该纳秒级别
        assertTrue(r.meanNs() < 1_000_000, "简单算术 < 1ms: " + r);
    }

    @Test
    void nanosToMs() {
        assertEquals(1.0, Bench.nanosToMs(1_000_000), 1e-9);
        assertEquals(0.001, Bench.nanosToMs(1_000), 1e-9);
    }

    @Test
    void resultsToString() {
        Bench.Results r = new Bench.Results("test", 10, 1000, 1100, 500, 2000, 250.5);
        String s = r.toString();
        assertTrue(s.contains("test"));
        assertTrue(s.contains("n=10"));
        assertTrue(s.contains("0.001")); // 1000ns = 0.001 ms
    }
}