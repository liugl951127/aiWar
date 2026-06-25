package com.openclaw.wargame.simulation;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.terrain.Terrain;
import com.openclaw.wargame.core.terrain.TerrainMap;
import com.openclaw.wargame.core.unit.Buff;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitType;
import com.openclaw.wargame.core.unit.UnitStatus;
import com.openclaw.wargame.core.weapon.Weapon;
import com.openclaw.wargame.core.weapon.WeaponType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 战斗录制器 —— 把每个 tick 的 BattleState 序列化为 JSON Lines 文件。
 * <p>
 * 文件格式：每行一个 JSON object，对应一个 tick 的快照。
 * <pre>
 * {"type":"header","version":1,"mapWidth":8000,"mapHeight":8000}
 * {"type":"tick","t":1,"time":5.0,"units":[{"id":"...","x":100.0,"y":200.0,"hp":10,"alive":true,...},...]}
 * {"type":"tick","t":2,...}
 * ...
 * {"type":"end","winner":"RED","endTick":22}
 * </pre>
 */
public final class BattleRecorder implements AutoCloseable {

    private final BufferedWriter writer;
    private final int version;
    private boolean headerWritten = false;

    public BattleRecorder(Path file) throws IOException {
        this(file, 1);
    }

    public BattleRecorder(Path file, int version) throws IOException {
        this.version = version;
        Files.createDirectories(file.getParent());
        this.writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
    }

    /** 写文件头（含地图尺寸） */
    public void writeHeader(BattleState initial) throws IOException {
        TerrainMap map = initial.map();
        writer.write(String.format(
                "{\"type\":\"header\",\"version\":%d,\"mapWidth\":%.0f,\"mapHeight\":%.0f,\"cellSize\":%.0f}",
                version, map.widthMeters(), map.heightMeters(), map.cellSize()));
        writer.newLine();
        headerWritten = true;
    }

    /** 写一个 tick 的快照 */
    public void recordTick(BattleState state) throws IOException {
        if (!headerWritten) throw new IllegalStateException("writeHeader first");
        StringBuilder sb = new StringBuilder(1024);
        sb.append("{\"type\":\"tick\",\"t\":").append(state.tick())
          .append(",\"time\":").append(state.timeSeconds())
          .append(",\"units\":[");
        boolean first = true;
        for (Unit u : state.units()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(unitJson(u));
        }
        sb.append("]}");
        writer.write(sb.toString());
        writer.newLine();
    }

