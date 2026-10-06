package cn.aqcraft.order;

import cn.aqcraft.QShopWebUIPlugin;
import cn.aqcraft.util.JsonUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 订单流水存储（独立于 trades.json 的增强订单系统）。
 *
 * <p>相比 WebStore.trades（仅成功交易、上限 10000、无索引），订单系统提供：</p>
 * <ul>
 *   <li><b>完整订单号</b>：全局自增 {@code ORD-<seq>}，稳定可查。</li>
 *   <li><b>失败订单</b>：FAILED 状态也被记录，可审计失败原因。</li>
 *   <li><b>幂等键</b>：同一 (player, shop, 订单键) 重复提交不重复记账。</li>
 *   <li><b>玩家索引</b>：按玩家名快速查询自己的订单。</li>
 *   <li><b>状态机</b>：SUCCESS / FAILED / CANCELLED，可标记。</li>
 * </ul>
 *
 * <p>存储于 {@code data/orders.json}，异步落盘，带节流。</p>
 */
public final class OrderStore {

    private final QShopWebUIPlugin plugin;
    private final File file;
    private final List<JsonObject> orders = new ArrayList<>();
    /** 玩家名(小写) → 订单下标（用于玩家自助查询） */
    private final ConcurrentHashMap<String, List<Integer>> playerIndex = new ConcurrentHashMap<>();
    private final AtomicBoolean savePending = new AtomicBoolean();
    private long seq = 0;
    private static final int MAX_ORDERS = 50000;

    public OrderStore(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(new File(plugin.getDataFolder(), "data"), "orders.json");
    }

    // ============================================================
    // 加载 / 保存
    // ============================================================

