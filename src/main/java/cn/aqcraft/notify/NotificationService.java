package cn.aqcraft.notify;

import cn.aqcraft.QShopWebUIPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 通知适配器：把库存告警 / 订单事件 / 服务器事件通过 Webhook（或可选的通知 URL）
 * 推送给外部服务（如 QQ 机器人、钉钉、企业微信等）。
 *
 * <p>启用条件：config.yml 里 {@code notifications.webhook-url} 非空。
 * 发送走异步线程，不阻塞交易主流程；带失败告警冷却，避免刷屏。</p>
 */
public final class NotificationService {

    private final QShopWebUIPlugin plugin;
    private final HttpClient client;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "QShopWebUI-Notify");
        t.setDaemon(true);
        return t;
    });
    private final AtomicLong lastWarnAt = new AtomicLong();
    private final AtomicBoolean warned = new AtomicBoolean();
    private volatile String webhookUrl = "";

    public NotificationService(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        applyConfig();
    }

    public void applyConfig() {
        String url = plugin.config().notificationWebhookUrl;
        this.webhookUrl = url == null ? "" : url.trim();
        warned.set(false);
    }

    /** 是否已配置通知地址 */
    public boolean available() {
        return webhookUrl != null && !webhookUrl.isEmpty();
    }

    /**
     * 推送一条通知（title + body 字段，JSON POST）。
     * 未配置 / 失败静默；连续失败会降级为仅控制台提示（冷却 60s）。
     */
    public void push(String title, String body) {
        if (!available()) return;
        String url = webhookUrl;
        exec.execute(() -> {
            try {
                String json = "{\"title\":\"" + escape(title) + "\",\"text\":\"" + escape(body) + "\","
                        + "\"server\":\"" + escape(plugin.config().serverName.isEmpty() ? "QShopWebUI" : plugin.config().serverName) + "\"}";
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header("User-Agent", "QShopWebUI/1.1")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                int code = resp.statusCode();
                if (code >= 200 && code < 300) {
                    warned.set(false);
                    return;
                }
                long now = System.currentTimeMillis();
                if (warned.compareAndSet(false, true)) {
                    lastWarnAt.set(now);
                    plugin.getLogger().warning("[通知] Webhook 返回 " + code + "：" + resp.body());
                }
            } catch (Throwable t) {
                long now = System.currentTimeMillis();
                if (warned.compareAndSet(false, true) || now - lastWarnAt.get() > 60_000L) {
                    lastWarnAt.set(now);
                    plugin.getLogger().warning("[通知] Webhook 发送失败: " + t.getMessage());
                }
            }
        });
    }

    /** 发送订单事件通知（标题固定，正文含交易摘要） */
    public void order(String type, String item, int amount, double total, String player) {
        String cn = "BUY".equalsIgnoreCase(type) ? "购买" : "收购";
        push("【" + cn + "订单】" + item + " ×" + amount,
                player + " 完成" + cn + "：\n" + item + " ×" + amount + "\n金额 " + total);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}