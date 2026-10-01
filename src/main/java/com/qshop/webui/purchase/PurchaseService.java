package com.qshop.webui.purchase;

import com.google.gson.JsonObject;
import com.qshop.webui.QShopWebUIPlugin;
import com.qshop.webui.bridge.EconomyBridge;
import com.qshop.webui.bridge.QuickShopBridge;
import com.qshop.webui.data.ShopEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网页「在线购买」：
 * 玩家在游戏内必须在线 → 扣款 → 从商店箱子出实物货 → 直接进背包（溢出掉落脚下）→ 店主收款。
 */
public final class PurchaseService {

    private final QShopWebUIPlugin plugin;
    private final Map<String, Long> cooldown = new ConcurrentHashMap<>();

    public PurchaseService(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
    }

    public JsonObject purchase(String shopId, String playerName, int amount) {
        if (shopId == null || shopId.trim().isEmpty()) return err("缺少商店 ID");
        if (playerName == null || playerName.trim().isEmpty()) return err("请输入你的游戏 ID");
        final String name = playerName.trim();
        int max = plugin.config().purchaseMaxAmount;
        final int amt = Math.max(1, Math.min(amount <= 0 ? 1 : amount, max));

        // 防连点
        long now = System.currentTimeMillis();
        Long last = cooldown.get(name.toLowerCase(Locale.ROOT));
        if (last != null && now - last < 1500) return err("操作太快，请稍后再试");
        cooldown.put(name.toLowerCase(Locale.ROOT), now);

        ShopEntry found = null;
        for (ShopEntry s : plugin.shopData().shops()) {
            if (shopId.trim().equals(s.shop_id)) {
                found = s;
                break;
            }
        }
        if (found == null) return err("商店不存在或数据未同步");
        if (!found.isSelling()) return err("这是收购商店，网页暂不支持");
        if (!(found.price > 0)) return err("该商店价格无效");

        final ShopEntry entry = found;
        try {
            return plugin.bridge().runOnMain(() -> doPurchase(entry, name, amt));
        } catch (Throwable t) {
            return err("交易执行失败: " + t.getMessage());
        }
    }

    /** 玩家出售给收购商店（网页收购界面） */
    public JsonObject sell(String shopId, String playerName, int amount) {
        if (shopId == null || shopId.trim().isEmpty()) return err("缺少商店 ID");
        if (playerName == null || playerName.trim().isEmpty()) return err("请输入你的游戏 ID");
        final String name = playerName.trim();
        int max = plugin.config().purchaseMaxAmount;
        final int amt = Math.max(1, Math.min(amount <= 0 ? 1 : amount, max));

        long now = System.currentTimeMillis();
        Long last = cooldown.get(name.toLowerCase(Locale.ROOT));
        if (last != null && now - last < 1500) return err("操作太快，请稍后再试");
        cooldown.put(name.toLowerCase(Locale.ROOT), now);

        ShopEntry found = null;
        for (ShopEntry s : plugin.shopData().shops()) {
            if (shopId.trim().equals(s.shop_id)) {
                found = s;
                break;
            }
        }
        if (found == null) return err("商店不存在或数据未同步");
        if (found.isSelling()) return err("这是出售商店，请到购买界面操作");
        if (!(found.price > 0)) return err("该商店价格无效");

        final ShopEntry entry = found;
        try {
            return plugin.bridge().runOnMain(() -> doSell(entry, name, amt));
        } catch (Throwable t) {
            return err("交易执行失败: " + t.getMessage());
        }
    }

