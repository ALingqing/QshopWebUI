package cn.aqcraft;

import cn.aqcraft.auth.SessionManager;
import cn.aqcraft.bridge.AuthMeBridge;
import cn.aqcraft.bridge.EconomyBridge;
import cn.aqcraft.bridge.QuickShopBridge;
import cn.aqcraft.data.RequestStats;
import cn.aqcraft.data.ShopDataService;
import cn.aqcraft.data.WebStore;
import cn.aqcraft.http.WebServer;
import cn.aqcraft.listener.GameTradeListener;
import cn.aqcraft.listener.PurchaseJoinListener;
import cn.aqcraft.listener.ShopDataListener;
import cn.aqcraft.listener.ShopRemovalListener;
import cn.aqcraft.purchase.PurchaseService;
import cn.aqcraft.util.Materials;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * QShopWebUI 主类。
 *
 * <p>把商店网页系统作为单个 Paper 插件运行：直接读取 QuickShop-Hikari 数据，
 * 内嵌 Web 服务器；支持「单端口复用」（HTTP / Minecraft 流量自动分流）。</p>
 */
public final class QShopWebUIPlugin extends JavaPlugin implements CommandExecutor {

    private PluginConfig config;
    private WebStore store;
    private QuickShopBridge bridge;
    private ShopDataService shopData;
    private SessionManager sessions;
    private RequestStats requestStats;
    private WebServer webServer;
    private EconomyBridge economy;
    private PurchaseService purchases;
    private AuthMeBridge authme;

