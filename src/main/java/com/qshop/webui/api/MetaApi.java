package com.qshop.webui.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.qshop.webui.QShopWebUIPlugin;
import com.qshop.webui.http.HttpRequest;
import com.qshop.webui.http.HttpResponse;

import java.util.Map;

/** 配置 / 设置 / 日志 / 活动 / 公告 */
public final class MetaApi extends ApiBase {

    public MetaApi(QShopWebUIPlugin plugin) {
        super(plugin);
    }

    // ============================================================
    // GET /api/config
    // ============================================================

    public HttpResponse config(HttpRequest req) {
        JsonObject c = obj();
        String sn = plugin.config().serverName;
        put(c, "app_name", (sn == null || sn.isEmpty()) ? "QshopWebUI" : sn);
        put(c, "server_name", (sn == null || sn.isEmpty()) ? null : sn);
        put(c, "default_page_size", plugin.config().defaultPageSize);
        put(c, "max_page_size", plugin.config().maxPageSize);
        put(c, "search_min_length", 2);
        put(c, "max_batch_size", 5000);
        put(c, "require_auth", plugin.config().requireAuth);
        put(c, "database_connected", true);

        // 用户可在“设置”里覆盖的同名配置
        JsonObject settings = plugin.store().settingsObject();
        for (String key : new String[]{"app_name", "default_page_size", "max_page_size",
                "search_min_length", "max_batch_size", "require_auth"}) {
            if (settings.has(key)) {
                JsonObject s = settings.getAsJsonObject(key);
                if (s.has("value") && !s.get("value").isJsonNull()) {
                    c.add(key, s.get("value"));
                }
            }
        }
        JsonObject o = obj();
        put(o, "success", true);
        o.add("config", c);
        return HttpResponse.json(o);
    }

    // ============================================================
    // /api/pages  页面可见性（config.yml pages.hide-* 为默认；网页后台设置优先）
    // ============================================================

    public HttpResponse pagesGet(HttpRequest req) {
        JsonObject o = obj();
        put(o, "success", true);
        o.add("hidden_pages", hiddenPages());
        return HttpResponse.json(o);
    }

