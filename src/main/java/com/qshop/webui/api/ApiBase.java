package com.qshop.webui.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.qshop.webui.QShopWebUIPlugin;
import com.qshop.webui.auth.SessionManager;
import com.qshop.webui.data.ShopEntry;
import com.qshop.webui.http.HttpRequest;
import com.qshop.webui.http.HttpResponse;
import com.qshop.webui.util.JsonUtil;

/** API 基类：通用参数 / 权限 / JSON 工具 */
public abstract class ApiBase {

    protected final QShopWebUIPlugin plugin;

    protected ApiBase(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
    }

    // ============================================================
    // 权限
    // ============================================================

    /** 管理操作校验；null = 通过；否则返回错误响应 */
    protected HttpResponse adminOnly(HttpRequest req) {
        if (!plugin.config().requireAuth) return null;
        SessionManager.Session s = plugin.sessions().fromRequest(req);
        if (s != null && (s.isAdmin() || s.username.equalsIgnoreCase(plugin.config().adminUsername))) {
            return null;
        }
        return HttpResponse.error(403, "权限不足");
    }

    protected boolean isAdmin(HttpRequest req) {
        if (!plugin.config().requireAuth) return true;
        SessionManager.Session s = plugin.sessions().fromRequest(req);
        return s != null && (s.isAdmin() || s.username.equalsIgnoreCase(plugin.config().adminUsername));
    }

    // ============================================================
    // 请求体
    // ============================================================

    protected JsonObject body(HttpRequest req) {
        try {
            String s = req.bodyString();
            if (s == null || s.trim().isEmpty()) return new JsonObject();
            JsonElement e = JsonUtil.parse(s);
            return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    protected JsonArray bodyArray(HttpRequest req) {
        try {
            String s = req.bodyString();
            if (s == null || s.trim().isEmpty()) return new JsonArray();
            JsonElement e = JsonUtil.parse(s);
            return e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
        } catch (Exception e) {
            return new JsonArray();
        }
    }

    // ============================================================
    // JSON 工具
    // ============================================================

    protected static JsonObject obj() {
        return new JsonObject();
    }

    protected static void put(JsonObject o, String key, Object value) {
        if (value == null) {
            o.add(key, JsonNull.INSTANCE);
            return;
        }
        if (value instanceof JsonElement) {
            o.add(key, (JsonElement) value);
        } else if (value instanceof String) {
            o.addProperty(key, (String) value);
        } else if (value instanceof Number) {
            o.addProperty(key, (Number) value);
        } else if (value instanceof Boolean) {
            o.addProperty(key, (Boolean) value);
        } else {
            o.addProperty(key, String.valueOf(value));
        }
    }

    protected static String jstr(JsonObject o, String key, String def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return def;
        try {
            return o.get(key).getAsString();
        } catch (Exception e) {
            return def;
        }
    }

    protected static int jint(JsonObject o, String key, int def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return def;
        try {
            return o.get(key).getAsInt();
        } catch (Exception e) {
            return def;
        }
    }

    protected static double jdouble(JsonObject o, String key, double def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return def;
        try {
            return o.get(key).getAsDouble();
        } catch (Exception e) {
            return def;
        }
    }

    protected static boolean jbool(JsonObject o, String key, boolean def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return def;
        try {
            JsonElement e = o.get(key);
            if (e.isJsonPrimitive()) {
                JsonPrimitive p = e.getAsJsonPrimitive();
                if (p.isBoolean()) return p.getAsBoolean();
                if (p.isNumber()) return p.getAsInt() != 0;
                return "true".equalsIgnoreCase(p.getAsString());
            }
            return def;
        } catch (Exception e) {
            return def;
        }
    }

    // ============================================================
    // 商店 JSON
    // ============================================================

    protected JsonObject shopJson(ShopEntry e) {
        return JsonUtil.gson().toJsonTree(e).getAsJsonObject();
    }

    protected JsonArray shopsJson(java.util.List<ShopEntry> list) {
        JsonArray arr = new JsonArray();
        for (ShopEntry e : list) arr.add(shopJson(e));
        return arr;
    }

    protected void addTimestamps(JsonObject o) {
        long sync = plugin.shopData().snapshotAt();
        if (sync > 0) o.addProperty("last_sync_at", sync);
        else o.add("last_sync_at", JsonNull.INSTANCE);
        long update = plugin.shopData().stats().lastUpdateAt;
        if (update > 0) o.addProperty("last_update_at", update);
        else o.add("last_update_at", JsonNull.INSTANCE);
    }
}
