package com.openclaw.wargame.realtime;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 战场时钟。单调递增 tick + timeSeconds。
 */
public final class BattleClock {
    private final double tickIntervalSeconds;
    private final AtomicLong tick = new AtomicLong(0);
    private volatile double currentTime = 0;

    public BattleClock(double tickIntervalSeconds) {
        if (tickIntervalSeconds <= 0) throw new IllegalArgumentException();
        this.tickIntervalSeconds = tickIntervalSeconds;
    }

    public double tickIntervalSeconds() { return tickIntervalSeconds; }
    public long currentTick() { return tick.get(); }
    public double currentTime() { return currentTime; }

    /**
     * 推进一个 tick，返回新的 tick 号。
     */
    public long advance() {
        long t = tick.incrementAndGet();
        currentTime = t * tickIntervalSeconds;
        return t;
    }
}
