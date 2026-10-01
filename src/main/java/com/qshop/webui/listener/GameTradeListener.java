package com.qshop.webui.listener;

import com.google.gson.JsonObject;
import com.qshop.webui.QShopWebUIPlugin;
import com.qshop.webui.bridge.QuickShopBridge;
import com.qshop.webui.util.Materials;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * 游戏内交易监听（QuickShop 交易成功事件）：
 * 玩家在游戏里点商店完成的购买/收购也会记入交易统计（source=game）。
 * 全反射动态注册，零编译依赖；兼容多个 QuickShop 版本的事件包路径。
 */
public final class GameTradeListener implements Listener {

    private static final String[] EVENT_CLASSES = {
            "com.ghostchu.quickshop.api.event.economy.ShopSuccessPurchaseEvent",
            "com.ghostchu.quickshop.api.event.ShopSuccessPurchaseEvent",
            "com.ghostchu.quickshop.api.event.shop.ShopSuccessPurchaseEvent",
            "org.maxgamer.quickshop.api.event.ShopSuccessPurchaseEvent",
            "org.maxgamer.quickshop.event.ShopSuccessPurchaseEvent"
    };

    private final QShopWebUIPlugin plugin;

    private GameTradeListener(QShopWebUIPlugin plugin) {
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
                GameTradeListener listener = new GameTradeListener(plugin);
                Bukkit.getPluginManager().registerEvent(evc, listener, EventPriority.MONITOR,
                        (l, event) -> listener.onTrade(event), plugin, true);
                plugin.getLogger().info("[交易监听] 已挂钩游戏内交易统计: " + cn);
                return true;
            } catch (Throwable ignored) {
            }
        }
        plugin.getLogger().warning("[交易监听] 未找到 QuickShop 交易事件类（游戏内交易不会计入统计）");
        return false;
    }

    private void onTrade(Event event) {
        try {
            Object shop = QuickShopBridge.call(event, "getShop");
            if (shop == null) return;

            int amount = (int) QuickShopBridge.asLong(QuickShopBridge.call(event, "getAmount"), 1);
            if (amount <= 0) amount = 1;
            double total = QuickShopBridge.asDouble(QuickShopBridge.call(event, "getBalance"), 0);
            if (total <= 0) total = QuickShopBridge.asDouble(QuickShopBridge.call(event, "getBalanceWithoutTax"), 0);
            double tax = QuickShopBridge.asDouble(QuickShopBridge.call(event, "getTax"), 0);

            Object purchaser = QuickShopBridge.unwrap(QuickShopBridge.call(event, "getPurchaser"));
            String playerName = purchaser == null ? null
                    : QuickShopBridge.asString(QuickShopBridge.call(purchaser, "getUsername", "getName"));
            if (playerName == null || playerName.isEmpty() || "null".equals(playerName)) {
                Object uid = purchaser == null ? null : QuickShopBridge.unwrap(QuickShopBridge.call(purchaser, "getUniqueId"));
                playerName = uid == null ? "unknown" : String.valueOf(uid);
            }

            Object selling = QuickShopBridge.unwrap(QuickShopBridge.call(shop, "isSelling"));
            boolean buy = selling instanceof Boolean && (Boolean) selling;

            Object itemObj = QuickShopBridge.unwrap(QuickShopBridge.call(shop, "getItem"));
            String material = null;
            int stack = 1;
            if (itemObj instanceof ItemStack) {
                ItemStack is = (ItemStack) itemObj;
                material = is.getType().name();
                stack = Math.max(1, is.getAmount());
            }
            String cnName = material == null ? "未知物品" : Materials.cn(material);

            long shopId = QuickShopBridge.asLong(QuickShopBridge.call(shop, "getShopId"), 0);
            Object ownerObj = QuickShopBridge.unwrap(QuickShopBridge.call(shop, "getOwner"));
            String owner = ownerObj == null ? null
                    : QuickShopBridge.asString(QuickShopBridge.call(ownerObj, "getUsername", "getName"));

            long items = (long) amount * stack;
            JsonObject t = new JsonObject();
            t.addProperty("t", System.currentTimeMillis());
            t.addProperty("type", buy ? "BUY" : "SELL");
            t.addProperty("source", "game");
            t.addProperty("shop_id", String.valueOf(shopId));
            t.addProperty("item", cnName);
            t.addProperty("material", material);
            t.addProperty("amount", amount);
            t.addProperty("items", items);
            t.addProperty("unit_price", amount > 0 ? Math.round(total / amount * 100.0) / 100.0 : total);
            t.addProperty("total", Math.round(total * 100.0) / 100.0);
            t.addProperty("tax", Math.round(tax * 100.0) / 100.0);
            t.addProperty("player", playerName);
            t.addProperty("owner", owner);
            t.addProperty("online", true);
            plugin.store().addTrade(t);
        } catch (Throwable ignored) {
        }
    }
}
