package com.qshop.webui.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qshop.webui.QShopWebUIPlugin;
import com.qshop.webui.data.ShopEntry;
import com.qshop.webui.http.HttpRequest;
import com.qshop.webui.http.HttpResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** 管理端：高级筛选 / 批量操作 / 导出导入 / 配置查看 */
public final class AdminApi extends ApiBase {

    public AdminApi(QShopWebUIPlugin plugin) {
        super(plugin);
    }

    // ============================================================
    // GET /api/admin/shops/search
    // ============================================================

    public HttpResponse shopsSearch(HttpRequest req) {
        List<ShopEntry> all = plugin.shopData().shops();
        String kw = req.param("q", "").trim().toLowerCase(Locale.ROOT);
        String mat = req.param("material", "").trim().toUpperCase(Locale.ROOT);
        String owner = req.param("owner", "").trim().toLowerCase(Locale.ROOT);
        String world = req.param("world", "").trim();
        String type = req.param("shop_type", "").trim().toUpperCase(Locale.ROOT);
        Double minP = parseDouble(req.param("min_price"));
        Double maxP = parseDouble(req.param("max_price"));
        Integer minAct = parseInt(req.param("min_activity"));
        boolean reasonable = "true".equalsIgnoreCase(req.param("reasonable", ""));
        int pageNum = Math.max(1, req.intParam("page", 1));
        int pageSize = Math.min(Math.max(1, req.intParam("pageSize", 50)), 500);

        List<ShopEntry> results = new ArrayList<>();
        for (ShopEntry s : all) {
            if (!kw.isEmpty()) {
                boolean hit = (s.item_name != null && s.item_name.toLowerCase(Locale.ROOT).contains(kw))
                        || (s.material != null && s.material.toLowerCase(Locale.ROOT).contains(kw))
                        || (s.owner_name != null && s.owner_name.toLowerCase(Locale.ROOT).contains(kw));
                if (!hit) continue;
            }
            if (!mat.isEmpty() && (s.material == null || !mat.equals(s.material.toUpperCase(Locale.ROOT)))) continue;
            if (!owner.isEmpty() && (s.owner_name == null || !s.owner_name.toLowerCase(Locale.ROOT).contains(owner))) continue;
            if (!world.isEmpty() && (s.world == null || !s.world.contains(world))) continue;
            if (!type.isEmpty() && !type.equalsIgnoreCase(s.shop_type)) continue;
            if (minP != null && s.price < minP) continue;
            if (maxP != null && s.price > maxP) continue;
            if (minAct != null && s.activity_score < minAct) continue;
            if (reasonable && !s.price_reasonable) continue;
            results.add(s);
        }
        results.sort((a, b) -> {
            int d = Integer.compare(b.activity_score, a.activity_score);
            if (d != 0) return d;
            return Long.compare(parseId(a.shop_id), parseId(b.shop_id));
        });

        int total = results.size();
        int start = (pageNum - 1) * pageSize;
        JsonArray shops = new JsonArray();
        for (int i = start; i < Math.min(start + pageSize, total); i++) {
            shops.add(shopJson(results.get(i)));
        }

        JsonObject o = obj();
        put(o, "success", true);
        o.add("shops", shops);
        put(o, "total", total);
        put(o, "page", pageNum);
        put(o, "page_size", pageSize);
        return HttpResponse.json(o);
    }

    // ============================================================
    // POST /api/admin/shops/batch
    // ============================================================

    private static final class BatchOutcome {
        int ok;
        final List<String> errors = new ArrayList<>();
    }

    public HttpResponse shopsBatch(HttpRequest req) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        JsonObject b = body(req);
        if (!b.has("ids") || !b.get("ids").isJsonArray() || b.getAsJsonArray("ids").size() == 0) {
            return HttpResponse.error(400, "缺少 ids 列表");
        }
        JsonArray arr = b.getAsJsonArray("ids");
        if (arr.size() > 5000) return HttpResponse.error(400, "批量操作最多 5000 条");
        List<Long> ids = new ArrayList<>(arr.size());
        for (JsonElement e : arr) {
            try {
                ids.add(e.getAsLong());
            } catch (Exception ignored) {
            }
        }

