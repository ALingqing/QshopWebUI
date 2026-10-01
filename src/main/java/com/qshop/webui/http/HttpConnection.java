package com.qshop.webui.http;

import com.qshop.webui.QShopWebUIPlugin;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

/** 单个 HTTP 连接的处理（含 keep-alive 循环） */
public final class HttpConnection {

    private static final int KEEP_ALIVE_TIMEOUT_MS = 15000;

    private final QShopWebUIPlugin plugin;
    private final Socket socket;
    private final byte[] prefix;
    private final Router router;

    public HttpConnection(QShopWebUIPlugin plugin, Socket socket, byte[] prefix, Router router) {
        this.plugin = plugin;
        this.socket = socket;
        this.prefix = prefix;
        this.router = router;
    }

    public void run() {
        long t0 = System.currentTimeMillis();
        try (Socket s = socket) {
            InputStream in = new HttpParser.PrefixInputStream(prefix, s.getInputStream());
            OutputStream out = new BufferedOutputStream(s.getOutputStream(), 32 * 1024);
            int reqCount = 0;
            while (!s.isClosed()) {
                s.setSoTimeout(reqCount == 0 ? KEEP_ALIVE_TIMEOUT_MS : KEEP_ALIVE_TIMEOUT_MS);
                HttpRequest req;
                try {
                    req = HttpParser.parse(in, plugin.config().maxBodySize);
                } catch (HttpParser.BadRequest bad) {
                    HttpParser.writeResponse(out, null,
                            HttpResponse.error(400, "请求格式错误: " + bad.getMessage()), false);
                    break;
                } catch (SocketTimeoutException timeout) {
                    break;
                } catch (IOException io) {
                    break;
                }
                if (req == null) break;
                reqCount++;
                req.clientIp = s.getInetAddress() == null ? "unknown" : s.getInetAddress().getHostAddress();

                HttpResponse resp;
                long rt0 = System.currentTimeMillis();
                try {
                    resp = router.handle(req);
                } catch (Throwable t) {
                    plugin.getLogger().warning("[Web] " + req.method + " " + req.target + " 处理异常: " + t);
                    resp = HttpResponse.error(500, "服务器内部错误");
                }
                boolean head = "HEAD".equals(req.method);
                HttpParser.writeResponse(out, req, resp, head);

                if (plugin.config().accessLog) {
                    plugin.getLogger().info("[Web] " + req.clientIp + " " + req.method + " " + req.target
                            + " → " + resp.status + " (" + (System.currentTimeMillis() - rt0) + "ms)");
                }
                if (resp.close || !req.keepAlive) break;
            }
        } catch (IOException ignored) {
        } finally {
            if (plugin.config().accessLog) {
                plugin.getLogger().info("[Web] 连接关闭 (" + (System.currentTimeMillis() - t0) + "ms)");
            }
        }
    }
}
