package com.qshop.webui.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.qshop.webui.QShopWebUIPlugin;
import com.qshop.webui.data.ShopEntry;
import com.qshop.webui.data.ShopStats;
import com.qshop.webui.http.HttpRequest;
import com.qshop.webui.http.HttpResponse;
import com.qshop.webui.util.JsonUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 统计 / 状态 / 健康检查 / 同步 相关 API */
public final class StatsApi extends ApiBase {

    public StatsApi(QShopWebUIPlugin plugin) {
        super(plugin);
    }

    // ============================================================
    // GET /api/stats
    // ============================================================

    public HttpResponse stats(HttpRequest req) {
        ShopStats st = plugin.shopData().stats();
        JsonObject stats = obj();
        put(stats, "total_shops", st.total);
        put(stats, "total_materials", st.materials);
        put(stats, "total_owners", st.owners);
        put(stats, "total_worlds", st.worlds);
        put(stats, "total_requests", plugin.requestStats().totalAll());
        put(stats, "total_activity", st.totalActivity);
        put(stats, "selling_shops", st.selling);
        put(stats, "buying_shops", st.buying);

        JsonObject o = obj();
        put(o, "success", true);
        put(o, "source", "cache");
        put(o, "qs_available", plugin.bridge().isAvailable());
        o.add("stats", stats);
        addTimestamps(o);
        return HttpResponse.json(o);
    }

    // ============================================================
    // GET /api/stats/realtime
    // ============================================================

    public HttpResponse statsRealtime(HttpRequest req) {
        long t0 = System.currentTimeMillis();
        List<ShopEntry> all = plugin.shopData().shops();
        ShopStats st = plugin.shopData().stats();

        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (ShopEntry s : all) {
            sum += s.price;
            min = Math.min(min, s.price);
            max = Math.max(max, s.price);
        }
        JsonObject stats = obj();
        put(stats, "total_shops", st.total);
        put(stats, "total_materials", st.materials);
        put(stats, "total_selling", st.selling);
        put(stats, "total_buying", st.buying);
        put(stats, "total_owners", st.owners);
        put(stats, "total_worlds", st.worlds);
        put(stats, "total_activity", st.totalActivity);
        put(stats, "avg_price", all.isEmpty() ? null : Math.round(sum / all.size() * 100.0) / 100.0);
        put(stats, "min_price", all.isEmpty() ? null : min);
        put(stats, "max_price", all.isEmpty() ? null : max);

        JsonObject memory = obj();
        put(memory, "total_shops", st.total);
        put(memory, "total_materials", st.materials);

        JsonObject o = obj();
        put(o, "success", true);
        put(o, "timestamp", JsonUtil.isoNow());
        put(o, "elapsed_ms", System.currentTimeMillis() - t0);
        put(o, "source", "quickshop-memory");
        o.add("stats", stats);
        o.add("memory", memory);
        return HttpResponse.json(o);
    }

    // ============================================================
    // GET /api/health
    // ============================================================

    public HttpResponse health(HttpRequest req) {
        long t0 = System.currentTimeMillis();
        boolean qs = plugin.bridge().isAvailable();
        ShopStats st = plugin.shopData().stats();

        JsonObject o = obj();
        put(o, "success", true);
        put(o, "status", qs ? "online" : "degraded");
        put(o, "version", plugin.getDescription().getVersion());
        String sn = plugin.config().serverName;
        put(o, "server_name", (sn == null || sn.isEmpty()) ? null : sn);
        String ss = plugin.config().serverSubtitle;
        put(o, "server_subtitle", (ss == null || ss.isEmpty()) ? null : ss);
        put(o, "timestamp", JsonUtil.isoNow());
        put(o, "response_time_ms", System.currentTimeMillis() - t0);
        o.add("qsfilter", qsfilterBlock());
        o.add("cache", cacheBlock());
        JsonObject db = obj();
        put(db, "connected", true);
        put(db, "type", "quickshop-memory");
        o.add("database", db);
        return HttpResponse.json(o);
    }

    // ============================================================
    // GET /api/qsfilter/status 与 POST /api/qsfilter/reconnect
    // ============================================================

    public HttpResponse qsfilterStatus(HttpRequest req) {
        JsonObject o = obj();
        put(o, "success", true);
        JsonObject q = qsfilterBlock();
        for (Map.Entry<String, com.google.gson.JsonElement> e : q.entrySet()) {
            o.add(e.getKey(), e.getValue());
        }
        o.add("cache", cacheBlock());
        return HttpResponse.json(o);
    }

