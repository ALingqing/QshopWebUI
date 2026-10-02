package com.qshop.webui.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.qshop.webui.QShopWebUIPlugin;
import com.qshop.webui.util.JsonUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Web 侧小型数据存储（插件数据目录下的 JSON 文件）：
 * settings / 公告 / 活动计数 / 抓取日志 / 港口坐标 / 注册用户。
 */
public final class WebStore {

    private final QShopWebUIPlugin plugin;
    private final File dir;

    // ---- settings（key → {"value","type","description","protected"}） ----
    private final Map<String, JsonObject> settings = new LinkedHashMap<>();
    // ---- 公告（id → announcement） ----
    private final Map<String, JsonObject> announcements = new LinkedHashMap<>();
    // ---- 活动计数 ----
    private static final class Act {
        volatile int views;
        volatile long lastVisit;
    }

    private final ConcurrentHashMap<String, Act> activity = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> activityBuffer = new ConcurrentHashMap<>();
    // ---- 抓取日志（倒序，最新在前） ----
    private final List<JsonObject> fetchLogs = new ArrayList<>();
    private long logSeq = 0;
    // ---- 港口 ----
    private volatile JsonObject harbor = defaultHarbor();
    // ---- 注册用户（小写用户名 → {"username","password_hash","role","email","created_at","last_login","active"}） ----
    private final ConcurrentHashMap<String, JsonObject> users = new ConcurrentHashMap<>();
    /** 网页离线购买的待领取物品：玩家名(小写) → [{i:base64物品, n:数量, t:时间}] */
    private final ConcurrentHashMap<String, List<JsonObject>> pending = new ConcurrentHashMap<>();
    /** 交易记录（购买/收购）：最近 MAX_TRADES 条 */
    private final List<JsonObject> trades = new ArrayList<>();
    private static final int MAX_TRADES = 10000;
    /** 被移除（删除）的商店记录：最近 MAX_REMOVALS 条 */
    private final List<JsonObject> removals = new ArrayList<>();
    private static final int MAX_REMOVALS = 20000;

