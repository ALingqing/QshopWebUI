package com.qshop.webui.listener;

import com.qshop.webui.QShopWebUIPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * QuickShop 实时数据监听：
 * 商店创建 / 删除 / 改价 / 改类型 / 改物品 / 改名 / 改店主 / 库存变化等
 * 任意变动都会触发网页快照自动刷新（尾部防抖合并），
 * 让网页数据紧跟游戏内状态（配合 5 秒 TTL 兜底）。
 * 全反射动态注册，零编译依赖；兼容多个 QuickShop 版本的事件包路径。
 */
public final class ShopDataListener implements Listener {

    private static final String[] EVENT_CLASSES = {
            // 商店增删
            "com.ghostchu.quickshop.api.event.management.ShopCreateEvent",
            "com.ghostchu.quickshop.api.event.management.ShopDeleteEvent",
            // 设置变更（改价 / 类型 / 物品 / 名称 / 店主 / 无限库存 / 显示）
            "com.ghostchu.quickshop.api.event.settings.type.ShopPriceEvent",
            "com.ghostchu.quickshop.api.event.settings.type.ShopTypeEvent",
            "com.ghostchu.quickshop.api.event.settings.type.ShopItemEvent",
            "com.ghostchu.quickshop.api.event.settings.type.ShopNameEvent",
            "com.ghostchu.quickshop.api.event.settings.type.ShopOwnerEvent",
            "com.ghostchu.quickshop.api.event.settings.type.ShopUnlimitedEvent",
            "com.ghostchu.quickshop.api.event.settings.type.ShopDisplayEvent",
            // 库存变化（买卖 / 补货）
            "com.ghostchu.quickshop.api.event.inventory.ShopInventoryChangedEvent"
    };

    private final QShopWebUIPlugin plugin;

    private ShopDataListener(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
    }

    /** 动态注册全部可用事件（QuickShop 未安装时静默跳过） */
    public static boolean register(QShopWebUIPlugin plugin) {
        Plugin qs = Bukkit.getPluginManager().getPlugin("QuickShop");
        if (qs == null) qs = Bukkit.getPluginManager().getPlugin("QuickShop-Hikari");
        if (qs == null || !qs.isEnabled()) return false;
        ClassLoader cl = qs.getClass().getClassLoader();

        ShopDataListener listener = new ShopDataListener(plugin);
        int hooked = 0;
        for (String cn : EVENT_CLASSES) {
            try {
                Class<?> ec = Class.forName(cn, false, cl);
                if (!Event.class.isAssignableFrom(ec)) continue;
                @SuppressWarnings("unchecked")
                Class<? extends Event> evc = (Class<? extends Event>) ec;
                Bukkit.getPluginManager().registerEvent(evc, listener, EventPriority.MONITOR,
                        (l, event) -> listener.onShopChanged(event), plugin, true);
                hooked++;
            } catch (Throwable ignored) {
            }
        }
        if (hooked > 0) {
            plugin.getLogger().info("[数据监听] 已挂钩 " + hooked + " 个 QuickShop 数据变更事件（网页实时刷新）");
            return true;
        }
        plugin.getLogger().warning("[数据监听] 未找到 QuickShop 数据变更事件（网页将仅靠 " + (plugin.config().snapshotTtlMs / 1000) + " 秒 TTL 刷新）");
        return false;
    }

    private void onShopChanged(Event event) {
        try {
            plugin.shopData().onShopChanged();
        } catch (Throwable ignored) {
        }
    }
}
