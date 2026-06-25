package com.openclaw.wargame.core.unit;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.weapon.Weapon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 战场单位。一个单位包含：
 * <ul>
 *   <li>唯一 ID + 阵营 + 类型</li>
 *   <li>当前位置</li>
 *   <li>当前生命值（基于 armor）</li>
 *   <li>挂载的武器列表</li>
 *   <li>行为状态（IDLE / MOVING / ENGAGING / RETREATING / DESTROYED）</li>
 * </ul>
 * Unit 不是线程安全的，使用方需要在单一线程（仿真主循环）中操作。
 */
public final class Unit {
    private final String id;
    private final UnitType type;
    private final Team team;
    private final double baseMaxHp;
    private final double baseSpeed;
    private final double baseDetectionRange;
    private final double baseFirepower;

    private Position position;
    private double hp;
    private final List<Weapon> weapons;
    private UnitStatus status;
    /** 当前移动目标（null 表示静止） */
    private Position moveTarget;
    /** 当前 Buff 列表（按 tick 衰减） */
    private final List<Buff> buffs;

    public Unit(String id, UnitType type, Team team, Position position) {
        this.id = Objects.requireNonNull(id);
        this.type = Objects.requireNonNull(type);
        this.team = Objects.requireNonNull(team);
        this.position = Objects.requireNonNull(position);
        this.baseMaxHp = type.baseArmor();
        this.hp = baseMaxHp;
        this.baseSpeed = type.baseSpeed();
        this.baseDetectionRange = type.baseDetectionRange();
        this.baseFirepower = type.baseFirepower();
        this.weapons = new ArrayList<>();
        this.status = UnitStatus.IDLE;
        this.buffs = new ArrayList<>();
    }

    public static Unit create(UnitType type, Team team, Position position) {
        return new Unit(UUID.randomUUID().toString(), type, team, position);
    }

    public String id() {
        return id;
    }

    public UnitType type() {
        return type;
    }

    public Team team() {
        return team;
    }

    public Position position() {
        return position;
    }

    public double hp() {
        return hp;
    }

    public double maxHp() {
        return baseMaxHp * (1 + totalBuffMultiplier(Buff.Kind.ARMOR));
    }

    /** 原始（未加 buff 的）maxHp，用于内部同步 */
    public double baseMaxHp() { return baseMaxHp; }

    public double hpRatio() {
        return hp / maxHp();
    }

    public double baseSpeed() {
        return baseSpeed;
    }

    /** 当前实际速度（含 buff） */
    public double effectiveSpeed() {
        return baseSpeed * (1 + totalBuffMultiplier(Buff.Kind.SPEED));
    }

    public double baseDetectionRange() {
        return baseDetectionRange;
    }

    /** 当前实际探测范围（含 buff） */
    public double effectiveDetectionRange() {
        return baseDetectionRange * (1 + totalBuffMultiplier(Buff.Kind.DETECTION));
    }

    /** 当前实际火力（含 buff） */
    public double effectiveFirepower() {
        return baseFirepower * (1 + totalBuffMultiplier(Buff.Kind.FIREPOWER));
    }

    /** 伪装（stealth）buff 总和：值越大越难被探测到 */
    public double stealthFactor() {
        return totalBuffMultiplier(Buff.Kind.STEALTH);
    }

    /** 压制（suppression）buff 总和：值越大越影响敌方命中率 */
    public double suppressionFactor() {
        return totalBuffMultiplier(Buff.Kind.SUPPRESSION);
    }

    private double totalBuffMultiplier(Buff.Kind kind) {
        double total = 0;
        for (Buff b : buffs) {
            if (b.kind() == kind) total += b.multiplier();
        }
        return total;
    }

    public UnitStatus status() {
        return status;
    }

    public Position moveTarget() {
        return moveTarget;
    }

    public List<Weapon> weapons() {
        return Collections.unmodifiableList(weapons);
    }

    public List<Buff> buffs() {
        return Collections.unmodifiableList(buffs);
    }

    /** 加一个 Buff */
    public void applyBuff(Buff buff) {
        buffs.add(Objects.requireNonNull(buff));
    }

    /** 推进一个 tick：buff 衰减 */
    public void tickBuffs() {
        buffs.removeIf(b -> !b.tick());
    }

    public boolean isAlive() {
        return status != UnitStatus.DESTROYED;
    }

    public boolean isEngaging() {
        return status == UnitStatus.ENGAGING;
    }

    /**
     * 挂载武器。
     */
    public void mountWeapon(Weapon weapon) {
        if (weapons.size() >= type.baseWeaponSlots()) {
            throw new IllegalStateException("no free weapon slot for " + type);
        }
        weapons.add(weapon);
    }

    /**
     * 选择一把当前能开火、且射程覆盖目标距离的武器。
     */
    public Weapon selectWeaponFor(double distanceMeters) {
        Weapon best = null;
        double bestDamage = -1;
        for (Weapon w : weapons) {
            if (!w.canFire()) continue;
            if (w.type().rangeMeters() < distanceMeters) continue;
            if (w.type().damage() > bestDamage) {
                best = w;
                bestDamage = w.type().damage();
            }
        }
        return best;
    }

    public void setMoveTarget(Position target) {
        this.moveTarget = target;
        if (target != null) {
            this.status = UnitStatus.MOVING;
        }
    }

    public void clearMoveTarget() {
        this.moveTarget = null;
        if (status == UnitStatus.MOVING) {
            status = UnitStatus.IDLE;
        }
    }

    public void enterEngagement() {
        if (isAlive()) status = UnitStatus.ENGAGING;
    }

    public void retreat() {
        if (isAlive()) status = UnitStatus.RETREATING;
    }

    /** 内部使用：仿真拷贝时直接设置 status（不走转换） */
    public void setStatusInternal(UnitStatus s) {
        this.status = s;
    }

    /** 内部使用：直接设置 position（不走 moveTowards 逻辑） */
    public void setPositionInternal(Position p) {
        this.position = p;
    }

    /**
     * 受到伤害。
     */
    public double takeDamage(double amount) {
        if (!isAlive()) return 0;
        double actual = Math.min(amount, hp);
        hp -= actual;
        if (hp <= 0) {
            hp = 0;
            status = UnitStatus.DESTROYED;
            moveTarget = null;
        }
        return actual;
    }

    /**
     * 修复 / 加血（基地补给）。
     */
    public void repair(double amount) {
        if (!isAlive()) return;
        hp = Math.min(maxHp(), hp + amount);
        if (status == UnitStatus.DESTROYED) {
            status = UnitStatus.IDLE;
        }
    }

    /**
     * 推进位置（按时间步 dtSeconds，应用地形机动系数）。
     */
    public void advance(double dtSeconds, double movementFactor) {
        if (!isAlive() || moveTarget == null) return;
        double distance = baseSpeed * dtSeconds * movementFactor;
        Position next = position.moveTowards(moveTarget, distance);
        if (next.distanceTo(moveTarget) < 0.5) {
            position = moveTarget;
            moveTarget = null;
            status = UnitStatus.IDLE;
        } else {
            position = next;
        }
    }
}