    public HttpResponse pagesSet(HttpRequest req) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        JsonObject b = body(req);
        JsonArray arr = new JsonArray();
        if (b.has("hidden") && b.get("hidden").isJsonArray()) {
            for (JsonElement e : b.getAsJsonArray("hidden")) {
                if (e == null || e.isJsonNull()) continue;
                String s = e.getAsString().toLowerCase(java.util.Locale.ROOT).trim();
                if (!s.isEmpty()) arr.add(s);
            }
        }
        plugin.store().setSetting("hidden_pages", arr, "array");
        JsonObject o = obj();
        put(o, "success", true);
        put(o, "action", "updated");
        o.add("hidden_pages", arr);
        return HttpResponse.json(o);
    }

    /** 合并：网页后台设置（优先）→ config.yml 默认 */
    private JsonArray hiddenPages() {
        JsonObject s = plugin.store().getSetting("hidden_pages");
        if (s != null && s.has("value") && s.get("value").isJsonArray()) {
            JsonArray copy = new JsonArray();
            for (JsonElement e : s.getAsJsonArray("value")) copy.add(e);
            return copy;
        }
        JsonArray arr = new JsonArray();
        for (String p : plugin.config().hiddenPages) arr.add(p);
        return arr;
    }

    // ============================================================
    // /api/settings
    // ============================================================

    public HttpResponse settingsGet(HttpRequest req) {
        JsonObject settings = plugin.store().settingsObject();
        JsonObject o = obj();
        put(o, "success", true);
        o.add("settings", settings);
        put(o, "total", settings.size());
        return HttpResponse.json(o);
    }

    public HttpResponse settingGet(HttpRequest req, String key) {
        JsonObject s = plugin.store().getSetting(key);
        if (s == null) return HttpResponse.error(404, "设置不存在");
        JsonObject o = obj();
        put(o, "success", true);
        JsonObject one = obj();
        put(one, "value", s.has("value") ? s.get("value") : null);
        put(one, "type", s.has("type") ? s.get("type").getAsString() : "string");
        put(one, "protected", s.has("protected") && s.get("protected").getAsBoolean());
        o.add("setting", one);
        return HttpResponse.json(o);
    }

    public HttpResponse settingPut(HttpRequest req, String key) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        JsonObject b = body(req);
        if (!b.has("value")) return HttpResponse.error(400, "缺少 value");
        JsonElement val = b.get("value");
        String type = typeOf(val);
        boolean ok = plugin.store().setSetting(key, val, type);
        if (!ok) return HttpResponse.error(400, "该设置项受保护，无法修改");
        JsonObject o = obj();
        put(o, "success", true);
        put(o, "action", "updated");
        put(o, "key", key);
        put(o, "value", val);
        return HttpResponse.json(o);
    }

    public HttpResponse settingsPut(HttpRequest req) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        JsonObject b = body(req);
        int updated = 0;
        for (Map.Entry<String, JsonElement> e : b.entrySet()) {
            String type = typeOf(e.getValue());
            if (plugin.store().setSetting(e.getKey(), e.getValue(), type)) updated++;
        }
        JsonObject o = obj();
        put(o, "success", true);
        put(o, "updated", updated);
        return HttpResponse.json(o);
    }

    private static String typeOf(JsonElement e) {
        if (e == null || e.isJsonNull()) return "string";
        if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isNumber()) return "number";
            if (p.isBoolean()) return "boolean";
        }
        return "string";
    }

    // ============================================================
    // GET /api/log
    // ============================================================

    public HttpResponse log(HttpRequest req) {
        int limit = Math.min(Math.max(1, req.intParam("limit", 50)), 200);
        JsonObject o = obj();
        put(o, "success", true);
        o.add("logs", plugin.store().fetchLogs(limit));
        return HttpResponse.json(o);
    }

    // ============================================================
    // /api/activity
    // ============================================================

    public HttpResponse activityPut(HttpRequest req, String material) {
        plugin.store().recordActivity(material);
        JsonObject o = obj();
        put(o, "success", true);
        put(o, "material", material == null ? null : material.toUpperCase(java.util.Locale.ROOT));
        return HttpResponse.json(o);
    }

    public HttpResponse activityGet(HttpRequest req) {
        int limit = Math.min(Math.max(1, req.intParam("limit", 20)), 100);
        JsonObject o = obj();
        put(o, "success", true);
        o.add("activity", plugin.store().activityTop(limit));
        return HttpResponse.json(o);
    }

    // ============================================================
    // 公告
    // ============================================================

    public HttpResponse announcementsGet(HttpRequest req) {
        boolean isAdmin = isAdmin(req);
        JsonArray all = plugin.store().announcementsArray(isAdmin);
        JsonObject o = obj();
        put(o, "success", true);
        put(o, "total", all.size());
        o.add("results", all);
        put(o, "is_admin", isAdmin);
        return HttpResponse.json(o);
    }

    public HttpResponse announcementsPost(HttpRequest req) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        JsonObject b = body(req);
        String title = jstr(b, "title", "").trim();
        String content = jstr(b, "content", "").trim();
        if (title.isEmpty() || title.length() > 200) {
            return HttpResponse.error(400, "标题不能为空且长度需不超过 200");
        }
        if (content.isEmpty() || content.length() > 10000) {
            return HttpResponse.error(400, "内容不能为空且长度需不超过 10000");
        }
        String author = jstr(b, "author", null);
        if (author == null || author.isEmpty()) {
            var s = plugin.sessions().fromRequest(req);
            author = s != null ? s.username : "admin";
        }
        JsonObject ann = plugin.store().createAnnouncement(title, content, author,
                jbool(b, "published", true), jstr(b, "priority", "normal"));
        JsonObject o = obj();
        put(o, "success", true);
        o.add("data", ann);
        return HttpResponse.json(o);
    }

    public HttpResponse announcementsPut(HttpRequest req, String id) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        JsonObject ann = plugin.store().getAnnouncement(id);
        if (ann == null) return HttpResponse.error(404, "公告不存在");
        JsonObject b = body(req);
        if (b.has("title")) {
            String t = jstr(b, "title", "").trim();
            if (t.isEmpty() || t.length() > 200) return HttpResponse.error(400, "标题无效");
            ann.addProperty("title", t);
        }
        if (b.has("content")) {
            String c = jstr(b, "content", "").trim();
            if (c.isEmpty() || c.length() > 10000) return HttpResponse.error(400, "内容无效");
            ann.addProperty("content", c);
        }
        if (b.has("published")) ann.addProperty("published", jbool(b, "published", true));
        if (b.has("priority")) ann.addProperty("priority", jstr(b, "priority", "normal"));
        ann.addProperty("updatedAt", System.currentTimeMillis());
        plugin.store().saveAnnouncements();
        JsonObject o = obj();
        put(o, "success", true);
        o.add("data", ann);
        return HttpResponse.json(o);
    }

    public HttpResponse announcementsDelete(HttpRequest req, String id) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        if (!plugin.store().deleteAnnouncement(id)) return HttpResponse.error(404, "公告不存在");
        JsonObject o = obj();
        put(o, "success", true);
        return HttpResponse.json(o);
    }

    // ============================================================
    // /api/allocate（原版为占位实现）
    // ============================================================

    public HttpResponse allocate(HttpRequest req) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        JsonObject o = obj();
        put(o, "success", true);
        put(o, "source", "manual");
        put(o, "total_shops", plugin.shopData().stats().total);
        put(o, "unique_materials", plugin.shopData().stats().materials);
        put(o, "triggered_at", System.currentTimeMillis());
        return HttpResponse.json(o);
    }

    public HttpResponse allocateConfig(HttpRequest req) {
        JsonObject o = obj();
        put(o, "success", true);
        put(o, "enabled", false);
        put(o, "interval_minutes", 0);
        put(o, "note", "插件版不使用定时分配");
        return HttpResponse.json(o);
    }
}
