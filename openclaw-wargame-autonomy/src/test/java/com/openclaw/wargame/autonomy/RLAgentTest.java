package com.openclaw.wargame.autonomy;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.terrain.TerrainMap;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitType;
import com.openclaw.wargame.core.weapon.Weapon;
import com.openclaw.wargame.core.weapon.WeaponType;
import com.openclaw.wargame.ai.decision.Action;
import com.openclaw.wargame.ai.decision.DecisionPlan;
import com.openclaw.wargame.rl.QLearner;
import com.openclaw.wargame.rl.StrategyInterpreter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RLAgentTest {

    private BattleState buildBattleState() {
        TerrainMap map = new TerrainMap(5000, 5000, 100);
        List<Unit> units = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Unit u = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(100, 100 + i * 100));
            u.mountWeapon(new Weapon(WeaponType.TANK_CANNON, 30));
            units.add(u);
        }
        for (int i = 0; i < 2; i++) {
            Unit u = Unit.create(UnitType.ARMOR, Team.RED, new Position(2000, 2000 + i * 100));
            u.mountWeapon(new Weapon(WeaponType.TANK_CANNON, 30));
            units.add(u);
        }
        return new BattleState(0, 0, map, units);
    }

    @Test
    void trainingModeExplores() {
        QLearner ql = new QLearner(42);
        StrategyInterpreter si = new StrategyInterpreter(43);
        RLAgent agent = new RLAgent(Team.BLUE, ql, si, true);
        BattleState s = buildBattleState();
        DecisionPlan plan = agent.decide(s, Team.BLUE);
        assertNotNull(plan);
        assertTrue(!plan.actions().isEmpty(), "训练模式下应该产出动作");
    }

    @Test
    void greedyModeUsesBest() {
        QLearner ql = new QLearner(0.1, 0.95, 0.05, 0.99, 42); // ε=0.05
        StrategyInterpreter si = new StrategyInterpreter(43);
        RLAgent agent = new RLAgent(Team.BLUE, ql, si, false); // greedy
        BattleState s = buildBattleState();
        DecisionPlan plan1 = agent.decide(s, Team.BLUE);
        DecisionPlan plan2 = agent.decide(s, Team.BLUE);
        // 同样状态，greedy 应该给同样的策略
        assertEquals(plan1.summary(), plan2.summary());
    }

    @Test
    void observeRewardUpdatesQTable() {
        QLearner ql = new QLearner(42);
        StrategyInterpreter si = new StrategyInterpreter(43);
        RLAgent agent = new RLAgent(Team.BLUE, ql, si, true);
        BattleState s1 = buildBattleState();
        agent.decide(s1, Team.BLUE);
        assertEquals(0, ql.updateCount());
        BattleState s2 = buildBattleState();
        agent.observeReward(s2, 50.0, false);
        assertTrue(ql.updateCount() > 0, "observeReward 应该触发 Q 更新");
    }

    @Test
    void endEpisodeDecaysEpsilon() {
        QLearner ql = new QLearner(42);
        StrategyInterpreter si = new StrategyInterpreter(43);
        RLAgent agent = new RLAgent(Team.BLUE, ql, si, true);
        double before = ql.epsilon();
        for (int i = 0; i < 5; i++) agent.endEpisode();
        assertTrue(ql.epsilon() < before);
    }
}