    private JsonObject doPurchase(ShopEntry e, String name, int amount) {
        Player buyer = Bukkit.getPlayerExact(name);
        if (buyer == null || !buyer.isOnline()) {
            return err("玩家 " + name + " 不在线（在线购买需要玩家在游戏内）");
        }
        EconomyBridge eco = plugin.economy();
        if (!eco.available()) return err("服务器未安装经济插件（需要 Vault 支持）");

        double unit = e.price;
        double total = round2(unit * amount);

        long id = parseLong(e.shop_id);
        Object shop = id > 0 ? plugin.bridge().getShopById(id) : null;
        Inventory chestInv = shop == null ? null : resolveInventory(shop);

        // 样品物品：优先从商店箱子拿实物（含附魔/NBT）
        ItemStack sample = findSample(chestInv);
        if (sample == null) sample = fallbackItem(e);
        if (sample == null) return err("无法确定该商店的物品");

        long needItems = (long) amount * Math.max(1, e.stacking_amount);

        // 库存检查（非系统商店）
        if (!e.system_shop) {
            if (chestInv == null) return err("无法访问商店库存容器");
            int available = countItems(chestInv, sample);
            if (available < needItems) {
                long canBuy = available / Math.max(1, e.stacking_amount);
                return err("商店库存不足：最多可购买 " + canBuy + " 份");
            }
        }

        // 余额检查
        double balance = eco.balance(buyer);
        if (balance < total) {
            return err("余额不足：需要 " + total + "，当前 " + round2(balance));
        }

        // 扣款
        if (!eco.withdraw(buyer, total)) {
            return err("扣款失败（请检查经济插件）");
        }

        try {
            if (!e.system_shop && chestInv != null) {
                removeItems(chestInv, sample, needItems);
            }
            deliverItems(buyer, sample, needItems);
            if (!e.system_shop && e.owner_uuid != null) {
                try {
                    OfflinePlayer seller = Bukkit.getOfflinePlayer(UUID.fromString(e.owner_uuid));
                    eco.deposit(seller, total);
                } catch (Throwable ignored) {
                }
            }

            buyer.sendMessage("§a[在线购买] §f成功购买 §e" + amount + "§f 份 §b" + e.shop_cn_name
                    + " §f花费 §e" + total);
            plugin.shopData().invalidate();
            plugin.store().addFetchLog(0, 0, plugin.shopData().stats().total, "purchase",
                    name + " 网页购买 " + e.shop_cn_name + " x" + amount + " 花费 " + total);

            JsonObject o = new JsonObject();
            o.addProperty("success", true);
            o.addProperty("item", e.shop_cn_name);
            o.addProperty("amount", amount);
            o.addProperty("unit_price", unit);
            o.addProperty("total_price", total);
            o.addProperty("balance_left", round2(eco.balance(buyer)));
            o.addProperty("message", "购买成功，物品已放入背包");
            return o;
        } catch (Throwable t) {
            try {
                eco.deposit(buyer, total); // 回滚退款
            } catch (Throwable ignored) {
            }
            return err("交易过程中出错，已自动退款（" + t.getMessage() + "）");
        }
    }

    // ============================================================
    // 玩家卖出（收购）
    // ============================================================

    private JsonObject doSell(ShopEntry e, String name, int amount) {
        Player seller = Bukkit.getPlayerExact(name);
        if (seller == null || !seller.isOnline()) {
            return err("玩家 " + name + " 不在线（在线收购需要玩家在游戏内）");
        }
        EconomyBridge eco = plugin.economy();
        if (!eco.available()) return err("服务器未安装经济插件（需要 Vault 支持）");

        double unit = e.price;
        double total = round2(unit * amount);

        long id = parseLong(e.shop_id);
        Object shop = id > 0 ? plugin.bridge().getShopById(id) : null;
        Inventory chestInv = shop == null ? null : resolveInventory(shop);

        ItemStack sample = findSample(chestInv);
        if (sample == null) sample = fallbackItem(e);
        if (sample == null) return err("无法确定该商店的物品");

        int stack = Math.max(1, e.stacking_amount);
        long needItems = (long) amount * stack;

        // 1) 玩家背包检查
        int available = countItems(seller.getInventory(), sample);
        if (available < needItems) {
            long canSell = available / stack;
            return err("背包里没有足够的「" + e.shop_cn_name + "」：当前可卖 " + canSell + " 份（需要 " + needItems + " 个，现有 " + available + " 个）");
        }

        // 2) 非系统商店：容器容量 + 店主余额
        OfflinePlayer owner = null;
        if (!e.system_shop) {
            if (chestInv == null) return err("无法访问商店库存容器");
            if (!canFit(chestInv, sample, needItems)) return err("商店容器已满，暂时无法收购更多");
            if (e.owner_uuid != null) {
                try {
                    owner = Bukkit.getOfflinePlayer(UUID.fromString(e.owner_uuid));
                } catch (Throwable ignored) {
                }
            }
            if (owner != null && eco.balance(owner) < total) {
                return err("店主余额不足，无法支付 " + total);
            }
        }

        // 3) 店主扣款（系统商店不扣）
        if (owner != null && !eco.withdraw(owner, total)) {
            return err("店主扣款失败（请检查经济插件）");
        }

        try {
            // 4) 玩家物品 → 商店容器
            removeItems(seller.getInventory(), sample, needItems);
            if (!e.system_shop && chestInv != null) {
                addItems(chestInv, sample, needItems);
            }
            // 5) 报酬到账
            if (!eco.deposit(seller, total)) {
                // 回滚
                if (!e.system_shop && chestInv != null) removeItems(chestInv, sample, needItems);
                deliverItems(seller, sample, needItems);
                if (owner != null) eco.deposit(owner, total);
                return err("给你打款失败，交易已取消");
            }

            seller.sendMessage("§a[在线收购] §f成功出售 §e" + amount + "§f 份 §b" + e.shop_cn_name
                    + " §f获得 §e" + total);
            plugin.shopData().invalidate();
            plugin.store().addFetchLog(0, 0, plugin.shopData().stats().total, "sell",
                    name + " 网页出售 " + e.shop_cn_name + " x" + amount + " 获得 " + total);

            JsonObject o = new JsonObject();
            o.addProperty("success", true);
            o.addProperty("item", e.shop_cn_name);
            o.addProperty("amount", amount);
            o.addProperty("unit_price", unit);
            o.addProperty("total_price", total);
            o.addProperty("balance_left", round2(eco.balance(seller)));
            o.addProperty("message", "出售成功，报酬已到账");
            return o;
        } catch (Throwable t) {
            return err("交易过程中出错（" + t.getMessage() + "）");
        }
    }

