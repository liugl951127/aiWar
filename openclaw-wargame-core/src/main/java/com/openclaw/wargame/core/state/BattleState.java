package com.openclaw.wargame.core.state;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.terrain.TerrainMap;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 战场态势的不可变快照。在每个仿真步长末生成，供分析模块使用。
 * <p>
 * 用 record 模式让快照自动获得 equals/hashCode/toString。
 */
public record BattleState(
        long tick,
        double timeSeconds,
        TerrainMap map,
        List<Unit> units
) {
    public BattleState {
        Objects.requireNonNull(map, "map");
        units = units == null ? List.of() : List.copyOf(units);
    }

    public List<Unit> unitsOf(Team team) {
        List<Unit> out = new ArrayList<>();
        for (Unit u : units) {
            if (u.team() == team && u.isAlive()) out.add(u);
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * 在指定阵营的全部单位中，找到距离 position 最近且仍存活的单位。
     */
    public Unit nearest(Team team, Position position) {
        Unit best = null;
        double bestDist = Double.MAX_VALUE;
        for (Unit u : units) {
            if (u.team() != team || !u.isAlive()) continue;
            double d = u.position().distanceTo(position);
            if (d < bestDist) {
                bestDist = d;
                best = u;
            }
        }
        return best;
    }

    /**
     * 全场仍存活的单位总数。
     */
    public long aliveCount(Team team) {
        long c = 0;
        for (Unit u : units) {
            if (u.team() == team && u.isAlive()) c++;
        }
        return c;
    }

    public long aliveCountAll() {
        long c = 0;
        for (Unit u : units) {
            if (u.isAlive()) c++;
        }
        return c;
    }

    /**
     * 计算火力优势指数：己方火力 / 敌方火力（无除零保护）。
     */
    public double firepowerRatio(Team self, Team enemy) {
        double s = 0, e = 0;
        for (Unit u : units) {
            if (!u.isAlive()) continue;
            double score = u.type().baseFirepower() * u.hpRatio();
            if (u.team() == self) s += score;
            else if (u.team() == enemy) e += score;
        }
        return e == 0 ? Double.POSITIVE_INFINITY : s / e;
    }

    public long engagingCount(Team team) {
        long c = 0;
        for (Unit u : units) {
            if (u.team() == team && u.status() == UnitStatus.ENGAGING) c++;
        }
        return c;
    }
}
