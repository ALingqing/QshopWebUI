package cn.aqcraft.service;

import cn.aqcraft.QShopWebUIPlugin;
import cn.aqcraft.util.JsonUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 商店营业状态管理：临时隐藏 / 维护模式 / 交易熔断。
 *
 * <ul>
 *   <li><b>营业开关</b>：{@code open} 全局营业 / 打烊。</li>
 *   <li><b>维护模式</b>：{@code maintenance} 开启后所有购买/收购被拒绝。</li>
 *   <li><b>临时隐藏</b>：{@code hiddenShops} 特定 shop_id 在网页端隐藏（前端配合）。</li>
 *   <li><b>交易熔断</b>：{@code circuitBreaker} 某商店连续失败 N 次后自动熔断，避免连环失败刷屏。</li>
 * </ul>
 *
 * <p>持久化于 {@code data/shop_status.json}。线程安全。</p>
 */
public final class ShopStatusService {

    private final QShopWebUIPlugin plugin;
    private final File file;
    private volatile boolean open = true;
    private volatile boolean maintenance = false;
    private final Map<String, Boolean> hiddenShops = new ConcurrentHashMap<>();
    private final Map<String, int[]> failureTracker = new ConcurrentHashMap<>(); // shop_id -> {count, firstMs}
    private final Map<String, Long> trippedUntil = new ConcurrentHashMap<>();
    private volatile String closedReason = "";

    public ShopStatusService(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/shop_status.json");
        load();
    }

    public synchronized void load() {
        try {
            if (!file.isFile()) return;
            JsonObject o = JsonParser.parseString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.has("open")) open = o.get("open").getAsBoolean();
            if (o.has("maintenance")) maintenance = o.get("maintenance").getAsBoolean();
            if (o.has("closed_reason")) closedReason = o.get("closed_reason").getAsString();
            if (o.has("hidden")) {
                for (var e : o.get("hidden").getAsJsonObject().entrySet()) {
                    hiddenShops.put(e.getKey(), e.getValue().getAsBoolean());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[状态] 读取 shop_status.json 失败: " + e.getMessage());
        }
    }

    public synchronized void save() {
        JsonObject o = new JsonObject();
        o.addProperty("open", open);
        o.addProperty("maintenance", maintenance);
        o.addProperty("closed_reason", closedReason);
        JsonObject h = new JsonObject();
        for (var e : hiddenShops.entrySet()) h.addProperty(e.getKey(), e.getValue());
        o.add("hidden", h);
        try {
            Files.write(file.toPath(), JsonUtil.toJson(o).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("[状态] 保存 shop_status.json 失败: " + e.getMessage());
        }
    }

    public void flush() {
        save();
    }

    // ==================== 全局开关 ====================

    public boolean isOpen() { return open; }
    public boolean isMaintenance() { return maintenance; }
    public String closedReason() { return closedReason; }

    public void setOpen(boolean v, String reason) {
        this.open = v;
        this.closedReason = reason == null ? "" : reason;
        save();
    }

    public void setMaintenance(boolean v) {
        this.maintenance = v;
        save();
    }

    // ==================== 隐藏商店 ====================

    public boolean isHidden(String shopId) { return hiddenShops.getOrDefault(shopId, false); }
    public void setHidden(String shopId, boolean v) { hiddenShops.put(shopId, v); save(); }
    public void removeHidden(String shopId) { hiddenShops.remove(shopId); save(); }
    public Map<String, Boolean> hiddenShops() { return new ConcurrentHashMap<>(hiddenShops); }

    // ==================== 交易熔断 ====================

    /**
     * 在交易失败时调用，累计失败次数，达阈值后熔断该商店一段时间。
     * @return 熔断后的剩余毫秒；未熔断返回 0
     */
    public long onTradeFailure(String shopId) {
        long now = System.currentTimeMillis();
        if (trippedUntil.containsKey(shopId) && trippedUntil.get(shopId) > now) {
            return trippedUntil.get(shopId) - now;
        }
        int[] t = failureTracker.computeIfAbsent(shopId, k -> new int[]{0, (int) now});
        t[0]++;
        if (t[0] >= plugin.config().circuitBreakerThreshold) {
            long trip = now + plugin.config().circuitBreakerDurationMs;
            trippedUntil.put(shopId, trip);
            failureTracker.remove(shopId);
            plugin.getLogger().warning("[熔断] 商店 " + shopId + " 连续失败，熔断 " + (plugin.config().circuitBreakerDurationMs / 1000) + "s");
            return trip - now;
        }
        return 0;
    }

    /** 交易成功时清零失败计数 */
    public void onTradeSuccess(String shopId) {
        failureTracker.remove(shopId);
        trippedUntil.remove(shopId);
    }

    /** 是否被熔断；返回剩余毫秒 */
    public long trippedMs(String shopId) {
        Long until = trippedUntil.get(shopId);
        if (until == null) return 0;
        long r = until - System.currentTimeMillis();
        return r > 0 ? r : 0;
    }

    public void resetTrip(String shopId) { trippedUntil.remove(shopId); failureTracker.remove(shopId); }

    // ==================== 快照 ====================

    public JsonObject snapshot() {
        JsonObject o = new JsonObject();
        o.addProperty("open", open);
        o.addProperty("maintenance", maintenance);
        o.addProperty("closed_reason", closedReason);
        JsonObject h = new JsonObject();
        for (var e : hiddenShops.entrySet()) h.addProperty(e.getKey(), e.getValue());
        o.add("hidden", h);
        o.addProperty("tripped", trippedUntil.size());
        return o;
    }
}