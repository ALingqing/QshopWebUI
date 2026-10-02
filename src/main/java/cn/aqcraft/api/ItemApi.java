package cn.aqcraft.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cn.aqcraft.QShopWebUIPlugin;
import cn.aqcraft.bridge.QuickShopBridge;
import cn.aqcraft.data.ShopEntry;
import cn.aqcraft.http.HttpRequest;
import cn.aqcraft.http.HttpResponse;
import cn.aqcraft.util.Materials;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** /api/items 与 /api/items/:material（物品聚合 / 详情） */
public final class ItemApi extends ApiBase {

    public ItemApi(QShopWebUIPlugin plugin) {
        super(plugin);
    }

    // ============================================================
    // GET /api/items — 物品聚合列表
    // ============================================================

    private static final class Agg {
        final String material;
        final String itemName;
        int totalShops;
        final Set<String> owners = new HashSet<>();
        final Set<String> worlds = new HashSet<>();
        int selling;
        int buying;
        final List<Double> sellPrices = new ArrayList<>();
        final List<Double> buyPrices = new ArrayList<>();
        long totalActivity;

        Agg(String material, String itemName) {
            this.material = material;
            this.itemName = itemName;
        }
    }

    public HttpResponse items(HttpRequest req) {
        long t0 = System.currentTimeMillis();
        List<ShopEntry> all = plugin.shopData().shops();

        String q = req.param("q", "").trim().toLowerCase(Locale.ROOT);
        String shopType = req.param("shop_type");
        String world = req.param("world");
        Double minPrice = parseDouble(req.param("min_price"));
        Double maxPrice = parseDouble(req.param("max_price"));
        String sort = req.param("sort", "shops_desc");
        int pageNum = Math.max(1, req.intParam("page", 1));
        int pageSize = Math.min(Math.max(1, req.intParam("pageSize", 30)), 200);

        Map<String, Agg> aggs = new LinkedHashMap<>();
        for (ShopEntry s : all) {
            if (shopType != null && !shopType.isEmpty() && !shopType.equalsIgnoreCase(s.shop_type)) continue;
            if (world != null && !world.isEmpty()
                    && (s.world == null || !s.world.toLowerCase(Locale.ROOT).contains(world.toLowerCase(Locale.ROOT))))
                continue;
            if (!q.isEmpty() && q.length() >= 2) {
                boolean inMat = s.material != null && s.material.toLowerCase(Locale.ROOT).contains(q);
                boolean inName = s.item_name != null && s.item_name.toLowerCase(Locale.ROOT).contains(q);
                boolean inOwner = s.owner_name != null && s.owner_name.toLowerCase(Locale.ROOT).contains(q);
                // 拼音 / 首字母搜索（蜘蛛 → zhizhu / zz）
                if (!inName && s.item_name != null) inName = cn.aqcraft.util.Pinyin.matches(q, s.item_name);
                if (!inName && s.shop_cn_name != null) inName = cn.aqcraft.util.Pinyin.matches(q, s.shop_cn_name);
                if (!inMat && !inName && !inOwner) continue;
            }
            String mat = s.material == null ? "UNKNOWN" : s.material;
            Agg a = aggs.computeIfAbsent(mat, k -> new Agg(k, s.item_name));
            a.totalShops++;
            if (s.owner_name != null) a.owners.add(s.owner_name);
            if (s.world != null) a.worlds.add(s.world);
            if (s.isSelling()) {
                a.selling++;
                a.sellPrices.add(s.price);
            } else if (s.isBuying()) {
                a.buying++;
                a.buyPrices.add(s.price);
            }
            a.totalActivity += s.activity_score;
        }

        List<JsonObject> items = new ArrayList<>(aggs.size());
        for (Agg a : aggs.values()) {
            Double minSell = a.sellPrices.isEmpty() ? null : minOf(a.sellPrices);
            Double maxSell = a.sellPrices.isEmpty() ? null : maxOf(a.sellPrices);
            Double avgSell = a.sellPrices.isEmpty() ? null : round2(avgOf(a.sellPrices));
            Double minBuy = a.buyPrices.isEmpty() ? null : minOf(a.buyPrices);
            Double maxBuy = a.buyPrices.isEmpty() ? null : maxOf(a.buyPrices);
            Double avgBuy = a.buyPrices.isEmpty() ? null : round2(avgOf(a.buyPrices));

            String imageName = Materials.imageName(a.material);
            String cnName;
            if (a.itemName != null && !a.itemName.equals(a.material)) {
                cnName = QuickShopBridge.hasChinese(a.itemName) ? a.itemName : Materials.cn(a.material);
            } else {
                cnName = Materials.cn(a.material);
            }
            String priceDisplay = null;
            if (avgSell != null && !avgSell.isNaN()) {
                priceDisplay = String.format(Locale.US, "%,.2f", avgSell);
            }

            JsonObject x = obj();
            put(x, "material", a.material);
            put(x, "item_name", a.itemName);
            put(x, "shop_cn_name", cnName);
            put(x, "item_image", imageName == null ? null : "item/" + imageName + ".png");
            put(x, "price_display", priceDisplay);
            put(x, "total_shops", a.totalShops);
            put(x, "owner_count", a.owners.size());
            put(x, "world_count", a.worlds.size());
            put(x, "selling_shops", a.selling);
            put(x, "buying_shops", a.buying);
            put(x, "avg_price", avgSell);
            put(x, "min_price", minSell);
            put(x, "max_price", maxSell);
            put(x, "avg_sell_price", avgSell);
            put(x, "min_sell_price", minSell);
            put(x, "max_sell_price", maxSell);
            put(x, "avg_buy_price", avgBuy);
            put(x, "min_buy_price", minBuy);
            put(x, "max_buy_price", maxBuy);
            put(x, "total_activity", a.totalActivity);
            items.add(x);
        }

        if (minPrice != null) {
            List<JsonObject> f = new ArrayList<>();
            for (JsonObject x : items) {
                if (!x.get("min_price").isJsonNull() && x.get("min_price").getAsDouble() >= minPrice) f.add(x);
            }
            items = f;
        }
        if (maxPrice != null) {
            List<JsonObject> f = new ArrayList<>();
            for (JsonObject x : items) {
                if (!x.get("max_price").isJsonNull() && x.get("max_price").getAsDouble() <= maxPrice) f.add(x);
            }
            items = f;
        }

        switch (sort == null ? "" : sort) {
            case "price_asc":
                items.sort((a, b) -> Double.compare(numOr0(a, "avg_price"), numOr0(b, "avg_price")));
                break;
            case "price_desc":
                items.sort((a, b) -> Double.compare(numOr0(b, "avg_price"), numOr0(a, "avg_price")));
                break;
            case "shops_asc":
                items.sort((a, b) -> Integer.compare(a.get("total_shops").getAsInt(), b.get("total_shops").getAsInt()));
                break;
            case "owners_desc":
                items.sort((a, b) -> Integer.compare(b.get("owner_count").getAsInt(), a.get("owner_count").getAsInt()));
                break;
            case "activity_desc":
                items.sort((a, b) -> Long.compare(b.get("total_activity").getAsLong(), a.get("total_activity").getAsLong()));
                break;
            case "name_asc":
                items.sort((a, b) -> a.get("material").getAsString().compareTo(b.get("material").getAsString()));
                break;
            case "name_desc":
                items.sort((a, b) -> b.get("material").getAsString().compareTo(a.get("material").getAsString()));
                break;
            case "shops_desc":
            default:
                items.sort((a, b) -> Integer.compare(b.get("total_shops").getAsInt(), a.get("total_shops").getAsInt()));
                break;
        }

        int total = items.size();
        int startIdx = (pageNum - 1) * pageSize;
        JsonArray pageItems = new JsonArray();
        if (startIdx < total) {
            for (int i = startIdx; i < Math.min(startIdx + pageSize, total); i++) pageItems.add(items.get(i));
        }

        JsonObject o = obj();
        put(o, "success", true);
        put(o, "source", "cache");
        put(o, "qs_available", plugin.bridge().isAvailable());
        o.add("results", pageItems);
        put(o, "total", total);
        put(o, "page", pageNum);
        put(o, "page_size", pageSize);
        put(o, "total_pages", Math.max(1, (int) Math.ceil(total / (double) pageSize)));
        addTimestamps(o);
        put(o, "elapsed_ms", System.currentTimeMillis() - t0);
        return HttpResponse.json(o);
    }

