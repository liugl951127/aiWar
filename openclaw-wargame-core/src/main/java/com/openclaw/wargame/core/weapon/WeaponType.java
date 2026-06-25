package com.openclaw.wargame.core.weapon;

/**
 * 武器类型。决定武器的射程、精度、伤害。
 */
public enum WeaponType {
    /** 小口径机炮 (200-2000m) */
    MACHINE_GUN("机炮", 2000, 0.6, 1.0, 2.0),
    /** 坦克炮 (3000m) */
    TANK_CANNON("坦克炮", 3000, 0.85, 4.0, 1.0),
    /** 反坦克导弹 (5000m) */
    ANTI_TANK_MISSILE("反坦克导弹", 5000, 0.9, 6.0, 1.0),
    /** 防空导弹 (10000m) */
    ANTI_AIR_MISSILE("防空导弹", 10000, 0.85, 5.0, 1.0),
    /** 空空导弹 (8000m) */
    AIR_TO_AIR("空空导弹", 8000, 0.8, 5.0, 1.0),
    /** 空地导弹 (6000m) */
    AIR_TO_GROUND("空地导弹", 6000, 0.85, 7.0, 1.0),
    /** 炮弹 (15000m) */
    ARTILLERY_SHELL("炮弹", 15000, 0.5, 10.0, 1.0),
    /** 火箭弹 (8000m) */
    ROCKET("火箭弹", 8000, 0.4, 8.0, 0.5),
    /** 鱼雷 (6000m) */
    TORPEDO("鱼雷", 6000, 0.7, 12.0, 0.5),
    /** 巡航导弹 (30000m) */
    CRUISE_MISSILE("巡航导弹", 30000, 0.95, 15.0, 1.0),
    /** 激光武器 (5000m) */
    LASER("激光武器", 5000, 1.0, 8.0, 4.0),
    /** 高能微波 (3000m) */
    MICROWAVE("高能微波", 3000, 0.9, 6.0, 3.0);

    private final String displayName;
    private final double rangeMeters;
    /** 0-1 精度（命中概率基准） */
    private final double accuracy;
    /** 单发伤害 */
    private final double damage;
    /** 射速（发/秒） */
    private final double rateOfFire;

    WeaponType(String displayName, double rangeMeters, double accuracy, double damage, double rateOfFire) {
        this.displayName = displayName;
        this.rangeMeters = rangeMeters;
        this.accuracy = accuracy;
        this.damage = damage;
        this.rateOfFire = rateOfFire;
    }

    public String displayName() {
        return displayName;
    }

    public double rangeMeters() {
        return rangeMeters;
    }

    public double accuracy() {
        return accuracy;
    }

    public double damage() {
        return damage;
    }

    public double rateOfFire() {
        return rateOfFire;
    }
}
