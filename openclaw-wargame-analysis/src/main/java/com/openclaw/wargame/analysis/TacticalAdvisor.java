package com.openclaw.wargame.analysis;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.unit.Buff;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitStatus;
import com.openclaw.wargame.core.weapon.Weapon;
import com.openclaw.wargame.core.weapon.WeaponType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 战术顾问（TacticalAdvisor）—— 持续为指挥官提供"建议 + Buff 应用"。
 * <p>
 * 输入：BattleState + perspective（己方阵营）
 * 输出：
 * <ul>
 *   <li>{@link Advice} —— 高层建议（进攻 / 防御 / 后撤 / 集火 / 重新部署 / 静默）</li>
 *   <li>{@link BuffAssignment} —— 给具体单位 Buff（火力 +、速度 +、探测 + 等）</li>
 * </ul>
 * <p>
 * 设计为"无副作用"的纯函数式调用。AutonomyLoop 每个 tick 调用 advise()，
 * 然后执行 applyBuffs() 真正修改 Unit。
 */
public final class TacticalAdvisor {

    /** 战术建议 */
    public enum AdviceKind {
        ATTACK,           // 建议集中攻击
        DEFEND,           // 建议防御
        RETREAT,          // 建议后撤
        FLANK,            // 建议侧翼包抄
        RECON,            // 建议侦察
        RESUPPLY,         // 建议补给
        COORDINATE_FIRE,  // 协同火力
        STAND_DOWN,       // 静默待机
        EMERGENCY_RETREAT // 紧急后撤
    }

    /** 单条建议 */
    public record Advice(AdviceKind kind, double priority, String reason) {}

    /** Buff 派发方案 */
    public record BuffAssignment(String unitId, Buff.Kind buffKind, double multiplier, int duration) {}

    public record AdvisoryReport(
            BattleAdvantage advantage,
            List<Advice> advices,
            List<BuffAssignment> buffAssignments
    ) {}

    private final Team perspective;

    public TacticalAdvisor(Team perspective) {
        this.perspective = Objects.requireNonNull(perspective);
    }

    public Team perspective() { return perspective; }

    /**
     * 生成建议报告（无副作用）。配合 applyBuffs() 真正应用 Buff。
     */
    public AdvisoryReport advise(BattleState state) {
        BattleAdvantage adv = BattleAdvantage.compute(state, perspective);
        List<Advice> advices = generateAdvices(state, adv);
        List<BuffAssignment> buffs = generateBuffs(state, adv, advices);
        return new AdvisoryReport(adv, advices, buffs);
    }

    /**
     * 应用 BuffAssignment 到 BattleState 中的单位（修改 in-place）。
     * 返回应用成功的数量。
     */
    public static int applyBuffs(BattleState state, List<BuffAssignment> assignments) {
        int applied = 0;
        if (assignments == null || assignments.isEmpty()) return 0;
        for (Unit u : state.units()) {
            for (BuffAssignment ba : assignments) {
                if (ba.unitId().equals(u.id())) {
                    u.applyBuff(new Buff(ba.buffKind(), ba.multiplier(), ba.duration(), "TacticalAdvisor"));
                    applied++;
                    break;
                }
            }
        }
        return applied;
    }

    /* ---- internals ---- */