    // ============================================================
    // GET /api/items/:material — 物品详情
    // ============================================================

    public HttpResponse itemDetail(HttpRequest req, String material) {
        long t0 = System.currentTimeMillis();
        String matUpper = material == null ? "" : material.trim().toUpperCase(Locale.ROOT);
        String matLower = matUpper.toLowerCase(Locale.ROOT);

        String shopType = req.param("shop_type");
        String world = req.param("world");
        String owner = req.param("owner");
        Double minPrice = parseDouble(req.param("min_price"));
        Double maxPrice = parseDouble(req.param("max_price"));
        String sort = req.param("sort", "price_asc");
        int pageNum = Math.max(1, req.intParam("page", 1));
        int pageSize = Math.min(Math.max(1, req.intParam("pageSize", 25)), 500);

        List<ShopEntry> shops = new ArrayList<>();
        Set<String> owners = new HashSet<>();
        Set<String> worlds = new HashSet<>();
        int sellingShops = 0;
        int buyingShops = 0;
        List<Double> sellPrices = new ArrayList<>();
        List<Double> buyPrices = new ArrayList<>();

        for (ShopEntry s : plugin.shopData().shops()) {
            if (s.material == null || !s.material.toLowerCase(Locale.ROOT).equals(matLower)) continue;
            if (shopType != null && !shopType.isEmpty() && !shopType.equalsIgnoreCase(s.shop_type)) continue;
            if (world != null && !world.isEmpty()
                    && (s.world == null || !s.world.toLowerCase(Locale.ROOT).contains(world.toLowerCase(Locale.ROOT))))
                continue;
            if (owner != null && !owner.isEmpty()
                    && (s.owner_name == null || !s.owner_name.toLowerCase(Locale.ROOT).contains(owner.toLowerCase(Locale.ROOT))))
                continue;
            if (minPrice != null && s.price < minPrice) continue;
            if (maxPrice != null && s.price > maxPrice) continue;
            shops.add(s);
            if (s.owner_name != null) owners.add(s.owner_name);
            if (s.world != null) worlds.add(s.world);
            if (s.isSelling()) {
                sellingShops++;
                sellPrices.add(s.price);
            } else if (s.isBuying()) {
                buyingShops++;
                buyPrices.add(s.price);
            }
        }

        switch (sort == null ? "" : sort) {
            case "price_desc":
                shops.sort((a, b) -> Double.compare(b.price, a.price));
                break;
            case "owner_asc":
                shops.sort((a, b) -> String.valueOf(a.owner_name).compareToIgnoreCase(String.valueOf(b.owner_name)));
                break;
            case "activity_desc":
                shops.sort((a, b) -> Integer.compare(b.activity_score, a.activity_score));
                break;
            case "quantity_desc":
                shops.sort((a, b) -> Integer.compare(q(a), q(b)));
                break;
            case "world_asc":
                shops.sort((a, b) -> String.valueOf(a.world).compareToIgnoreCase(String.valueOf(b.world)));
                break;
            case "newest":
                shops.sort((a, b) -> String.valueOf(b.fetched_at).compareTo(String.valueOf(a.fetched_at)));
                break;
            case "price_asc":
            default:
                shops.sort((a, b) -> Double.compare(a.price, b.price));
                break;
        }

        int total = shops.size();
        int startIdx = (pageNum - 1) * pageSize;
        JsonArray pageShops = new JsonArray();
        for (int i = startIdx; i < Math.min(startIdx + pageSize, total); i++) {
            pageShops.add(enhancedShopJson(shops.get(i)));
        }

        String firstMat = shops.isEmpty() ? matUpper : shops.get(0).material;
        String firstItemName = shops.isEmpty() ? matUpper : (shops.get(0).item_name == null ? matUpper : shops.get(0).item_name);

        long totalActivity = 0;
        for (ShopEntry s : shops) totalActivity += s.activity_score;

        JsonObject stats = obj();
        put(stats, "total_shops", total);
        put(stats, "owner_count", owners.size());
        put(stats, "world_count", worlds.size());
        put(stats, "selling_shops", sellingShops);
        put(stats, "buying_shops", buyingShops);
        put(stats, "avg_sell_price", sellPrices.isEmpty() ? null : round2(avgOf(sellPrices)));
        put(stats, "min_sell_price", sellPrices.isEmpty() ? null : minOf(sellPrices));
        put(stats, "max_sell_price", sellPrices.isEmpty() ? null : maxOf(sellPrices));
        put(stats, "avg_buy_price", buyPrices.isEmpty() ? null : round2(avgOf(buyPrices)));
        put(stats, "min_buy_price", buyPrices.isEmpty() ? null : minOf(buyPrices));
        put(stats, "max_buy_price", buyPrices.isEmpty() ? null : maxOf(buyPrices));
        put(stats, "total_activity", totalActivity);

        JsonObject o = obj();
        put(o, "success", true);
        put(o, "source", "cache");
        put(o, "qs_available", plugin.bridge().isAvailable());
        put(o, "material", firstMat);
        put(o, "item_name", firstItemName);
        put(o, "shop_cn_name", Materials.cn(firstMat));
        o.add("stats", stats);
        o.add("shops", pageShops);
        put(o, "total", total);
        put(o, "page", pageNum);
        put(o, "page_size", pageSize);
        put(o, "total_pages", Math.max(1, (int) Math.ceil(total / (double) pageSize)));
        addTimestamps(o);
        put(o, "elapsed_ms", System.currentTimeMillis() - t0);
        return HttpResponse.json(o);
    }

