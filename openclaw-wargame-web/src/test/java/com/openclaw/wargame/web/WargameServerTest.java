package com.openclaw.wargame.web;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.terrain.TerrainMap;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitType;
import com.openclaw.wargame.realtime.BattleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class WargameServerTest {

    private WargameServer server;
    private BattleStateHolder holder;
    private BattleEventBus bus;
    private int port;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        holder = new BattleStateHolder();
        bus = new BattleEventBus(64, BattleEventBus.BackpressurePolicy.BLOCK);
        // 多轮随机避开端口冲突（偶发同时跑测试抢同一端口）
        java.net.BindException last = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            port = 18080 + (int) (java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 200));
            try {
                server = new WargameServer(port, holder, bus);
                server.start();
                last = null;
                break;
            } catch (java.net.BindException e) {
                last = e;
                try { Thread.sleep(50); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        if (last != null) throw last;
        // 给点时间 bind
        Thread.sleep(100);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void snapshotEmptyState() throws Exception {
        URL url = URI.create("http://localhost:" + port + "/api/snapshot").toURL();
        String body = httpGet(url);
        assertTrue(body.contains("\"running\":false"));
        assertTrue(body.contains("\"blueAlive\":0"));
    }

    @Test
    void snapshotWithState() throws Exception {
        TerrainMap map = new TerrainMap(1000, 1000, 100);
        Unit u1 = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(100, 100));
        Unit u2 = Unit.create(UnitType.ARMOR, Team.RED, new Position(500, 500));
        BattleState s = new BattleState(5, 25.0, map, java.util.List.of(u1, u2));
        holder.set(s);

        String body = httpGet(URI.create("http://localhost:" + port + "/api/snapshot").toURL());
        assertTrue(body.contains("\"tick\":5"));
        assertTrue(body.contains("\"blueAlive\":1"));
        assertTrue(body.contains("\"redAlive\":1"));
        assertTrue(body.contains("ARMOR"));
    }

    @Test
    void analysisEndpoint() throws Exception {
        TerrainMap map = new TerrainMap(1000, 1000, 100);
        Unit b = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(100, 100));
        Unit r = Unit.create(UnitType.ARMOR, Team.RED, new Position(800, 800));
        holder.set(new BattleState(0, 0, map, java.util.List.of(b, r)));

        String body = httpGet(URI.create("http://localhost:" + port + "/api/analysis/blue").toURL());
        assertTrue(body.contains("\"team\":\"BLUE\""));
        assertTrue(body.contains("\"firepowerRatio\""));
        assertTrue(body.contains("\"topThreats\""));
    }

    @Test
    void indexPageServes() throws Exception {
        URL url = URI.create("http://localhost:" + port + "/").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        assertEquals(200, code);
        String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(body.contains("OpenClaw Wargame"));
        assertTrue(body.contains("dashboard.js"));
    }

    @Test
    void staticCssServes() throws Exception {
        String body = httpGet(URI.create("http://localhost:" + port + "/static/style.css").toURL());
        assertTrue(body.contains(".topbar"));
    }

    @Test
    void eventsEndpointReturns() throws Exception {
        String body = httpGet(URI.create("http://localhost:" + port + "/api/events").toURL());
        assertTrue(body.contains("\"events\""));
    }

    @Test
    void trainingEndpointEmpty() throws Exception {
        // 默认 supplier 为 null：返回 {"episodes":[]}
        String body = httpGet(URI.create("http://localhost:" + port + "/api/training").toURL());
        assertEquals("{\"episodes\":[]}", body);
    }

    @Test
    void trainingEndpointCustomJson() throws Exception {
        server.setTrainingHistorySupplier(
                () -> "{\"episodes\":[{\"e\":1,\"r\":100.5}],\"q\":2.5}");
        String body = httpGet(URI.create("http://localhost:" + port + "/api/training").toURL());
        assertTrue(body.contains("\"r\":100.5"));
        assertTrue(body.contains("\"q\":2.5"));
    }

    private String httpGet(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        int code = conn.getResponseCode();
        if (code >= 400) {
            return "HTTP " + code;
        }
        return new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
