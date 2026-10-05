package cn.aqcraft.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import cn.aqcraft.QShopWebUIPlugin;
import cn.aqcraft.data.ShopEntry;
import cn.aqcraft.http.HttpRequest;
import cn.aqcraft.http.HttpResponse;

import java.util.List;
import java.util.Map;

/**
 * P1/P2 增强功能 API：
 * 订单流水、我的订单、比价、收藏、经营统计、审计、营业状态、库存监控、通知。
 */
public final class EnhancedApi extends ApiBase {

    public EnhancedApi(QShopWebUIPlugin plugin) {
        super(plugin);
    }

    // ============================================================
    // 订单流水
    // ============================================================

    /** GET /api/orders 管理端：全部订单（支持 type/player/limit/offset） */
    public HttpResponse orders(HttpRequest req) {
        HttpResponse denied = adminOnly(req);
        if (denied != null) return denied;
        JsonArray arr = new JsonArray();
        for (JsonObject j : plugin.orders().snapshotPaged(req.param("type", null),
                req.param("player", null), req.intParam("limit", 50), req.intParam("offset", 0))) {
            arr.add(j);
        }
        JsonObject o = obj();
        o.addProperty("success", true);
        o.addProperty("total", plugin.orders().countAll());
        o.add("orders", arr);
        return HttpResponse.json(o);
    }

    /** GET /api/orders/me 玩家自助查询（需 player + code 验证码） */
    public HttpResponse myOrders(HttpRequest req) {
        String player = req.param("player", "");
        String code = req.param("code", "");
        if (player.isEmpty() || code.isEmpty()) {
            return HttpResponse.error(400, "需要 player 与 code（游戏内 /qshopwebui code 生成）");
        }
        if (!plugin.gameCodes().check(player, code)) {
            return HttpResponse.error(403, "验证码无效或已过期");
        }
        String type = req.param("type", null);
        int limit = req.intParam("limit", 20);
        int offset = req.intParam("offset", 0);
        JsonArray arr = new JsonArray();
        for (JsonObject j : plugin.orders().byPlayer(player, type, limit, offset)) arr.add(j);
        JsonObject o = obj();
        o.addProperty("success", true);
        o.addProperty("total", plugin.orders().countByPlayer(player, type));
        o.add("orders", arr);
        return HttpResponse.json(o);
    }

    /** GET /api/orders/{id} 单笔订单（管理端） */
    public HttpResponse orderById(HttpRequest req, String id) {
        HttpResponse denied = adminOnly(req);
        if (denied != null) return denied;
        JsonObject order = plugin.orders().byId(id);
        if (order == null) return HttpResponse.notFound();
        JsonObject o = obj();
        o.addProperty("success", true);
        o.add("order", order);
        return HttpResponse.json(o);
    }

    /** POST /api/orders/clear 清空订单（管理端） */
    public HttpResponse ordersClear(HttpRequest req) {
        HttpResponse denied = adminOnly(req);
        if (denied != null) return denied;
        int cleared = plugin.orders().countAll();
        plugin.orders().clear();
        plugin.audit().log("admin", "orders.clear", "清空订单流水 " + cleared + " 条");
        JsonObject o = obj();
        o.addProperty("success", true);
        o.addProperty("cleared", cleared);
        return HttpResponse.json(o);
    }

    // ============================================================
    // 比价
    // ============================================================

    /** GET /api/compare?material=xxx 同物品比价 */
    public HttpResponse compare(HttpRequest req) {
        String material = req.param("material", "");
        if (material.isEmpty()) return HttpResponse.error(400, "需要 material 参数");
        JsonObject result = plugin.comparison().compare(material, plugin.shopData().shops());
        JsonObject o = obj();
        o.addProperty("success", true);
        o.add("result", result);
        return HttpResponse.json(o);
    }

    /** GET /api/compare/all 全物品聚合（最低卖出/最高收购） */
    public HttpResponse compareAll(HttpRequest req) {
        JsonArray agg = plugin.comparison().aggregate(plugin.shopData().shops());
        JsonObject o = obj();
        o.addProperty("success", true);
        o.add("items", agg);
        return HttpResponse.json(o);
    }

    // ============================================================
    // 收藏
    // ============================================================

    /** GET /api/favorites?player=xxx&code=xxx 玩家收藏列表 */
    public HttpResponse favoritesList(HttpRequest req) {
        String player = req.param("player", "");
        String code = req.param("code", "");
        if (player.isEmpty() || code.isEmpty()) {
            return HttpResponse.error(400, "需要 player 与 code");
        }
        if (!plugin.gameCodes().check(player, code)) {
            return HttpResponse.error(403, "验证码无效或已过期");
        }
        List<String> ids = plugin.favorites().favorites(player);
        JsonArray shops = new JsonArray();
        Map<String, ShopEntry> byId = new java.util.LinkedHashMap<>();
        for (ShopEntry e : plugin.shopData().shops()) byId.put(e.shop_id, e);
        for (String id : ids) {
            ShopEntry e = byId.get(id);
            if (e == null) continue;
            JsonObject so = new JsonObject();
            so.addProperty("shop_id", e.shop_id);
            so.addProperty("item", e.shop_cn_name);
            so.addProperty("material", e.material);
            so.addProperty("price_display", e.price_display);
            so.addProperty("price", e.price);
            so.addProperty("type", e.shop_type);
            so.addProperty("owner", e.owner_name);
            so.addProperty("world", e.world);
            so.addProperty("x", e.x);
            so.addProperty("y", e.y);
            so.addProperty("z", e.z);
            shops.add(so);
        }
        JsonObject o = obj();
        o.addProperty("success", true);
        o.add("favorites", shops);
        return HttpResponse.json(o);
    }

