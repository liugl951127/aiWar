package com.openclaw.wargame.core.terrain;

import com.openclaw.wargame.core.coord.Position;

/**
 * 地形类型枚举。不同地形对单位机动、探测、火力都有修正。
 */
public enum Terrain {
    PLAIN("平原", 1.00, 1.00, 1.00),
    FOREST("森林", 0.60, 0.70, 0.85),
    URBAN("城市", 0.50, 0.80, 1.20),
    MOUNTAIN("山地", 0.30, 0.60, 0.70),
    WATER("水域", 0.00, 0.50, 0.00),
    DESERT("沙漠", 0.85, 1.10, 1.00),
    SWAMP("沼泽", 0.40, 0.50, 0.60);

    private final String displayName;
    /** 机动系数（< 1 表示通行变慢） */
    private final double movementFactor;
    /** 探测系数（< 1 表示更难被发现） */
    private final double detectionFactor;
    /** 火力系数（< 1 表示火力削弱） */
    private final double firepowerFactor;

    Terrain(String displayName, double movementFactor, double detectionFactor, double firepowerFactor) {
        this.displayName = displayName;
        this.movementFactor = movementFactor;
        this.detectionFactor = detectionFactor;
        this.firepowerFactor = firepowerFactor;
    }

    public String displayName() {
        return displayName;
    }

    public double movementFactor() {
        return movementFactor;
    }

    public double detectionFactor() {
        return detectionFactor;
    }

    public double firepowerFactor() {
        return firepowerFactor;
    }

    /**
     * 判断单位是否能进入该地形（水域一般装甲无法进入）。
     */
    public boolean canEnter(com.openclaw.wargame.core.unit.UnitType type) {
        if (this == WATER) {
            return type == com.openclaw.wargame.core.unit.UnitType.NAVY;
        }
        return true;
    }
}
