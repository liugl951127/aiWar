package com.openclaw.wargame.analysis;

import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.unit.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 态势分析器：对一份 BattleState 做整体评估，输出威胁列表、弱点位置、建议。
 */
public final class SituationalAnalysis {
    private static final Logger log = LoggerFactory.getLogger(SituationalAnalysis.class);

    private final BattleState state;
    private final Team perspective;

    public SituationalAnalysis(BattleState state, Team perspective) {
        this.state = state;
        this.perspective = perspective;
    }

    public BattleState state() { return state; }
    public Team perspective() { return perspective; }

    /**
     * 敌方单位按威胁值降序。
     */
    public List<ThreatAssessment> topThreats(int limit) {
        List<ThreatAssessment> all = new ArrayList<>();
        for (Unit u : state.units()) {
            if (!u.isAlive() || u.team() == perspective || u.team() == Team.NEUTRAL) continue;
            all.add(ThreatAssessment.assess(u, state, perspective));
        }
        all.sort(Comparator.comparingDouble(ThreatAssessment::threatScore).reversed());
        if (all.size() > limit) return all.subList(0, limit);
        return all;
    }

    /**
     * 己方火力优势指数。
     */
    public double firepowerRatio() {
        Team enemy = perspective == Team.BLUE ? Team.RED : Team.BLUE;
        return state.firepowerRatio(perspective, enemy);
    }

    /**
     * 找出己方阵形弱点：覆盖最稀疏的扇区（简单按 4 象限划分）。
     */
    public List<QuadrantCoverage> quadrantCoverage() {
        double cx = state.map().widthMeters() / 2;
        double cy = state.map().heightMeters() / 2;
        int[] count = new int[4];
        for (Unit u : state.units()) {
            if (u.team() != perspective || !u.isAlive()) continue;
            int q = quadrant(u.position().x() - cx, u.position().y() - cy);
            count[q]++;
        }
        return List.of(
                new QuadrantCoverage(Quadrant.NE, count[0]),
                new QuadrantCoverage(Quadrant.NW, count[1]),
                new QuadrantCoverage(Quadrant.SW, count[2]),
                new QuadrantCoverage(Quadrant.SE, count[3])
        );
    }

    /**
     * 最薄弱象限。
     */
    public QuadrantCoverage weakestQuadrant() {
        return quadrantCoverage().stream()
                .min(Comparator.comparingInt(QuadrantCoverage::count))
                .orElseThrow();
    }

    /**
     * 最近的敌方单位（"即时威胁"）。
     */
    public Unit nearestEnemy() {
        Unit best = null;
        double bestDist = Double.MAX_VALUE;
        for (Unit u : state.units()) {
            if (!u.isAlive() || u.team() == perspective || u.team() == Team.NEUTRAL) continue;
            // 距离己方中心
            double dToCenter = distanceToOurCenter(u);
            if (dToCenter < bestDist) {
                bestDist = dToCenter;
                best = u;
            }
        }
        return best;
    }

    private double distanceToOurCenter(Unit enemy) {
        double sx = 0, sy = 0; int n = 0;
        for (Unit u : state.units()) {
            if (u.team() != perspective || !u.isAlive()) continue;
            sx += u.position().x();
            sy += u.position().y();
            n++;
        }
        if (n == 0) return Double.MAX_VALUE;
        return enemy.position().distanceTo(new com.openclaw.wargame.core.coord.Position(sx / n, sy / n));
    }

    private static int quadrant(double dx, double dy) {
        if (dx >= 0 && dy >= 0) return 0; // NE
        if (dx < 0 && dy >= 0) return 1;  // NW
        if (dx < 0 && dy < 0) return 2;   // SW
        return 3;                          // SE
    }

    public enum Quadrant { NE, NW, SW, SE }

    public record QuadrantCoverage(Quadrant quadrant, int count) {
        public boolean isGap(int threshold) {
            return count < threshold;
        }
    }
}
