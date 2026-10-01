package com.qshop.webui;

import org.bukkit.configuration.file.FileConfiguration;

/** 插件配置（config.yml） */
public final class PluginConfig {

    public int port;
    public String bind = "0.0.0.0";
    public String mode = "auto";

    public String mcHost = "127.0.0.1";
    public int mcPort = 0;

    public String adminUsername = "admin";
    public String adminPassword = "admin123";
    public int sessionTimeout = 3600;
    public boolean requireAuth = true;

    public int maxPageSize = 500;
    public int defaultPageSize = 60;
    public int maxBodySize = 10 * 1024 * 1024;
    public boolean accessLog = false;
    public long snapshotTtlMs = 5000;
    public boolean debug = false;

    // === 玩家商店展示参数（与原版行为对齐） ===
    public int playerShopMaxBuy = 1000;
    public int playerShopMaxStock = 2000;
    public int playerShopLowStockThreshold = 10;

    // === 网页在线购买 ===
    public boolean purchaseEnabled = true;
    public int purchaseMaxAmount = 64;

    // === 网站显示 ===
    public String serverName = "";

    // === 页面可见性（config.yml pages.hide-*，网页后台可覆盖） ===
    public final java.util.Set<String> hiddenPages = new java.util.HashSet<>();

    // === 运行时解析结果 ===
    public boolean multiplex = false;
    public int mcPortResolved = 0;

    public static PluginConfig load(QShopWebUIPlugin plugin) {
        FileConfiguration c = plugin.getConfig();
        PluginConfig cfg = new PluginConfig();
        cfg.port = c.getInt("port", 20130);
        cfg.bind = c.getString("bind", "0.0.0.0");
        cfg.mode = c.getString("mode", "auto");
        cfg.mcHost = c.getString("mc-host", "127.0.0.1");
        cfg.mcPort = c.getInt("mc-port", 0);
        cfg.adminUsername = c.getString("admin.username", "admin");
        cfg.adminPassword = c.getString("admin.password", "admin123");
        cfg.sessionTimeout = Math.max(60, c.getInt("session-timeout", 3600));
        cfg.requireAuth = c.getBoolean("require-auth", true);
        cfg.maxPageSize = Math.max(1, c.getInt("web.max-page-size", 500));
        cfg.defaultPageSize = Math.max(1, c.getInt("web.default-page-size", 60));
        cfg.maxBodySize = Math.max(1024, c.getInt("web.max-body-size", 10 * 1024 * 1024));
        cfg.accessLog = c.getBoolean("web.access-log", false);
        cfg.snapshotTtlMs = Math.max(1000, c.getLong("web.snapshot-ttl-ms", 5000));
        cfg.debug = c.getBoolean("debug", false);
        cfg.playerShopMaxBuy = c.getInt("shop.player-max-buy", 1000);
        cfg.playerShopMaxStock = c.getInt("shop.player-max-stock", 2000);
        cfg.playerShopLowStockThreshold = c.getInt("shop.player-low-stock-threshold", 10);
        cfg.purchaseEnabled = c.getBoolean("purchase.enabled", true);
        cfg.purchaseMaxAmount = Math.max(1, c.getInt("purchase.max-amount", 64));
        cfg.serverName = c.getString("server-name", "").trim();
        for (String k : new String[]{"home", "buy", "sell", "browse", "shops", "stats"}) {
            if (c.getBoolean("pages.hide-" + k, false)) cfg.hiddenPages.add(k);
        }
        return cfg;
    }

    /** 解析端口模式；serverPort = 当前游戏服务器端口 */
    public void resolve(int serverPort) {
        boolean m;
        if ("multiplex".equalsIgnoreCase(mode) || "single".equalsIgnoreCase(mode)) {
            m = true;
        } else if ("standalone".equalsIgnoreCase(mode) || "independent".equalsIgnoreCase(mode)) {
            m = false;
        } else {
            // auto：网页端口 == 游戏端口 → 单端口复用
            m = serverPort > 0 && port == serverPort;
        }
        this.multiplex = m;
        this.mcPortResolved = m ? (mcPort > 0 ? mcPort : serverPort) : mcPort > 0 ? mcPort : serverPort;
    }
}