    /** POST /api/favorites/toggle 添加/移除收藏（body: player, code, shop_id） */
    public HttpResponse favoritesToggle(HttpRequest req) {
        JsonObject b = body(req);
        String player = jstr(b, "player", "");
        String code = jstr(b, "code", "");
        String shopId = jstr(b, "shop_id", "");
        if (player.isEmpty() || code.isEmpty() || shopId.isEmpty()) {
            return HttpResponse.error(400, "需要 player、code、shop_id");
        }
        if (!plugin.gameCodes().check(player, code)) {
            return HttpResponse.error(403, "验证码无效或已过期");
        }
        boolean now = plugin.favorites().toggle(player, shopId);
        plugin.favorites().flush();
        JsonObject o = obj();
        o.addProperty("success", true);
        o.addProperty("favorited", now);
        o.addProperty("shop_id", shopId);
        return HttpResponse.json(o);
    }

    // ============================================================
    // 经营统计
    // ============================================================

    /** GET /api/stats/business?days=30 经营统计仪表盘 */
    public HttpResponse businessStats(HttpRequest req) {
        int days = req.intParam("days", 30);
        JsonArray snap = plugin.stats().snapshot(days);
        JsonObject totals = plugin.stats().totals();
        JsonObject o = obj();
        o.addProperty("success", true);
        o.add("days", snap);
        o.add("totals", totals);
        return HttpResponse.json(o);
    }

    // ============================================================
    // 审计日志
    // ============================================================

    /** GET /api/audit 审计日志（管理端） */
    public HttpResponse audit(HttpRequest req) {
        HttpResponse denied = adminOnly(req);
        if (denied != null) return denied;
        JsonArray arr = new JsonArray();
        for (JsonObject j : plugin.audit().snapshot(req.intParam("limit", 50), req.intParam("offset", 0))) {
            arr.add(j);
        }
        JsonObject o = obj();
        o.addProperty("success", true);
        o.addProperty("total", plugin.audit().count());
        o.add("entries", arr);
        return HttpResponse.json(o);
    }

    /** POST /api/audit/clear 清空审计（管理端） */
    public HttpResponse auditClear(HttpRequest req) {
        HttpResponse denied = adminOnly(req);
        if (denied != null) return denied;
        plugin.audit().clear();
        JsonObject o = obj();
        o.addProperty("success", true);
        return HttpResponse.json(o);
    }

    // ============================================================
    // 营业状态
    // ============================================================

    /** GET /api/status 营业状态 */
    public HttpResponse shopStatus(HttpRequest req) {
        JsonObject snap = plugin.shopStatus().snapshot();
        JsonObject o = obj();
        o.addProperty("success", true);
        o.add("status", snap);
        return HttpResponse.json(o);
    }

    /** POST /api/status/open 开/关业（body: open bool；公开可调用） */
    public HttpResponse setOpen(HttpRequest req) {
        JsonObject b = body(req);
        boolean open = !b.has("open") || b.get("open").getAsBoolean();
        plugin.shopStatus().setOpen(open, "网页切换");
        plugin.audit().log("web", "shop.status", (open ? "开店" : "关店"));
        JsonObject o = obj();
        o.addProperty("success", true);
        o.addProperty("open", open);
        return HttpResponse.json(o);
    }

    /** POST /api/status/maintenance 维护模式（管理端） */
    public HttpResponse setMaintenance(HttpRequest req) {
        HttpResponse denied = adminOnly(req);
        if (denied != null) return denied;
        JsonObject b = body(req);
        boolean maintenance = jstr(b, "on", "true").equalsIgnoreCase("true");
        plugin.shopStatus().setMaintenance(maintenance);
        plugin.audit().log("admin", "shop.maintenance", (maintenance ? "开启维护模式" : "关闭维护模式"));
        JsonObject o = obj();
        o.addProperty("success", true);
        o.addProperty("maintenance", maintenance);
        return HttpResponse.json(o);
    }

    // ============================================================
    // 库存监控
    // ============================================================

    /** GET /api/monitor/stock?live=true 库存告警列表；live=true 走实时读取容器 */
    public HttpResponse stockMonitor(HttpRequest req) {
        boolean live = "true".equalsIgnoreCase(req.param("live", "false"));
        List<ShopEntry> shops = plugin.shopData().shops();
        List<JsonObject> alerts;
        if (live) {
            alerts = plugin.stockAlerts().scanLive(shops, plugin.purchases()::stockOf);
        } else {
            alerts = plugin.stockAlerts().scan(shops);
        }
        JsonArray arr = new JsonArray();
        for (JsonObject a : alerts) arr.add(a);
        JsonObject o = obj();
        o.addProperty("success", true);
        o.addProperty("live", live);
        o.add("alerts", arr);
        return HttpResponse.json(o);
    }

    // ============================================================
    // 通知
    // ============================================================

    /** GET /api/notify/status 通知配置状态 */
    public HttpResponse notifyStatus(HttpRequest req) {
        JsonObject o = obj();
        o.addProperty("success", true);
        o.addProperty("available", plugin.notifications().available());
        o.addProperty("configured", plugin.notifications().available());
        return HttpResponse.json(o);
    }
}