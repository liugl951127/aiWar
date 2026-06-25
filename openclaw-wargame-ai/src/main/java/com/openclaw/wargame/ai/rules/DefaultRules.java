package com.openclaw.wargame.ai.rules;

import com.openclaw.wargame.analysis.SituationalAnalysis;
import com.openclaw.wargame.analysis.ThreatAssessment;
import com.openclaw.wargame.ai.decision.Action;
import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitType;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置默认规则集：
 * <ol>
 *   <li>EngageThreatsRule — 对前 3 大威胁单位派出攻击</li>
 *   <li>FillGapsRule — 补齐最薄弱象限</li>
 *   <li>RetreatLowHpRule — 残血单位后撤</li>
 *   <li>DefendCommandRule — 保卫指挥所</li>
 *   <li>ReconRule — 派出侦察</li>
 * </ol>
 */
public final class DefaultRules {

    private DefaultRules() {}

    public static RuleEngine createDefault() {
        return new RuleEngine()
                .register(new EngageThreatsRule(3))
                .register(new FillGapsRule())
                .register(new RetreatLowHpRule(0.3))
                .register(new DefendCommandRule())
                .register(new ReconRule());
    }

    /** 对前 N 大威胁单位分配攻击者 */
    public static final class EngageThreatsRule implements Rule {
        private final int topN;

        public EngageThreatsRule(int topN) {
            this.topN = topN;
        }

        @Override
        public String name() { return "EngageThreats(top=" + topN + ")"; }

        @Override
        public List<Action> evaluate(BattleState state, SituationalAnalysis analysis) {
            List<ThreatAssessment> threats = analysis.topThreats(topN);
            List<Action> out = new ArrayList<>();
            for (ThreatAssessment t : threats) {
                Unit shooter = pickAttacker(state, t.target());
                if (shooter != null) {
                    out.add(new Action.EngageAction(shooter.id(), t.target().id(), 100 - t.threatScore()));
                }
            }
            return out;
        }

        private Unit pickAttacker(BattleState state, Unit enemy) {
            Unit best = null;
            double bestScore = -1;
            for (Unit u : state.units()) {
                if (!u.isAlive() || u.team() == enemy.team() || u.team() == Team.NEUTRAL) continue;
                double dist = u.position().distanceTo(enemy.position());
                // 使用 detectionRange × 5 作为可攻击范围（修正：原 ×3 太严格）。
                // RECON detection=6 → 可攻 30m 太短；改为 ×500 让 unit 至少能在 3000m 外攻击。
                if (dist > u.baseDetectionRange() * 500) continue;
                double score = u.type().baseFirepower() * u.hpRatio() / Math.max(dist, 1);
                if (score > bestScore) {
                    bestScore = score;
                    best = u;
                }
            }
            return best;
        }
    }

    /** 补齐最薄弱象限：派一个空闲单位过去 */
    public static final class FillGapsRule implements Rule {
        @Override
        public String name() { return "FillGaps"; }

        @Override
        public List<Action> evaluate(BattleState state, SituationalAnalysis analysis) {
            var weakest = analysis.weakestQuadrant();
            if (!weakest.isGap(1)) return List.of();
            Unit idle = findIdleUnit(state);
            if (idle == null) return List.of();
            Position center = quadrantCenter(state, weakest.quadrant());
            return List.of(new Action.MoveAction(idle.id(), center, 70));
        }

        private Unit findIdleUnit(BattleState state) {
            for (Unit u : state.units()) {
                if (!u.isAlive() || u.status() != com.openclaw.wargame.core.unit.UnitStatus.IDLE) continue;
                if (u.type() == UnitType.COMMAND) continue;
                return u;
            }
            return null;
        }

