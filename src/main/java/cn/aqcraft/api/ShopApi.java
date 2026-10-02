package cn.aqcraft.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cn.aqcraft.QShopWebUIPlugin;
import cn.aqcraft.data.QueryParams;
import cn.aqcraft.data.ShopEntry;
import cn.aqcraft.data.ShopQuery;
import cn.aqcraft.data.ShopStats;
import cn.aqcraft.http.HttpRequest;
import cn.aqcraft.http.HttpResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /api/shops 系列 + materials / owners / worlds / harbor / price */
public final class ShopApi extends ApiBase {

    public ShopApi(QShopWebUIPlugin plugin) {
        super(plugin);
    }

    // ============================================================
    // GET /api/shops 与 /api/shops/search
    // ============================================================

    public HttpResponse list(HttpRequest req) {
        long t0 = System.currentTimeMillis();
        List<ShopEntry> all = plugin.shopData().shops();
        ShopQuery.Result r = ShopQuery.query(all, paramsFrom(req));
        ShopStats st = plugin.shopData().stats();

        JsonObject o = obj();
        put(o, "success", true);
        put(o, "source", "cache");
        put(o, "qs_available", plugin.bridge().isAvailable());
        o.add("results", shopsJson(r.shops));
        put(o, "total", r.total);
        put(o, "page", r.page);
        put(o, "page_size", r.limit);
        put(o, "total_pages", r.totalPages);
        addTimestamps(o);
        put(o, "elapsed_ms", System.currentTimeMillis() - t0);
        return HttpResponse.json(o);
    }

    // ============================================================
    // GET /api/shops/top
    // ============================================================

    public HttpResponse top(HttpRequest req) {
        long t0 = System.currentTimeMillis();
        int limit = Math.min(Math.max(1, req.intParam("limit", 12)), 100);
        String material = req.param("material");
        List<ShopEntry> all = plugin.shopData().shops();

        List<ShopEntry> candidates = new ArrayList<>();
        for (ShopEntry s : all) {
            if (material != null && !material.isEmpty()) {
                if (s.material == null || !s.material.equalsIgnoreCase(material.trim())) continue;
            }
            candidates.add(s);
        }
        candidates.sort((a, b) -> {
            int d = Integer.compare(b.activity_score, a.activity_score);
            if (d != 0) return d;
            return Double.compare(a.price, b.price);
        });
        if (candidates.size() > limit) candidates = new ArrayList<>(candidates.subList(0, limit));

        JsonObject o = obj();
        put(o, "success", true);
        put(o, "source", "cache");
        put(o, "qs_available", plugin.bridge().isAvailable());
        o.add("results", shopsJson(candidates));
        put(o, "total", candidates.size());
        addTimestamps(o);
        put(o, "elapsed_ms", System.currentTimeMillis() - t0);
        return HttpResponse.json(o);
    }

    // ============================================================
    // GET / PUT / DELETE /api/shops/:id
    // ============================================================

    public HttpResponse getById(HttpRequest req, String idStr) {
        ShopEntry found = findById(idStr);
        if (found == null) return HttpResponse.error(404, "店铺不存在");
        JsonObject o = obj();
        put(o, "success", true);
        o.add("shop", shopJson(found));
        return HttpResponse.json(o);
    }

    public HttpResponse update(HttpRequest req, String idStr) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        long id = parseLong(idStr);
        if (id <= 0) return HttpResponse.error(400, "无效的商店 ID");
        JsonObject b = body(req);

        String err = plugin.bridge().mutateShop(id, shop -> {
            if (b.has("price")) {
                double np = jdouble(b, "price", -1);
                if (np < 0) return "price 必须是非负数";
                String e1 = plugin.bridge().setShopPrice(shop, np);
                if (e1 != null) return e1;
            }
            if (b.has("shop_type")) {
                String t = jstr(b, "shop_type", "").toUpperCase(Locale.ROOT);
                if ("BUYING".equals(t) || "SELLING".equals(t)) {
                    String cur = plugin.bridge().resolveShopType(shop);
                    if (!t.equalsIgnoreCase(cur)) {
                        String e2 = plugin.bridge().setShopType(shop, t);
                        if (e2 != null) return e2;
                    }
                }
            }
            if (!b.has("price") && !b.has("shop_type")) {
                return "插件版仅支持修改 price 与 shop_type（其余字段由 QuickShop 管理）";
            }
            return null;
        });