    private List<Advice> generateAdvices(BattleState state, BattleAdvantage adv) {
        List<Advice> out = new ArrayList<>();

        // 1. 紧急后撤：己方接近全灭
        long selfAlive = 0, totalSelf = 0;
        for (Unit u : state.units()) {
            if (u.team() == perspective) {
                totalSelf++;
                if (u.isAlive()) selfAlive++;
            }
        }
        if (totalSelf > 0 && selfAlive / (double) totalSelf < 0.3) {
            out.add(new Advice(AdviceKind.EMERGENCY_RETREAT, 100,
                    "Self losses > 70%, emergency retreat recommended"));
        }

        // 2. 协同火力：火力优势压倒时建议集中火力
        if (adv.firepower() > 0.7) {
            out.add(new Advice(AdviceKind.COORDINATE_FIRE, 80,
                    String.format("Firepower dominance %.0f%% -> concentrate fire", adv.firepower() * 100)));
        }

        // 3. 攻击：综合优势 > 60% 且存活比例高
        if (adv.overall() > 0.6 && adv.manpower() > 0.5) {
            out.add(new Advice(AdviceKind.ATTACK, 70,
                    String.format("Overall advantage %.0f%% -> push attack", adv.overall() * 100)));
        }

        // 4. 侧翼：机动优势大时
        if (adv.mobility() > 0.65 && adv.firepower() < 0.55) {
            out.add(new Advice(AdviceKind.FLANK, 65,
                    "Mobility advantage but firepower weak -> flank maneuver"));
        }

        // 5. 防御：综合劣势
        if (adv.overall() < 0.4) {
            out.add(new Advice(AdviceKind.DEFEND, 60,
                    String.format("Overall weakness %.0f%% -> defensive posture", (1 - adv.overall()) * 100)));
        }

        // 6. 补给：弹药不足
        int lowAmmo = 0;
        for (Unit u : state.units()) {
            if (u.team() != perspective || !u.isAlive()) continue;
            for (Weapon w : u.weapons()) {
                if (w.ammo() < w.maxAmmo() * 0.2) lowAmmo++;
            }
        }
        if (lowAmmo > 0) {
            out.add(new Advice(AdviceKind.RESUPPLY, 55,
                    lowAmmo + " weapons low on ammo"));
        }

        // 7. 静默：如果什么都不紧急
        if (out.isEmpty()) {
            out.add(new Advice(AdviceKind.STAND_DOWN, 30, "No critical actions needed"));
        }

        return out;
    }

    private List<BuffAssignment> generateBuffs(BattleState state, BattleAdvantage adv, List<Advice> advices) {
        List<BuffAssignment> buffs = new ArrayList<>();

        boolean needFirepower = advices.stream().anyMatch(a -> a.kind() == AdviceKind.ATTACK || a.kind() == AdviceKind.COORDINATE_FIRE);
        boolean needMobility = advices.stream().anyMatch(a -> a.kind() == AdviceKind.FLANK || a.kind() == AdviceKind.RETREAT);
        boolean needDefense = advices.stream().anyMatch(a -> a.kind() == AdviceKind.DEFEND);

        // 给高血量单位加火力 buff（持续 8 tick）
        if (needFirepower) {
            for (Unit u : state.units()) {
                if (u.team() != perspective || !u.isAlive()) continue;
                if (u.hpRatio() > 0.6 && u.effectiveFirepower() > 1) {
                    buffs.add(new BuffAssignment(u.id(), Buff.Kind.FIREPOWER, 0.3, 8));
                }
            }
        }

        // 给速度型单位加速
        if (needMobility) {
            for (Unit u : state.units()) {
                if (u.team() != perspective || !u.isAlive()) continue;
                if (u.baseSpeed() >= 5 && u.hpRatio() > 0.4) {
                    buffs.add(new BuffAssignment(u.id(), Buff.Kind.SPEED, 0.4, 6));
                }
            }
        }

        // 防御：给前排装甲
        if (needDefense) {
            for (Unit u : state.units()) {
                if (u.team() != perspective || !u.isAlive()) continue;
                if (u.type() == com.openclaw.wargame.core.unit.UnitType.ARMOR
                        || u.type() == com.openclaw.wargame.core.unit.UnitType.COMMAND) {
                    buffs.add(new BuffAssignment(u.id(), Buff.Kind.ARMOR, 0.5, 10));
                }
            }
        }

        // 总是给侦察单位一个探测 buff（看清战场）
        for (Unit u : state.units()) {
            if (u.team() != perspective || !u.isAlive()) continue;
            if (u.type() == com.openclaw.wargame.core.unit.UnitType.RECON
                    || u.type() == com.openclaw.wargame.core.unit.UnitType.DRONE) {
                buffs.add(new BuffAssignment(u.id(), Buff.Kind.DETECTION, 0.5, 12));
            }
        }

        return buffs;
    }

    /**
     * 找一个空位推荐重新部署（供 RESUPPLY / RETREAT 等使用）。
     */
    public Position suggestRetreatPosition(BattleState state, Team team) {
        double sx = 0, sy = 0; int n = 0;
        for (Unit u : state.units()) {
            if (u.team() == team && u.isAlive()) {
                sx += u.position().x();
                sy += u.position().y();
                n++;
            }
        }
        if (n == 0) return new Position(0, 0);
        return new Position(sx / n, sy / n);
    }
}