    public HttpResponse qsfilterReconnect(HttpRequest req) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        plugin.bridge().reload();
        plugin.shopData().invalidate();
        JsonObject o = obj();
        put(o, "success", true);
        put(o, "connected", plugin.bridge().isAvailable());
        put(o, "message", plugin.bridge().getStatus());
        return HttpResponse.json(o);
    }

    // ============================================================
    // /api/sync/status 与 /api/sync/now
    // ============================================================

    public HttpResponse syncStatus(HttpRequest req) {
        ShopStats st = plugin.shopData().stats();
        JsonObject o = obj();
        put(o, "success", true);
        if (plugin.shopData().snapshotAt() > 0) put(o, "last_full_sync_at", plugin.shopData().snapshotAt());
        else o.add("last_full_sync_at", JsonNull.INSTANCE);
        put(o, "last_full_sync_ok", plugin.bridge().isAvailable());
        put(o, "last_full_sync_shops", st.total);
        put(o, "last_full_sync_duration_ms", 0);
        o.add("sync_schedule_times", new JsonArray());
        o.add("next_sync_at_list", new JsonArray());
        o.add("next_backup_at", JsonNull.INSTANCE);
        put(o, "retry_count", 0);
        put(o, "max_retries", 0);
        JsonArray history = new JsonArray();
        for (JsonObject h : plugin.shopData().syncHistory()) history.add(h);
        o.add("history", history);
        return HttpResponse.json(o);
    }

    public HttpResponse syncNow(HttpRequest req) {
        if (!plugin.bridge().isAvailable()) {
            return HttpResponse.error(400, "QuickShop 未连接");
        }
        long t0 = System.currentTimeMillis();
        plugin.shopData().refresh();
        JsonObject o = obj();
        put(o, "success", true);
        put(o, "shops_count", plugin.shopData().shops().size());
        put(o, "duration_ms", System.currentTimeMillis() - t0);
        return HttpResponse.json(o);
    }

    // ============================================================
    // GET /api/stats/requests
    // ============================================================

    public HttpResponse statsRequests(HttpRequest req) {
        JsonObject o = plugin.requestStats().toJson();

        JsonObject q = obj();
        put(q, "connected", plugin.bridge().isAvailable());
        put(q, "polling_count", plugin.shopData().stats().total);
        if (plugin.shopData().snapshotAt() > 0) put(q, "polling_last_at", plugin.shopData().snapshotAt());
        else put(q, "polling_last_at", 0);
        put(q, "webhook_event_count", 0);
        put(q, "webhook_last_at", null);
        put(q, "webhook_last_event", null);
        o.add("qsfilter", q);

        o.add("cache", cacheBlock());
        return HttpResponse.json(o);
    }

    // ============================================================
    // GET /api/admin/dashboard
    // ============================================================

    public HttpResponse dashboard(HttpRequest req) {
        JsonObject deny = null;
        HttpResponse d = adminOnly(req);
        if (d != null) return d;

        List<ShopEntry> all = plugin.shopData().shops();
        ShopStats st = plugin.shopData().stats();

        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        int r1 = 0, r2 = 0, r3 = 0, r4 = 0, r5 = 0;
        Map<String, int[]> owners = new HashMap<>();      // owner → [shops]
        Map<String, Long> ownerActivity = new HashMap<>();
        Map<String, int[]> mats = new HashMap<>();        // material → [shops, sumPrice]
        for (ShopEntry s : all) {
            sum += s.price;
            min = Math.min(min, s.price);
            max = Math.max(max, s.price);
            if (s.price < 1) r1++;
            else if (s.price < 10) r2++;
            else if (s.price < 100) r3++;
            else if (s.price < 1000) r4++;
            else r5++;
            if (s.owner_name != null) {
                owners.computeIfAbsent(s.owner_name, k -> new int[1])[0]++;
                ownerActivity.merge(s.owner_name, (long) s.activity_score, Long::sum);
            }
            if (s.material != null) {
                int[] m = mats.computeIfAbsent(s.material, k -> new int[2]);
                m[0]++;
                m[1] += (int) s.price;
            }
        }

        JsonObject o = obj();
        put(o, "success", true);

        JsonObject summary = obj();
        put(summary, "total_shops", st.total);
        put(summary, "selling_shops", st.selling);
        put(summary, "buying_shops", st.buying);
        put(summary, "unique_materials", st.materials);
        put(summary, "unique_owners", st.owners);
        put(summary, "unique_worlds", st.worlds);
        put(summary, "avg_price", all.isEmpty() ? null : Math.round(sum / all.size() * 100.0) / 100.0);
        put(summary, "min_price", all.isEmpty() ? null : min);
        put(summary, "max_price", all.isEmpty() ? null : max);
        put(summary, "total_activity", st.totalActivity);
        o.add("database_summary", summary);

        JsonObject dist = obj();
        put(dist, "r1", r1);
        put(dist, "r2", r2);
        put(dist, "r3", r3);
        put(dist, "r4", r4);
        put(dist, "r5", r5);
        o.add("price_distribution", dist);

        List<Map.Entry<String, int[]>> ownerList = new ArrayList<>(owners.entrySet());
        ownerList.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
        JsonArray topOwners = new JsonArray();
        for (int i = 0; i < Math.min(10, ownerList.size()); i++) {
            Map.Entry<String, int[]> e = ownerList.get(i);
            JsonObject x = obj();
            put(x, "owner_name", e.getKey());
            put(x, "shop_count", e.getValue()[0]);
            put(x, "total_activity", ownerActivity.getOrDefault(e.getKey(), 0L));
            topOwners.add(x);
        }
        o.add("top_owners", topOwners);

        List<Map.Entry<String, int[]>> matList = new ArrayList<>(mats.entrySet());
        matList.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
        JsonArray topMats = new JsonArray();
        for (int i = 0; i < Math.min(10, matList.size()); i++) {
            Map.Entry<String, int[]> e = matList.get(i);
            JsonObject x = obj();
            put(x, "material", e.getKey());
            put(x, "shop_count", e.getValue()[0]);
            put(x, "avg_price", e.getValue()[0] == 0 ? null
                    : Math.round(e.getValue()[1] * 100.0 / e.getValue()[0]) / 100.0);
            topMats.add(x);
        }
        o.add("top_materials", topMats);

        o.add("cache_stats", cacheBlock());

        JsonObject qs = obj();
        put(qs, "enabled", true);
        put(qs, "connected", plugin.bridge().isAvailable());
        put(qs, "base_url", "quickshop://" + plugin.bridge().getQuickShopVersion());
        put(qs, "polling_last_at", plugin.shopData().snapshotAt());
        put(qs, "polling_last_count", st.total);
        put(qs, "webhook_enabled", false);
        put(qs, "webhook_event_count", 0);
        put(qs, "webhook_last_at", null);
        o.add("qsfilter_status", qs);

        JsonObject rs = obj();
        put(rs, "total_today", plugin.requestStats().toJson().get("total_today").getAsLong());
        o.add("request_stats", rs);

        put(o, "from_cache", true);
        if (deny == null) {
            // no-op（保持结构）
        }
        return HttpResponse.json(o);
    }

    // ============================================================
    // 内部块
    // ============================================================

    private JsonObject qsfilterBlock() {
        boolean qs = plugin.bridge().isAvailable();
        ShopStats st = plugin.shopData().stats();
        JsonObject q = obj();
        put(q, "enabled", true);
        put(q, "connected", qs);
        put(q, "qs_available", qs);
        put(q, "base_url", "quickshop://" + plugin.bridge().getQuickShopVersion());
        put(q, "latency_ms", 0);
        put(q, "total_requests", plugin.requestStats().totalAll());
        put(q, "total_errors", 0);
        if (plugin.shopData().snapshotAt() > 0) put(q, "last_success_at", plugin.shopData().snapshotAt());
        else put(q, "last_success_at", null);
        put(q, "last_error", qs ? null : plugin.bridge().getStatus());
        put(q, "last_error_at", null);

        JsonObject lastSync = obj();
        put(lastSync, "total", st.total);
        put(lastSync, "selling", st.selling);
        put(lastSync, "buying", st.buying);
        put(lastSync, "materials", st.materials);
        put(lastSync, "owners", st.owners);
        put(lastSync, "worlds", st.worlds);
        q.add("last_sync_stats", lastSync);

        JsonObject polling = obj();
        put(polling, "interval_sec", plugin.config().snapshotTtlMs / 1000);
        if (plugin.shopData().snapshotAt() > 0) put(polling, "last_at", plugin.shopData().snapshotAt());
        else put(polling, "last_at", null);
        put(polling, "last_count", st.total);
        put(polling, "error_count", 0);
        q.add("polling", polling);

        JsonObject webhook = obj();
        put(webhook, "enabled", false);
        put(webhook, "last_at", null);
        put(webhook, "last_event", null);
        put(webhook, "event_count", 0);
        put(webhook, "error_count", 0);
        put(webhook, "source", null);
        q.add("webhook", webhook);
        return q;
    }

    private JsonObject cacheBlock() {
        ShopStats st = plugin.shopData().stats();
        JsonObject c = obj();
        put(c, "total_shops", st.total);
        put(c, "selling_shops", st.selling);
        put(c, "buying_shops", st.buying);
        put(c, "unique_owners", st.owners);
        put(c, "unique_worlds", st.worlds);
        put(c, "unique_materials", st.materials);
        if (st.lastSyncAt > 0) put(c, "last_full_sync_at", st.lastSyncAt);
        else put(c, "last_full_sync_at", null);
        if (st.lastUpdateAt > 0) put(c, "last_update_at", st.lastUpdateAt);
        else put(c, "last_update_at", null);
        put(c, "last_update_source", "quickshop");
        return c;
    }
}
