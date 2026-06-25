package com.openclaw.wargame.weapon;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.weapon.Weapon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 火控计算机：把"己方单位"和"敌方目标"做最优配对，给出开火建议。
 * <p>
 * 配对策略：成本 = -火力优势（高伤目标优先），优先低距离、武器射程覆盖、命中率高的配对。
 * 使用 Hungarian 算法的简化版本：贪心 + 排序（对中小规模可行）。
 */
public final class FireControlComputer {
    private static final Logger log = LoggerFactory.getLogger(FireControlComputer.class);

    private final RulesOfEngagement roe;

    public FireControlComputer(RulesOfEngagement roe) {
        this.roe = roe;
    }

    public RulesOfEngagement roe() { return roe; }

    /**
     * 计算本 tick 内的所有开火方案。
     */
    public List<Engagement> compute(BattleState state, Team self) {
        List<Unit> friendlies = new ArrayList<>();
        for (Unit u : state.units()) {
            if (u.team() == self && u.isAlive() && !u.weapons().isEmpty()) {
                friendlies.add(u);
            }
        }
        List<Unit> enemies = new ArrayList<>();
        for (Unit u : state.units()) {
            if (u.team() != self && u.team() != Team.NEUTRAL && u.isAlive()) {
                enemies.add(u);
            }
        }
        List<Engagement> out = new ArrayList<>();
        // 简单贪心：每个射手独立选最高收益目标
        for (Unit shooter : friendlies) {
            if (shooter.status() == com.openclaw.wargame.core.unit.UnitStatus.DESTROYED) continue;
            Engagement best = pickBest(shooter, enemies, false);
            if (best != null) out.add(best);
        }
        return out;
    }

    private Engagement pickBest(Unit shooter, List<Unit> enemies, boolean targetHasFiredAtUs) {
        Engagement best = null;
        double bestScore = -1;
        for (Unit enemy : enemies) {
            double dist = shooter.position().distanceTo(enemy.position());
            if (!roe.canEngage(dist, targetHasFiredAtUs)) continue;
            Weapon weapon = shooter.selectWeaponFor(dist);
            if (weapon == null) continue;
            double accuracy = computeAccuracy(shooter, weapon, enemy, dist);
            double expected = accuracy * weapon.type().damage();
            double score = expected / Math.max(dist / 1000.0, 0.1);
            if (score > bestScore) {
                bestScore = score;
                best = new Engagement(shooter, weapon, enemy, dist, accuracy, expected);
            }
        }
        return best;
    }

    /**
     * 计算命中率：基础精度 × 距离衰减 × 生命衰减（残血目标更易命中）× 射击方血量影响。
     */
    public double computeAccuracy(Unit shooter, Weapon weapon, Unit target, double distance) {
        double base = weapon.type().accuracy();
        double rangeFactor = weapon.type().rangeMeters() / Math.max(distance, 1.0);
        rangeFactor = Math.min(1.0, rangeFactor);
        double shooterFactor = 0.7 + 0.3 * shooter.hpRatio();
        double targetFactor = 0.7 + 0.3 * target.hpRatio();
        return Math.max(0, Math.min(0.99, base * rangeFactor * shooterFactor * targetFactor));
    }

    /**
     * 按阵地划分火力：把多个射手集中在同一目标上（饱和攻击）。
     */
    public Map<Unit, List<Engagement>> saturatedFire(BattleState state, Team self, int maxConcentration) {
        List<Engagement> all = compute(state, self);
        Map<Unit, List<Engagement>> grouped = new HashMap<>();
        for (Engagement e : all) {
            grouped.computeIfAbsent(e.target(), k -> new ArrayList<>()).add(e);
        }
        // 每个目标最多保留 maxConcentration 个射手
        Map<Unit, List<Engagement>> limited = new HashMap<>();
        grouped.forEach((target, list) -> {
            list.sort((a, b) -> Double.compare(b.expectedDamage(), a.expectedDamage()));
            limited.put(target, list.subList(0, Math.min(list.size(), maxConcentration)));
        });
        return limited;
    }
}
