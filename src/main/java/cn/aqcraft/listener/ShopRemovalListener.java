package cn.aqcraft.listener;

import com.google.gson.JsonObject;
import cn.aqcraft.QShopWebUIPlugin;
import cn.aqcraft.bridge.QuickShopBridge;
import cn.aqcraft.util.Materials;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * 商店移除监听（QuickShop 商店删除事件）：
 * 游戏内删除商店（含后台网页删除）时记录一条"移除商店"日志——
 * 时间 / 店主 / 物品 / 价格 / 位置，供后台导出审计。
 * 全反射动态注册，零编译依赖；兼容多个 QuickShop 版本的事件包路径。
 */
public final class ShopRemovalListener implements Listener {

    private static final String[] EVENT_CLASSES = {
            "com.ghostchu.quickshop.api.event.management.ShopDeleteEvent",
            "com.ghostchu.quickshop.api.event.ShopDeleteEvent",
            "com.ghostchu.quickshop.api.event.shop.ShopDeleteEvent",
            "org.maxgamer.quickshop.api.event.ShopRemoveEvent",
            "org.maxgamer.quickshop.event.ShopRemoveEvent"
    };

    private final QShopWebUIPlugin plugin;

    private ShopRemovalListener(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
    }

    /** 动态注册（QuickShop 未安装 / 事件类不存在时静默跳过） */
    public static boolean register(QShopWebUIPlugin plugin) {
        Plugin qs = Bukkit.getPluginManager().getPlugin("QuickShop");
        if (qs == null) qs = Bukkit.getPluginManager().getPlugin("QuickShop-Hikari");
        if (qs == null || !qs.isEnabled()) return false;
        ClassLoader cl = qs.getClass().getClassLoader();
        for (String cn : EVENT_CLASSES) {
            try {
                Class<?> ec = Class.forName(cn, false, cl);
                if (!Event.class.isAssignableFrom(ec)) continue;
                @SuppressWarnings("unchecked")
                Class<? extends Event> evc = (Class<? extends Event>) ec;
                ShopRemovalListener listener = new ShopRemovalListener(plugin);
                Bukkit.getPluginManager().registerEvent(evc, listener, EventPriority.MONITOR,
                        (l, event) -> listener.onDelete(event), plugin, true);
                plugin.getLogger().info("[商店移除监听] 已挂钩移除商店记录: " + cn);
                return true;
            } catch (Throwable ignored) {
            }
        }
        plugin.getLogger().warning("[商店移除监听] 未找到 QuickShop 商店删除事件类（删除记录不会实时记录）");
        return false;
    }

    private void onDelete(Event event) {
        try {
            // ShopDeleteEvent 是分阶段事件（PRE / PRE_CANCELLABLE / POST），同一删除会触发多次。
            // 策略：不丢弃任何阶段（防某些版本/路径不触发 POST 导致漏记），
            // 由 WebStore.addRemoval 按“位置 + 5 秒窗口”自动去重合并为一条。
            Object shop = QuickShopBridge.call(event, "getShop");
            if (shop == null) return;

            long shopId = QuickShopBridge.asLong(QuickShopBridge.call(shop, "getShopId"), 0);

            Object ownerObj = QuickShopBridge.unwrap(QuickShopBridge.call(shop, "getOwner"));
            String ownerName = ownerObj == null ? null
                    : QuickShopBridge.asString(QuickShopBridge.call(ownerObj, "getUsername", "getName"));
            String ownerUuid = ownerObj == null ? null
                    : QuickShopBridge.asString(QuickShopBridge.call(ownerObj, "getUniqueId"));

            Object itemObj = QuickShopBridge.unwrap(QuickShopBridge.call(shop, "getItem"));
            String material = null;
            if (itemObj instanceof ItemStack) material = ((ItemStack) itemObj).getType().name();

            double price = QuickShopBridge.asDouble(QuickShopBridge.call(shop, "getPrice"), 0);

            String world = null;
            double x = 0, y = 0, z = 0;
            Object locObj = QuickShopBridge.unwrap(QuickShopBridge.call(shop, "getLocation"));
            if (locObj != null) {
                Object w = QuickShopBridge.unwrap(QuickShopBridge.call(locObj, "getWorld"));
                world = w == null ? null : QuickShopBridge.asString(QuickShopBridge.call(w, "getName"));
                x = QuickShopBridge.asDouble(QuickShopBridge.call(locObj, "getX"), 0);
                y = QuickShopBridge.asDouble(QuickShopBridge.call(locObj, "getY"), 0);
                z = QuickShopBridge.asDouble(QuickShopBridge.call(locObj, "getZ"), 0);
            }

            JsonObject r = new JsonObject();
            r.addProperty("t", System.currentTimeMillis());
            r.addProperty("source", "game"); // 实时事件（游戏内 / 后台删除均会触发）
            r.addProperty("shop_id", String.valueOf(shopId));
            if (ownerName != null) r.addProperty("owner", ownerName);
            if (ownerUuid != null) r.addProperty("owner_uuid", ownerUuid);
            r.addProperty("item", material == null ? "未知物品" : Materials.cn(material));
            if (material != null) r.addProperty("material", material);
            r.addProperty("price", Math.round(price * 100.0) / 100.0);
            if (world != null) {
                r.addProperty("world", world);
                r.addProperty("x", Math.round(x));
                r.addProperty("y", Math.round(y));
                r.addProperty("z", Math.round(z));
            }
            plugin.store().addRemoval(r);
        } catch (Throwable ignored) {
        }
    }
}
