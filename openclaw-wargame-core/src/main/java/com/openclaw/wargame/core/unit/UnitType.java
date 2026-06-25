package com.openclaw.wargame.core.unit;

/**
 * 单位类型。决定单位的基础属性（火力/装甲/机动/探测）。
 */
public enum UnitType {
    /** 步兵 */
    INFANTRY("步兵", 1.0, 1.0, 1.0, 1.0, 1),
    /** 装甲车 / 坦克 */
    ARMOR("装甲", 8.0, 10.0, 5.0, 1.5, 2),
    /** 炮兵 */
    ARTILLERY("炮兵", 20.0, 0.5, 0.5, 1.0, 1),
    /** 防空 */
    ANTI_AIR("防空", 5.0, 3.0, 3.0, 2.0, 1),
    /** 战斗机 */
    FIGHTER("战机", 3.0, 1.0, 30.0, 5.0, 8),
    /** 武装直升机 */
    ATTACK_HELI("武装直升机", 4.0, 2.0, 15.0, 4.0, 6),
    /** 海军舰艇 */
    NAVY("海军", 6.0, 8.0, 8.0, 3.0, 4),
    /** 指挥所 */
    COMMAND("指挥所", 0.0, 5.0, 0.0, 0.0, 0),
    /** 侦察 */
    RECON("侦察", 0.5, 0.5, 8.0, 6.0, 1),
    /** 无人系统（无人机/无人车） */
    DRONE("无人系统", 2.0, 1.0, 20.0, 5.0, 4);

    private final String displayName;
    /** 火力点数 */
    private final double firepower;
    /** 装甲/生命值 */
    private final double armor;
    /** 机动速度（米/秒） */
    private final double speed;
    /** 探测半径（米） */
    private final double detectionRange;
    /** 武器挂载数量 */
    private final int weaponSlots;

    UnitType(String displayName, double firepower, double armor, double speed,
             double detectionRange, int weaponSlots) {
        this.displayName = displayName;
        this.firepower = firepower;
        this.armor = armor;
        this.speed = speed;
        this.detectionRange = detectionRange;
        this.weaponSlots = weaponSlots;
    }

    public String displayName() {
        return displayName;
    }

    public double baseFirepower() {
        return firepower;
    }

    public double baseArmor() {
        return armor;
    }

    public double baseSpeed() {
        return speed;
    }

    public double baseDetectionRange() {
        return detectionRange;
    }

    public int baseWeaponSlots() {
        return weaponSlots;
    }
}
