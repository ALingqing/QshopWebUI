package com.qshop.webui.listener;

import com.qshop.webui.QShopWebUIPlugin;
import com.qshop.webui.util.ItemCodec;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** 玩家上线时发放网页离线购买的暂存物品 */
public final class PurchaseJoinListener implements Listener {

    private final QShopWebUIPlugin plugin;

    public PurchaseJoinListener(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        List<JsonObject> pending = plugin.store().takePending(player.getName());
        if (pending == null || pending.isEmpty()) return;

        int delivered = 0;
        for (JsonObject entry : pending) {
            try {
                String b64 = entry.has("i") ? entry.get("i").getAsString() : null;
                long count = entry.has("n") ? entry.get("n").getAsLong() : 1;
                ItemStack base = ItemCodec.decode(b64);
                if (base == null || count <= 0) continue;
                int maxStack = Math.max(1, base.getMaxStackSize());
                long left = count;
                while (left > 0) {
                    int give = (int) Math.min(left, maxStack);
                    left -= give;
                    ItemStack stack = base.clone();
                    stack.setAmount(give);
                    for (ItemStack drop : player.getInventory().addItem(stack).values()) {
                        player.getWorld().dropItem(player.getLocation(), drop);
                    }
                }
                delivered += (int) Math.min(count, Integer.MAX_VALUE);
            } catch (Throwable ignored) {
            }
        }
        if (delivered > 0) {
            final int n = delivered;
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                    player.sendMessage("§a[在线商店] §f你网页购买的 §e" + n + "§f 件物品已发放！"), 20L);
        }
    }
}
