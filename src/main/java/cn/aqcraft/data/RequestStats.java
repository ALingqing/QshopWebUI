package cn.aqcraft.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cn.aqcraft.http.HttpRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 请求统计（供 /api/stats/requests 使用） */
public final class RequestStats {

    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH");

    private static final class Hour {
        int count;
        int port;
        int shops;
    }

    private final Map<String, Hour> byHour = new ConcurrentHashMap<>();
    private final Map<String, Integer> byShopType = new ConcurrentHashMap<>();
    private final Map<String, Integer> byWorld = new ConcurrentHashMap<>();
    private final Map<String, Integer> byMaterial = new ConcurrentHashMap<>();
    private final AtomicLong totalToday = new AtomicLong();
    private final AtomicLong totalAll = new AtomicLong();
    private final Deque<JsonObject> recent = new ArrayDeque<>();
    private volatile String lastResetDate = LocalDate.now().toString();

    public void record(HttpRequest req) {
        try {
            String today = LocalDate.now().toString();
            if (!today.equals(lastResetDate)) {
                byHour.clear();
                totalToday.set(0);
                lastResetDate = today;
            }
            totalToday.incrementAndGet();
            totalAll.incrementAndGet();

            String key = LocalDateTime.now().format(HOUR_FMT);
            Hour h = byHour.computeIfAbsent(key, k -> new Hour());
            synchronized (h) {
                h.count++;
                String target = req.target == null ? "" : req.target;
                if (target.matches(".*/api/(shops|price|items|qsfilter).*")) h.port++;
            }

            String shopType = req.param("shop_type");
            if (shopType != null && !shopType.isEmpty()) {
                byShopType.merge(shopType.toUpperCase(java.util.Locale.ROOT), 1, Integer::sum);
            }
            String world = req.param("world");
            if (world != null && !world.isEmpty()) {
                byWorld.merge(world, 1, Integer::sum);
            }
            String material = req.param("material");
            if (material != null && !material.isEmpty()) {
                byMaterial.merge(material.toUpperCase(java.util.Locale.ROOT), 1, Integer::sum);
            }

            JsonObject ev = new JsonObject();
            ev.addProperty("time", System.currentTimeMillis());
            ev.addProperty("method", req.method);
            ev.addProperty("path", req.target);
            synchronized (recent) {
                recent.addFirst(ev);
                while (recent.size() > 100) recent.removeLast();
            }
        } catch (Exception ignored) {
        }
    }

    public long totalAll() {
        return totalAll.get();
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("success", true);
        o.addProperty("total_today", totalToday.get());
        o.addProperty("today_date", lastResetDate);

        JsonArray hours = new JsonArray();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 23; i >= 0; i--) {
            LocalDateTime t = now.minusHours(i).withMinute(0).withSecond(0).withNano(0);
            String key = t.format(HOUR_FMT);
            Hour h = byHour.get(key);
            JsonObject x = new JsonObject();
            x.addProperty("hour", String.format("%02d:00", t.getHour()));
            x.addProperty("hour_key", key);
            x.addProperty("count", h == null ? 0 : h.count);
            x.addProperty("port_requests", h == null ? 0 : h.port);
            x.addProperty("shops_viewed", h == null ? 0 : h.shops);
            hours.add(x);
        }
        o.add("by_hour", hours);

        o.add("by_shop_type", mapToArray(byShopType, "type"));
        o.add("by_world", mapToArray(byWorld, "world"));

        JsonArray mats = mapToArray(byMaterial, "material");
        java.util.List<JsonObject> list = new java.util.ArrayList<>();
        for (com.google.gson.JsonElement e : mats) list.add(e.getAsJsonObject());
        list.sort((a, b) -> Integer.compare(b.get("count").getAsInt(), a.get("count").getAsInt()));
        JsonArray top = new JsonArray();
        for (int i = 0; i < Math.min(20, list.size()); i++) top.add(list.get(i));
        o.add("by_material_top", top);

        JsonArray events = new JsonArray();
        synchronized (recent) {
            int n = 0;
            for (JsonObject e : recent) {
                if (n++ >= 30) break;
                events.add(e);
            }
        }
        o.add("recent_events", events);
        return o;
    }

    private static JsonArray mapToArray(Map<String, Integer> map, String keyName) {
        JsonArray arr = new JsonArray();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            JsonObject x = new JsonObject();
            x.addProperty(keyName, e.getKey());
            x.addProperty("count", e.getValue());
            arr.add(x);
        }
        return arr;
    }
}