    // ============================================================
    // 工具
    // ============================================================

    private Inventory resolveInventory(Object shop) {
        try {
            Object loc = QuickShopBridge.unwrap(QuickShopBridge.call(shop, "getLocation", "bukkitLocation"));
            if (loc instanceof Location) {
                Location l = (Location) loc;
                World w = l.getWorld();
                if (w == null) return null;
                BlockState st = w.getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ()).getState();
                if (st instanceof Container) return ((Container) st).getInventory();
                if (st instanceof org.bukkit.inventory.InventoryHolder) {
                    return ((org.bukkit.inventory.InventoryHolder) st).getInventory();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private ItemStack findSample(Inventory inv) {
        if (inv == null) return null;
        for (ItemStack it : inv.getContents()) {
            if (it != null && !it.getType().isAir()) return it.clone();
        }
        return null;
    }

    private ItemStack fallbackItem(ShopEntry e) {
        try {
            String base = e.material == null ? "" : e.material.split("\\|")[0];
            Material m = Material.matchMaterial(base);
            if (m == null || m.isAir()) return null;
            return new ItemStack(m);
        } catch (Throwable t) {
            return null;
        }
    }

    private int countItems(Inventory inv, ItemStack sample) {
        int n = 0;
        for (ItemStack it : inv.getContents()) {
            if (it != null && !it.getType().isAir() && it.isSimilar(sample)) n += it.getAmount();
        }
        return n;
    }

    private void removeItems(Inventory inv, ItemStack sample, long count) {
        long left = count;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType().isAir() || !it.isSimilar(sample)) continue;
            int take = (int) Math.min(left, it.getAmount());
            left -= take;
            if (take >= it.getAmount()) {
                inv.setItem(i, null);
            } else {
                ItemStack copy = it.clone();
                copy.setAmount(it.getAmount() - take);
                inv.setItem(i, copy);
            }
        }
    }

    private void deliverItems(Player buyer, ItemStack sample, long count) {
        int maxStack = Math.max(1, sample.getMaxStackSize());
        long left = count;
        while (left > 0) {
            int give = (int) Math.min(left, maxStack);
            left -= give;
            ItemStack stack = sample.clone();
            stack.setAmount(give);
            Map<Integer, ItemStack> leftover = buyer.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) {
                    buyer.getWorld().dropItem(buyer.getLocation(), drop);
                }
            }
        }
    }

    /** 容器还能容纳多少个该物品 */
    private int capacityFor(Inventory inv, ItemStack sample) {
        int maxStack = Math.max(1, sample.getMaxStackSize());
        int cap = 0;
        for (ItemStack it : inv.getContents()) {
            if (it == null || it.getType().isAir()) cap += maxStack;
            else if (it.isSimilar(sample)) cap += Math.max(0, maxStack - it.getAmount());
        }
        return cap;
    }

    private boolean canFit(Inventory inv, ItemStack sample, long count) {
        return count <= capacityFor(inv, sample);
    }

    /** 把物品放入容器（先叠加已有的同类，再占空格） */
    private void addItems(Inventory inv, ItemStack sample, long count) {
        int maxStack = Math.max(1, sample.getMaxStackSize());
        long left = count;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType().isAir() || !it.isSimilar(sample)) continue;
            int space = maxStack - it.getAmount();
            if (space <= 0) continue;
            int add = (int) Math.min(left, space);
            ItemStack copy = it.clone();
            copy.setAmount(it.getAmount() + add);
            inv.setItem(i, copy);
            left -= add;
        }
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && !it.getType().isAir()) continue;
            int add = (int) Math.min(left, maxStack);
            ItemStack stack = sample.clone();
            stack.setAmount(add);
            inv.setItem(i, stack);
            left -= add;
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(String.valueOf(s).trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static JsonObject err(String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("success", false);
        o.addProperty("error", msg);
        return o;
    }
}
