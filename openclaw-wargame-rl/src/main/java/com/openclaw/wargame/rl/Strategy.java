package com.openclaw.wargame.rl;

/**
 * 高层战略动作（RL 智能体的动作空间）。
 * 每个动作会被 AutonomousCommander 翻译为多个底层 Action。
 */
public enum Strategy {
    AGGRESSIVE("aggressive", 0),   // 集火强攻
    DEFENSIVE("defensive", 1),     // 收缩防御
    FLANKING("flanking", 2),       // 侧翼包抄
    RETREAT("retreat", 3),         // 全面后撤
    RECON_FOCUS("recon", 4);       // 侦察优先

    private final String code;
    private final int index;

    Strategy(String code, int index) {
        this.code = code;
        this.index = index;
    }

    public String code() { return code; }
    public int index() { return index; }
    public static int size() { return values().length; }
}