    /** 写盘节流：批量场景（如一次删除上百家商店）合并为一次写盘，避免连续 IO 卡顿 */
    private final ScheduledExecutorService saveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "QShopWebUI-Save");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean tradesSavePending = new AtomicBoolean();
    private final AtomicBoolean removalsSavePending = new AtomicBoolean();

    public WebStore(QShopWebUIPlugin plugin, File dataFolder) {
        this.plugin = plugin;
        this.dir = new File(dataFolder, "data");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("[Store] 数据目录创建失败: " + dir);
        }
    }

    // ============================================================
    // 加载 / 保存
    // ============================================================

    public void load() {
        loadSettings();
        loadAnnouncements();
        loadActivity();
        loadFetchLogs();
        loadHarbor();
        loadUsers();
        loadPending();
        loadTrades();
        loadRemovals();
    }

    private JsonElement readJson(String name) {
        try {
            File f = new File(dir, name);
            if (!f.isFile()) return null;
            return JsonParser.parseString(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("[Store] 读取 " + name + " 失败: " + e.getMessage());
            return null;
        }
    }

    private void writeJson(String name, Object data) {
        try {
            File f = new File(dir, name);
            File tmp = new File(dir, name + ".tmp");
            Files.write(tmp.toPath(), JsonUtil.toJson(data).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Store] 保存 " + name + " 失败: " + e.getMessage());
        }
    }

    // ============================================================
    // settings
    // ============================================================

    private void loadSettings() {
        addSettingDefault("app_name", "QshopWebUI", "string");
        addSettingDefault("items_per_page", 12, "number");
        addSettingDefault("default_sort", "relevance", "string");
        addSettingDefault("enable_3d", true, "boolean");
        addSettingDefault("enable_search", true, "boolean");
        addSettingDefault("enable_activity", true, "boolean");
        addSettingDefault("search_min_length", 2, "number");
        addSettingDefault("max_batch_size", 5000, "number");
        addSettingDefault("log_retention", 30, "number");
        addSettingDefault("api_rate_limit", 1000, "number");
        addSettingDefault("require_auth", true, "boolean");
        addSettingDefault("admin_username", plugin.config().adminUsername, "string");
        addSettingDefault("admin_password", "******", "string", true);
        addSettingDefault("session_timeout", plugin.config().sessionTimeout, "number");

        JsonElement loaded = readJson("settings.json");
        if (loaded != null && loaded.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : loaded.getAsJsonObject().entrySet()) {
                if (e.getValue().isJsonObject()) {
                    settings.put(e.getKey(), e.getValue().getAsJsonObject());
                }
            }
        }
        saveSettings();
    }

    private void addSettingDefault(String key, Object value, String type) {
        addSettingDefault(key, value, type, false);
    }

    private void addSettingDefault(String key, Object value, String type, boolean protect) {
        if (settings.containsKey(key)) return;
        JsonObject o = new JsonObject();
        if (value instanceof Boolean) o.addProperty("value", (Boolean) value);
        else if (value instanceof Number) o.addProperty("value", (Number) value);
        else o.addProperty("value", String.valueOf(value));
        o.addProperty("type", type);
        o.add("description", null);
        o.addProperty("protected", protect);
        settings.put(key, o);
    }

    public synchronized JsonObject settingsObject() {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, JsonObject> e : settings.entrySet()) {
            o.add(e.getKey(), e.getValue().deepCopy());
        }
        return o;
    }

    public synchronized JsonObject getSetting(String key) {
        JsonObject v = settings.get(key);
        return v == null ? null : v.deepCopy();
    }

    public synchronized boolean setSetting(String key, JsonElement value, String type) {
        JsonObject cur = settings.get(key);
        if (cur == null) {
            cur = new JsonObject();
            cur.addProperty("protected", false);
            cur.addProperty("type", type == null ? "string" : type);
            cur.add("description", null);
            settings.put(key, cur);
        }
        if (cur.has("protected") && cur.get("protected").getAsBoolean()) {
            return false;
        }
        cur.add("value", value == null ? null : value.deepCopy());
        if (type != null) {
            cur.addProperty("type", type);
        }
        saveSettings();
        return true;
    }

    public void saveSettings() {
        writeJson("settings.json", settings);
    }

    // ============================================================
    // 公告
    // ============================================================

    private void loadAnnouncements() {
        JsonElement loaded = readJson("announcements.json");
        if (loaded != null && loaded.isJsonArray()) {
            for (JsonElement e : loaded.getAsJsonArray()) {
                if (e.isJsonObject() && e.getAsJsonObject().has("id")) {
                    announcements.put(e.getAsJsonObject().get("id").getAsString(), e.getAsJsonObject());
                }
            }
            return;
        }
        // 默认 3 条（与原版一致）
        long now = System.currentTimeMillis();
        String[][] defaults = {
                {"欢迎来到 Qshop 商店系统",
                        "本站直接读取 Minecraft 服务器中 QuickShop 插件的商店数据，提供物品价格查询、店主信息等功能。\n\n使用\"物品浏览\"可查看所有物品汇总数据，\"商店浏览\"查看单家商店详情。",
                        "high"},
                {"关于数据实时性",
                        "页面数据来自服务器内存中的 QuickShop 商店数据，默认 5 秒缓存刷新一次。\n\n在\"信息统计\"页可以查看当前同步状态。",
                        "normal"},
                {"管理员公告测试",
                        "这是一条普通公告，用于测试公告列表展示。\n\n系统时间基于服务器本地时间。",
                        "normal"}
        };
        int idx = 0;
        for (String[] d : defaults) {
            JsonObject o = new JsonObject();
            String id = "ann_" + (now - (defaults.length - idx - 1) * 60000L);
            o.addProperty("id", id);
            o.addProperty("title", d[0]);
            o.addProperty("content", d[1]);
            o.addProperty("author", "system");
            long t = now - (defaults.length - idx - 1) * 3600_000L;
            o.addProperty("createdAt", t);
            o.addProperty("updatedAt", t);
            o.addProperty("published", true);
            o.addProperty("priority", d[2]);
            announcements.put(id, o);
            idx++;
        }
        saveAnnouncements();
    }

    public synchronized JsonArray announcementsArray(boolean includeUnpublished) {
        List<JsonObject> list = new ArrayList<>();
        for (JsonObject a : announcements.values()) {
            if (!includeUnpublished && !a.get("published").getAsBoolean()) continue;
            list.add(a);
        }
        Map<String, Integer> order = Map.of("high", 0, "normal", 1, "low", 2);
        list.sort(Comparator
                .comparingInt((JsonObject a) -> order.getOrDefault(a.get("priority").getAsString(), 1))
                .thenComparingLong(a -> -a.get("createdAt").getAsLong()));
        JsonArray arr = new JsonArray();
        for (JsonObject a : list) arr.add(a);
        return arr;
    }

    public synchronized JsonObject createAnnouncement(String title, String content, String author,
                                                      boolean published, String priority) {
        long now = System.currentTimeMillis();
        String id = "ann_" + now + "_" + UUID.randomUUID().toString().substring(0, 6);
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("title", title);
        o.addProperty("content", content);
        o.addProperty("author", author);
        o.addProperty("createdAt", now);
        o.addProperty("updatedAt", now);
        o.addProperty("published", published);
        o.addProperty("priority", priority);
        announcements.put(id, o);
        saveAnnouncements();
        return o;
    }

    public synchronized JsonObject getAnnouncement(String id) {
        return announcements.get(id);
    }

    public synchronized boolean deleteAnnouncement(String id) {
        if (announcements.remove(id) == null) return false;
        saveAnnouncements();
        return true;
    }

    public void saveAnnouncements() {
        JsonArray arr = new JsonArray();
        synchronized (this) {
            for (JsonObject a : announcements.values()) arr.add(a);
        }
        writeJson("announcements.json", arr);
    }

    // ============================================================
    // 活动计数
    // ============================================================

    private void loadActivity() {
        JsonElement loaded = readJson("activity.json");
        if (loaded == null || !loaded.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : loaded.getAsJsonObject().entrySet()) {
            try {
                JsonObject o = e.getValue().getAsJsonObject();
                Act a = new Act();
                a.views = o.has("views") ? o.get("views").getAsInt() : 0;
                a.lastVisit = o.has("last_visit_ms") ? o.get("last_visit_ms").getAsLong() : 0;
                activity.put(e.getKey().toUpperCase(Locale.ROOT), a);
            } catch (Exception ignored) {
            }
        }
    }

    public int viewsOf(String material) {
        if (material == null) return 0;
        Act a = activity.get(material.toUpperCase(Locale.ROOT));
        return a == null ? 0 : a.views;
    }

    public void recordActivity(String material) {
        if (material == null || material.isEmpty()) return;
        activityBuffer.merge(material.toUpperCase(Locale.ROOT), 1, Integer::sum);
    }

    /** 定时调用：把 buffer 合并进 activity 并落盘 */
    public void flushActivity() {
        if (activityBuffer.isEmpty()) return;
        Map<String, Integer> batch = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : activityBuffer.entrySet()) {
            Integer v = activityBuffer.remove(e.getKey());
            if (v != null) batch.merge(e.getKey(), v, Integer::sum);
        }
        if (batch.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Integer> e : batch.entrySet()) {
            Act a = activity.computeIfAbsent(e.getKey(), k -> new Act());
            a.views += e.getValue();
            a.lastVisit = now;
        }
        saveActivity();
    }

    public void saveActivity() {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, Act> e : activity.entrySet()) {
            JsonObject v = new JsonObject();
            v.addProperty("material", e.getKey());
            v.addProperty("views", e.getValue().views);
            v.addProperty("last_visit", JsonUtil.iso(e.getValue().lastVisit));
            v.addProperty("last_visit_ms", e.getValue().lastVisit);
            o.add(e.getKey(), v);
        }
        writeJson("activity.json", o);
    }

    public JsonArray activityTop(int limit) {
        List<Map.Entry<String, Act>> list = new ArrayList<>(activity.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue().views, a.getValue().views));
        JsonArray arr = new JsonArray();
        for (Map.Entry<String, Act> e : list) {
            if (arr.size() >= limit) break;
            JsonObject v = new JsonObject();
            v.addProperty("material", e.getKey());
            v.addProperty("views", e.getValue().views);
            v.addProperty("last_visit", JsonUtil.iso(e.getValue().lastVisit));
            arr.add(v);
        }
        return arr;
    }

    // ============================================================
    // 抓取日志
    // ============================================================

    private void loadFetchLogs() {
        JsonElement loaded = readJson("fetch-log.json");
        if (loaded == null || !loaded.isJsonArray()) return;
        for (JsonElement e : loaded.getAsJsonArray()) {
            if (e.isJsonObject()) {
                fetchLogs.add(e.getAsJsonObject());
                try {
                    logSeq = Math.max(logSeq, e.getAsJsonObject().get("id").getAsLong());
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void addFetchLog(int added, int updated, int total, String source, String remark) {
        JsonObject o = new JsonObject();
        synchronized (fetchLogs) {
            o.addProperty("id", ++logSeq);
            o.addProperty("time", JsonUtil.isoNow());
            o.addProperty("added", added);
            o.addProperty("updated", updated);
            o.addProperty("total", total);
            o.addProperty("source", source);
            o.addProperty("remark", remark);
            fetchLogs.add(0, o);
            while (fetchLogs.size() > 500) {
                fetchLogs.remove(fetchLogs.size() - 1);
            }
        }
        writeJson("fetch-log.json", fetchLogs);
    }

    public JsonArray fetchLogs(int limit) {
        JsonArray arr = new JsonArray();
        synchronized (fetchLogs) {
            for (int i = 0; i < Math.min(limit, fetchLogs.size()); i++) {
                arr.add(fetchLogs.get(i));
            }
        }
        return arr;
    }

    // ============================================================
    // 港口
    // ============================================================

    private static JsonObject defaultHarbor() {
        JsonObject o = new JsonObject();
        o.addProperty("world", "world");
        o.addProperty("x", 0);
        o.addProperty("y", 64);
        o.addProperty("z", 0);
        return o;
    }

    private void loadHarbor() {
        JsonElement loaded = readJson("harbor.json");
        if (loaded != null && loaded.isJsonObject()) {
            harbor = loaded.getAsJsonObject();
        } else {
            saveHarbor();
        }
    }

    public JsonObject harbor() {
        return harbor.deepCopy();
    }

    public void setHarbor(JsonObject h) {
        this.harbor = h.deepCopy();
        saveHarbor();
    }

    public void saveHarbor() {
        writeJson("harbor.json", harbor);
    }

    // ============================================================
    // 用户（注册功能）
    // ============================================================

    private void loadUsers() {
        JsonElement loaded = readJson("users.json");
        if (loaded == null || !loaded.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : loaded.getAsJsonObject().entrySet()) {
            if (e.getValue().isJsonObject()) {
                users.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsJsonObject());
            }
        }
    }

    public JsonObject findUser(String username) {
        if (username == null) return null;
        return users.get(username.toLowerCase(Locale.ROOT));
    }

    public boolean userExists(String username) {
        return findUser(username) != null;
    }

    public JsonObject createUser(String username, String passwordHash, String email) {
        JsonObject o = new JsonObject();
        o.addProperty("username", username);
        o.addProperty("password_hash", passwordHash);
        o.addProperty("role", "user");
        o.addProperty("email", email);
        o.addProperty("created_at", JsonUtil.isoNow());
        o.add("last_login", null);
        o.addProperty("active", true);
        users.put(username.toLowerCase(Locale.ROOT), o);
        saveUsers();
        return o;
    }

    public void updateUserLastLogin(String username) {
        JsonObject o = findUser(username);
        if (o == null) return;
        o.addProperty("last_login", JsonUtil.isoNow());
        saveUsers();
    }

    public void updateUserPassword(String username, String passwordHash) {
        JsonObject o = findUser(username);
        if (o == null) return;
        o.addProperty("password_hash", passwordHash);
        saveUsers();
    }

    public void saveUsers() {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, JsonObject> e : users.entrySet()) o.add(e.getKey(), e.getValue());
        writeJson("users.json", o);
    }

    /** 未使用但保留：把 number/boolean 字符串还原为原生类型 */
    public static JsonElement castValue(String raw, String type) {
        if (raw == null) return null;
        try {
            switch (type == null ? "string" : type) {
                case "number":
                    return new JsonPrimitive(Double.parseDouble(raw));
                case "boolean":
                    return new JsonPrimitive(Boolean.parseBoolean(raw));
                default:
                    return new JsonPrimitive(raw);
            }
        } catch (Exception e) {
            return new JsonPrimitive(raw);
        }
    }

    // ============================================================
    // pending（网页离线购买暂存物品）
    // ============================================================

    private void loadPending() {
        try {
            JsonElement el = readJson("pending.json");
            if (el == null || !el.isJsonObject()) return;
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                if (!e.getValue().isJsonArray()) continue;
                List<JsonObject> list = new ArrayList<>();
                for (JsonElement item : e.getValue().getAsJsonArray()) {
                    if (item.isJsonObject()) list.add(item.getAsJsonObject());
                }
                if (!list.isEmpty()) pending.put(e.getKey(), list);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Store] 读取 pending.json 失败: " + e.getMessage());
        }
    }

    /** 添加待领取物品（玩家上线时发放） */
    public synchronized void addPending(String playerName, String base64Item, long count) {
        if (playerName == null || base64Item == null || count <= 0) return;
        String key = playerName.toLowerCase(java.util.Locale.ROOT);
        List<JsonObject> list = pending.computeIfAbsent(key, k -> new ArrayList<>());
        JsonObject o = new JsonObject();
        o.addProperty("i", base64Item);
        o.addProperty("n", count);
        o.addProperty("t", System.currentTimeMillis());
        list.add(o);
        savePending();
    }

    /** 取出并清空某玩家的待领取物品；无则返回 null */
    public synchronized List<JsonObject> takePending(String playerName) {
        if (playerName == null) return null;
        List<JsonObject> list = pending.remove(playerName.toLowerCase(java.util.Locale.ROOT));
        if (list == null || list.isEmpty()) return null;
        savePending();
        return new ArrayList<>(list);
    }

    /** 待领取物品总数量（件） */
    public int pendingCount(String playerName) {
        if (playerName == null) return 0;
        List<JsonObject> list = pending.get(playerName.toLowerCase(java.util.Locale.ROOT));
        if (list == null) return 0;
        int n = 0;
        for (JsonObject o : list) {
            try {
                n += o.has("n") ? o.get("n").getAsInt() : 0;
            } catch (Throwable ignored) {
            }
        }
        return n;
    }

    public void savePending() {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, List<JsonObject>> e : pending.entrySet()) {
            JsonArray arr = new JsonArray();
            for (JsonObject item : e.getValue()) arr.add(item);
            o.add(e.getKey(), arr);
        }
        writeJson("pending.json", o);
    }

    // ============================================================
    // trades（交易记录：购买 / 收购，供统计与导出）
    // ============================================================

    private void loadTrades() {
        try {
            JsonElement el = readJson("trades.json");
            if (el == null || !el.isJsonArray()) return;
            for (JsonElement item : el.getAsJsonArray()) {
                if (item.isJsonObject()) trades.add(item.getAsJsonObject());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Store] 读取 trades.json 失败: " + e.getMessage());
        }
    }

    /** 记录一笔交易（购买/收购），按时间有序插入 */
    public synchronized void addTrade(JsonObject t) {
        if (t == null) return;
        long ts = t.has("t") ? t.get("t").getAsLong() : System.currentTimeMillis();
        int idx = trades.size();
        while (idx > 0 && tradeTs(trades.get(idx - 1)) > ts) idx--;
        trades.add(idx, t);
        while (trades.size() > MAX_TRADES) trades.remove(0);
        scheduleSaveTrades();
    }

    /** 批量记录交易（历史导入用，只写盘一次） */
    public synchronized void addTrades(List<JsonObject> list) {
        if (list == null || list.isEmpty()) return;
        trades.addAll(list);
        trades.sort((a, b) -> Long.compare(tradeTs(a), tradeTs(b)));
        while (trades.size() > MAX_TRADES) trades.remove(0);
        saveTrades();
    }

    private static long tradeTs(JsonObject t) {
        try {
            return t != null && t.has("t") ? t.get("t").getAsLong() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    /** 全部交易记录快照 */
    public synchronized List<JsonObject> tradesSnapshot() {
        return new ArrayList<>(trades);
    }

    public synchronized void clearTrades() {
        trades.clear();
        saveTrades();
    }

    public void saveTrades() {
        JsonArray arr = new JsonArray();
        synchronized (this) {
            for (JsonObject t : trades) arr.add(t);
        }
        writeJson("trades.json", arr);
    }

    // ============================================================
    // 移除（删除）商店记录
    // ============================================================

    private void loadRemovals() {
        try {
            JsonElement el = readJson("removals.json");
            if (el == null || !el.isJsonArray()) return;
            for (JsonElement item : el.getAsJsonArray()) {
                if (item.isJsonObject()) removals.add(item.getAsJsonObject());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Store] 读取 removals.json 失败: " + e.getMessage());
        }
    }

    /** 记录一家被移除的商店（同一商店 5 秒内重复事件自动合并；位置优先匹配，兼容 POST 阶段字段变化） */
    public synchronized void addRemoval(JsonObject r) {
        if (r == null) return;
        long ts = tradeTs(r);
        String sid = r.has("shop_id") ? r.get("shop_id").getAsString() : "";
        String pos = (r.has("world") ? r.get("world").getAsString() : "") + "|"
                + (r.has("x") ? r.get("x").getAsString() : "") + "|"
                + (r.has("y") ? r.get("y").getAsString() : "") + "|"
                + (r.has("z") ? r.get("z").getAsString() : "");
        boolean hasPos = r.has("world") && !r.get("world").getAsString().isEmpty();
        int from = Math.max(0, removals.size() - 50);
        for (int i = removals.size() - 1; i >= from; i--) {
            JsonObject o = removals.get(i);
            if (Math.abs(tradeTs(o) - ts) > 5000L) continue;
            String oPos = (o.has("world") ? o.get("world").getAsString() : "") + "|"
                    + (o.has("x") ? o.get("x").getAsString() : "") + "|"
                    + (o.has("y") ? o.get("y").getAsString() : "") + "|"
                    + (o.has("z") ? o.get("z").getAsString() : "");
            boolean oHasPos = o.has("world") && !o.get("world").getAsString().isEmpty();
            if (hasPos && oHasPos) {
                // 位置相同（且都有位置） → 同一次删除
                if (pos.equals(oPos)) return;
            } else {
                // 无位置信息时回退到 shop_id 比较
                String oSid = o.has("shop_id") ? o.get("shop_id").getAsString() : "";
                if (sid.equals(oSid)) return;
            }
        }
        removals.add(r);
        while (removals.size() > MAX_REMOVALS) removals.remove(0);
        scheduleSaveRemovals();
    }

    /** 批量记录被移除的商店（历史导入用） */
    public synchronized void addRemovals(List<JsonObject> list) {
        if (list == null || list.isEmpty()) return;
        removals.addAll(list);
        removals.sort(Comparator.comparingLong(WebStore::tradeTs));
        while (removals.size() > MAX_REMOVALS) removals.remove(0);
        saveRemovals();
    }

    /** 全部移除记录快照（按时间正序） */
    public synchronized List<JsonObject> removalsSnapshot() {
        return new ArrayList<>(removals);
    }

    public void saveRemovals() {
        JsonArray arr = new JsonArray();
        synchronized (this) {
            for (JsonObject r : removals) arr.add(r);
        }
        writeJson("removals.json", arr);
    }

    /** 延迟合并写盘：2 秒内的多次变更只写一次（防批量操作时连续 IO） */
    private void scheduleSaveTrades() {
        if (!tradesSavePending.compareAndSet(false, true)) return;
        saveExecutor.schedule(() -> {
            try {
                saveTrades();
            } catch (Throwable ignored) {
            } finally {
                tradesSavePending.set(false);
            }
        }, 2, TimeUnit.SECONDS);
    }

    private void scheduleSaveRemovals() {
        if (!removalsSavePending.compareAndSet(false, true)) return;
        saveExecutor.schedule(() -> {
            try {
                saveRemovals();
            } catch (Throwable ignored) {
            } finally {
                removalsSavePending.set(false);
            }
        }, 2, TimeUnit.SECONDS);
    }

    /** 立即写出交易与移除记录（插件停用时调用，防止节流窗口丢数据） */
    public void flushSaves() {
        try {
            saveTrades();
        } catch (Throwable ignored) {
        }
        try {
            saveRemovals();
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    // 备份导出 / 恢复
    // ============================================================

    /** 导出 Web 侧全部数据（用于备份文件） */
    public JsonObject exportAll() {
        JsonObject o = new JsonObject();
        o.add("settings", settingsObject());
        JsonArray ann = new JsonArray();
        synchronized (this) {
            for (JsonObject a : announcements.values()) ann.add(a.deepCopy());
        }
        o.add("announcements", ann);
        JsonObject act = new JsonObject();
        for (Map.Entry<String, Act> e : activity.entrySet()) {
            JsonObject v = new JsonObject();
            v.addProperty("material", e.getKey());
            v.addProperty("views", e.getValue().views);
            v.addProperty("last_visit", JsonUtil.iso(e.getValue().lastVisit));
            v.addProperty("last_visit_ms", e.getValue().lastVisit);
            act.add(e.getKey(), v);
        }
        o.add("activity", act);
        o.add("harbor", harbor());
        JsonObject usersJson = new JsonObject();
        for (Map.Entry<String, JsonObject> e : users.entrySet()) usersJson.add(e.getKey(), e.getValue().deepCopy());
        o.add("users", usersJson);
        o.add("fetch_logs", fetchLogs(500));
        JsonObject pendingJson = new JsonObject();
        for (Map.Entry<String, List<JsonObject>> e : pending.entrySet()) {
            JsonArray arr = new JsonArray();
            for (JsonObject it : e.getValue()) arr.add(it.deepCopy());
            pendingJson.add(e.getKey(), arr);
        }
        o.add("pending", pendingJson);
        JsonArray tradesArr = new JsonArray();
        synchronized (this) {
            for (JsonObject t : trades) tradesArr.add(t.deepCopy());
        }
        o.add("trades", tradesArr);
        JsonArray removalsArr = new JsonArray();
        synchronized (this) {
            for (JsonObject r : removals) removalsArr.add(r.deepCopy());
        }
        o.add("removals", removalsArr);
        return o;
    }

    /** 从备份数据恢复（立即生效并落盘） */
    public void importAll(JsonObject data) {
        if (data == null) return;
        try {
            // settings
            if (data.has("settings") && data.get("settings").isJsonObject()) {
                synchronized (this) {
                    for (Map.Entry<String, JsonElement> e : data.getAsJsonObject("settings").entrySet()) {
                        if (!e.getValue().isJsonObject()) continue;
                        JsonObject incoming = e.getValue().getAsJsonObject();
                        JsonObject cur = settings.get(e.getKey());
                        if (cur != null) {
                            boolean prot = cur.has("protected") && cur.get("protected").getAsBoolean();
                            cur.add("value", incoming.has("value") ? incoming.get("value") : null);
                            if (incoming.has("type")) cur.addProperty("type", incoming.get("type").getAsString());
                            cur.addProperty("protected", prot);
                        } else {
                            settings.put(e.getKey(), incoming.deepCopy());
                        }
                    }
                }
                saveSettings();
            }
            // announcements
            if (data.has("announcements") && data.get("announcements").isJsonArray()) {
                synchronized (this) {
                    announcements.clear();
                    for (JsonElement e : data.getAsJsonArray("announcements")) {
                        if (e.isJsonObject() && e.getAsJsonObject().has("id")) {
                            announcements.put(e.getAsJsonObject().get("id").getAsString(), e.getAsJsonObject());
                        }
                    }
                }
                saveAnnouncements();
            }
            // activity
            if (data.has("activity") && data.get("activity").isJsonObject()) {
                activity.clear();
                for (Map.Entry<String, JsonElement> e : data.getAsJsonObject("activity").entrySet()) {
                    try {
                        JsonObject v = e.getValue().getAsJsonObject();
                        Act a = new Act();
                        a.views = v.has("views") ? v.get("views").getAsInt() : 0;
                        a.lastVisit = v.has("last_visit_ms") ? v.get("last_visit_ms").getAsLong() : 0;
                        activity.put(e.getKey().toUpperCase(Locale.ROOT), a);
                    } catch (Exception ignored) {
                    }
                }
                saveActivity();
            }
            // harbor
            if (data.has("harbor") && data.get("harbor").isJsonObject()) {
                harbor = data.getAsJsonObject("harbor").deepCopy();
                saveHarbor();
            }
            // users
            if (data.has("users") && data.get("users").isJsonObject()) {
                users.clear();
                for (Map.Entry<String, JsonElement> e : data.getAsJsonObject("users").entrySet()) {
                    if (e.getValue().isJsonObject()) {
                        users.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsJsonObject());
                    }
                }
                saveUsers();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Store] 恢复数据失败: " + e.getMessage());
        }
    }
}