        if (err != null) return HttpResponse.error(400, err);
        plugin.shopData().invalidate();
        plugin.getLogger().info("[Admin] 已修改商店 #" + id + " "
                + (b.has("price") ? "price=" + b.get("price") : "")
                + (b.has("shop_type") ? " type=" + jstr(b, "shop_type", "") : ""));
        JsonObject o = obj();
        put(o, "success", true);
        return HttpResponse.json(o);
    }

    public HttpResponse delete(HttpRequest req, String idStr) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        long id = parseLong(idStr);
        if (id <= 0) return HttpResponse.error(400, "无效的商店 ID");

        String err = plugin.bridge().mutateShop(id, shop -> plugin.bridge().deleteShop(shop));
        if (err != null) return HttpResponse.error(400, err);
        plugin.shopData().invalidate();
        plugin.getLogger().info("[Admin] 已删除商店 #" + id);
        JsonObject o = obj();
        put(o, "success", true);
        put(o, "deleted", 1);
        return HttpResponse.json(o);
    }

    /** 清空全部商店：为防误操作，插件版不提供（真实数据在游戏内） */
    public HttpResponse clearAll(HttpRequest req) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        return HttpResponse.error(400,
                "插件版直接管理游戏内真实商店，为防止误删，不支持一键清空；请在管理员面板中勾选具体商店后批量删除");
    }

    // ============================================================
    // seed 系列（插件版不需要）
    // ============================================================

    public HttpResponse seedDisabled(HttpRequest req) {
        return HttpResponse.error(400, "插件版直接读取 QuickShop 实时数据，无需生成测试数据");
    }

    // ============================================================
    // GET /api/materials | /api/owners | /api/worlds
    // ============================================================

    public HttpResponse materials(HttpRequest req) {
        List<ShopEntry> all = plugin.shopData().shops();
        Map<String, int[]> agg = new HashMap<>(); // material → [shops, activity]
        for (ShopEntry s : all) {
            if (s.material == null) continue;
            int[] a = agg.computeIfAbsent(s.material, k -> new int[2]);
            a[0]++;
            a[1] += s.activity_score;
        }
        List<Map.Entry<String, int[]>> list = new ArrayList<>(agg.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
        JsonArray arr = new JsonArray();
        for (Map.Entry<String, int[]> e : list) {
            if (arr.size() >= 500) break;
            JsonObject x = obj();
            put(x, "material", e.getKey());
            put(x, "shops", e.getValue()[0]);
            put(x, "activity", e.getValue()[1]);
            arr.add(x);
        }
        JsonObject o = obj();
        put(o, "success", true);
        o.add("materials", arr);
        put(o, "total", arr.size());
        return HttpResponse.json(o);
    }

    public HttpResponse owners(HttpRequest req) {
        List<ShopEntry> all = plugin.shopData().shops();
        Map<String, Integer> agg = new HashMap<>();
        for (ShopEntry s : all) {
            if (s.owner_name == null) continue;
            agg.merge(s.owner_name, 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> list = new ArrayList<>(agg.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        JsonArray arr = new JsonArray();
        for (Map.Entry<String, Integer> e : list) {
            if (arr.size() >= 500) break;
            JsonObject x = obj();
            put(x, "owner_name", e.getKey());
            put(x, "shops", e.getValue());
            arr.add(x);
        }
        JsonObject o = obj();
        put(o, "success", true);
        o.add("owners", arr);
        return HttpResponse.json(o);
    }

    public HttpResponse worlds(HttpRequest req) {
        List<ShopEntry> all = plugin.shopData().shops();
        Map<String, Integer> agg = new HashMap<>();
        for (ShopEntry s : all) {
            if (s.world == null) continue;
            agg.merge(s.world, 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> list = new ArrayList<>(agg.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        JsonArray arr = new JsonArray();
        for (Map.Entry<String, Integer> e : list) {
            JsonObject x = obj();
            put(x, "world", e.getKey());
            put(x, "shops", e.getValue());
            arr.add(x);
        }
        JsonObject o = obj();
        put(o, "success", true);
        o.add("worlds", arr);
        return HttpResponse.json(o);
    }

    // ============================================================
    // GET /api/price/:item_id（聚合价格）
    // ============================================================

    public HttpResponse price(HttpRequest req, String itemId) {
        long t0 = System.currentTimeMillis();
        if (itemId == null || itemId.trim().isEmpty()) {
            return HttpResponse.error(400, "item_id 不能为空");
        }
        String key = itemId.trim().toLowerCase(Locale.ROOT);
        List<ShopEntry> shops = new ArrayList<>();
        for (ShopEntry s : plugin.shopData().shops()) {
            if (s.material != null && s.material.toLowerCase(Locale.ROOT).equals(key)) {
                shops.add(s);
            }
        }

        JsonObject o = obj();
        put(o, "success", true);
        if (shops.isEmpty()) {
            put(o, "note", "该物品暂无商店数据（可能还未同步或物品不存在）");
            put(o, "item_id", key);
            put(o, "material", itemId.trim().toUpperCase(Locale.ROOT));
            put(o, "weighted_avg_price", -1);
            put(o, "current_shop_count", 0);
            put(o, "selling_shop_count", 0);
            put(o, "selling_min_price", -1);
            put(o, "selling_max_price", -1);
            put(o, "selling_avg_price", -1);
            put(o, "buying_shop_count", 0);
            put(o, "buying_min_price", -1);
            put(o, "buying_max_price", -1);
            put(o, "buying_avg_price", -1);
            put(o, "qs_available", plugin.bridge().isAvailable());
            addTimestamps(o);
            put(o, "elapsed_ms", System.currentTimeMillis() - t0);
            return HttpResponse.json(o);
        }

        List<Double> sell = new ArrayList<>();
        List<Double> buy = new ArrayList<>();
        List<Double> reasonable = new ArrayList<>();
        for (ShopEntry s : shops) {
            if (s.isSelling()) sell.add(s.price);
            else if (s.isBuying()) buy.add(s.price);
            if (s.price_reasonable) reasonable.add(s.price);
        }
        JsonObject out = obj();
        put(out, "success", true);
        put(out, "source", "cache");
        put(out, "qs_available", plugin.bridge().isAvailable());
        put(out, "material", shops.get(0).material);
        put(out, "item_id", key);
        put(out, "item_name", shops.get(0).item_name);
        put(out, "weighted_avg_price", avg(reasonable.isEmpty() ? sell : reasonable));
        put(out, "current_shop_count", shops.size());
        put(out, "selling_shop_count", sell.size());
        put(out, "selling_min_price", min(sell));
        put(out, "selling_max_price", max(sell));
        put(out, "selling_avg_price", avg(sell));
        put(out, "buying_shop_count", buy.size());
        put(out, "buying_min_price", min(buy));
        put(out, "buying_max_price", max(buy));
        put(out, "buying_avg_price", avg(buy));
        addTimestamps(out);
        put(out, "elapsed_ms", System.currentTimeMillis() - t0);
        return HttpResponse.json(out);
    }

    // ============================================================
    // /api/harbor
    // ============================================================

    public HttpResponse harborGet(HttpRequest req) {
        JsonObject o = obj();
        put(o, "success", true);
        o.add("harbor", plugin.store().harbor());
        return HttpResponse.json(o);
    }

    public HttpResponse harborPut(HttpRequest req) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        JsonObject b = body(req);
        JsonObject h = obj();
        put(h, "world", jstr(b, "world", "world"));
        put(h, "x", jint(b, "x", 0));
        put(h, "y", jint(b, "y", 64));
        put(h, "z", jint(b, "z", 0));
        plugin.store().setHarbor(h);
        JsonObject o = obj();
        put(o, "success", true);
        o.add("harbor", h);
        return HttpResponse.json(o);
    }

    // ============================================================
    // 内部
    // ============================================================

    private QueryParams paramsFrom(HttpRequest req) {
        QueryParams p = new QueryParams();
        String kw = req.param("q");
        if (kw == null) kw = req.param("keyword");
        p.keyword = kw == null ? "" : kw;
        p.material = req.param("material", "");
        p.shopType = req.param("shop_type", "");
        p.owner = req.param("owner", "");
        p.world = req.param("world", "");
        p.minPrice = parseDouble(req.param("min_price"));
        p.maxPrice = parseDouble(req.param("max_price"));
        p.sort = req.param("sort", "");
        p.page = Math.max(1, req.intParam("page", 1));
        p.pageSize = req.intParam("pageSize", plugin.config().defaultPageSize);
        String sa = req.param("show_all");
        if (sa != null) p.showAll = !"false".equalsIgnoreCase(sa.trim());
        return p;
    }

    public ShopEntry findById(String idStr) {
        if (idStr == null || idStr.isEmpty()) return null;
        String id = idStr.trim();
        for (ShopEntry s : plugin.shopData().shops()) {
            if (id.equals(s.shop_id)) return s;
        }
        return null;
    }

    private static Double parseDouble(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(String.valueOf(s).trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static Double avg(List<Double> list) {
        if (list.isEmpty()) return -1.0;
        double sum = 0;
        for (double d : list) sum += d;
        return Math.round(sum / list.size() * 100.0) / 100.0;
    }

    private static Double min(List<Double> list) {
        if (list.isEmpty()) return -1.0;
        return list.stream().min(Comparator.naturalOrder()).orElse(-1.0);
    }

    private static Double max(List<Double> list) {
        if (list.isEmpty()) return -1.0;
        return list.stream().max(Comparator.naturalOrder()).orElse(-1.0);
    }
}
