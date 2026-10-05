package cn.aqcraft.service;

import cn.aqcraft.data.ShopEntry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 同物品比价：按 material 聚合所有商店，给出价格排序、最优价、均价，供网页「比价」页使用。
 */
public final class ComparisonService {

    /** 对某物品的所有商店做比价聚合，返回已排序的比价结果。 */
    public JsonObject compare(String material, List<ShopEntry> shops) {
        List<ShopEntry> match = new ArrayList<>();
        for (ShopEntry e : shops) {
            if (e.material == null) continue;
            if (material != null && !material.isEmpty() && !e.material.equalsIgnoreCase(material)) continue;
            match.add(e);
        }
        // 出售店按单价升序（买便宜），收购店按单价降序（卖高价）
        match.sort((a, b) -> {
            boolean sa = a.isSelling(), sb = b.isSelling();
            if (sa && sb) return Double.compare(a.price, b.price);
            if (!sa && !sb) return Double.compare(b.price, a.price);
            // 出售店在前
            return sa ? -1 : 1;
        });

        JsonObject out = new JsonObject();
        out.addProperty("material", material);
        out.addProperty("count", match.size());
        JsonArray sellers = new JsonArray();
        JsonArray buyers = new JsonArray();
        double minSell = Double.MAX_VALUE, maxBuy = -1;
        int sellN = 0, buyN = 0;
        for (ShopEntry e : match) {
            JsonObject j = shopJson(e);
            if (e.isSelling()) {
                sellers.add(j);
                minSell = Math.min(minSell, e.price);
                sellN++;
            } else {
                buyers.add(j);
                maxBuy = Math.max(maxBuy, e.price);
                buyN++;
            }
        }
        out.add("sellers", sellers);
        out.add("buyers", buyers);
        out.addProperty("lowest_sell", sellN > 0 ? minSell : 0);
        out.addProperty("highest_buy", buyN > 0 ? maxBuy : 0);
        out.addProperty("sell_count", sellN);
        out.addProperty("buy_count", buyN);
        return out;
    }

    private JsonObject shopJson(ShopEntry e) {
        JsonObject j = new JsonObject();
        j.addProperty("shop_id", e.shop_id);
        j.addProperty("item", e.shop_cn_name);
        j.addProperty("material", e.material);
        j.addProperty("price", e.price);
        j.addProperty("price_display", e.price_display);
        j.addProperty("owner", e.owner_name);
        j.addProperty("type", e.shop_type);
        j.addProperty("world", e.world);
        j.addProperty("x", e.x);
        j.addProperty("y", e.y);
        j.addProperty("z", e.z);
        j.addProperty("stacking_amount", e.stacking_amount);
        j.addProperty("system_shop", e.is_system_shop != null && e.is_system_shop);
        return j;
    }

    /** 聚合全量：按 material 列出每个物品的最低卖出价 / 最高收购价。 */
    public JsonArray aggregate(List<ShopEntry> shops) {
        Map<String, double[]> map = new TreeMap<>(); // material -> [lowest_sell, highest_buy, sell_n, buy_n]
        Map<String, String> names = new TreeMap<>();
        for (ShopEntry e : shops) {
            if (e.material == null) continue;
            double[] a = map.computeIfAbsent(e.material, k -> new double[]{Double.MAX_VALUE, -1, 0, 0});
            if (e.material.equals("AIR")) continue;
            if (names.get(e.material) == null && e.shop_cn_name != null) names.put(e.material, e.shop_cn_name);
            if (e.isSelling()) {
                a[0] = Math.min(a[0], e.price);
                a[2]++;
            } else {
                a[1] = Math.max(a[1], e.price);
                a[3]++;
            }
        }
        JsonArray out = new JsonArray();
        for (Map.Entry<String, double[]> e : map.entrySet()) {
            double[] v = e.getValue();
            if (v[0] == Double.MAX_VALUE) v[0] = 0;
            JsonObject o = new JsonObject();
            o.addProperty("material", e.getKey());
            o.addProperty("name", names.get(e.getKey()));
            o.addProperty("lowest_sell", v[0]);
            o.addProperty("highest_buy", v[1]);
            o.addProperty("sell_count", (int) v[2]);
            o.addProperty("buy_count", (int) v[3]);
            out.add(o);
        }
        return out;
    }
}