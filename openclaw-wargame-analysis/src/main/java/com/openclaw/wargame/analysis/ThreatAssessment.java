package com.openclaw.wargame.analysis;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.unit.Unit;

import java.util.Objects;

/**
 * 威胁评估：针对某个敌方单位，计算"我方谁最容易被它攻击"以及"威胁值"。
 */
public final class ThreatAssessment {

    private final Unit target;
    private final double threatScore;
    private final double distance;
    private final Position bestTargetPosition;

    public ThreatAssessment(Unit target, double threatScore, double distance, Position bestTargetPosition) {
        this.target = Objects.requireNonNull(target);
        this.threatScore = threatScore;
        this.distance = distance;
        this.bestTargetPosition = bestTargetPosition;
    }

    public Unit target() { return target; }
    public double threatScore() { return threatScore; }
    public double distance() { return distance; }
    public Position bestTargetPosition() { return bestTargetPosition; }

    /**
     * 评估单个敌方单位对己方阵营的威胁。
     *
     * 威胁分 = 火力 × 生命比例 × (1 - 距离衰减)
     * 距离衰减 = min(1, 探测半径 / 实际距离)
     */
    public static ThreatAssessment assess(Unit enemy, BattleState state, Team self) {
        // 找距离敌人最近的己方单位
        Unit nearest = state.nearest(self, enemy.position());
        if (nearest == null) {
            return new ThreatAssessment(enemy, 0, Double.MAX_VALUE, enemy.position());
        }
        double distance = nearest.position().distanceTo(enemy.position());
        double firepower = enemy.type().baseFirepower();
        double hpRatio = enemy.hpRatio();
        double decay = enemy.type().baseDetectionRange() / Math.max(distance, 1.0);
        decay = Math.min(1.0, decay);
        double score = firepower * hpRatio * decay * 10.0;
        return new ThreatAssessment(enemy, score, distance, nearest.position());
    }
}
