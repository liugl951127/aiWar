package com.openclaw.wargame.web;

import com.openclaw.wargame.core.state.BattleState;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 共享的 BattleState 容器：仿真主循环写入，HTTP 处理器读取。
 * <p>
 * 使用 volatile + AtomicReference 保证可见性。
 */
public final class BattleStateHolder {
    private final AtomicReference<BattleState> state = new AtomicReference<>();
    private final AtomicLong lastUpdateMs = new AtomicLong(0);
    private volatile boolean running = false;
    private volatile String winner = null;
    private volatile long winnerTick = -1;

    public void set(BattleState s) {
        state.set(s);
        lastUpdateMs.set(System.currentTimeMillis());
    }

    public BattleState get() {
        return state.get();
    }

    public long lastUpdateMs() { return lastUpdateMs.get(); }
    public boolean isRunning() { return running; }
    public void setRunning(boolean r) { this.running = r; }

    public void setWinner(String w, long tick) {
        this.winner = w;
        this.winnerTick = tick;
    }

    public String winner() { return winner; }
    public long winnerTick() { return winnerTick; }
}