    public void load() {
        try {
            if (!file.isFile()) return;
            JsonElement el = JsonParser.parseString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            JsonArray arr = el.isJsonArray() ? el.getAsJsonArray() : new JsonArray();
            for (JsonElement e : arr) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                orders.add(o);
                try {
                    seq = Math.max(seq, o.get("seq").getAsLong());
                } catch (Exception ignored) {
                }
                index(o);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Order] 读取 orders.json 失败: " + e.getMessage());
        }
    }

    private void index(JsonObject o) {
        String p = str(o, "player").toLowerCase(Locale.ROOT);
        if (p.isEmpty()) return;
        playerIndex.computeIfAbsent(p, k -> new ArrayList<>()).add(orders.size() - 1);
    }

    /** 安全取字符串：缺失 / JsonNull / 非基本类型 均返回 ""（避免对 JsonNull 调 getAsString 抛异常） */
    private static String str(JsonObject o, String key) {
        if (o == null) return "";
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull() || !e.isJsonPrimitive()) ? "" : e.getAsString();
    }

    /** null → ""，避免 addProperty 写入 JsonNull */
    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private void save() {
        JsonArray arr = new JsonArray();
        synchronized (this) {
            for (JsonObject o : orders) arr.add(o);
        }
        try {
            File tmp = new File(file.getParentFile(), "orders.json.tmp");
            Files.write(tmp.toPath(), JsonUtil.toJson(arr).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Order] 保存 orders.json 失败: " + e.getMessage());
        }
    }

    private void scheduleSave() {
        if (!savePending.compareAndSet(false, true)) return;
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                save();
            } finally {
                savePending.set(false);
            }
        }, 40L);
    }

    public void flush() {
        try {
            save();
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    // 下单（核心）
    // ============================================================

    /**
     * 记录一笔订单.
     *
     * @param idempotencyKey 幂等键（如 "WEB-<uuid>"）；同键重复调用返回已有订单而非重复记账，可传 null 关闭
     * @return 订单对象；若命中幂等键返回已有订单
     */
    public synchronized JsonObject recordOrder(String idempotencyKey, String type, String status,
                                               String shopId, String item, String material,
                                               int amount, long items, double unitPrice, double total,
                                               String player, String owner, boolean online, String reason) {
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            JsonObject dup = findByKey(idempotencyKey);
            if (dup != null) return dupCommit(dup);
        }
        long now = System.currentTimeMillis();
        String seqStr = Long.toString(++seq);
        String orderId = "ORD-" + seqStr;
        JsonObject o = new JsonObject();
        o.addProperty("order_id", orderId);
        o.addProperty("seq", seq);
        o.addProperty("key", safe(idempotencyKey));
        o.addProperty("type", safe(type));      // BUY / SELL
        o.addProperty("status", safe(status));  // SUCCESS / FAILED / CANCELLED
        o.addProperty("t", now);
        o.addProperty("shop_id", safe(shopId));
        o.addProperty("item", safe(item));
        o.addProperty("material", safe(material));
        o.addProperty("amount", amount);
        o.addProperty("items", items);
        o.addProperty("unit_price", unitPrice);
        o.addProperty("total", total);
        o.addProperty("player", safe(player));
        o.addProperty("owner", safe(owner));
        o.addProperty("online", online);
        o.addProperty("source", "web");
        if (reason != null && !reason.isEmpty()) o.addProperty("reason", reason);
        orders.add(o);
        while (orders.size() > MAX_ORDERS) {
            orders.remove(0);
        }
        // 重建索引（简单起见全量重建；订单量小，性能可接受）
        rebuildIndex();
        scheduleSave();
        return o;
    }

    private JsonObject findByKey(String key) {
        for (int i = orders.size() - 1; i >= 0; i--) {
            JsonObject o = orders.get(i);
            if (key.equals(str(o, "key"))) return o;
        }
        return null;
    }

    private JsonObject dupCommit(JsonObject o) {
        // 幂等命中：返回已有订单（带 duplicate 标记，供 API 层识别）
        JsonObject r = o.deepCopy();
        r.addProperty("duplicate", true);
        return r;
    }

    private void rebuildIndex() {
        playerIndex.clear();
        for (int i = 0; i < orders.size(); i++) {
            JsonObject o = orders.get(i);
            String p = str(o, "player").toLowerCase(Locale.ROOT);
            if (p.isEmpty()) continue;
            playerIndex.computeIfAbsent(p, k -> new ArrayList<>()).add(i);
        }
    }

    // ============================================================
    // 查询
    // ============================================================

    /** 按订单号查单条；不存在返回 null */
    public synchronized JsonObject byId(String orderId) {
        if (orderId == null) return null;
        for (int i = orders.size() - 1; i >= 0; i--) {
            if (orderId.equals(str(orders.get(i), "order_id"))) {
                return orders.get(i).deepCopy();
            }
        }
        return null;
    }

    /** 玩家自助查询：按玩家名 + 可选过滤 */
    public synchronized List<JsonObject> byPlayer(String player, String type, int limit, int offset) {
        String key = player == null ? "" : player.toLowerCase(Locale.ROOT);
        List<Integer> idx = playerIndex.get(key);
        if (idx == null || idx.isEmpty()) return new ArrayList<>();
        String upType = type == null ? "" : type.toUpperCase(Locale.ROOT);
        List<JsonObject> out = new ArrayList<>();
        // 索引下标是按加入顺序的，倒序遍历得到时间倒序
        for (int i = idx.size() - 1; i >= 0; i--) {
            JsonObject o = orders.get(idx.get(i));
            if (o == null) continue;
            if (!upType.isEmpty() && !upType.equals(str(o, "type"))) continue;
            out.add(o.deepCopy());
        }
        // 分页
        if (offset < 0) offset = 0;
        int end = Math.min(out.size(), offset + Math.max(0, limit));
        return new ArrayList<>(out.subList(Math.min(offset, out.size()), end));
    }

    /** 玩家订单总数 */
    public synchronized int countByPlayer(String player, String type) {
        String key = player == null ? "" : player.toLowerCase(Locale.ROOT);
        List<Integer> idx = playerIndex.get(key);
        if (idx == null || idx.isEmpty()) return 0;
        String upType = type == null ? "" : type.toUpperCase(Locale.ROOT);
        int n = 0;
        for (int i : idx) {
            JsonObject o = orders.get(i);
            if (o == null) continue;
            if (!upType.isEmpty() && !upType.equals(str(o, "type"))) continue;
            n++;
        }
        return n;
    }

    /** 全部订单快照（管理端/统计用） */
    public synchronized List<JsonObject> snapshot() {
        return new ArrayList<>(orders);
    }

    /** 全部订单数量 */
    public synchronized int countAll() {
        return orders.size();
    }

    /** 管理端分页查询：按可选 type / player 过滤，时间倒序 */
    public synchronized List<JsonObject> snapshotPaged(String type, String player, int limit, int offset) {
        String upType = type == null ? "" : type.toUpperCase(Locale.ROOT);
        String p = player == null ? "" : player.toLowerCase(Locale.ROOT);
        List<JsonObject> all = new ArrayList<>();
        for (int i = orders.size() - 1; i >= 0; i--) {
            JsonObject o = orders.get(i);
            if (o == null) continue;
            if (!upType.isEmpty() && !upType.equals(str(o, "type"))) continue;
            if (!p.isEmpty()) {
                String op = str(o, "player").toLowerCase(Locale.ROOT);
                if (!p.equals(op)) continue;
            }
            all.add(o.deepCopy());
        }
        if (offset < 0) offset = 0;
        int end = Math.min(all.size(), offset + Math.max(0, limit));
        return new ArrayList<>(all.subList(Math.min(offset, all.size()), end));
    }

    public synchronized void clear() {
        orders.clear();
        playerIndex.clear();
        seq = 0;
        save();
    }
}