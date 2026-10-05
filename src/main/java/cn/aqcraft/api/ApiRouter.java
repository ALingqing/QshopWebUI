package cn.aqcraft.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cn.aqcraft.QShopWebUIPlugin;
import cn.aqcraft.http.HttpRequest;
import cn.aqcraft.http.HttpResponse;

/**
 * /api/* 分发器。
 * <p>路径与响应结构尽量与原 Node 后端保持一致，前端零改动。</p>
 */
public final class ApiRouter {

    private final QShopWebUIPlugin plugin;
    private final ShopApi shopApi;
    private final ItemApi itemApi;
    private final StatsApi statsApi;
    private final MetaApi metaApi;
    private final AuthApi authApi;
    private final AdminApi adminApi;
    private final BackupApi backupApi;
    private final PurchaseApi purchaseApi;
    private final EnhancedApi enhancedApi;

    public ApiRouter(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
        this.shopApi = new ShopApi(plugin);
        this.itemApi = new ItemApi(plugin);
        this.statsApi = new StatsApi(plugin);
        this.metaApi = new MetaApi(plugin);
        this.authApi = new AuthApi(plugin);
        this.adminApi = new AdminApi(plugin);
        this.backupApi = new BackupApi(plugin);
        this.purchaseApi = new PurchaseApi(plugin);
        this.enhancedApi = new EnhancedApi(plugin);
    }

    public HttpResponse handle(HttpRequest req) {
        plugin.requestStats().record(req);
        String p = req.path == null ? "/" : req.path;
        String m = req.method == null ? "GET" : req.method;
        try {
            return route(m, p, req);
        } catch (Throwable t) {
            plugin.getLogger().warning("[API] " + m + " " + req.target + " 异常: " + t);
            return HttpResponse.error(500, "服务器内部错误");
        }
    }

