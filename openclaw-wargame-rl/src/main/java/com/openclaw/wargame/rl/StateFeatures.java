package com.openclaw.wargame.rl;

import com.openclaw.wargame.analysis.SituationalAnalysis;
import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitStatus;

import java.util.Objects;

/**
 * 把 BattleState 离散化为有限的状态特征（用于 Q-Learning）。
 * <p>
 * 离散化让 Q 表可枚举（避免状态爆炸）：
 * <ul>
 *   <li>火力优势档位：{劣势, 均势, 优势, 压倒}（4 档）</li>
 *   <li>存活比例档位：{低, 中, 高}（3 档）</li>
 *   <li>最近距离档位：{远, 中, 近}（3 档）</li>
 *   <li>威胁数档位：{无, 少, 多}（3 档）</li>
 *   <li>残血单位档位：{无, 有}（2 档）</li>
 * </ul>
 * 总状态数：4 × 3 × 3 × 3 × 2 = 216
 */
public final class StateFeatures {

    public static final int FIREPOWER_BUCKETS = 4;
    public static final int ALIVE_BUCKETS = 3;
    public static final int DISTANCE_BUCKETS = 3;
    public static final int THREAT_BUCKETS = 3;
    public static final int LOWHP_BUCKETS = 2;

    private final int firepowerBucket;
    private final int aliveBucket;
    private final int distanceBucket;
    private final int threatBucket;
    private final int lowHpBucket;

    public StateFeatures(int firepowerBucket, int aliveBucket,
                         int distanceBucket, int threatBucket, int lowHpBucket) {
        this.firepowerBucket = clamp(firepowerBucket, FIREPOWER_BUCKETS);
        this.aliveBucket = clamp(aliveBucket, ALIVE_BUCKETS);
        this.distanceBucket = clamp(distanceBucket, DISTANCE_BUCKETS);
        this.threatBucket = clamp(threatBucket, THREAT_BUCKETS);
        this.lowHpBucket = clamp(lowHpBucket, LOWHP_BUCKETS);
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(max - 1, v));
    }

    public int firepowerBucket() { return firepowerBucket; }
    public int aliveBucket() { return aliveBucket; }
    public int distanceBucket() { return distanceBucket; }
    public int threatBucket() { return threatBucket; }
    public int lowHpBucket() { return lowHpBucket; }

    /**
     * 把 StateFeatures 编码成一个 long 整数（用于 HashMap key）。
     */
    public long encode() {
        long code = firepowerBucket;
        code = code * ALIVE_BUCKETS + aliveBucket;
        code = code * DISTANCE_BUCKETS + distanceBucket;
        code = code * THREAT_BUCKETS + threatBucket;
        code = code * LOWHP_BUCKETS + lowHpBucket;
        return code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StateFeatures s)) return false;
        return firepowerBucket == s.firepowerBucket
                && aliveBucket == s.aliveBucket
                && distanceBucket == s.distanceBucket
                && threatBucket == s.threatBucket
                && lowHpBucket == s.lowHpBucket;
    }

    @Override
    public int hashCode() {
        return Objects.hash(firepowerBucket, aliveBucket, distanceBucket, threatBucket, lowHpBucket);
    }

    @Override
    public String toString() {
        return String.format("FP=%d ALIVE=%d DIST=%d THREAT=%d LOWHP=%d",
                firepowerBucket, aliveBucket, distanceBucket, threatBucket, lowHpBucket);
    }

    /**
     * 从 BattleState 提取状态特征。
     */
    public static StateFeatures extract(BattleState state, Team perspective) {
        Objects.requireNonNull(state);
        Objects.requireNonNull(perspective);

        // 火力优势
        double ratio = state.firepowerRatio(perspective, perspective == Team.BLUE ? Team.RED : Team.BLUE);
        int fpBucket;
        if (Double.isInfinite(ratio) || ratio > 2.0) fpBucket = 3;       // 压倒
        else if (ratio > 1.2) fpBucket = 2;                              // 优势
        else if (ratio > 0.8) fpBucket = 1;                              // 均势
        else fpBucket = 0;                                                // 劣势

        // 存活比例
        long selfAlive = state.aliveCount(perspective);
        long totalSelf = 0;
        for (Unit u : state.units()) if (u.team() == perspective) totalSelf++;
        double aliveRatio = totalSelf == 0 ? 0 : (double) selfAlive / totalSelf;
        int aliveBucket;
        if (aliveRatio < 0.4) aliveBucket = 0;
        else if (aliveRatio < 0.75) aliveBucket = 1;
        else aliveBucket = 2;

        // 最近敌方距离
        SituationalAnalysis sa = new SituationalAnalysis(state, perspective);
        Unit nearestEnemy = sa.nearestEnemy();
        int distBucket;
        if (nearestEnemy == null) {
            distBucket = 0; // 远（无威胁）
        } else {
            // 找到己方中心
            double sx = 0, sy = 0; int n = 0;
            for (Unit u : state.units()) {
                if (u.team() == perspective && u.isAlive()) {
                    sx += u.position().x(); sy += u.position().y(); n++;
                }
            }
            Position center = n == 0 ? new Position(0, 0) : new Position(sx / n, sy / n);
            double dist = nearestEnemy.position().distanceTo(center);
            if (dist > 4000) distBucket = 0; // 远
            else if (dist > 1500) distBucket = 1; // 中
            else distBucket = 2; // 近
        }

        // 威胁数
        var threats = sa.topThreats(10);
        int threatBucket;
        if (threats.isEmpty()) threatBucket = 0;
        else if (threats.size() <= 2) threatBucket = 1;
        else threatBucket = 2;

        // 残血单位
        boolean hasLowHp = false;
        for (Unit u : state.units()) {
            if (u.team() == perspective && u.isAlive() && u.hpRatio() < 0.3) {
                hasLowHp = true; break;
            }
        }

        return new StateFeatures(fpBucket, aliveBucket, distBucket, threatBucket, hasLowHp ? 1 : 0);
    }
}
