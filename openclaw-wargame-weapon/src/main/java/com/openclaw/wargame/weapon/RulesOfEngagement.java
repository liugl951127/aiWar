package com.openclaw.wargame.weapon;

/**
 * 交战规则（ROE, Rules of Engagement）—— 控制 AI 在哪些条件下可以开火。
 */
public final class RulesOfEngagement {

    public enum Mode {
        WEAPONS_HOLD,       // 禁止开火
        WEAPONS_TIGHT,      // 仅当敌方明确攻击时才还击
        WEAPONS_FREE,       // 对探测到的敌方目标可自由开火
        WEAPONS_FREE_ALL    // 包含中立目标（敌我识别有问题时）
    }

    private Mode mode = Mode.WEAPONS_FREE;
    /** 最小距离阈值（米），低于此不开火（避免误伤） */
    private double minEngagementDistance = 50;
    /** 最大交战距离 */
    private double maxEngagementDistance = 30_000;

    public Mode mode() { return mode; }
    public RulesOfEngagement setMode(Mode mode) { this.mode = mode; return this; }

    public double minEngagementDistance() { return minEngagementDistance; }
    public RulesOfEngagement setMinEngagementDistance(double d) { this.minEngagementDistance = d; return this; }

    public double maxEngagementDistance() { return maxEngagementDistance; }
    public RulesOfEngagement setMaxEngagementDistance(double d) { this.maxEngagementDistance = d; return this; }

    /**
     * 判断射手在 ROE 下是否可以攻击目标。
     */
    public boolean canEngage(double distance, boolean targetHasFiredAtUs) {
        if (mode == Mode.WEAPONS_HOLD) return false;
        if (distance < minEngagementDistance) return false;
        if (distance > maxEngagementDistance) return false;
        return switch (mode) {
            case WEAPONS_TIGHT -> targetHasFiredAtUs;
            case WEAPONS_FREE, WEAPONS_FREE_ALL -> true;
            default -> false;
        };
    }
}