    /** 详情页增强字段（库存语义 / 图片 / 名称） */
    private JsonObject enhancedShopJson(ShopEntry s) {
        JsonObject x = shopJson(s);
        Integer qNum;
        boolean infinite;
        boolean lowStock = false;
        String stockText;
        if (s.system_shop) {
            qNum = -1;
            infinite = true;
            stockText = "无限";
        } else if (s.quantity == null) {
            qNum = null;
            infinite = false;
            stockText = "—";
        } else {
            qNum = s.quantity;
            infinite = false;
            stockText = String.valueOf(qNum);
            lowStock = qNum <= plugin.config().playerShopLowStockThreshold;
        }
        put(x, "quantity_num", qNum);
        put(x, "is_infinite", infinite);
        put(x, "is_low_stock", lowStock);
        put(x, "stock_text", stockText);
        put(x, "stock_label", s.isBuying() ? "收购量" : "库存量");
        put(x, "is_system_shop", s.system_shop);
        put(x, "max_buy_quantity", plugin.config().playerShopMaxBuy);
        put(x, "max_stock_capacity", plugin.config().playerShopMaxStock);
        return x;
    }

    private static int q(ShopEntry s) {
        if (s.quantity == null || s.quantity < 0) return Integer.MIN_VALUE;
        return s.quantity;
    }

    private static Double parseDouble(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static double numOr0(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return 0;
        try {
            return o.get(key).getAsDouble();
        } catch (Exception e) {
            return 0;
        }
    }

    private static double avgOf(List<Double> list) {
        double sum = 0;
        for (double d : list) sum += d;
        return sum / list.size();
    }

    private static double round2(double d) {
        return Math.round(d * 100.0) / 100.0;
    }

    private static double minOf(List<Double> list) {
        double m = Double.MAX_VALUE;
        for (double d : list) m = Math.min(m, d);
        return m;
    }

    private static double maxOf(List<Double> list) {
        double m = -Double.MAX_VALUE;
        for (double d : list) m = Math.max(m, d);
        return m;
    }
}