    /** 写入结束标记 */
    public void writeEnd(String winner, long endTick) throws IOException {
        writer.write(String.format("{\"type\":\"end\",\"winner\":\"%s\",\"endTick\":%d}",
                winner == null ? "NONE" : winner, endTick));
        writer.newLine();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    /** 把 Unit 序列化为紧凑 JSON */
    private String unitJson(Unit u) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"id\":\"").append(u.id()).append("\"")
          .append(",\"team\":\"").append(u.team().name()).append("\"")
          .append(",\"type\":\"").append(u.type().name()).append("\"")
          .append(",\"x\":").append(u.position().x())
          .append(",\"y\":").append(u.position().y())
          .append(",\"hp\":").append(u.hp())
          .append(",\"maxHp\":").append(u.maxHp())
          .append(",\"status\":\"").append(u.status().name()).append("\"")
          .append(",\"alive\":").append(u.isAlive())
          .append(",\"buffs\":[");
        boolean firstBuff = true;
        for (Buff b : u.buffs()) {
            if (!firstBuff) sb.append(",");
            firstBuff = false;
            sb.append("{\"kind\":\"").append(b.kind()).append("\",\"mul\":").append(b.multiplier())
              .append(",\"dur\":").append(b.durationTicks()).append("}");
        }
        sb.append("],\"weapons\":[");
        boolean firstW = true;
        for (Weapon w : u.weapons()) {
            if (!firstW) sb.append(",");
            firstW = false;
            sb.append("{\"type\":\"").append(w.type().name()).append("\",\"ammo\":").append(w.ammo())
              .append(",\"maxAmmo\":").append(w.maxAmmo()).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    /* ---- 回放 ---- */

    /** 从 JSON Lines 文件读取所有 tick */
    public static List<BattleState> readAll(Path file) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<BattleState> states = new ArrayList<>();
            double mapWidth = 0, mapHeight = 0, cellSize = 100;
            long baseTick = 0;
            double baseTime = 0;
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.contains("\"type\":\"header\"")) {
                    mapWidth = extractDouble(line, "mapWidth", 0);
                    mapHeight = extractDouble(line, "mapHeight", 0);
                    cellSize = extractDouble(line, "cellSize", 100);
                } else if (line.contains("\"type\":\"tick\"")) {
                    long tick = (long) extractDouble(line, "t", 0);
                    double time = extractDouble(line, "time", 0);
                    List<Unit> units = extractUnits(line);
                    TerrainMap map = new TerrainMap(mapWidth, mapHeight, cellSize);
                    states.add(new BattleState(tick, time, map, units));
                    baseTick = tick;
                    baseTime = time;
                }
            }
            return states;
        }
    }

    /** 简化的 JSON 字段提取（仅数字） */
    private static double extractDouble(String json, String field, double def) {
        String marker = "\"" + field + "\":";
        int i = json.indexOf(marker);
        if (i < 0) return def;
        i += marker.length();
        int end = i;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == ',' || c == '}' || c == ' ' || c == '\n') break;
            end++;
        }
        try {
            return Double.parseDouble(json.substring(i, end).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 简化的 JSON 字符串提取 */
    private static String extractString(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int i = json.indexOf(marker);
        if (i < 0) return null;
        i += marker.length();
        int end = json.indexOf("\"", i);
        return end < 0 ? null : json.substring(i, end);
    }

    /** 极简 JSON array parser for units */
    private static List<Unit> extractUnits(String json) {
        List<Unit> units = new ArrayList<>();
        String arrayMarker = "\"units\":[";
        int arrStart = json.indexOf(arrayMarker);
        if (arrStart < 0) return units;
        arrStart += arrayMarker.length();
        int depth = 0;
        int objStart = -1;
        for (int i = arrStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{' && depth == 0) {
                objStart = i;
                depth = 1;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    units.add(parseUnit(json.substring(objStart, i + 1)));
                    objStart = -1;
                }
            }
        }
        return units;
    }

    private static Unit parseUnit(String obj) {
        String id = extractString(obj, "id");
        UnitType type = UnitType.valueOf(extractString(obj, "type"));
        Team team = Team.valueOf(extractString(obj, "team"));
        double x = extractDouble(obj, "x", 0);
        double y = extractDouble(obj, "y", 0);
        Unit u = Unit.create(type, team, new Position(x, y));
        // 简化：hp 已经通过 takeDamage 调整
        double hp = extractDouble(obj, "hp", u.hp());
        if (hp < u.hp()) {
            u.takeDamage(u.hp() - hp);
        }
        // status (note: don't apply via API; status reflects current behavior)
        // buffs
        // 简单解析：从 obj 找 "buffs":[{...},...]
        String buffsMarker = "\"buffs\":[";
        int bm = obj.indexOf(buffsMarker);
        if (bm >= 0) {
            int bend = obj.indexOf("]", bm);
            String sub = obj.substring(bm + buffsMarker.length(), bend);
            // 简单拆分 {...},{...}
            int d = 0, ps = -1;
            for (int i = 0; i < sub.length(); i++) {
                char c = sub.charAt(i);
                if (c == '{' && d == 0) ps = i;
                if (c == '{') d++;
                if (c == '}') {
                    d--;
                    if (d == 0 && ps >= 0) {
                        String bObj = sub.substring(ps, i + 1);
                        Buff.Kind kind = Buff.Kind.valueOf(extractString(bObj, "kind"));
                        double mul = extractDouble(bObj, "mul", 0);
                        int dur = (int) extractDouble(bObj, "dur", 0);
                        u.applyBuff(new Buff(kind, mul, dur, "replay"));
                        ps = -1;
                    }
                }
            }
        }
        return u;
    }
}