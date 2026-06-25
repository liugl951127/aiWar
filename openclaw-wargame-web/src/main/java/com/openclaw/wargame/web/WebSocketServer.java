package com.openclaw.wargame.web;

import com.openclaw.wargame.analysis.BattleAdvantage;
import com.openclaw.wargame.analysis.TacticalAdvisor;
import com.openclaw.wargame.core.state.BattleState;
import com.openclaw.wargame.core.team.Team;
import com.openclaw.wargame.core.unit.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 极简 WebSocket 服务器（RFC 6455 帧解析，无外部依赖）。
 * <p>
 * 路径：{@code /ws/snapshot}
 * <ul>
 *   <li>客户端连接后，服务器立即推送完整 snapshot JSON</li>
 *   <li>BattleStateHolder.set() 时回调 broadcast()，有新数据立即推</li>
 *   <li>客户端发 "ping" 帧 → 服务器回 "pong"</li>
 *   <li>客户端断开 → 自动清理</li>
 * </ul>
 * <p>
 * 实现要点：
 * <ul>
 *   <li>从 HttpExchange 提取 socket（反射拿私有字段）</li>
 *   <li>完整 HTTP upgrade 握手</li>
 *   <li>解析 client→server 帧（仅处理 text/binary/close/ping/pong）</li>
 *   <li>构造 server→client 帧（mask=0）</li>
 * </ul>
 */
public final class WebSocketServer {
    private static final Logger log = LoggerFactory.getLogger(WebSocketServer.class);

    private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final int port;
    private final String path;
    private final BattleStateHolder holder;
    /** 已连接的 client socket */
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    /** 推送线程：检测 holder 变化 + 广播 */
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = false;
    private volatile com.sun.net.httpserver.HttpServer httpServer;
    private volatile BattleState lastBroadcast;

