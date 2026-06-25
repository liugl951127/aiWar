package com.openclaw.wargame.ai.mcts;

import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

/**
 * 蒙特卡洛树搜索（MCTS）—— 在战术推演中评估决策分支。
 * <p>
 * 算法：
 * <ol>
 *   <li>Selection：从根节点沿 UCB1 选择到叶节点</li>
 *   <li>Expansion：随机展开一个动作分支</li>
 *   <li>Simulation：随机 rollout 直到 horizonSteps</li>
 *   <li>Backprop：把结果回填到路径上</li>
 * </ol>
 * 输出：访问次数最多的子节点代表的最佳动作序列。
 */
public final class MonteCarloTreeSearch {
    /** 仿真 step 回调（由 autonomy 注入，避免 AI 直接依赖 simulation 模块）。 */
    public interface SimulationFn {
        BattleState stepOne(BattleState state, String preferredUnitId);
        BattleState randomStep(BattleState state, Random rng);
    }

    private final SimulationFn simulator;
    private final Team perspective;
    private final int iterations;
    private final int horizonSteps;
    private final Random random;
    private final double explorationC = Math.sqrt(2);

    private final AtomicLong totalSimulations = new AtomicLong();

    public MonteCarloTreeSearch(SimulationFn simulator, Team perspective,
                                int iterations, int horizonSteps, long seed) {
        this.simulator = simulator;
        this.perspective = perspective;
        this.iterations = iterations;
        this.horizonSteps = horizonSteps;
        this.random = new Random(seed);
    }

    public Team perspective() { return perspective; }
    public int iterations() { return iterations; }
    public long totalSimulations() { return totalSimulations.get(); }

    /**
     * 对当前态势执行 MCTS，返回最佳首步动作。
     */
    public MCTSResult search(BattleState root) {
        MCTSNode rootNode = new MCTSNode(root, null, null);
        for (int i = 0; i < iterations; i++) {
            MCTSNode selected = select(rootNode);
            MCTSNode expanded = selected.isLeaf() ? expand(selected) : selected;
            double reward = simulate(expanded);
            backpropagate(expanded, reward);
            totalSimulations.incrementAndGet();
        }
        MCTSNode best = rootNode.bestByVisits();
        return new MCTSResult(
                best.moveFromParent(),
                best.visits(),
                best.wins(),
                best.value(),
                rootNode.children().size()
        );
    }

    /** 选择：UCB1 */
    private MCTSNode select(MCTSNode node) {
        while (!node.isLeaf()) {
            MCTSNode best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (MCTSNode c : node.children()) {
                double ucb;
                if (c.visits() == 0) {
                    ucb = Double.POSITIVE_INFINITY;
                } else {
                    ucb = c.value() + explorationC * Math.sqrt(Math.log(node.visits()) / c.visits());
                }
                if (ucb > bestScore) {
                    bestScore = ucb;
                    best = c;
                }
            }
            if (best == null) return node;
            node = best;
        }
        return node;
    }

    /** 展开：随机生成一个首步动作（此处为占位实现，用 simulator 的 stepAction） */
    private MCTSNode expand(MCTSNode node) {
        List<String> candidates = new ArrayList<>();
        for (var u : node.state().units()) {
            if (u.team() == perspective && u.isAlive()) {
                candidates.add(u.id());
            }
        }
        if (candidates.isEmpty()) return node;
        String uid = candidates.get(random.nextInt(candidates.size()));
        BattleState next = simulator.stepOne(node.state(), uid);
        return node.addChild(next, uid);
    }

    /** 模拟：随机 rollout 几步后评估态势 */
    private double simulate(MCTSNode node) {
        BattleState cur = node.state();
        for (int i = 0; i < horizonSteps; i++) {
            cur = simulator.randomStep(cur, random);
        }
        return evaluate(cur);
    }

    /** 评估：己方火力优势 + 存活单位差 */
    private double evaluate(BattleState state) {
        Team enemy = perspective == Team.BLUE ? Team.RED : Team.BLUE;
        long aliveSelf = state.aliveCount(perspective);
        long aliveEnemy = state.aliveCount(enemy);
        double ratio = state.firepowerRatio(perspective, enemy);
        double ratioNorm = Math.min(ratio, 5.0) / 5.0;
        double countNorm = (aliveSelf - aliveEnemy) / 10.0;
        return ratioNorm + countNorm;
    }

    private void backpropagate(MCTSNode node, double reward) {
        while (node != null) {
            node.recordVisit(reward);
            node = node.parent();
        }
    }

    public record MCTSResult(String firstAction, long visits, double wins, double value, int childrenCount) {}
}
