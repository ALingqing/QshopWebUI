package cn.aqcraft.http;

import cn.aqcraft.QShopWebUIPlugin;
import cn.aqcraft.PluginConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内嵌 Web 服务。
 * <p>支持两种模式：</p>
 * <ul>
 *   <li>standalone —— 独立端口，全部按 HTTP 处理；</li>
 *   <li>multiplex  —— 单端口复用：监听“游戏对外端口”，嗅探首个数据包，
 *       HTTP 流量由插件处理，其余流量原样转发给本机实际运行的 MC 服务器。</li>
 * </ul>
 */
public final class WebServer {

    private static final int MAX_CONNECTIONS = 400;
    private static final int SNIFF_TIMEOUT_MS = 800;
    private static final int SNIFF_MAX_BYTES = 16;
    private static final int FORWARD_BUFFER = 32 * 1024;

    private final QShopWebUIPlugin plugin;

    private volatile ServerSocket server;
    private volatile boolean running;
    private Thread acceptThread;
    private ExecutorService connPool;
    private Router router;

    private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
    private final AtomicInteger connCount = new AtomicInteger();

    private boolean multiplex;
    private String mcHost;
    private int mcPort;

    public WebServer(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void start() throws IOException {
        if (running) return;
        PluginConfig cfg = plugin.config();
        this.multiplex = cfg.multiplex;
        this.mcHost = cfg.mcHost;
        this.mcPort = cfg.mcPortResolved > 0 ? cfg.mcPortResolved : cfg.mcPort;
        this.router = new Router(plugin);

        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress(cfg.bind, cfg.port), 256);
        this.server = ss;
        this.running = true;

        this.connPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(null, r, "QShopWebUI-Conn", 256 * 1024);
            t.setDaemon(true);
            return t;
        });

        this.acceptThread = new Thread(null, this::acceptLoop, "QShopWebUI-Accept", 128 * 1024);
        this.acceptThread.setDaemon(true);
        this.acceptThread.start();

        String modeText = multiplex
                ? "单端口复用 (游戏流量 → " + mcHost + ":" + mcPort + ")"
                : "独立端口";
        plugin.getLogger().info("Web 服务已启动: 0.0.0.0:" + cfg.port + " [" + modeText + "]");
    }

    public synchronized void stop() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
        }
        for (Socket s : connections) {
            closeQuietly(s);
        }
        connections.clear();
        if (connPool != null) {
            connPool.shutdownNow();
        }
        if (acceptThread != null) {
            acceptThread.interrupt();
        }
        server = null;
        plugin.getLogger().info("Web 服务已停止");
    }

    public boolean isRunning() {
        return running;
    }

    public int getActualPort() {
        ServerSocket s = server;
        return s == null ? -1 : s.getLocalPort();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = server.accept();
                if (client == null) continue;
                if (connCount.incrementAndGet() > MAX_CONNECTIONS) {
                    connCount.decrementAndGet();
                    closeQuietly(client);
                    continue;
                }
                client.setTcpNoDelay(true);
                connections.add(client);
                connPool.execute(() -> handleClient(client));
            } catch (IOException e) {
                if (!running || (server != null && server.isClosed())) {
                    break;
                }
                plugin.getLogger().warning("[Web] 接受连接失败: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket client) {
        try {
            byte[] first = sniff(client);
            Boolean verdict = HttpSniff.check(first, first.length);
            boolean isHttp = verdict != null && verdict;

            if (multiplex && !isHttp) {
                if (first.length == 0) {
                    // 端口扫描/空连接：直接关闭
                    closeQuietly(client);
                    return;
                }
                forward(client, first);
            } else {
                new HttpConnection(plugin, client, first, router).run();
            }
        } catch (Exception e) {
            if (plugin.config().debug) {
                plugin.getLogger().warning("[Web] 连接处理异常: " + e);
            }
            closeQuietly(client);
        } finally {
            connections.remove(client);
            connCount.decrementAndGet();
        }
    }

    /** 预读首包（最多 SNIFF_MAX_BYTES 字节），用于协议判定 */
    private byte[] sniff(Socket client) throws IOException {
        client.setSoTimeout(SNIFF_TIMEOUT_MS);
        InputStream in = client.getInputStream();
        byte[] buf = new byte[SNIFF_MAX_BYTES];
        int n = 0;
        while (n < SNIFF_MAX_BYTES) {
            Boolean v = HttpSniff.check(buf, n);
            if (v != null) break;
            int r;
            try {
                r = in.read(buf, n, SNIFF_MAX_BYTES - n);
            } catch (SocketTimeoutException e) {
                break;
            }
            if (r < 0) break;
            n += r;
        }
        byte[] out = new byte[n];
        System.arraycopy(buf, 0, out, 0, n);
        return out;
    }

    /** 把连接转发给真实 MC 服务器（本线程负责 client→up 方向，另起一线程负责反向） */
    private void forward(Socket client, byte[] first) {
        Socket upstream = new Socket();
        try {
            upstream.setTcpNoDelay(true);
            upstream.connect(new InetSocketAddress(mcHost, mcPort), 5000);
            connections.add(upstream);
            if (first.length > 0) {
                OutputStream upOut = upstream.getOutputStream();
                upOut.write(first);
                upOut.flush();
            }
            Thread down = new Thread(null, () -> pump(upstream, client), "QShopWebUI-FwdDown", 256 * 1024);
            down.setDaemon(true);
            down.start();
            pump(client, upstream); // 本线程：client → MC
            try {
                down.join(60000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } catch (IOException e) {
            closeQuietly(client);
        } finally {
            connections.remove(upstream);
            closeQuietly(upstream);
        }
    }

    /** 单向泵：读 from 写 to，EOF 时对 to 半关闭（传播 FIN） */
    private static void pump(Socket from, Socket to) {
        byte[] buf = new byte[FORWARD_BUFFER];
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            int r;
            while ((r = in.read(buf)) >= 0) {
                if (r == 0) continue;
                out.write(buf, 0, r);
                out.flush();
            }
            try {
                to.shutdownOutput();
            } catch (IOException ignored) {
            }
        } catch (IOException ignored) {
            // 任一端断开：静默结束
        } finally {
            closeQuietly(from);
            closeQuietly(to);
        }
    }

    private static void closeQuietly(Socket s) {
        if (s == null) return;
        try {
            s.close();
        } catch (IOException ignored) {
        }
    }
}
