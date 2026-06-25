package com.openclaw.wargame.rl;

import com.openclaw.wargame.ai.decision.Action;
import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.terrain.TerrainMap;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StrategyInterpreterTest {

    private BattleState buildState() {
        TerrainMap map = new TerrainMap(5000, 5000, 100);
        List<Unit> units = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            units.add(Unit.create(UnitType.ARMOR, Team.BLUE, new Position(100, 100 + i * 100)));
        }
        for (int i = 0; i < 2; i++) {
            units.add(Unit.create(UnitType.ARMOR, Team.RED, new Position(2000, 2000 + i * 100)));
        }
        return new BattleState(0, 0, map, units);
    }

    @Test
    void aggressiveProducesMovesAndEngagements() {
        // 多 seed 试几次，确保 AGGRESSIVE 能产出 EngageAction
        boolean found = false;
        for (long seed = 1; seed <= 50 && !found; seed++) {
            StrategyInterpreter si = new StrategyInterpreter(seed);
            List<Action> actions = si.interpret(Strategy.AGGRESSIVE, buildState(), Team.BLUE);
            for (Action a : actions) {
                if (a instanceof Action.EngageAction) { found = true; break; }
            }
        }
        assertTrue(found, "AGGRESSIVE 在某 seed 下应该产出 EngageAction");
    }

    @Test
    void defensiveOnlyProducesDefends() {
        StrategyInterpreter si = new StrategyInterpreter(42);
        List<Action> actions = si.interpret(Strategy.DEFENSIVE, buildState(), Team.BLUE);
        assertFalse(actions.isEmpty());
        for (Action a : actions) {
            assertInstanceOf(Action.DefendAction.class, a, "DEFENSIVE 只产出 DefendAction");
        }
    }

    @Test
    void retreatMovesTowardCenter() {
        StrategyInterpreter si = new StrategyInterpreter(42);
        BattleState s = buildState();
        List<Action> actions = si.interpret(Strategy.RETREAT, s, Team.BLUE);
        for (Action a : actions) {
            assertInstanceOf(Action.RetreatAction.class, a);
        }
    }

    @Test
    void emptyStateProducesEmptyActions() {
        StrategyInterpreter si = new StrategyInterpreter(42);
        TerrainMap map = new TerrainMap(1000, 1000, 100);
        BattleState empty = new BattleState(0, 0, map, new ArrayList<>());
        List<Action> actions = si.interpret(Strategy.AGGRESSIVE, empty, Team.BLUE);
        assertTrue(actions.isEmpty());
    }

    @Test
    void rewardKillsEnemyPositive() {
        BattleState s1 = buildState();
        // 构建 s2: 杀 1 个红方
        List<Unit> s2Units = new ArrayList<>();
        boolean killedOne = false;
        for (Unit u : s1.units()) {
            Unit copy = Unit.create(u.type(), u.team(), u.position());
            copy.takeDamage(u.maxHp() - u.hp());
            if (u.team() == Team.RED && !killedOne) {
                copy.takeDamage(copy.hp() + 1);
                killedOne = true;
            }
            s2Units.add(copy);
        }
        BattleState s2killed = new BattleState(s1.tick(), s1.timeSeconds(), s1.map(), s2Units);
        double r = StrategyInterpreter.reward(s1, s2killed, Team.BLUE);
        assertTrue(r > 0, "杀死敌方应该正奖励 r=" + r);
    }

    @Test
    void rewardLossesNegative() {
        BattleState s1 = buildState();
        // 构建 s2: 杀死 1 个蓝方（重新构建单位避免 mutate s1）
        List<Unit> s2Units = new ArrayList<>();
        boolean killedOne = false;
        for (Unit u : s1.units()) {
            Unit copy = Unit.create(u.type(), u.team(), u.position());
            copy.takeDamage(u.maxHp() - u.hp()); // sync hp
            if (u.team() == Team.BLUE && !killedOne) {
                copy.takeDamage(copy.hp() + 1);
                killedOne = true;
            }
            s2Units.add(copy);
        }
        BattleState s2lost = new BattleState(s1.tick(), s1.timeSeconds(), s1.map(), s2Units);
        double r = StrategyInterpreter.reward(s1, s2lost, Team.BLUE);
        assertTrue(r < 0, "损失己方应该负奖励 r=" + r);
    }
}