    public WebSocketServer(int port, String path, BattleStateHolder holder) {
        this.port = port;
        this.path = path;
        this.holder = holder;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ws-broadcaster");
            t.setDaemon(true);
            return t;
        });
    }

    public int port() { return port; }
    public String path() { return path; }
    public int clientCount() { return clients.size(); }

    /** 注册到现有的 HttpServer 上 */
    public void attachTo(com.sun.net.httpserver.HttpServer server) {
        // JDK HttpServer 不支持 WebSocket upgrade（会返回 400）。
        // 因此我们在另一个端口启动独立 ServerSocket，仅认 WebSocket 握手请求。
        log.info("Starting WebSocket on independent port {} (path {})", port + 1, path);
        Thread acceptThread = new Thread(this::acceptLoop, "ws-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    /** 独立端口监听：读取 HTTP 头，识别 WebSocket upgrade 后交握 */
    private void acceptLoop() {
        java.net.ServerSocket server = null;
        try {
            server = new java.net.ServerSocket(port + 1);
            while (running) {
                Socket client = server.accept();
                Thread t = new Thread(() -> handleRawConnection(client), "ws-conn");
                t.setDaemon(true);
                t.start();
            }
        } catch (Exception e) {
            if (running) log.warn("ws acceptLoop error: {}", e.getMessage());
        } finally {
            if (server != null) try { server.close(); } catch (Exception ignored) {}
        }
    }

    private void handleRawConnection(Socket socket) {
        try {
            InputStream in = socket.getInputStream();
            // 读 HTTP 头
            StringBuilder sb = new StringBuilder();
            int b;
            while ((b = in.read()) != -1) {
                sb.append((char) b);
                if (sb.toString().endsWith("\r\n\r\n")) break;
                if (sb.length() > 8192) break;
            }
            String headers = sb.toString();
            if (!headers.contains("Upgrade: websocket") && !headers.contains("upgrade: websocket")) {
                String resp = "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n";
                socket.getOutputStream().write(resp.getBytes(StandardCharsets.US_ASCII));
                socket.close();
                return;
            }
            // 提取 Sec-WebSocket-Key
            String key = null;
            for (String line : headers.split("\r\n")) {
                if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                    key = line.substring("sec-websocket-key:".length()).trim();
                    break;
                }
            }
            if (key == null) {
                socket.close();
                return;
            }
            String accept = computeAccept(key);
            String response = "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n"
                    + "\r\n";
            OutputStream os = socket.getOutputStream();
            synchronized (os) {
                os.write(response.getBytes(StandardCharsets.US_ASCII));
                os.flush();
            }
            clients.add(socket);
            log.info("WS client connected from {} (total={})", socket.getRemoteSocketAddress(), clients.size());

            // 立即推一次
            sendTextFrame(socket, buildSnapshotJson(holder.get()));
            // 读循环
            readLoop(socket);
        } catch (Exception e) {
            log.debug("handleRawConnection exit: {}", e.getMessage());
            clients.remove(socket);
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    /** 启动推送循环 */
    public void startBroadcaster(long intervalMs) {
        running = true;
        scheduler.scheduleAtFixedRate(() -> {
            try {
                BattleState current = holder.get();
                if (current != null && current != lastBroadcast) {
                    broadcast(current);
                    lastBroadcast = current;
                }
            } catch (Exception e) {
                log.warn("broadcast error: {}", e.getMessage());
            }
        }, 200, intervalMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        running = false;
        scheduler.shutdownNow();
        for (Socket s : clients) {
            try { s.close(); } catch (Exception ignored) {}
        }
        clients.clear();
    }

    /* ---- 帧 I/O ---- */

    private void handleUpgrade(com.sun.net.httpserver.HttpExchange exchange) throws Exception {
        // 校验必要 header
        String key = exchange.getRequestHeaders().getFirst("Sec-WebSocket-Key");
        if (key == null) {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
            return;
        }
        String accept = computeAccept(key);

        // 反射拿 HttpExchange 的私有 socket 字段
        Socket socket = extractSocket(exchange);
        if (socket == null) {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
            return;
        }

        // 写握手响应（不能 close，必须保留连接）
        String response = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n"
                + "\r\n";
        OutputStream os = socket.getOutputStream();
        os.write(response.getBytes(StandardCharsets.US_ASCII));
        os.flush();

        clients.add(socket);
        log.info("WebSocket client connected from {} (total={})", socket.getRemoteSocketAddress(), clients.size());

        // 立即推一次 snapshot
        sendTextFrame(socket, buildSnapshotJson(holder.get()));

        // 起一个读循环线程（处理 client 帧）
        Thread reader = new Thread(() -> readLoop(socket), "ws-reader-" + socket.getPort());
        reader.setDaemon(true);
        reader.start();
    }

    private void readLoop(Socket socket) {
        try (InputStream in = socket.getInputStream()) {
            while (running && !socket.isClosed()) {
                Frame frame = readFrame(in);
                if (frame == null) break;
                switch (frame.opcode) {
                    case 0x8 -> { // CLOSE
                        log.debug("client closed");
                        return;
                    }
                    case 0x9 -> { // PING
                        sendPong(socket, frame.payload);
                        break;
                    }
                    case 0x1, 0x2 -> { // TEXT/BINARY
                        // 简单协议：收到 "ping" 文本回 "pong"
                        String msg = new String(frame.payload, StandardCharsets.UTF_8);
                        if ("ping".equals(msg)) {
                            sendTextFrame(socket, "pong");
                        }
                        break;
                    }
                    default -> {} // ignore
                }
            }
        } catch (Exception e) {
            log.debug("readLoop exit: {}", e.getMessage());
        } finally {
            clients.remove(socket);
            try { socket.close(); } catch (Exception ignored) {}
            log.info("WebSocket client disconnected (remaining={})", clients.size());
        }
    }

    private void broadcast(BattleState s) {
        String json = buildSnapshotJson(s);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        for (Socket sock : clients) {
            try {
                sendFrame(sock, (byte) 0x1, bytes);
            } catch (Exception e) {
                clients.remove(sock);
                try { sock.close(); } catch (Exception ignored) {}
            }
        }
    }

    private void sendTextFrame(Socket sock, String text) {
        try {
            sendFrame(sock, (byte) 0x1, text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.debug("sendTextFrame failed: {}", e.getMessage());
        }
    }

    private void sendPong(Socket sock, byte[] payload) {
        try {
            sendFrame(sock, (byte) 0xA, payload == null ? new byte[0] : payload);
        } catch (IOException e) {
            log.debug("sendPong failed: {}", e.getMessage());
        }
    }

    private void sendFrame(Socket sock, byte opcode, byte[] payload) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(opcode | 0x80); // FIN=1
        int len = payload.length;
        if (len < 126) {
            out.write(len);
        } else if (len < 65536) {
            out.write(126);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        } else {
            out.write(127);
            for (int i = 7; i >= 0; i--) {
                out.write((int) ((len >> (i * 8)) & 0xFF));
            }
        }
        out.write(payload);
        synchronized (sock.getOutputStream()) {
            sock.getOutputStream().write(out.toByteArray());
            sock.getOutputStream().flush();
        }
    }

    private static class Frame {
        byte opcode;
        byte[] payload;
    }

    private static Frame readFrame(InputStream in) throws IOException {
        int b1 = in.read();
        if (b1 < 0) return null;
        int b2 = in.read();
        if (b2 < 0) return null;
        Frame f = new Frame();
        f.opcode = (byte) (b1 & 0x0F);
        boolean masked = (b2 & 0x80) != 0;
        long len = b2 & 0x7F;
        if (len == 126) {
            len = ((long) in.read() << 8) | in.read();
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) len = (len << 8) | in.read();
        }
        byte[] mask = null;
        if (masked) {
            mask = new byte[4];
            if (in.read(mask) != 4) return null;
        }
        f.payload = new byte[(int) len];
        int read = 0;
        while (read < len) {
            int r = in.read(f.payload, read, (int) len - read);
            if (r < 0) return null;
            read += r;
        }
        if (masked && mask != null) {
            for (int i = 0; i < f.payload.length; i++) {
                f.payload[i] ^= mask[i & 3];
            }
        }
        return f;
    }

    /* ---- helpers ---- */

    private static String computeAccept(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(key.getBytes(StandardCharsets.US_ASCII));
            md.update(WS_MAGIC.getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(md.digest());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 反射拿 HttpExchange 的 socket（com.sun.net.httpserver 没暴露） */
    private static Socket extractSocket(com.sun.net.httpserver.HttpExchange ex) {
        try {
            // 兼容 JDK 17: 字段名 "impl" 或 "connection"
            String[] candidates = {"impl", "connection", "sock"};
            for (String name : candidates) {
                try {
                    Field f = ex.getClass().getDeclaredField(name);
                    f.setAccessible(true);
                    Object o = f.get(ex);
                    if (o instanceof Socket s) return s;
                } catch (NoSuchFieldException ignored) {}
            }
            // 退路：遍历所有字段
            for (Field f : ex.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    Object o = f.get(ex);
                    if (o instanceof Socket s) return s;
                } catch (IllegalAccessException ignored) {}
            }
        } catch (Exception e) {
            log.error("extractSocket failed", e);
        }
        return null;
    }

    /** 序列化 snapshot JSON（紧凑格式，简化减少字节） */
    private String buildSnapshotJson(BattleState s) {
        if (s == null) return "{\"state\":null}";
        StringBuilder sb = new StringBuilder(1024);
        sb.append("{\"type\":\"snapshot\"");
        sb.append(",\"tick\":").append(s.tick());
        sb.append(",\"t\":").append((long)(s.timeSeconds() * 1000));
        sb.append(",\"running\":").append(holder.isRunning());
        sb.append(",\"winner\":").append(jsonStr(holder.winner()));
        sb.append(",\"w\":").append((int) s.map().widthMeters());
        sb.append(",\"h\":").append((int) s.map().heightMeters());
        sb.append(",\"ba\":").append(s.aliveCount(Team.BLUE));
        sb.append(",\"ra\":").append(s.aliveCount(Team.RED));
        sb.append(",\"units\":[");
        boolean first = true;
        for (Unit u : s.units()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"id\":\"").append(u.id(), 0, 8).append("\"")
              .append(",\"t\":\"").append(u.team().name().charAt(0)).append("\"")
              .append(",\"k\":\"").append(u.type().name()).append("\"")
              .append(",\"x\":").append((int) u.position().x())
              .append(",\"y\":").append((int) u.position().y())
              .append(",\"hp\":").append((int) u.hp())
              .append(",\"mh\":").append((int) u.maxHp())
              .append(",\"s\":\"").append(u.status().name().charAt(0)).append("\"")
              .append(",\"a\":").append(u.isAlive() ? 1 : 0)
              .append(",\"bf\":").append(u.buffs().size())
              .append("}");
        }
        sb.append("]");
        // 嵌入 advisor 报告（如果存在）
        var blueReport = holder.getReport(Team.BLUE);
        var redReport = holder.getReport(Team.RED);
        sb.append(",\"adv\":{");
        if (blueReport != null) sb.append("\"b\":").append(advantageJson(blueReport.advantage())).append(",");
        else sb.append("\"b\":null,");
        if (redReport != null) sb.append("\"r\":").append(advantageJson(redReport.advantage()));
        else sb.append("\"r\":null");
        sb.append("}");
        sb.append("}");
        return sb.toString();
    }

    private String advantageJson(BattleAdvantage adv) {
        return String.format("{\"f\":%.2f,\"m\":%.2f,\"d\":%.2f,\"s\":%.2f,\"c\":%.2f,\"o\":%.2f}",
                adv.firepower(), adv.manpower(), adv.detection(),
                adv.mobility(), adv.cohesion(), adv.overall());
    }

    private String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
