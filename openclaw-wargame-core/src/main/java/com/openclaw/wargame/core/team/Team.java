package com.openclaw.wargame.core.team;

/**
 * 阵营枚举。每个阵营在对抗时有自己的指挥链路。
 */
public enum Team {
    BLUE("蓝方", "BLUE"),
    RED("红方", "RED"),
    NEUTRAL("中立", "NEUTRAL");

    private final String displayName;
    private final String code;

    Team(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String displayName() {
        return displayName;
    }

    public String code() {
        return code;
    }

    public boolean isHostileTo(Team other) {
        if (this == NEUTRAL || other == NEUTRAL) return false;
        return this != other;
    }
}
