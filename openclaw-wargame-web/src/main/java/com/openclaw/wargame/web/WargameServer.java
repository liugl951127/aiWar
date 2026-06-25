package com.openclaw.wargame.web;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.unit.Buff;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.realtime.BattleEvent;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * 内嵌 HTTP 服务器（基于 JDK 内置的 com.sun.net.httpserver）。
 * <p>
 * 路由：
 * <ul>
 *   <li>GET /api/state         → 当前 BattleState JSON</li>
 *   <li>GET /api/snapshot      → 轻量快照（仅单位 + tick + winner）</li>
 *   <li>GET /api/analysis/{team} → 态势分析</li>
 *   <li>GET /api/events        → 最近事件</li>
 *   <li>GET /                  → HTML 大屏</li>
 *   <li>GET /static/*          → CSS/JS 静态资源</li>
 * </ul>
 */
public final class WargameServer {
    private static final Logger log = LoggerFactory.getLogger(WargameServer.class);

    private final int port;
    private final BattleStateHolder holder;
    private final com.openclaw.wargame.realtime.BattleEventBus eventBus;
    private HttpServer server;

    public WargameServer(int port, BattleStateHolder holder,
                         com.openclaw.wargame.realtime.BattleEventBus eventBus) {
        this.port = port;
        this.holder = holder;
        this.eventBus = eventBus;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(2));

        server.createContext("/api/state", this::handleState);
        server.createContext("/api/snapshot", this::handleSnapshot);
        server.createContext("/api/analysis", this::handleAnalysis);
        server.createContext("/api/advisory", this::handleAdvisory);
        server.createContext("/api/events", this::handleEvents);
        server.createContext("/static", this::handleStatic);
        server.createContext("/", this::handleRoot);

        server.start();
        log.info("Wargame HTTP server started on http://localhost:{}", port);
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    public int port() {
        return port;
    }

    /* ---- handlers ---- */

    private void handleState(HttpExchange ex) throws IOException {
        BattleState s = holder.get();
        if (s == null) {
            send(ex, 200, "application/json", "{\"running\":" + holder.isRunning() + ",\"state\":null}");
            return;
        }
        send(ex, 200, "application/json", stateToJson(s));
    }

