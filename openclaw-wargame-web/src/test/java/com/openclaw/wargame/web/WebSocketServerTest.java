package com.openclaw.wargame.web;

import com.openclaw.wargame.core.coord.Position;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.terrain.TerrainMap;
import com.openclaw.wargame.core.unit.Unit;
import com.openclaw.wargame.core.unit.UnitType;
import com.openclaw.wargame.realtime.BattleEventBus;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketServerTest {

    private HttpServer httpServer;
    private WebSocketServer wsServer;
    private BattleStateHolder holder;
    private BattleEventBus bus;
    private int port;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        holder = new BattleStateHolder();
        bus = new BattleEventBus(64, BattleEventBus.BackpressurePolicy.BLOCK);
        port = 19000 + (int) (Math.random() * 200);
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2));
        wsServer = new WebSocketServer(port, "/ws/snapshot", holder);
        wsServer.attachTo(httpServer);
        wsServer.startBroadcaster(50);
        httpServer.start();
        Thread.sleep(100);
    }

    @AfterEach
    void tearDown() {
        wsServer.stop();
        httpServer.stop(0);
    }

    @Test
    void clientReceivesInitialSnapshot() throws Exception {
        TerrainMap map = new TerrainMap(1000, 1000, 100);
        Unit u = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(100, 100));
        BattleState s = new BattleState(0, 0, map, java.util.List.of(u));
        holder.set(s);

        String frame = connectAndReadFrame();
        assertNotNull(frame);
        assertTrue(frame.contains("\"type\":\"snapshot\""));
        assertTrue(frame.contains("\"tick\":0"));
        assertTrue(frame.contains("ARMOR"));
        assertTrue(frame.contains("\"ba\":1"));
    }

    @Test
    void clientReceivesPushOnStateChange() throws Exception {
        TerrainMap map = new TerrainMap(1000, 1000, 100);
        Unit u = Unit.create(UnitType.ARMOR, Team.BLUE, new Position(100, 100));
        holder.set(new BattleState(0, 0, map, java.util.List.of(u)));

        try (Socket sock = openWebSocket()) {
            // 读初始 frame
            String f1 = readFrame(sock);
            assertNotNull(f1);
            assertTrue(f1.contains("tick"));

            // 更新 state，触发 push
            holder.set(new BattleState(5, 25.0, map, java.util.List.of(u)));
            String f2 = readFrame(sock);
            assertNotNull(f2);
            assertTrue(f2.contains("\"tick\":5"));
        }
    }

    @Test
    void pingPongFrame() throws Exception {
        TerrainMap map = new TerrainMap(1000, 1000, 100);
        holder.set(new BattleState(0, 0, map, java.util.List.of()));

        try (Socket sock = openWebSocket()) {
            // 读初始帧（丢弃）
            readFrame(sock);

            // 发 "ping" 文本帧
            sendTextFrame(sock, "ping");
            String response = readFrame(sock);
            assertEquals("pong", response);
        }
    }

    @Test
    void disconnectRemovesClient() throws Exception {
        holder.set(new BattleState(0, 0, new TerrainMap(1000, 1000, 100),
                java.util.List.of()));
        Socket s1 = openWebSocket();
        readFrame(s1); // 初始帧
        assertEquals(1, wsServer.clientCount());
        s1.close();
        Thread.sleep(200);
        assertEquals(0, wsServer.clientCount());
    }

    @Test
    void multipleClientsBroadcast() throws Exception {
        TerrainMap map = new TerrainMap(1000, 1000, 100);
        holder.set(new BattleState(0, 0, map, java.util.List.of()));

        Socket s1 = openWebSocket();
        Socket s2 = openWebSocket();
        readFrame(s1);
        readFrame(s2);
        assertEquals(2, wsServer.clientCount());

        holder.set(new BattleState(1, 5.0, map, java.util.List.of()));
        String f1 = readFrame(s1);
        String f2 = readFrame(s2);
        assertTrue(f1.contains("tick"));
        assertTrue(f2.contains("tick"));
        s1.close();
        s2.close();
    }

    /* ---- WebSocket 客户端 helper ---- */

    private Socket openWebSocket() throws Exception {
        Socket sock = new Socket("localhost", port + 1);
        sock.setSoTimeout(3000);
        String key = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        String req = "GET /ws/snapshot HTTP/1.1\r\n"
                + "Host: localhost:" + (port + 1) + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "\r\n";
        sock.getOutputStream().write(req.getBytes(StandardCharsets.US_ASCII));
        sock.getOutputStream().flush();

        // 读响应
        InputStream in = sock.getInputStream();
        StringBuilder headers = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            char c = (char) b;
            headers.append(c);
            if (headers.toString().endsWith("\r\n\r\n")) break;
        }
        String h = headers.toString();
        assertTrue(h.contains("101"), "expected 101 Switching Protocols, got: " + h);
        return sock;
    }

    private String connectAndReadFrame() throws Exception {
        try (Socket sock = openWebSocket()) {
            return readFrame(sock);
        }
    }

    private String readFrame(Socket sock) throws Exception {
        InputStream in = sock.getInputStream();
        int b1 = in.read();
        if (b1 < 0) return null;
        int b2 = in.read();
        if (b2 < 0) return null;
        int opcode = b1 & 0x0F;
        long len = b2 & 0x7F;
        if (len == 126) {
            len = ((long) in.read() << 8) | in.read();
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) len = (len << 8) | in.read();
        }
        byte[] payload = new byte[(int) len];
        int read = 0;
        while (read < len) {
            int r = in.read(payload, read, (int) len - read);
            if (r < 0) break;
            read += r;
        }
        if (opcode == 0x1) return new String(payload, StandardCharsets.UTF_8);
        if (opcode == 0x8) return null;
        return ""; // ping/pong/binary skip
    }

    private void sendTextFrame(Socket sock, String text) throws Exception {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(0x81); // FIN + text
        if (payload.length < 126) {
            out.write(0x80 | payload.length);
        } else if (payload.length < 65536) {
            out.write(0x80 | 126);
            out.write((payload.length >> 8) & 0xFF);
            out.write(payload.length & 0xFF);
        }
        // mask key (any 4 bytes)
        byte[] mask = {1, 2, 3, 4};
        out.write(mask);
        for (int i = 0; i < payload.length; i++) {
            out.write(payload[i] ^ mask[i & 3]);
        }
        sock.getOutputStream().write(out.toByteArray());
        sock.getOutputStream().flush();
    }
}