        String action = jstr(b, "action", "").toLowerCase(Locale.ROOT);
        switch (action) {
            case "delete": {
                BatchOutcome out = batch(ids, shop -> plugin.bridge().deleteShop(shop));
                plugin.shopData().invalidate();
                plugin.getLogger().info("[Admin] 批量删除商店: 成功 " + out.ok + " / " + ids.size());
                JsonObject o = obj();
                put(o, "success", true);
                put(o, "action", "delete");
                put(o, "deleted_count", out.ok);
                if (!out.errors.isEmpty()) o.add("errors", toArray(out.errors));
                return HttpResponse.json(o);
            }
            case "set_price": {
                double np = jdouble(b, "price", -1);
                if (Double.isNaN(np) || np < 0) return HttpResponse.error(400, "price 必须是非负数");
                BatchOutcome out = batch(ids, shop -> plugin.bridge().setShopPrice(shop, np));
                plugin.shopData().invalidate();
                plugin.getLogger().info("[Admin] 批量改价: 成功 " + out.ok + " / " + ids.size() + " → " + np);
                JsonObject o = obj();
                put(o, "success", true);
                put(o, "action", "set_price");
                put(o, "updated_count", out.ok);
                put(o, "new_price", np);
                if (!out.errors.isEmpty()) o.add("errors", toArray(out.errors));
                return HttpResponse.json(o);
            }
            case "set_type": {
                String t = jstr(b, "shop_type", "").toUpperCase(Locale.ROOT);
                if (!"SELLING".equals(t) && !"BUYING".equals(t)) {
                    return HttpResponse.error(400, "shop_type 必须是 SELLING 或 BUYING");
                }
                BatchOutcome out = batch(ids, shop -> plugin.bridge().setShopType(shop, t));
                plugin.shopData().invalidate();
                JsonObject o = obj();
                put(o, "success", true);
                put(o, "action", "set_type");
                put(o, "updated_count", out.ok);
                put(o, "new_type", t);
                if (!out.errors.isEmpty()) o.add("errors", toArray(out.errors));
                return HttpResponse.json(o);
            }
            case "set_reasonable":
                return HttpResponse.error(400, "插件版不支持修改价格合理性标记");
            default:
                return HttpResponse.error(400, "不支持的 action");
        }
    }

    private BatchOutcome batch(List<Long> ids, Function<Object, String> op) {
        BatchOutcome out = new BatchOutcome();
        try {
            plugin.bridge().runOnMain(() -> {
                for (Long id : ids) {
                    Object shop = plugin.bridge().getShopById(id);
                    if (shop == null) {
                        out.errors.add(id + ": 商店不存在");
                        continue;
                    }
                    String err;
                    try {
                        err = op.apply(shop);
                    } catch (Throwable t) {
                        err = "异常: " + t.getMessage();
                    }
                    if (err == null) out.ok++;
                    else out.errors.add(id + ": " + err);
                }
                return null;
            });
        } catch (Throwable t) {
            out.errors.add("批量执行失败: " + t.getMessage());
        }
        return out;
    }

    private static JsonArray toArray(List<String> list) {
        JsonArray a = new JsonArray();
        for (String s : list) a.add(s);
        return a;
    }

    // ============================================================
    // GET /api/export/shops
    // ============================================================

    public HttpResponse exportShops(HttpRequest req) {
        String format = req.param("format", "json").toLowerCase(Locale.ROOT);
        String kw = req.param("q", "").trim().toLowerCase(Locale.ROOT);
        String mat = req.param("material", "").trim().toUpperCase(Locale.ROOT);
        String owner = req.param("owner", "").trim().toLowerCase(Locale.ROOT);
        String world = req.param("world", "").trim();
        String type = req.param("shop_type", "").trim().toUpperCase(Locale.ROOT);

        List<ShopEntry> rows = new ArrayList<>();
        for (ShopEntry s : plugin.shopData().shops()) {
            if (!kw.isEmpty()) {
                boolean hit = (s.item_name != null && s.item_name.toLowerCase(Locale.ROOT).contains(kw))
                        || (s.material != null && s.material.toLowerCase(Locale.ROOT).contains(kw))
                        || (s.owner_name != null && s.owner_name.toLowerCase(Locale.ROOT).contains(kw));
                if (!hit) continue;
            }
            if (!mat.isEmpty() && (s.material == null || !mat.equals(s.material.toUpperCase(Locale.ROOT)))) continue;
            if (!owner.isEmpty() && (s.owner_name == null || !s.owner_name.toLowerCase(Locale.ROOT).contains(owner))) continue;
            if (!world.isEmpty() && (s.world == null || !s.world.contains(world))) continue;
            if (!type.isEmpty() && !type.equalsIgnoreCase(s.shop_type)) continue;
            rows.add(s);
        }
        rows.sort((a, b) -> Long.compare(parseId(a.shop_id), parseId(b.shop_id)));

        long ts = System.currentTimeMillis();
        if ("csv".equals(format)) {
            String[] header = {"shop_id", "material", "item_name", "owner_name", "owner_uuid", "price",
                    "stacking_amount", "shop_type", "world", "x", "y", "z", "price_reasonable",
                    "activity_score", "fetched_at", "updated_at"};
            StringBuilder csv = new StringBuilder(header.length * 10);
            csv.append(String.join(",", header)).append('\n');
            for (ShopEntry s : rows) {
                String[] vals = {
                        s.shop_id, s.material, s.item_name, s.owner_name, s.owner_uuid,
                        String.valueOf(s.price), String.valueOf(s.stacking_amount), s.shop_type,
                        s.world, String.valueOf(s.x), String.valueOf(s.y), String.valueOf(s.z),
                        String.valueOf(s.price_reasonable), String.valueOf(s.activity_score),
                        s.fetched_at, s.updated_at
                };
                for (int i = 0; i < vals.length; i++) {
                    if (i > 0) csv.append(',');
                    csv.append(csvEscape(vals[i]));
                }
                csv.append('\n');
            }
            HttpResponse r = HttpResponse.text("\ufeff" + csv, "text/csv; charset=utf-8");
            r.headers.put("Content-Disposition", "attachment; filename=\"shops_" + ts + ".csv\"");
            return r;
        }

        JsonObject o = obj();
        put(o, "success", true);
        put(o, "export_time", com.qshop.webui.util.JsonUtil.isoNow());
        put(o, "total", rows.size());
        o.add("shops", shopsJson(rows));
        HttpResponse r = HttpResponse.json(o);
        r.headers.put("Content-Disposition", "attachment; filename=\"shops_" + ts + ".json\"");
        return r;
    }

    private static String csvEscape(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    // ============================================================
    // POST /api/import/shops（不支持真实导入）
    // ============================================================

    public HttpResponse importShops(HttpRequest req) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        return HttpResponse.error(400, "插件版商店数据来自游戏内 QuickShop，不支持从文件导入");
    }

    // ============================================================
    // /api/admin/config 与 /api/server/restart
    // ============================================================

    public HttpResponse adminConfigGet(HttpRequest req) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        JsonObject c = obj();
        put(c, "port", plugin.config().port);
        put(c, "bind", plugin.config().bind);
        put(c, "mode", plugin.config().mode);
        put(c, "multiplex", plugin.config().multiplex);
        put(c, "admin_username", plugin.config().adminUsername);
        put(c, "session_timeout", plugin.config().sessionTimeout);
        put(c, "require_auth", plugin.config().requireAuth);
        put(c, "snapshot_ttl_ms", plugin.config().snapshotTtlMs);
        put(c, "quickshop_connected", plugin.bridge().isAvailable());
        put(c, "quickshop_version", plugin.bridge().getQuickShopVersion());

        JsonObject o = obj();
        put(o, "success", true);
        o.add("config", c);
        o.add("schema", new JsonArray());
        put(o, "note", "插件版配置请直接编辑 plugins/QShopWebUI/config.yml，然后执行 /qshopwebui reload");
        return HttpResponse.json(o);
    }

    public HttpResponse adminConfigPost(HttpRequest req) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        return HttpResponse.error(400, "插件版配置请直接编辑 plugins/QShopWebUI/config.yml，然后执行 /qshopwebui reload");
    }

    public HttpResponse serverRestart(HttpRequest req) {
        return HttpResponse.error(400, "插件版不支持从网页重启服务器；请使用控制台或 /restart 命令");
    }

    // ============================================================
    // 工具
    // ============================================================

    private static Double parseDouble(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer parseInt(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return (int) Double.parseDouble(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(String.valueOf(id));
        } catch (Exception e) {
            return 0;
        }
    }
}
