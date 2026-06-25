package com.openclaw.wargame.simulation;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.terrain.TerrainMap;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 仿真器：在 BattleState 上推进 1 tick，应用移动、地形、随机事件。
 * <p>
 * 设计目标：
 * <ul>
 *   <li>无副作用：纯函数式地返回新 BattleState，不修改入参</li>
 *   <li>确定性：给定相同 seed 产生相同结果（用于 MCTS 回放）</li>
 *   <li>轻量：不依赖事件总线，可在 MCTS rollout 中高速调用</li>
 * </ul>
 */
public final class Simulator {
    private final double dtSeconds;
    private final Random random;

    public Simulator(double dtSeconds, long seed) {
        if (dtSeconds <= 0) throw new IllegalArgumentException();
        this.dtSeconds = dtSeconds;
        this.random = new Random(seed);
    }

    public double dtSeconds() { return dtSeconds; }

    /**
     * 推进 1 tick：单位移动 + 状态推进，不开火。
     */
    public BattleState stepOne(BattleState state, String preferredUnitId) {
        long nextTick = state.tick() + 1;
        double nextTime = state.timeSeconds() + dtSeconds;
        List<Unit> nextUnits = new ArrayList<>(state.units().size());
        for (Unit u : state.units()) {
            if (!u.isAlive()) { nextUnits.add(u); continue; }
            Unit copy = shallowCopy(u);
            // 推进
            if (copy.moveTarget() != null) {
                double factor = state.map().worstMovementFactor(copy.position(), copy.moveTarget(), 5);
                copy.advance(dtSeconds, factor);
                // 防御性：clamp 到地图边界
                Position clamped = copy.position().clamp(state.map().widthMeters(), state.map().heightMeters());
                if (!clamped.equals(copy.position())) {
                    copy.setPositionInternal(clamped);
                    copy.clearMoveTarget();
                }
            }
            // 简化随机事件：小概率发现随机变动
            if (random.nextDouble() < 0.005 && copy.status() == com.openclaw.wargame.core.unit.UnitStatus.IDLE) {
                // 随机微调方向（演示扰动）
                Position cur = copy.position();
                Position drift = new Position(
                        cur.x() + (random.nextDouble() - 0.5) * 20,
                        cur.y() + (random.nextDouble() - 0.5) * 20
                );
                copy.setMoveTarget(drift);
            }
            nextUnits.add(copy);
        }
        return new BattleState(nextTick, nextTime, state.map(), nextUnits);
    }

    /**
     * 随机推进：随机移动若干单位。用于 MCTS rollout。
     */
    public BattleState randomStep(BattleState state, Random rng) {
        List<Unit> next = new ArrayList<>(state.units().size());
        for (Unit u : state.units()) {
            if (!u.isAlive()) { next.add(u); continue; }
            Unit copy = shallowCopy(u);
            if (rng.nextDouble() < 0.3) {
                // 随机移动
                Position p = copy.position();
                Position target = new Position(
                        p.x() + (rng.nextDouble() - 0.5) * 1000,
                        p.y() + (rng.nextDouble() - 0.5) * 1000
                );
                copy.setMoveTarget(target);
            }
            if (copy.moveTarget() != null) {
                double factor = state.map().worstMovementFactor(copy.position(), copy.moveTarget(), 3);
                copy.advance(dtSeconds, factor);
            }
            next.add(copy);
        }
        return new BattleState(state.tick() + 1, state.timeSeconds() + dtSeconds, state.map(), next);
    }

    /**
     * 拷贝一个单位（保留 ID、阵营、类型、血量、武器、状态、目标）。
     */
    private Unit shallowCopy(Unit u) {
        Unit copy = new Unit(u.id(), u.type(), u.team(), u.position());
        // 调整 hp 到原值（构造时满血，需要扣除差额）
        if (copy.hp() != u.hp()) {
            copy.takeDamage(copy.hp() - u.hp());
        }
        // 重新挂武器
        for (var w : u.weapons()) {
            var wc = new com.openclaw.wargame.core.weapon.Weapon(w.type(), w.maxAmmo());
            int delta = w.ammo() - w.maxAmmo();
            if (delta != 0) wc.resupply(delta);
            copy.mountWeapon(wc);
        }
        if (u.moveTarget() != null) copy.setMoveTarget(u.moveTarget());
        copy.setStatusInternal(u.status());
        return copy;
    }

    /**
     * 创建一个全新的起始战场。
     */
    public static BattleState createInitial(TerrainMap map,
                                            List<Unit> blueUnits,
                                            List<Unit> redUnits,
                                            List<Unit> neutralUnits) {
        List<Unit> all = new ArrayList<>();
        all.addAll(blueUnits);
        all.addAll(redUnits);
        all.addAll(neutralUnits);
        return new BattleState(0, 0.0, map, all);
    }
}
