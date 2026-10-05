package cn.aqcraft.service;

import cn.aqcraft.QShopWebUIPlugin;
import cn.aqcraft.util.JsonUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 经营统计：独立于订单/流水的按天聚合统计（购买 / 收购分开，失败不计入）。
 *
 * <p>每个完成的交易（SUCCESS）都会调用 {@link #record} 累加；按自然日分桶。
 * 网页「统计」页通过 {@link #snapshot} 拿天级别数据画折线图。</p>
 *
 * <p>存储于 {@code data/biz_stats.json}：{@code { "days": { "2026-10-01": { buy:{amount,total,orders}, sell:{...} } } }}。</p>
 */
public final class BusinessStatsService {

    private final QShopWebUIPlugin plugin;
    private final File file;
    private final Map<String, Day> days = new ConcurrentHashMap<>();

    public BusinessStatsService(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/biz_stats.json");
        load();
    }

    private static final class Day {
        long buyAmount = 0, buyTotal = 0, buyOrders = 0;
        long sellAmount = 0, sellTotal = 0, sellOrders = 0;
    }

    private void load() {
        try {
            if (!file.isFile()) return;
            JsonObject root = JsonParser.parseString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject d = root.has("days") ? root.getAsJsonObject("days") : new JsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> e : d.entrySet()) {
                Day day = new Day();
                JsonObject o = e.getValue().getAsJsonObject();
                readDay(day, o);
                days.put(e.getKey(), day);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[统计] 读取 biz_stats.json 失败: " + e.getMessage());
        }
    }

    private void readDay(Day day, JsonObject o) {
        if (o.has("buy")) readSide(day, o.getAsJsonObject("buy"), true);
        if (o.has("sell")) readSide(day, o.getAsJsonObject("sell"), false);
    }

    private void readSide(Day day, JsonObject s, boolean buy) {
        if (buy) {
            day.buyAmount = s.has("amount") ? s.get("amount").getAsLong() : 0;
            day.buyTotal = s.has("total") ? s.get("total").getAsLong() : 0;
            day.buyOrders = s.has("orders") ? s.get("orders").getAsLong() : 0;
        } else {
            day.sellAmount = s.has("amount") ? s.get("amount").getAsLong() : 0;
            day.sellTotal = s.has("total") ? s.get("total").getAsLong() : 0;
            day.sellOrders = s.has("orders") ? s.get("orders").getAsLong() : 0;
        }
    }

    private void save() {
        JsonObject root = new JsonObject();
        JsonObject d = new JsonObject();
        for (Map.Entry<String, Day> e : days.entrySet()) {
            Day day = e.getValue();
            JsonObject o = new JsonObject();
            o.add("buy", side(day, true));
            o.add("sell", side(day, false));
            d.add(e.getKey(), o);
        }
        root.add("days", d);
        try {
            Files.write(file.toPath(), JsonUtil.toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("[统计] 保存 biz_stats.json 失败: " + e.getMessage());
        }
    }

    private JsonObject side(Day day, boolean buy) {
        JsonObject o = new JsonObject();
        if (buy) {
            o.addProperty("amount", day.buyAmount);
            o.addProperty("total", day.buyTotal);
            o.addProperty("orders", day.buyOrders);
        } else {
            o.addProperty("amount", day.sellAmount);
            o.addProperty("total", day.sellTotal);
            o.addProperty("orders", day.sellOrders);
        }
        return o;
    }

    public void flush() { save(); }

    private static String today() {
        Calendar c = Calendar.getInstance();
        return String.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * 记录一笔成功交易。
     * @param type BUY / SELL
     * @param amount 交易份数
     * @param total 交易总金额（向上取整存整数，避免浮点误差）
     */
    public void record(String type, int amount, double total) {
        Day day = days.computeIfAbsent(today(), k -> new Day());
        long amt = amount;
        long tot = Math.round(total);
        if ("SELL".equalsIgnoreCase(type)) {
            day.sellAmount += amt;
            day.sellTotal += tot;
            day.sellOrders++;
        } else {
            day.buyAmount += amt;
            day.buyTotal += tot;
            day.buyOrders++;
        }
        // 每次成功记录后都落盘（量小，直接同步写）
        save();
    }

    /** 返回最近 N 天的统计（时间正序，供折线图）。 */
    public JsonArray snapshot(int daysN) {
        List<String> keys = new ArrayList<>(days.keySet());
        keys.sort(String::compareTo);
        if (daysN > 0 && keys.size() > daysN) keys = keys.subList(keys.size() - daysN, keys.size());
        JsonArray arr = new JsonArray();
        for (String k : keys) {
            Day day = days.get(k);
            JsonObject o = new JsonObject();
            o.addProperty("day", k);
            o.addProperty("buy_amount", day.buyAmount);
            o.addProperty("buy_total", day.buyTotal);
            o.addProperty("buy_orders", day.buyOrders);
            o.addProperty("sell_amount", day.sellAmount);
            o.addProperty("sell_total", day.sellTotal);
            o.addProperty("sell_orders", day.sellOrders);
            arr.add(o);
        }
        return arr;
    }

    /** 汇总累计值 */
    public JsonObject totals() {
        long buyAmt = 0, buyTot = 0, buyOrd = 0, sellAmt = 0, sellTot = 0, sellOrd = 0;
        for (Day d : days.values()) {
            buyAmt += d.buyAmount;
            buyTot += d.buyTotal;
            buyOrd += d.buyOrders;
            sellAmt += d.sellAmount;
            sellTot += d.sellTotal;
            sellOrd += d.sellOrders;
        }
        JsonObject o = new JsonObject();
        o.addProperty("buy_amount", buyAmt);
        o.addProperty("buy_total", buyTot);
        o.addProperty("buy_orders", buyOrd);
        o.addProperty("sell_amount", sellAmt);
        o.addProperty("sell_total", sellTot);
        o.addProperty("sell_orders", sellOrd);
        return o;
    }
}