package com.openclaw.wargame.ai.mcts;

import com.openclaw.wargame.core.state.BattleState;

import java.util.ArrayList;
import java.util.List;

/**
 * MCTS 树节点。
 */
public final class MCTSNode {
    private final BattleState state;
    private final MCTSNode parent;
    private final String moveFromParent; // 进入此节点对应的动作
    private final List<MCTSNode> children = new ArrayList<>();
    private long visits;
    private double wins;

    public MCTSNode(BattleState state, MCTSNode parent, String moveFromParent) {
        this.state = state;
        this.parent = parent;
        this.moveFromParent = moveFromParent;
    }

    public BattleState state() { return state; }
    public MCTSNode parent() { return parent; }
    public String moveFromParent() { return moveFromParent; }
    public List<MCTSNode> children() { return List.copyOf(children); }
    public long visits() { return visits; }
    public double wins() { return wins; }

    public double value() {
        return visits == 0 ? 0 : wins / visits;
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public MCTSNode addChild(BattleState childState, String move) {
        MCTSNode c = new MCTSNode(childState, this, move);
        children.add(c);
        return c;
    }

    public void recordVisit(double reward) {
        visits++;
        wins += reward;
    }

    public MCTSNode bestByVisits() {
        MCTSNode best = this;
        long bestV = visits;
        for (MCTSNode c : children) {
            if (c.visits() > bestV) {
                bestV = c.visits();
                best = c;
            }
        }
        return best;
    }
}