    private void handleSnapshot(HttpExchange ex) throws IOException {
        BattleState s = holder.get();
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"running\":").append(holder.isRunning());
        sb.append(",\"winner\":").append(jsonString(holder.winner()));
        sb.append(",\"winnerTick\":").append(holder.winnerTick());
        sb.append(",\"lastUpdateMs\":").append(holder.lastUpdateMs());
        if (s != null) {
            sb.append(",\"tick\":").append(s.tick());
            sb.append(",\"timeSeconds\":").append(s.timeSeconds());
            sb.append(",\"mapWidth\":").append(s.map().widthMeters());
            sb.append(",\"mapHeight\":").append(s.map().heightMeters());
        } else {
            sb.append(",\"tick\":0,\"timeSeconds\":0,\"mapWidth\":1000,\"mapHeight\":1000");
        }
        sb.append(",\"blueAlive\":").append(s == null ? 0 : s.aliveCount(Team.BLUE));
        sb.append(",\"redAlive\":").append(s == null ? 0 : s.aliveCount(Team.RED));
        sb.append(",\"units\":[");
        if (s != null) {
            boolean first = true;
            for (Unit u : s.units()) {
                if (!first) sb.append(",");
                first = false;
                sb.append(unitJson(u));
            }
        }
        sb.append("]}");
        send(ex, 200, "application/json", sb.toString());
    }

    private void handleAnalysis(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        Team team;
        if (path.endsWith("/blue")) team = Team.BLUE;
        else if (path.endsWith("/red")) team = Team.RED;
        else {
            send(ex, 400, "application/json", "{\"error\":\"specify /blue or /red\"}");
            return;
        }
        BattleState s = holder.get();
        if (s == null) {
            send(ex, 200, "application/json", "{\"error\":\"no state\"}");
            return;
        }
        com.openclaw.wargame.analysis.SituationalAnalysis sa =
                new com.openclaw.wargame.analysis.SituationalAnalysis(s, team);
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"team\":\"").append(team.name()).append("\"");
        sb.append(",\"firepowerRatio\":").append(sa.firepowerRatio());
        sb.append(",\"alive\":").append(s.aliveCount(team));
        sb.append(",\"enemyAlive\":").append(s.aliveCount(team == Team.BLUE ? Team.RED : Team.BLUE));
        sb.append(",\"topThreats\":[");
        var threats = sa.topThreats(3);
        for (int i = 0; i < threats.size(); i++) {
            if (i > 0) sb.append(",");
            var t = threats.get(i);
            sb.append("{\"target\":\"").append(t.target().id()).append("\"")
              .append(",\"type\":\"").append(t.target().type().name()).append("\"")
              .append(",\"score\":").append(t.threatScore())
              .append(",\"distance\":").append(t.distance())
              .append("}");
        }
        sb.append("]}");
        send(ex, 200, "application/json", sb.toString());
    }

    private void handleAdvisory(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        Team team;
        if (path.endsWith("/blue")) team = Team.BLUE;
        else if (path.endsWith("/red")) team = Team.RED;
        else {
            send(ex, 400, "application/json", "{\"error\":\"specify /blue or /red\"}");
            return;
        }
        var report = holder.getReport(team);
        if (report == null) {
            send(ex, 200, "application/json", "{\"error\":\"no advisory report\"}");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"team\":\"").append(team.name()).append("\"");
        var adv = report.advantage();
        sb.append(",\"advantage\":{")
          .append("\"firepower\":").append(adv.firepower())
          .append(",\"manpower\":").append(adv.manpower())
          .append(",\"detection\":").append(adv.detection())
          .append(",\"mobility\":").append(adv.mobility())
          .append(",\"cohesion\":").append(adv.cohesion())
          .append(",\"overall\":").append(adv.overall())
          .append("}");
        sb.append(",\"advices\":[");
        var advices = report.advices();
        for (int i = 0; i < advices.size(); i++) {
            if (i > 0) sb.append(",");
            var a = advices.get(i);
            sb.append("{\"kind\":\"").append(a.kind()).append("\"")
              .append(",\"priority\":").append(a.priority())
              .append(",\"reason\":\"").append(jsonEscape(a.reason())).append("\"}");
        }
        sb.append("]");
        sb.append(",\"buffAssignments\":[");
        var buffs = report.buffAssignments();
        for (int i = 0; i < buffs.size(); i++) {
            if (i > 0) sb.append(",");
            var b = buffs.get(i);
            sb.append("{\"unit\":\"").append(b.unitId().substring(0, 8)).append("\"")
              .append(",\"buff\":\"").append(b.buffKind()).append("\"")
              .append(",\"multiplier\":").append(b.multiplier())
              .append(",\"duration\":").append(b.duration())
              .append("}");
        }
        sb.append("]}");
        send(ex, 200, "application/json", sb.toString());
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private void handleEvents(HttpExchange ex) throws IOException {
        var events = eventBus.recent(50);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"events\":[");
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) sb.append(",");
            var e = events.get(i);
            sb.append("{\"kind\":\"").append(e.kind()).append("\"")
              .append(",\"tick\":").append(e.tick())
              .append(",\"time\":").append(e.timeSeconds())
              .append(",\"id\":\"").append(e.id()).append("\"}");
        }
        sb.append("]}");
        send(ex, 200, "application/json", sb.toString());
    }

    private void handleRoot(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/") || path.equals("/index.html")) {
            String html = loadResource("index.html");
            if (html == null) html = defaultIndex();
            send(ex, 200, "text/html; charset=utf-8", html);
        } else {
            send(ex, 404, "text/plain", "Not Found");
        }
    }

    private void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        // /static/xxx → xxx
        String rel = path.substring("/static/".length());
        if (rel.contains("..")) {
            send(ex, 400, "text/plain", "bad path");
            return;
        }
        String content = loadResource("static/" + rel);
        if (content == null) {
            send(ex, 404, "text/plain", "Not Found");
            return;
        }
        String mime = rel.endsWith(".css") ? "text/css; charset=utf-8"
                : rel.endsWith(".js") ? "application/javascript; charset=utf-8"
                : "text/plain; charset=utf-8";
        send(ex, 200, mime, content);
    }

    /* ---- helpers ---- */

    private void send(HttpExchange ex, int code, String mime, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", mime);
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String stateToJson(BattleState s) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"tick\":").append(s.tick());
        sb.append(",\"timeSeconds\":").append(s.timeSeconds());
        sb.append(",\"running\":").append(holder.isRunning());
        sb.append(",\"winner\":").append(jsonString(holder.winner()));
        sb.append(",\"mapWidth\":").append(s.map().widthMeters());
        sb.append(",\"mapHeight\":").append(s.map().heightMeters());
        sb.append(",\"blueAlive\":").append(s.aliveCount(Team.BLUE));
        sb.append(",\"redAlive\":").append(s.aliveCount(Team.RED));
        sb.append(",\"units\":[");
        boolean first = true;
        for (Unit u : s.units()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(unitJson(u));
        }
        sb.append("]}");
        return sb.toString();
    }

    private String unitJson(Unit u) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"id\":\"").append(u.id()).append("\"")
          .append(",\"team\":\"").append(u.team().name()).append("\"")
          .append(",\"type\":\"").append(u.type().name()).append("\"")
          .append(",\"x\":").append(u.position().x())
          .append(",\"y\":").append(u.position().y())
          .append(",\"hp\":").append(u.hp())
          .append(",\"maxHp\":").append(u.maxHp())
          .append(",\"status\":\"").append(u.status().name()).append("\"")
          .append(",\"alive\":").append(u.isAlive());
        // buffs
        sb.append(",\"buffs\":[");
        boolean firstBuff = true;
        for (var b : u.buffs()) {
            if (!firstBuff) sb.append(",");
            firstBuff = false;
            sb.append("{\"kind\":\"").append(b.kind()).append("\"")
              .append(",\"multiplier\":").append(b.multiplier())
              .append(",\"duration\":").append(b.durationTicks())
              .append("}");
        }
        sb.append("]");
        return sb.append("}").toString();
    }

    private static String jsonString(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String loadResource(String path) {
        try (InputStream is = WargameServer.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) return null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private String defaultIndex() {
        return "<!doctype html><html><body><h1>Wargame Server</h1>"
                + "<p>index.html not found in classpath; place under resources/</p>"
                + "</body></html>";
    }

    /** 静态查询参数工具（备用） */
    public static Map<String, String> parseQuery(String q) {
        Map<String, String> out = new HashMap<>();
        if (q == null || q.isEmpty()) return out;
        for (String kv : q.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0) out.put(kv.substring(0, i), kv.substring(i + 1));
        }
        return out;
    }
}
