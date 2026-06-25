package com.openclaw.wargame.analysis;

import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.terrain.TerrainMap;
import com.openclaw.wargame.core.unit.Unit;

import java.util.Objects;

/**
 * 战场优势（BattleAdvantage）—— 多维度量化"我方 vs 敌方"的对比。
 * <p>
 * 5 个维度，每个维度独立计算 [0, 1] 范围的"优势分"：
 * <ul>
 *   <li>{@code firepower} —— 总火力（含 buff）</li>
 *   <li>{@code manpower} —— 存活单位数 / 总单位数</li>
 *   <li>{@code detection} —— 总探测范围</li>
 *   <li>{@code mobility} —— 总机动能力</li>
 *   <li>{@code cohesion} —— 阵形紧凑度（敌方越分散越高）</li>
 * </ul>
 * 综合优势 = 加权平均（可调权重）。
 */
public final class BattleAdvantage {

    private final Team perspective;
    private final double firepower;
    private final double manpower;
    private final double detection;
    private final double mobility;
    private final double cohesion;
    private final double overall;

    public BattleAdvantage(Team perspective, double firepower, double manpower,
                           double detection, double mobility, double cohesion, double overall) {
        this.perspective = perspective;
        this.firepower = firepower;
        this.manpower = manpower;
        this.detection = detection;
        this.mobility = mobility;
        this.cohesion = cohesion;
        this.overall = overall;
    }

    public Team perspective() { return perspective; }
    public double firepower() { return firepower; }
    public double manpower() { return manpower; }
    public double detection() { return detection; }
    public double mobility() { return mobility; }
    public double cohesion() { return cohesion; }
    public double overall() { return overall; }

    /**
     * 判断哪一方在某个维度上占优势。>0.55 视为优势，<0.45 视为劣势。
     */
    public boolean isDominant(double threshold) {
        return overall > threshold;
    }

    public boolean isWeaker(double threshold) {
        return overall < threshold;
    }

    /**
     * 从 BattleState 计算 self 的战场优势。
     */
    public static BattleAdvantage compute(BattleState state, Team self) {
        Objects.requireNonNull(state);
        Objects.requireNonNull(self);
        Team enemy = self == Team.BLUE ? Team.RED : Team.BLUE;

        double selfFp = 0, enemyFp = 0;
        double selfSpeed = 0, enemySpeed = 0;
        double selfDet = 0, enemyDet = 0;
        int selfTotal = 0, enemyTotal = 0;
        int selfAlive = 0, enemyAlive = 0;
        double selfHpRatioSum = 0, enemyHpRatioSum = 0;
        double selfX = 0, selfY = 0, enemyX = 0, enemyY = 0;

        for (Unit u : state.units()) {
            boolean isSelf = u.team() == self;
            boolean isEnemy = u.team() == enemy;
            if (!isSelf && !isEnemy) continue;

            double fp = u.effectiveFirepower();
            double spd = u.effectiveSpeed();
            double det = u.effectiveDetectionRange();

            if (isSelf) {
                selfTotal++;
                selfFp += fp;
                selfSpeed += spd;
                selfDet += det;
                if (u.isAlive()) {
                    selfAlive++;
                    selfHpRatioSum += u.hpRatio();
                    selfX += u.position().x();
                    selfY += u.position().y();
                }
            } else {
                enemyTotal++;
                enemyFp += fp;
                enemySpeed += spd;
                enemyDet += det;
                if (u.isAlive()) {
                    enemyAlive++;
                    enemyHpRatioSum += u.hpRatio();
                    enemyX += u.position().x();
                    enemyY += u.position().y();
                }
            }
        }

        double fpRatio = ratio(selfFp, enemyFp);
        double detRatio = ratio(selfDet, enemyDet);
        double mobRatio = ratio(selfSpeed, enemySpeed);
        double selfManpower = selfTotal == 0 ? 0 : (double) selfAlive / selfTotal;
        double selfAvgHp = selfAlive == 0 ? 0 : selfHpRatioSum / selfAlive;
        double enemyAvgHp = enemyAlive == 0 ? 0 : enemyHpRatioSum / enemyAlive;
        // manpower 综合考虑"存活数 + 平均血量"
        double manpowerRatio = (selfManpower + selfAvgHp) / 2.0;
        if (selfTotal == 0) manpowerRatio = 0.5; // 空阵营 0.5 占位
        // 阵形：己方越紧凑越好（自己中心 vs 敌人中心）
        double coh = 0.5;
        if (selfAlive > 0 && enemyAlive > 0) {
            double selfCx = selfX / selfAlive, selfCy = selfY / selfAlive;
            double enemyCx = enemyX / enemyAlive, enemyCy = enemyY / enemyAlive;
            double dist = Math.hypot(selfCx - enemyCx, selfCy - enemyCy);
            double mapDiag = Math.hypot(state.map().widthMeters(), state.map().heightMeters());
            // 距离适中最好（不太近能避免被包抄，不太远能控制战场）
            double idealDist = mapDiag * 0.3;
            double penalty = Math.abs(dist - idealDist) / mapDiag;
            coh = Math.max(0, Math.min(1, 1 - penalty));
        }

        double overall = 0.35 * fpRatio + 0.30 * manpowerRatio
                + 0.15 * detRatio + 0.10 * mobRatio + 0.10 * coh;

        return new BattleAdvantage(self, fpRatio, manpowerRatio, detRatio, mobRatio, coh, overall);
    }

    private static double ratio(double self, double enemy) {
        if (self + enemy == 0) return 0.5;
        return self / (self + enemy);
    }

    @Override
    public String toString() {
        return String.format("BattleAdvantage[fp=%.2f manpower=%.2f det=%.2f mob=%.2f coh=%.2f -> overall=%.2f]",
                firepower, manpower, detection, mobility, cohesion, overall);
    }
}