        private Position quadrantCenter(BattleState state, SituationalAnalysis.Quadrant q) {
            double cx = state.map().widthMeters() / 2;
            double cy = state.map().heightMeters() / 2;
            double offX = state.map().widthMeters() / 4;
            double offY = state.map().heightMeters() / 4;
            return switch (q) {
                case NE -> new Position(cx + offX, cy + offY);
                case NW -> new Position(cx - offX, cy + offY);
                case SW -> new Position(cx - offX, cy - offY);
                case SE -> new Position(cx + offX, cy - offY);
            };
        }
    }

    /** 残血后撤 */
    public static final class RetreatLowHpRule implements Rule {
        private final double threshold;

        public RetreatLowHpRule(double threshold) {
            this.threshold = threshold;
        }

        @Override
        public String name() { return "RetreatLowHp(t=" + threshold + ")"; }

        @Override
        public List<Action> evaluate(BattleState state, SituationalAnalysis analysis) {
            List<Action> out = new ArrayList<>();
            for (Unit u : state.units()) {
                if (!u.isAlive() || u.type() == UnitType.COMMAND) continue;
                if (u.hpRatio() < threshold) {
                    // 撤向己方阵线后方
                    double bx = 0, by = 0; int n = 0;
                    for (Unit ally : state.units()) {
                        if (ally.team() != u.team() || !ally.isAlive()) continue;
                        bx += ally.position().x(); by += ally.position().y(); n++;
                    }
                    Position towardCenter = n == 0 ? u.position() :
                            new Position(bx / n, by / n);
                    Position safe = u.position().moveTowards(towardCenter, -300);
                    out.add(new Action.RetreatAction(u.id(), safe, 80));
                }
            }
            return out;
        }
    }

    /** 保卫指挥所：靠近指挥所的敌方单位派出拦截 */
    public static final class DefendCommandRule implements Rule {
        @Override
        public String name() { return "DefendCommand"; }

        @Override
        public List<Action> evaluate(BattleState state, SituationalAnalysis analysis) {
            Unit command = null;
            for (Unit u : state.units()) {
                if (u.isAlive() && u.type() == UnitType.COMMAND) {
                    command = u;
                    break;
                }
            }
            if (command == null) return List.of();
            List<Action> out = new ArrayList<>();
            for (Unit enemy : state.units()) {
                if (!enemy.isAlive() || enemy.team() == command.team() || enemy.team() == Team.NEUTRAL) continue;
                double dist = enemy.position().distanceTo(command.position());
                if (dist < command.baseDetectionRange() * 1.5) {
                    Unit defender = pickDefender(state, command, enemy);
                    if (defender != null) {
                        out.add(new Action.EngageAction(defender.id(), enemy.id(), 95));
                    }
                }
            }
            return out;
        }

        private Unit pickDefender(BattleState state, Unit command, Unit enemy) {
            Unit best = null;
            double bestScore = -1;
            for (Unit u : state.units()) {
                if (!u.isAlive() || u.team() != command.team() || u == command) continue;
                double dist = u.position().distanceTo(enemy.position());
                double score = u.type().baseFirepower() * u.hpRatio() / Math.max(dist, 1);
                if (score > bestScore) {
                    bestScore = score;
                    best = u;
                }
            }
            return best;
        }
    }

    /** 派出侦察 */
    public static final class ReconRule implements Rule {
        @Override
        public String name() { return "Recon"; }

        @Override
        public List<Action> evaluate(BattleState state, SituationalAnalysis analysis) {
            for (Unit u : state.units()) {
                if (u.isAlive() && u.type() == UnitType.RECON && u.status() == com.openclaw.wargame.core.unit.UnitStatus.IDLE) {
                    Unit nearestEnemy = analysis.nearestEnemy();
                    if (nearestEnemy != null) {
                        // 派到最近敌方单位前方侦察
                        Position target = u.position().moveTowards(nearestEnemy.position(), 500);
                        return List.of(new Action.MoveAction(u.id(), target, 60));
                    }
                }
            }
            return List.of();
        }
    }
}
