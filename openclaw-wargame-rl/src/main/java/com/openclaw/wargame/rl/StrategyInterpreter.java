package com.openclaw.wargame.rl;

import com.openclaw.wargame.ai.decision.Action;
import com.openclaw.wargame.ai.decision.DecisionPlan;
import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 把 RL 选择的 Strategy 翻译成具体的 Action 列表。
 * 这是 RL 智能体和 RuleEngine 之间的桥梁。
 */
public final class StrategyInterpreter {

    private final Random random;

    public StrategyInterpreter(long seed) {
        this.random = new Random(seed);
    }

    /**
     * 把 Strategy 翻译成 DecisionPlan 形式的具体 Action。
     */
    public List<Action> interpret(Strategy strategy, BattleState state, Team team) {
        List<Action> actions = new ArrayList<>();
        List<Unit> ownUnits = new ArrayList<>();
        for (Unit u : state.units()) {
            if (u.team() == team && u.isAlive() && u.status() != UnitStatus.DESTROYED) {
                ownUnits.add(u);
            }
        }
        if (ownUnits.isEmpty()) return actions;

        // 敌方单位
        List<Unit> enemies = new ArrayList<>();
        for (Unit u : state.units()) {
            if (u.team() != team && u.team() != Team.NEUTRAL && u.isAlive()) {
                enemies.add(u);
            }
        }

        switch (strategy) {
            case AGGRESSIVE -> {
                // 所有单位都进攻（确定性）
                for (Unit u : ownUnits) {
                    if (enemies.isEmpty()) break;
                    Unit target = pickNearest(u, enemies);
                    if (target != null) {
                        actions.add(new Action.MoveAction(u.id(),
                                u.position().moveTowards(target.position(), 500),
                                90));
                        actions.add(new Action.EngageAction(u.id(), target.id(), 100));
                    }
                }
            }
            case DEFENSIVE -> {
                // 全部收缩到己方中心位置 + 高血量单位断后
                double cx = 0, cy = 0; int n = 0;
                for (Unit u : ownUnits) { cx += u.position().x(); cy += u.position().y(); n++; }
                Position center = n == 0 ? new Position(0, 0) : new Position(cx / n, cy / n);
                for (Unit u : ownUnits) {
                    Position target = u.position().moveTowards(center, 300);
                    actions.add(new Action.DefendAction(u.id(), target, 80));
                }
            }
            case FLANKING -> {
                // 派快单位去侧翼，剩余防御
                for (Unit u : ownUnits) {
                    if (u.baseSpeed() >= 5.0 && enemies.size() > 0) {
                        Unit target = enemies.get(random.nextInt(enemies.size()));
                        // 偏移 30 度方向的方位
                        double angle = u.position().bearingTo(target.position()) + Math.PI / 6;
                        double r = 1000;
                        Position flank = new Position(
                                u.position().x() + r * Math.cos(angle),
                                u.position().y() + r * Math.sin(angle)
                        );
                        actions.add(new Action.MoveAction(u.id(), flank, 90));
                    } else {
                        actions.add(new Action.DefendAction(u.id(), u.position(), 50));
                    }
                }
            }
            case RETREAT -> {
                // 全部后撤
                double bx = 0, by = 0; int n = 0;
                for (Unit u : ownUnits) { bx += u.position().x(); by += u.position().y(); n++; }
                Position center = n == 0 ? new Position(0, 0) : new Position(bx / n, by / n);
                for (Unit u : ownUnits) {
                    Position safe = u.position().moveTowards(center, -500);
                    actions.add(new Action.RetreatAction(u.id(), safe, 100));
                }
            }
            case RECON_FOCUS -> {
                // 派出 RECON/无人系统侦察，其他防御
                for (Unit u : ownUnits) {
                    String tn = u.type().name();
                    if (tn.equals("RECON") || tn.equals("DRONE")) {
                        if (enemies.size() > 0) {
                            Unit target = enemies.get(random.nextInt(enemies.size()));
                            actions.add(new Action.MoveAction(u.id(),
                                    u.position().moveTowards(target.position(), 800), 100));
                        }
                    } else {
                        actions.add(new Action.DefendAction(u.id(), u.position(), 60));
                    }
                }
            }
        }
        // 单位去重：每个单位保留 priority 最高的动作
        java.util.Map<String, Action> best = new java.util.HashMap<>();
        for (Action a : actions) {
            Action prev = best.get(a.unitId());
            if (prev == null || a.priority() > prev.priority()) {
                best.put(a.unitId(), a);
            }
        }
        return new ArrayList<>(best.values());
    }

    private Unit pickNearest(Unit self, List<Unit> enemies) {
        Unit best = null;
        double bestD = Double.MAX_VALUE;
        for (Unit e : enemies) {
            double d = self.position().distanceTo(e.position());
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    /**
     * 评估当前态势的奖励。
     * <p>
     * 鼓励：消灭敌人、保存己方、保持火力优势。
     * 惩罚：己方损失、被包围。
     */
    public static double reward(BattleState prev, BattleState curr, Team team) {
        if (prev == null || curr == null) return 0;
        Team enemy = team == Team.BLUE ? Team.RED : Team.BLUE;
        long prevEnemy = prev.aliveCount(enemy);
        long currEnemy = curr.aliveCount(enemy);
        long prevSelf = prev.aliveCount(team);
        long currSelf = curr.aliveCount(team);

        double r = 0;
        r += 10.0 * (prevEnemy - currEnemy);   // 杀死敌人
        r -= 15.0 * (prevSelf - currSelf);       // 损失己方
        // 火力优势奖励
        double ratio = curr.firepowerRatio(team, enemy);
        if (!Double.isInfinite(ratio) && ratio > 0) {
            r += 0.5 * Math.min(ratio, 3.0);
        }
        return r;
    }
}
