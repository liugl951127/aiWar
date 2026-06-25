package com.openclaw.wargame.rl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Q-Learning 智能体。
 * <p>
 * 算法：
 * <ul>
 *   <li>Q(s, a) ← Q(s, a) + α [r + γ · max_a' Q(s', a') − Q(s, a)]</li>
 *   <li>ε-greedy 探索：概率 ε 随机选动作</li>
 *   <li>ε 衰减：每 episode ε *= decayRate（floor 0.05）</li>
 * </ul>
 * 状态编码用 long（StateFeatures.encode），动作是 Strategy 枚举。
 * Q 表用 HashMap<stateCode, double[Strategy.size]>，零值默认。
 */
public final class QLearner {

    public static final double DEFAULT_LEARNING_RATE = 0.1;
    public static final double DEFAULT_DISCOUNT = 0.95;
    public static final double DEFAULT_EPSILON = 0.3;
    public static final double DEFAULT_EPSILON_DECAY = 0.995;
    public static final double MIN_EPSILON = 0.05;

    private final Map<Long, double[]> qTable = new HashMap<>();
    private final Random random;
    private double alpha = DEFAULT_LEARNING_RATE;
    private final double gamma;
    private double epsilon;
    private final double epsilonDecay;
    private long episodeCount = 0;
    private long updateCount = 0;

    public QLearner(double alpha, double gamma, double epsilon, double epsilonDecay, long seed) {
        this.alpha = alpha;
        this.gamma = gamma;
        this.epsilon = epsilon;
        this.epsilonDecay = epsilonDecay;
        this.random = new Random(seed);
    }

    public QLearner(long seed) {
        this(DEFAULT_LEARNING_RATE, DEFAULT_DISCOUNT, DEFAULT_EPSILON, DEFAULT_EPSILON_DECAY, seed);
    }

    public double epsilon() { return epsilon; }
    public long episodeCount() { return episodeCount; }
    public long updateCount() { return updateCount; }
    public int tableSize() { return qTable.size(); }

    /**
     * 用 ε-greedy 选择动作。
     */
    public Strategy selectAction(StateFeatures state) {
        if (random.nextDouble() < epsilon) {
            return Strategy.values()[random.nextInt(Strategy.size())];
        }
        double[] qValues = qTable.computeIfAbsent(state.encode(), k -> new double[Strategy.size()]);
        int best = 0;
        double bestV = qValues[0];
        for (int i = 1; i < Strategy.size(); i++) {
            if (qValues[i] > bestV) {
                bestV = qValues[i];
                best = i;
            }
        }
        return Strategy.values()[best];
    }

    /**
     * 纯贪心（评估/部署时使用）。
     */
    public Strategy bestAction(StateFeatures state) {
        double[] qValues = qTable.computeIfAbsent(state.encode(), k -> new double[Strategy.size()]);
        int best = 0;
        double bestV = qValues[0];
        for (int i = 1; i < Strategy.size(); i++) {
            if (qValues[i] > bestV) {
                bestV = qValues[i];
                best = i;
            }
        }
        return Strategy.values()[best];
    }

    /**
     * Q-Learning 更新。
     */
    public void update(StateFeatures state, Strategy action, double reward,
                       StateFeatures nextState, boolean terminal) {
        double[] qa = qTable.computeIfAbsent(state.encode(), k -> new double[Strategy.size()]);
        int aIdx = action.index();
        double currentQ = qa[aIdx];

        double target;
        if (terminal) {
            target = reward;
        } else {
            double[] qn = qTable.computeIfAbsent(nextState.encode(), k -> new double[Strategy.size()]);
            double maxQ = qn[0];
            for (int i = 1; i < Strategy.size(); i++) {
                if (qn[i] > maxQ) maxQ = qn[i];
            }
            target = reward + gamma * maxQ;
        }
        qa[aIdx] = currentQ + alpha * (target - currentQ);
        updateCount++;
    }

    /**
     * 一个 episode 结束时调用，衰减 ε。
     */
    public void endEpisode() {
        episodeCount++;
        epsilon = Math.max(MIN_EPSILON, epsilon * epsilonDecay);
    }

    /**
     * 重置 ε 到初始值。
     */
    public void resetEpsilon() {
        epsilon = DEFAULT_EPSILON;
    }

    /**
     * 查询 Q 值（用于调试 / 报告）。
     */
    public double qValue(StateFeatures state, Strategy action) {
        double[] q = qTable.get(state.encode());
        if (q == null) return 0.0;
        return q[action.index()];
    }

    /**
     * 导出 Q 表到字符串（调试用）。
     */
    public String dumpQTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("Q-Learner dump: ").append(qTable.size()).append(" states, ")
          .append(updateCount).append(" updates, ε=").append(String.format("%.4f", epsilon)).append("\n");
        for (Map.Entry<Long, double[]> e : qTable.entrySet()) {
            sb.append(String.format("  s=%d -> [", e.getKey()));
            double[] qs = e.getValue();
            for (int i = 0; i < qs.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(String.format("%s=%.3f", Strategy.values()[i].code(), qs[i]));
            }
            sb.append("]\n");
        }
        return sb.toString();
    }

    /**
     * 导出 Q 表为 CSV（每个 state-action 对一行），便于外部分析/可视化。
     * Format: state_code,strategy,value
     */
    public String dumpCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("state_code,strategy,value\n");
        for (Map.Entry<Long, double[]> e : qTable.entrySet()) {
            double[] qs = e.getValue();
            for (int i = 0; i < qs.length; i++) {
                sb.append(e.getKey()).append(",")
                  .append(Strategy.values()[i].code()).append(",")
                  .append(String.format("%.6f", qs[i])).append("\n");
            }
        }
        return sb.toString();
    }

    /** 当前 Q 表快照（不可变） */
    public Map<Long, double[]> snapshot() {
        Map<Long, double[]> out = new java.util.HashMap<>();
        for (Map.Entry<Long, double[]> e : qTable.entrySet()) {
            out.put(e.getKey(), e.getValue().clone());
        }
        return out;
    }

    /**
     * 训练历史统计（用于绘制训练曲线）。
     */
    private final List<TrainingStats> trainingHistory = new ArrayList<>();

    public void recordEpisodeStats(double totalReward) {
        trainingHistory.add(new TrainingStats(episodeCount, totalReward,
                qTable.size(), epsilon, updateCount));
    }

    public List<TrainingStats> getTrainingHistory() {
        return Collections.unmodifiableList(trainingHistory);
    }

    public void clearHistory() {
        trainingHistory.clear();
    }

    public record TrainingStats(long episode, double totalReward, int tableSize, double epsilon, long updates) {}
}