    // ============================================================
    // 生命周期
    // ============================================================

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            Materials.init(this);
        cn.aqcraft.util.Pinyin.init(this);
        } catch (Throwable t) {
            getLogger().warning("材质中文表初始化失败: " + t);
        }

        config = PluginConfig.load(this);
        config.resolve(resolveServerPort());

        store = new WebStore(this, getDataFolder());
        store.load();

        bridge = new QuickShopBridge(this);
        bridge.reload();

        economy = new EconomyBridge(this);
        economy.reload();
        purchases = new PurchaseService(this);

        authme = new AuthMeBridge(this);
        authme.reload();
        getServer().getPluginManager().registerEvents(new PurchaseJoinListener(this), this);
        GameTradeListener.register(this);
        ShopRemovalListener.register(this);
        ShopDataListener.register(this);

        shopData = new ShopDataService(this, bridge);
        sessions = new SessionManager(this);
        requestStats = new RequestStats();
        webServer = new WebServer(this);

        startWeb();

        // 每 10 秒把网页访问计数落盘
        try {
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                try {
                    store.flushActivity();
                } catch (Throwable ignored) {
                }
            }, 200L, 200L);
        } catch (Throwable ignored) {
        }

        if (getCommand("qshopwebui") != null) {
            getCommand("qshopwebui").setExecutor(this);
        }

        getLogger().info("QShopWebUI 已启用 — " + bridge.getStatus());
    }

    @Override
    public void onDisable() {
        if (webServer != null) {
            webServer.stop();
        }
        if (store != null) {
            try {
                store.flushActivity();
            } catch (Throwable ignored) {
            }
            try {
                store.flushSaves();
            } catch (Throwable ignored) {
            }
        }
        getLogger().info("QShopWebUI 已停用");
    }

    // ============================================================
    // 启停 Web 服务
    // ============================================================

    private void startWeb() {
        try {
            webServer.start();
        } catch (IOException e) {
            getLogger().severe("==============================================");
            getLogger().severe(" Web 服务启动失败: " + e.getMessage());
            if (config.multiplex) {
                getLogger().severe(" 当前为【单端口复用】模式，端口 " + config.port + " 可能仍被 Minecraft 占用。");
                getLogger().severe(" 请把 server.properties 中的 server-port 改成另一个端口（如 25565），");
                getLogger().severe(" 然后执行 /qshopwebui reload 或重启服务器。");
            } else {
                getLogger().severe(" 端口 " + config.port + " 可能已被占用，或面板未放行该端口。");
            }
            getLogger().severe("==============================================");
            return;
        }
        if (config.multiplex) {
            getLogger().info("单端口复用已开启: 端口 " + config.port
                    + " → 游戏流量转交 127.0.0.1:" + config.mcPortResolved + "，网页由插件处理");
        }
        getLogger().info("网页访问: http://<服务器IP>:" + config.port + "/" + "（管理员账号: " + config.adminUsername + "）");
    }

    private void stopWeb() {
        if (webServer != null) {
            webServer.stop();
        }
    }

    // ============================================================
    // 命令
    // ============================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("qshopwebui.admin")) {
            sender.sendMessage("§c权限不足");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "status": {
                sender.sendMessage("§6===== QShopWebUI 状态 =====");
                sender.sendMessage("§e监听端口: §f" + config.port + (webServer.isRunning() ? " §a(运行中)" : " §c(未运行)"));
                sender.sendMessage("§e端口模式: §f" + (config.multiplex ? "单端口复用" : "独立端口")
                        + (config.multiplex ? " §7(游戏→ 127.0.0.1:" + config.mcPortResolved + ")" : ""));
                sender.sendMessage("§eQuickShop: §f" + bridge.getStatus());
                sender.sendMessage("§e商店数量: §f" + shopData.stats().total
                        + " §7(出售 " + shopData.stats().selling + " / 收购 " + shopData.stats().buying + ")");
                sender.sendMessage("§e数据目录: §f" + new File(getDataFolder(), "data").getPath());
                return true;
            }
            case "reload": {
                reloadPlugin();
                sender.sendMessage("§aQShopWebUI 已重载" + (webServer.isRunning() ? "" : "（注意：Web 服务未运行，请查看控制台日志）"));
                return true;
            }
            case "port": {
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /qshopwebui port <端口>");
                    return true;
                }
                int p;
                try {
                    p = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c无效端口");
                    return true;
                }
                if (p < 1 || p > 65535) {
                    sender.sendMessage("§c端口范围 1-65535");
                    return true;
                }
                getConfig().set("port", p);
                saveConfig();
                reloadPlugin();
                sender.sendMessage("§a端口已改为 " + p + (webServer.isRunning() ? " 并已重绑" : "，但绑定失败，请查看控制台"));
                return true;
            }
            default:
                help(sender);
                return true;
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§6===== QShopWebUI =====");
        sender.sendMessage("§e/qshopwebui status §7- 查看运行状态");
        sender.sendMessage("§e/qshopwebui reload §7- 重载配置");
        sender.sendMessage("§e/qshopwebui port <端口> §7- 修改并重绑端口");
    }

    // ============================================================
    // 重载
    // ============================================================

    public void reloadPlugin() {
        reloadConfig();
        config = PluginConfig.load(this);
        config.resolve(resolveServerPort());
        bridge.reload();
        economy.reload();
        if (authme != null) authme.reload();
        stopWeb();
        startWeb();
    }

    /** 读取当前游戏端口（绑定失败时回退读取 server.properties） */
    private int resolveServerPort() {
        try {
            int p = getServer().getPort();
            if (p > 0) return p;
        } catch (Throwable ignored) {
        }
        try {
            File f = new File("server.properties");
            if (f.isFile()) {
                for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                    String t = line.trim();
                    if (t.startsWith("server-port=")) {
                        return Integer.parseInt(t.substring("server-port=".length()).trim());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return 25565;
    }

    // ============================================================
    // 供其他组件使用
    // ============================================================

    public PluginConfig config() {
        return config;
    }

    public WebStore store() {
        return store;
    }

    public QuickShopBridge bridge() {
        return bridge;
    }

    public ShopDataService shopData() {
        return shopData;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public RequestStats requestStats() {
        return requestStats;
    }

    public WebServer webServer() {
        return webServer;
    }

    public EconomyBridge economy() {
        return economy;
    }

    public PurchaseService purchases() {
        return purchases;
    }

    public AuthMeBridge authme() {
        return authme;
    }

    /** 网页修改密码后刷新内存中的配置 */
    public void applyAdminPassword(String hashed) {
        if (config != null) {
            config.adminPassword = hashed;
        }
    }
}