    private HttpResponse route(String m, String p, HttpRequest req) {
        // ============================================================
        // 精确路径
        // ============================================================
        switch (p) {
            case "/api/health":
                return statsApi.health(req);
            case "/api/config":
                return metaApi.config(req);

            case "/api/stats":
                return statsApi.stats(req);
            case "/api/stats/realtime":
                return statsApi.statsRealtime(req);
            case "/api/stats/requests":
                return statsApi.statsRequests(req);

            case "/api/shops":
                if ("GET".equals(m)) return shopApi.list(req);
                if ("DELETE".equals(m)) return shopApi.clearAll(req);
                if ("POST".equals(m)) return HttpResponse.error(400,
                        "插件版商店数据来自游戏内 QuickShop，不支持通过网页写入");
                break;
            case "/api/shops/search":
                return shopApi.list(req);
            case "/api/shops/top":
                return shopApi.top(req);
            case "/api/shops/seed":
            case "/api/shops/seed/terminate":
            case "/api/shops/seed/cancel":
                return shopApi.seedDisabled(req);
            case "/api/shops/seed/progress": {
                JsonObject o = new JsonObject();
                o.addProperty("running", false);
                o.addProperty("progress", 0);
                o.addProperty("inserted", 0);
                o.addProperty("total", 0);
                o.addProperty("cancelled", false);
                o.addProperty("terminated", false);
                o.add("started_at", null);
                o.addProperty("message", "插件版无需生成测试数据");
                return HttpResponse.json(o);
            }

            case "/api/materials":
                return shopApi.materials(req);
            case "/api/owners":
                return shopApi.owners(req);
            case "/api/worlds":
                return shopApi.worlds(req);

            case "/api/items":
                return itemApi.items(req);

            case "/api/log":
                return metaApi.log(req);
            case "/api/activity":
                return metaApi.activityGet(req);

            case "/api/settings":
                if ("GET".equals(m)) return metaApi.settingsGet(req);
                if ("PUT".equals(m)) return metaApi.settingsPut(req);
                break;

            case "/api/qsfilter/status":
                return statsApi.qsfilterStatus(req);
            case "/api/qsfilter/reconnect":
                return statsApi.qsfilterReconnect(req);

            case "/api/sync/status":
                return statsApi.syncStatus(req);
            case "/api/sync/now":
                return statsApi.syncNow(req);

            case "/api/allocate":
                return metaApi.allocate(req);
            case "/api/allocate/config":
                return metaApi.allocateConfig(req);

            case "/api/admin/shops/search":
                return adminApi.shopsSearch(req);
            case "/api/admin/shops/batch":
                return adminApi.shopsBatch(req);
            case "/api/admin/config":
                if ("GET".equals(m)) return adminApi.adminConfigGet(req);
                return adminApi.adminConfigPost(req);
            case "/api/admin/dashboard":
                return statsApi.dashboard(req);

            case "/api/export/shops":
                return adminApi.exportShops(req);
            case "/api/import/shops":
                return adminApi.importShops(req);
            case "/api/server/restart":
                return adminApi.serverRestart(req);

            case "/api/backup/status":
                return backupApi.status(req);
            case "/api/backup/list":
                return backupApi.list(req);
            case "/api/backup/now":
                return backupApi.now(req);
            case "/api/backup/restore":
                return backupApi.restore(req);
            case "/api/backup/cleanup":
                return backupApi.cleanup(req);

            case "/api/announcements":
                if ("GET".equals(m)) return metaApi.announcementsGet(req);
                if ("POST".equals(m)) return metaApi.announcementsPost(req);
                break;

            case "/api/auth/login":
                return authApi.login(req);
            case "/api/auth/logout":
                return authApi.logout(req);
            case "/api/auth/status":
                return authApi.status(req);
            case "/api/auth/me":
                return authApi.me(req);
            case "/api/auth/register":
                return authApi.register(req);
            case "/api/auth/change-password":
                return authApi.changePassword(req);

            case "/api/purchase":
                return purchaseApi.purchase(req);

            case "/api/sell":
                return purchaseApi.sell(req);

            case "/api/inventory-check":
                return purchaseApi.inventoryCheck(req);

            case "/api/limit":
                return purchaseApi.limit(req);

            case "/api/wallet":
                return authApi.wallet(req);

            // ================= P1/P2 增强功能 =================
            case "/api/orders":
                if ("GET".equals(m)) return enhancedApi.orders(req);
                break;
            case "/api/orders/me":
                return enhancedApi.myOrders(req);
            case "/api/orders/clear":
                if ("POST".equals(m)) return enhancedApi.ordersClear(req);
                break;
            case "/api/compare":
                return enhancedApi.compare(req);
            case "/api/compare/all":
                return enhancedApi.compareAll(req);
            case "/api/favorites":
                if ("GET".equals(m)) return enhancedApi.favoritesList(req);
                break;
            case "/api/favorites/toggle":
                if ("POST".equals(m)) return enhancedApi.favoritesToggle(req);
                break;
            case "/api/stats/business":
                return enhancedApi.businessStats(req);
            case "/api/audit":
                if ("GET".equals(m)) return enhancedApi.audit(req);
                break;
            case "/api/audit/clear":
                if ("POST".equals(m)) return enhancedApi.auditClear(req);
                break;
            case "/api/status":
                return enhancedApi.shopStatus(req);
            case "/api/status/open":
                if ("POST".equals(m)) return enhancedApi.setOpen(req);
                break;
            case "/api/status/maintenance":
                if ("POST".equals(m)) return enhancedApi.setMaintenance(req);
                break;
            case "/api/monitor/stock":
                return enhancedApi.stockMonitor(req);
            case "/api/notify/status":
                return enhancedApi.notifyStatus(req);

            case "/api/pages":
                return "GET".equalsIgnoreCase(m) ? metaApi.pagesGet(req) : metaApi.pagesSet(req);

            case "/api/trades":
                return adminApi.trades(req);

            case "/api/trades/clear":
                return adminApi.tradeClear(req);

            case "/api/trades/import":
                return adminApi.tradeImport(req);

            case "/api/admin/balances":
                return adminApi.balances(req);

            case "/api/admin/shops/removals":
                return adminApi.shopRemovals(req);

            case "/api/harbor":
                if ("GET".equals(m)) return shopApi.harborGet(req);
                if ("PUT".equals(m)) return shopApi.harborPut(req);
                break;

            default:
                break;
        }

        // ============================================================
        // 动态路径
        // ============================================================
        if (p.startsWith("/api/shops/")) {
            String rest = p.substring("/api/shops/".length());
            if (!rest.isEmpty()) {
                if ("GET".equals(m)) return shopApi.getById(req, rest);
                if ("PUT".equals(m)) return shopApi.update(req, rest);
                if ("DELETE".equals(m)) return shopApi.delete(req, rest);
                return HttpResponse.error(405, "Method Not Allowed");
            }
        }

        if (p.startsWith("/api/orders/")) {
            String rest = p.substring("/api/orders/".length());
            if (!rest.isEmpty() && "GET".equals(m)) {
                return enhancedApi.orderById(req, rest);
            }
        }

        if (p.startsWith("/api/items/")) {
            String material = p.substring("/api/items/".length());
            if (!material.isEmpty() && "GET".equals(m)) {
                return itemApi.itemDetail(req, material);
            }
        }

        if (p.startsWith("/api/price/")) {
            String itemId = p.substring("/api/price/".length());
            if (!itemId.isEmpty() && "GET".equals(m)) {
                return shopApi.price(req, itemId);
            }
        }

        if (p.startsWith("/api/activity/")) {
            String material = p.substring("/api/activity/".length());
            if (!material.isEmpty() && "PUT".equals(m)) {
                return metaApi.activityPut(req, material);
            }
        }

        if (p.startsWith("/api/settings/")) {
            String key = p.substring("/api/settings/".length());
            if (!key.isEmpty()) {
                if ("GET".equals(m)) return metaApi.settingGet(req, key);
                if ("PUT".equals(m)) return metaApi.settingPut(req, key);
            }
        }

        if (p.startsWith("/api/backup/")) {
            String file = p.substring("/api/backup/".length());
            if (!file.isEmpty() && "DELETE".equals(m)) {
                return backupApi.delete(req, file);
            }
        }

        if (p.startsWith("/api/announcements/")) {
            String id = p.substring("/api/announcements/".length());
            if (!id.isEmpty()) {
                if ("PUT".equals(m)) return metaApi.announcementsPut(req, id);
                if ("DELETE".equals(m)) return metaApi.announcementsDelete(req, id);
            }
        }

        // 旧版 WebHook 端点：插件版不需要（数据直连），返回成功避免插件端报错
        if (p.startsWith("/api/webhook/") || p.startsWith("/webhook/")) {
            JsonObject o = ApiRouter.obj();
            o.addProperty("success", true);
            o.addProperty("note", "插件版已直连 QuickShop，无需 WebHook 同步");
            return HttpResponse.json(o);
        }

        return HttpResponse.notFound();
    }

    private static JsonObject obj() {
        JsonObject o = new JsonObject();
        return o;
    }
}
