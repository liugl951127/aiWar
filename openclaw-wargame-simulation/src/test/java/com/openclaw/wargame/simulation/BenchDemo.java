package com.openclaw.wargame.simulation;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.terrain.TerrainMap;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitType;
import java.util.ArrayList;
import java.util.List;

/**
 * Bench 工具 —— 跑常见操作微基准测试并打印报告。
 * 运行：java -cp target/test-classes:... com.openclaw.wargame.simulation.BenchDemo
 * <p>
 * 注意：作为 test 文件是为了不引入新的 main class 模块。
 */
public final class BenchDemo {

    public static void main(String[] args) {
        System.out.println("OpenClaw Wargame - Microbench");
        System.out.println("==============================");

        // 1. 简单算术
        Bench.Results r1 = Bench.run("arith-100x", 50000, 1000, () -> {
            long s = 0;
            for (int i = 0; i < 100; i++) s += i * (long) i;
            if (s == Long.MIN_VALUE) System.out.print("");
        });
        System.out.println(r1);

        // 2. StringBuilder
        Bench.Results r2 = Bench.run("stringbuilder-1000", 5000, 200, () -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) sb.append(i).append(",");
            if (sb.length() == 0) System.out.print("");
        });
        System.out.println(r2);

        // 3. List construction
        Bench.Results r3 = Bench.run("list-1000-construct", 5000, 200, () -> {
            List<Integer> l = new ArrayList<>();
            for (int i = 0; i < 1000; i++) l.add(i);
            if (l.isEmpty()) System.out.print("");
        });
        System.out.println(r3);

        // 4. Unit creation (实际业务)
        Bench.Results r4 = Bench.run("unit-create-10", 5000, 200, () -> {
            for (int i = 0; i < 10; i++) {
                Unit u = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(i * 100.0, i * 100.0));
                if (u == null) System.out.print("");
            }
        });
        System.out.println(r4);

        // 5. BattleState 创建 + 单位遍历
        TerrainMap map = new TerrainMap(5000, 5000, 100);
        List<Unit> units = new ArrayList<>();
        for (int i = 0; i < 10; i++) units.add(Unit.create(UnitType.ARMOR, Team.BLUE, new Position(i * 200.0, i * 200.0)));
        final com.openclaw.wargame.core.state.BattleState state = new com.openclaw.wargame.core.state.BattleState(
                0, 0.0, map, units);
        Bench.Results r5 = Bench.run("state-units-iterate", 10000, 200, () -> {
            double s = 0;
            for (Unit u : state.units()) s += u.hp();
            if (s == Double.NEGATIVE_INFINITY) System.out.print("");
        });
        System.out.println(r5);

        // 6. HashMap put
        Bench.Results r6 = Bench.run("hashmap-1000", 2000, 100, () -> {
            java.util.HashMap<Integer, Integer> m = new java.util.HashMap<>();
            for (int i = 0; i < 1000; i++) m.put(i, i * 2);
            if (m.size() != 1000) System.out.print("");
        });
        System.out.println(r6);

        // 7. BattleRecorder snapshot encode
        Bench.Results r7 = Bench.run("snapshot-json", 5000, 200, () -> {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"tick\":").append(state.tick())
              .append(",\"units\":[");
            for (Unit u : units) {
                sb.append("{\"id\":\"").append(u.id()).append("\",\"hp\":").append(u.hp()).append("}");
            }
            sb.append("]}");
            if (sb.length() == 0) System.out.print("");
        });
        System.out.println(r7);

        System.out.println("==============================");
        System.out.println("All benchmarks done.");
    }
}