package com.openclaw.wargame.simulation;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 轻量级微基准测试 —— 无 JMH 依赖，纯 JDK。
 * <p>
 * 用法：
 * <pre>{@code
 * Bench.Results r = Bench.run("fire-control", 1000, () -> {
 *     FireControlComputer.compute(state, ...);
 * });
 * System.out.println(r);
 * }</pre>
 */
public final class Bench {

    private Bench() {}

    public record Results(String label, int iterations,
                          long meanNs, long medianNs, long minNs, long maxNs, double stdNs) {
        @Override
        public String toString() {
            return String.format(
                    "%s: n=%d  mean=%.3f ms  median=%.3f ms  min=%.3f ms  max=%.3f ms  std=%.3f ms",
                    label, iterations,
                    nanosToMs(meanNs), nanosToMs(medianNs),
                    nanosToMs(minNs), nanosToMs(maxNs),
                    nanosToMs((long) stdNs));
        }
    }

    /** 跑 n 次（默认预热 10 次），返回统计结果 */
    public static Results run(String label, int iterations, Runnable action) {
        return run(label, iterations, 10, action);
    }

    /** 跑 n 次（带预热 warmup 次） */
    public static Results run(String label, int iterations, int warmup, Runnable action) {
        // 预热
        for (int i = 0; i < warmup; i++) action.run();
        // 测量（用 long 累加防止 JIT 完全消除）
        long[] ns = new long[iterations];
        long blackhole = 0;
        for (int i = 0; i < iterations; i++) {
            long t0 = System.nanoTime();
            action.run();
            long t1 = System.nanoTime();
            ns[i] = t1 - t0;
            blackhole += ns[i];
        }
        Arrays.sort(ns);
        long sum = 0;
        for (long x : ns) sum += x;
        // 防消除
        if (blackhole == Long.MIN_VALUE) System.out.print("");
        long mean = sum / iterations;
        long median = ns[iterations / 2];
        long min = ns[0];
        long max = ns[iterations - 1];
        double sqSum = 0;
        for (long x : ns) sqSum += (x - mean) * (x - mean);
        double std = Math.sqrt(sqSum / iterations);
        return new Results(label, iterations, mean, median, min, max, std);
    }

    public static double nanosToMs(long ns) {
        return ns / 1_000_000.0;
    }

    public static TimeUnit defaultUnit() {
        return TimeUnit.NANOSECONDS;
    }

    public static long nowNs() {
        return System.nanoTime();
    }
}