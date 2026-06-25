package com.openclaw.wargame.realtime;

import com.openclaw.wargame.core.team.Team;

import java.util.UUID;

/**
 * 战场事件基类。所有事件都包含 tick + timeSeconds + id（去重用）。
 */
public abstract class BattleEvent {
    private final String id;
    private final long tick;
    private final double timeSeconds;

    protected BattleEvent(long tick, double timeSeconds) {
        this.id = UUID.randomUUID().toString();
        this.tick = tick;
        this.timeSeconds = timeSeconds;
    }

    public String id() { return id; }
    public long tick() { return tick; }
    public double timeSeconds() { return timeSeconds; }

    public abstract EventKind kind();

    /** 事件涉及的阵营（攻击者 / 防守者），用于过滤。 */
    public abstract Team[] involvedTeams();

    public enum EventKind {
        DETECTION,        // 探测到敌方
        ENGAGEMENT_START, // 开始交火
        HIT,              // 命中
        KILL,             // 击毁
        UNIT_MOVED,       // 单位位置更新
        WEAPON_FIRED,     // 武器开火
        THREAT_ALERT,     // 威胁告警
        DECISION_MADE,    // 决策下达
        STATUS_CHANGE     // 状态变化
    }
}
