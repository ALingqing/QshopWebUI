package cn.aqcraft.service;

import cn.aqcraft.QShopWebUIPlugin;
import cn.aqcraft.data.ShopEntry;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 库存与收购容量提醒。
 *
 * <p>对出售商店：容器内物品数量低于 {@code stock-low-threshold} 时告警；
 * 对收购商店：容器剩余可容纳份数低于阈值时告警。</p>
 *
 * <p>告警在网页「监控」页展示，并可通过 {@link NotificationService} 推送到 Webhook。
 * 带冷却时间，同一商店同一类型短时间内只告警一次，避免刷屏。</p>
 */
public final class StockAlertService {

    private final QShopWebUIPlugin plugin;
    private final Map<String, Long> lastAlert = new ConcurrentHashMap<>();

    public StockAlertService(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
    }

    public JsonObject alertJson(ShopEntry e, String kind, String detail) {
        JsonObject o = new JsonObject();
        o.addProperty("shop_id", e.shop_id);
        o.addProperty("item", e.shop_cn_name);
        o.addProperty("material", e.material);
        o.addProperty("owner", e.owner_name);
        o.addProperty("world", e.world);
        o.addProperty("x", e.x);
        o.addProperty("y", e.y);
        o.addProperty("z", e.z);
        o.addProperty("type", e.shop_type);
        o.addProperty("kind", kind);
        o.addProperty("detail", detail);
        o.addProperty("t", System.currentTimeMillis());
        return o;
    }

    /**
     * 判断某商店是否需要告警；需要且通过冷却则返回告警对象并推送，否则返回 null。
     *
     * @param stockItems     容器内当前物品件数；null 表示容器不可访问
     * @param stack          每份物品数（stacking_amount）
     * @param capacityItems  容器总容量（件数），收购商店计算剩余空间用；0 表示未知
     */
    public JsonObject evaluate(ShopEntry e, Long stockItems, int stack, int capacityItems) {
        if (e == null) return null;
        String kind;
        String detail;
        int threshold = plugin.config().stockLowThreshold;

        if (e.isSelling()) {
            if (stockItems == null) return null;
            int canSell = (int) (stockItems / Math.max(1, stack));
            if (stockItems <= 0) {
                kind = "out-of-stock";
                detail = "库存已耗尽，无法继续出售";
            } else if (canSell <= threshold) {
                kind = "low-stock";
                detail = "库存不足：仅剩 " + canSell + " 份可售（阈值 " + threshold + "）";
            } else {
                return null;
            }
        } else {
            if (capacityItems <= 0 || stockItems == null) return null;
            int used = (int) (stockItems / Math.max(1, stack));
            int capUnits = capacityItems / Math.max(1, stack);
            int remain = Math.max(0, capUnits - used);
            if (remain <= 0) {
                kind = "buy-capacity";
                detail = "收购容器已满，无法再收购";
            } else if (remain <= threshold) {
                kind = "buy-capacity";
                detail = "收购容量不足：仅剩 " + remain + " 份空间（阈值 " + threshold + "）";
            } else {
                return null;
            }
        }

        String cooldownKey = e.shop_id + "|" + kind;
        long now = System.currentTimeMillis();
        Long last = lastAlert.get(cooldownKey);
        if (last != null && now - last < plugin.config().alertCooldownMs) return null;

        lastAlert.put(cooldownKey, now);
        JsonObject alert = alertJson(e, kind, detail);
        if (plugin.notifications().available()) {
            plugin.notifications().push("【库存告警】" + e.shop_cn_name,
                    e.shop_cn_name + "（" + e.owner_name + "）\n" + detail);
        }
        return alert;
    }

    /** 扫描全量商店，返回当前应显示的告警列表（不推送，网页监控页用） */
    public List<JsonObject> scan(List<ShopEntry> shops) {
        java.util.List<JsonObject> out = new java.util.ArrayList<>();
        for (ShopEntry e : shops) {
            if (e.is_system_shop != null && e.is_system_shop) continue;
            Long stock = e.quantity == null ? null : (long) e.quantity;
            JsonObject a = evaluate(e, stock, e.stacking_amount, 0);
            if (a != null) out.add(a);
        }
        return out;
    }

    /**
     * 实时扫描：对每个商店通过 stockReader 读取容器物品件数，再求告警。
     * 用于网页「监控」页手动刷新；stockReader 需在服务器主线程执行（读取容器）。
     */
    public List<JsonObject> scanLive(List<ShopEntry> shops, java.util.function.Function<ShopEntry, Long> stockReader) {
        java.util.List<JsonObject> out = new java.util.ArrayList<>();
        if (stockReader == null) return out;
        for (ShopEntry e : shops) {
            if (e.is_system_shop != null && e.is_system_shop) continue;
            Long stock;
            try {
                stock = stockReader.apply(e);
            } catch (Throwable t) {
                stock = null;
            }
            JsonObject a = evaluate(e, stock, e.stacking_amount, 0);
            if (a != null) out.add(a);
        }
        return out;
    }
}