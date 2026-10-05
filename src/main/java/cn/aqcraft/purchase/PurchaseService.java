package cn.aqcraft.purchase;

import com.google.gson.JsonObject;
import cn.aqcraft.QShopWebUIPlugin;
import cn.aqcraft.bridge.EconomyBridge;
import cn.aqcraft.bridge.LimitedBridge;
import cn.aqcraft.bridge.QuickShopBridge;
import cn.aqcraft.data.ShopEntry;
import cn.aqcraft.util.ItemCodec;
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
 * 填游戏 ID 直接下单 → 扣款 → 从商店箱子出实物货。
 * 玩家在线：物品直接进背包（溢出掉落脚下）；玩家离线：物品缓存，上线时自动发放（可在配置关闭）。
 */
public final class PurchaseService {

    private final QShopWebUIPlugin plugin;
    private final Map<String, Long> cooldown = new ConcurrentHashMap<>();

    public PurchaseService(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
    }

    public JsonObject purchase(String shopId, String playerName, int amount, String code) {
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

        if (!plugin.gameCodes().verifyAndConsume(name, code)) return err("验证码无效、已过期或玩家不在线，请在游戏内执行 /qshopwebui code");

        ShopEntry found = null;
        for (ShopEntry s : plugin.shopData().shops()) {
            if (shopId.trim().equals(s.shop_id)) {
                found = s;
                break;
            }
        }
        if (found == null) return err("商店不存在或数据未同步");
        if (!found.isSelling()) return err("这是收购商店，请到「收购界面」操作");
        if (!(found.price > 0)) return err("该商店价格无效");

        final ShopEntry entry = found;
        try {
            return plugin.bridge().runOnMain(() -> doPurchase(entry, name, amt));
        } catch (Throwable t) {
            return failRecord("BUY", entry, name, amt, "交易执行失败: " + t.getMessage());
        }
    }

    /** 玩家出售给收购商店（网页收购界面） */
    public JsonObject sell(String shopId, String playerName, int amount, String code) {
        if (shopId == null || shopId.trim().isEmpty()) return err("缺少商店 ID");
        if (playerName == null || playerName.trim().isEmpty()) return err("请输入你的游戏 ID");
        final String name = playerName.trim();
        int max = plugin.config().purchaseMaxAmount;
        final int amt = Math.max(1, Math.min(amount <= 0 ? 1 : amount, max));

        long now = System.currentTimeMillis();
        Long last = cooldown.get(name.toLowerCase(Locale.ROOT));
        if (last != null && now - last < 1500) return err("操作太快，请稍后再试");
        cooldown.put(name.toLowerCase(Locale.ROOT), now);

        if (!plugin.gameCodes().verifyAndConsume(name, code)) return err("验证码无效、已过期或玩家不在线，请在游戏内执行 /qshopwebui code");

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
            return failRecord("SELL", entry, name, amt, "交易执行失败: " + t.getMessage());
        }
    }

    /** 查询在线玩家背包中该商店物品的数量（收购界面「最大」按钮用） */
    public JsonObject inventoryCheck(String shopId, String playerName) {
        if (shopId == null || shopId.trim().isEmpty()) return err("缺少商店 ID");
        final String name = playerName == null ? "" : playerName.trim();
        if (name.isEmpty()) return err("请输入你的游戏 ID");
        ShopEntry found = null;
        for (ShopEntry s : plugin.shopData().shops()) {
            if (shopId.trim().equals(s.shop_id)) {
                found = s;
                break;
            }
        }
        if (found == null) return err("商店不存在或数据未同步");
        final ShopEntry entry = found;
        try {
            return plugin.bridge().runOnMain(() -> {
                JsonObject o = new JsonObject();
                Player p = Bukkit.getPlayerExact(name);
                if (p == null || !p.isOnline()) {
                    o.addProperty("success", true);
                    o.addProperty("online", false);
                    o.addProperty("count", 0);
                    o.addProperty("can_sell", 0);
                    o.addProperty("message", "玩家不在线（收购需要玩家在游戏内）");
                    return o;
                }
                long id = parseLong(entry.shop_id);
                Object shop = id > 0 ? plugin.bridge().getShopById(id) : null;
                Inventory chestInv = shop == null ? null : resolveInventory(shop);
                ItemStack sample = resolveSample(entry, shop, chestInv);
                int count = sample == null ? 0 : countItems(p.getInventory(), sample);
                int stack = Math.max(1, entry.stacking_amount);
                o.addProperty("success", true);
                o.addProperty("online", true);
                o.addProperty("count", count);
                o.addProperty("can_sell", count / stack);
                return o;
            });
        } catch (Throwable t) {
            return err("查询失败: " + t.getMessage());
        }
    }

    /** 查询玩家在该商店的限购剩余额度（网页购买弹窗用） */
    public JsonObject limitInfo(String shopId, String playerName) {
        if (!plugin.limited().available()) {
            JsonObject o = new JsonObject();
            o.addProperty("success", true);
            o.addProperty("available", false);
            o.addProperty("limited", false);
            return o;
        }
        if (shopId == null || shopId.trim().isEmpty()) return err("缺少商店 ID");
        ShopEntry found = null;
        for (ShopEntry s : plugin.shopData().shops()) {
            if (shopId.trim().equals(s.shop_id)) {
                found = s;
                break;
            }
        }
        if (found == null) return err("商店不存在或数据未同步");
        final ShopEntry entry = found;
        final String name = playerName == null ? "" : playerName.trim();
        try {
            return plugin.bridge().runOnMain(() -> {
                JsonObject o = new JsonObject();
                o.addProperty("success", true);
                o.addProperty("available", true);
                long id = parseLong(entry.shop_id);
                Object shop = id > 0 ? plugin.bridge().getShopById(id) : null;
                if (shop == null) {
                    o.addProperty("limited", false);
                    return o;
                }
                int limit = plugin.limited().limitOf(shop);
                if (limit < 1) {
                    o.addProperty("limited", false);
                    return o;
                }
                String period = plugin.limited().periodOf(shop);
                int used = 0;
                boolean unknown = true;
                if (!name.isEmpty()) {
                    Player online = Bukkit.getPlayerExact(name);
                    OfflinePlayer op = online != null ? online : resolvePlayer(name);
                    if (op != null) {
                        UUID pid = op.getUniqueId();
                        if (pid != null) {
                            used = plugin.limited().usedOf(shop, pid);
                            unknown = false;
                        }
                    }
                }
                o.addProperty("limited", true);
                o.addProperty("limit", limit);
                o.addProperty("used", used);
                o.addProperty("remaining", Math.max(0, limit - used));
                o.addProperty("period", period);
                o.addProperty("period_label", LimitedBridge.periodLabel(period));
                o.addProperty("player_unknown", unknown);
                return o;
            });
        } catch (Throwable t) {
            return err("查询失败: " + t.getMessage());
        }
    }

    /**
     * 内部程序化购买（供扩展 API / 其他插件调用）。
     * 跳过验证码校验——调用方为受信任的插件上下文。
     */
    public JsonObject purchaseInternal(String shopId, String playerName, int amount) {
        if (shopId == null || shopId.trim().isEmpty()) return err("缺少商店 ID");
        if (playerName == null || playerName.trim().isEmpty()) return err("请输入你的游戏 ID");
        final String name = playerName.trim();
        int max = plugin.config().purchaseMaxAmount;
        final int amt = Math.max(1, Math.min(amount <= 0 ? 1 : amount, max));
        ShopEntry found = null;
        for (ShopEntry s : plugin.shopData().shops()) {
            if (shopId.trim().equals(s.shop_id)) { found = s; break; }
        }
        if (found == null) return err("商店不存在或数据未同步");
        if (!found.isSelling()) return err("这是收购商店，请使用 sell");
        if (!(found.price > 0)) return err("该商店价格无效");
        final ShopEntry entry = found;
        try {
            return plugin.bridge().runOnMain(() -> doPurchase(entry, name, amt));
        } catch (Throwable t) {
            return failRecord("BUY", entry, name, amt, "交易执行失败: " + t.getMessage());
        }
    }

    /**
     * 内部程序化出售（供扩展 API / 其他插件调用）。
     * 跳过验证码校验——调用方为受信任的插件上下文。
     */
    public JsonObject sellInternal(String shopId, String playerName, int amount) {
        if (shopId == null || shopId.trim().isEmpty()) return err("缺少商店 ID");
        if (playerName == null || playerName.trim().isEmpty()) return err("请输入你的游戏 ID");
        final String name = playerName.trim();
        int max = plugin.config().purchaseMaxAmount;
        final int amt = Math.max(1, Math.min(amount <= 0 ? 1 : amount, max));
        ShopEntry found = null;
        for (ShopEntry s : plugin.shopData().shops()) {
            if (shopId.trim().equals(s.shop_id)) { found = s; break; }
        }
        if (found == null) return err("商店不存在或数据未同步");
        if (found.isSelling()) return err("这是出售商店，请使用 purchase");
        if (!(found.price > 0)) return err("该商店价格无效");
        final ShopEntry entry = found;
        try {
            return plugin.bridge().runOnMain(() -> doSell(entry, name, amt));
        } catch (Throwable t) {
            return failRecord("SELL", entry, name, amt, "交易执行失败: " + t.getMessage());
        }
    }

    private JsonObject doPurchase(ShopEntry e, String name, int amount) {
        Player buyer = Bukkit.getPlayerExact(name);
        boolean online = buyer != null && buyer.isOnline();
        if (!online && !plugin.config().allowOfflineBuy) {
            return failRecord("BUY", e, name, amount, "玩家 " + name + " 不在线（离线购买已被服务器关闭：config.yml purchase.allow-offline-buy）");
        }
        EconomyBridge eco = plugin.economy();
        if (!eco.available()) return failRecord("BUY", e, name, amount, "服务器未安装经济插件（需要 Vault 支持）");

        double unit = e.price;
        double total = round2(unit * amount);

        long id = parseLong(e.shop_id);
        Object shop = id > 0 ? plugin.bridge().getShopById(id) : null;
        Inventory chestInv = shop == null ? null : resolveInventory(shop);

        // 样品物品：容器实物（NBT 最完整）→ QuickShop 数据 → 基础材质；玩家头皮肤双源补齐
        ItemStack sample = resolveSample(e, shop, chestInv);
        if (sample == null) return failRecord("BUY", e, name, amount, "无法确定该商店的物品");

        long needItems = (long) amount * Math.max(1, e.stacking_amount);

        // 库存检查（仅容器可访问时；容器不可访问则虚拟发货，不依赖容器）
        if (!e.system_shop && chestInv != null) {
            int available = countItems(chestInv, sample);
            if (available < needItems) {
                long canBuy = available / Math.max(1, e.stacking_amount);
                return failRecord("BUY", e, name, amount, "商店库存不足：最多可购买 " + canBuy + " 份");
            }
        }

        // 付款人（在线用 Player；离线用缓冲的离线账户）
        OfflinePlayer payer = online ? buyer : resolvePlayer(name);
        if (payer == null) {
            return failRecord("BUY", e, name, amount, "找不到玩家 " + name + " 的账户（需至少登录过一次服务器）");
        }

        // 限购（QuickShop「Limited」扩展）：与游戏内共用每人每周期额度
        if (shop != null && plugin.limited().available()) {
            String deny = plugin.limited().checkTrade(shop, payer.getUniqueId(), amount);
            if (deny != null) return failRecord("BUY", e, name, amount, deny);
        }

        // 余额检查
        double balance = eco.balance(payer);
        if (balance < total) {
            return failRecord("BUY", e, name, amount, "余额不足：需要 " + total + "，当前 " + round2(balance));
        }

        // 扣款
        if (!eco.withdraw(payer, total)) {
            return failRecord("BUY", e, name, amount, "扣款失败（请检查经济插件）");
        }

        try {
            if (!e.system_shop) {
                if (chestInv != null) {
                    removeItems(chestInv, sample, needItems);
                } else {
                    plugin.getLogger().warning("[购买] 商店 #" + e.shop_id + " 容器不可访问：虚拟发货（未从箱子扣除）");
                }
            }
            if (online) {
                deliverItems(buyer, sample, needItems);
            } else {
                // 离线购买：物品暂存，玩家上线自动发放
                String b64 = ItemCodec.encode(sample);
                if (b64 == null) throw new IllegalStateException("物品序列化失败");
                plugin.store().addPending(name, b64, needItems);
            }
            if (!e.system_shop && e.owner_uuid != null) {
                try {
                    OfflinePlayer seller = Bukkit.getOfflinePlayer(UUID.fromString(e.owner_uuid));
                    eco.deposit(seller, total);
                } catch (Throwable ignored) {
                }
            }

            if (online) {
                buyer.sendMessage("§a[在线购买] §f成功购买 §e" + amount + "§f 份 §b" + e.shop_cn_name
                        + " §f花费 §e" + total);
            }
            plugin.shopData().invalidate();
            plugin.store().addFetchLog(0, 0, plugin.shopData().stats().total, "purchase",
                    name + " 网页购买 " + e.shop_cn_name + " x" + amount + " 花费 " + total);
            JsonObject trade = new JsonObject();
            trade.addProperty("order_id", "WEB-" + UUID.randomUUID());
            trade.addProperty("status", "SUCCESS");
            trade.addProperty("t", System.currentTimeMillis());
            trade.addProperty("type", "BUY");
            trade.addProperty("source", "web");
            trade.addProperty("shop_id", e.shop_id);
            trade.addProperty("item", e.shop_cn_name);
            trade.addProperty("material", e.material);
            trade.addProperty("amount", amount);
            trade.addProperty("items", needItems);
            trade.addProperty("unit_price", unit);
            trade.addProperty("total", total);
            trade.addProperty("player", name);
            trade.addProperty("owner", e.owner_name);
            trade.addProperty("online", online);
            plugin.store().addTrade(trade);
            recordSuccess("BUY", e, name, amount, needItems, unit, total, online);

            // 限购计数（+本次数量，与游戏内共用同一计数）
            if (shop != null && plugin.limited().available()) {
                plugin.limited().addUsed(shop, payer.getUniqueId(), amount);
            }

            JsonObject o = new JsonObject();
            o.addProperty("success", true);
            o.addProperty("item", e.shop_cn_name);
            o.addProperty("amount", amount);
            o.addProperty("unit_price", unit);
            o.addProperty("total_price", total);
            o.addProperty("balance_left", round2(eco.balance(payer)));
            o.addProperty("message", online ? "购买成功，物品已放入背包" : "购买成功！物品将在你上线时自动发放");
            return o;
        } catch (Throwable t) {
            try {
                eco.deposit(payer, total); // 回滚退款
            } catch (Throwable ignored) {
            }
            return failRecord("BUY", e, name, amount, "交易过程中出错，已自动退款（" + t.getMessage() + "）");
        }
    }

    /** 名字 → 玩家对象（在线优先；离线用 Paper 缓存接口，不阻塞主线程） */
    public static OfflinePlayer resolvePlayer(String name) {
        try {
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) return online;
            try {
                java.lang.reflect.Method m = Bukkit.getServer().getClass().getMethod("getOfflinePlayerIfCached", String.class);
                Object r = m.invoke(Bukkit.getServer(), name);
                if (r instanceof OfflinePlayer) return (OfflinePlayer) r;
            } catch (Throwable ignored) {
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ============================================================
    // 玩家卖出（收购）
    // ============================================================

    private JsonObject doSell(ShopEntry e, String name, int amount) {
        Player seller = Bukkit.getPlayerExact(name);
        if (seller == null || !seller.isOnline()) {
            return failRecord("SELL", e, name, amount, "玩家 " + name + " 不在线（在线收购需要玩家在游戏内）");
        }
        EconomyBridge eco = plugin.economy();
        if (!eco.available()) return failRecord("SELL", e, name, amount, "服务器未安装经济插件（需要 Vault 支持）");

        double unit = e.price;
        double total = round2(unit * amount);

        long id = parseLong(e.shop_id);
        Object shop = id > 0 ? plugin.bridge().getShopById(id) : null;
        Inventory chestInv = shop == null ? null : resolveInventory(shop);

        ItemStack sample = resolveSample(e, shop, chestInv);
        if (sample == null) return failRecord("SELL", e, name, amount, "无法确定该商店的物品");

        int stack = Math.max(1, e.stacking_amount);
        long needItems = (long) amount * stack;

        // 1) 玩家背包检查
        int available = countItems(seller.getInventory(), sample);
        if (available < needItems) {
            long canSell = available / stack;
            return failRecord("SELL", e, name, amount,
                    "背包里没有足够的「" + e.shop_cn_name + "」：当前可卖 " + canSell + " 份（需要 " + needItems + " 个，现有 " + available + " 个）");
        }

        // 2) 非系统商店：容器容量（仅容器可访问时检查）+ 店主余额
        OfflinePlayer owner = null;
        if (!e.system_shop) {
            if (chestInv != null && !canFit(chestInv, sample, needItems)) {
                return failRecord("SELL", e, name, amount, "商店容器已满，暂时无法收购更多");
            }
            if (e.owner_uuid != null) {
                try {
                    owner = Bukkit.getOfflinePlayer(UUID.fromString(e.owner_uuid));
                } catch (Throwable ignored) {
                }
            }
            if (owner != null && eco.balance(owner) < total) {
                return failRecord("SELL", e, name, amount, "店主余额不足，无法支付 " + total);
            }
        }

        // 3) 店主扣款（系统商店不扣）
        if (owner != null && !eco.withdraw(owner, total)) {
            return failRecord("SELL", e, name, amount, "店主扣款失败（请检查经济插件）");
        }

        try {
            // 4) 玩家物品 → 商店容器（容器不可访问时跳过，不依赖容器）
            removeItems(seller.getInventory(), sample, needItems);
            if (!e.system_shop) {
                if (chestInv != null) {
                    addItems(chestInv, sample, needItems);
                } else {
                    plugin.getLogger().warning("[收购] 商店 #" + e.shop_id + " 容器不可访问：收到的 " + needItems + " 个物品未入箱");
                }
            }
            // 5) 报酬到账
            if (!eco.deposit(seller, total)) {
                // 回滚
                if (!e.system_shop && chestInv != null) removeItems(chestInv, sample, needItems);
                deliverItems(seller, sample, needItems);
                if (owner != null) eco.deposit(owner, total);
                return failRecord("SELL", e, name, amount, "给你打款失败，交易已取消");
            }

            seller.sendMessage("§a[在线收购] §f成功出售 §e" + amount + "§f 份 §b" + e.shop_cn_name
                    + " §f获得 §e" + total);
            plugin.shopData().invalidate();
            plugin.store().addFetchLog(0, 0, plugin.shopData().stats().total, "sell",
                    name + " 网页出售 " + e.shop_cn_name + " x" + amount + " 获得 " + total);
            JsonObject trade = new JsonObject();
            trade.addProperty("order_id", "WEB-" + UUID.randomUUID());
            trade.addProperty("status", "SUCCESS");
            trade.addProperty("t", System.currentTimeMillis());
            trade.addProperty("type", "SELL");
            trade.addProperty("source", "web");
            trade.addProperty("shop_id", e.shop_id);
            trade.addProperty("item", e.shop_cn_name);
            trade.addProperty("material", e.material);
            trade.addProperty("amount", amount);
            trade.addProperty("items", needItems);
            trade.addProperty("unit_price", unit);
            trade.addProperty("total", total);
            trade.addProperty("player", name);
            trade.addProperty("owner", e.owner_name);
            trade.addProperty("online", true);
            plugin.store().addTrade(trade);
            recordSuccess("SELL", e, name, amount, needItems, unit, total, true);

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
            return failRecord("SELL", e, name, amount, "交易过程中出错（" + t.getMessage() + "）");
        }
    }

    // ============================================================
    // 工具
    // ============================================================

    /** QuickShop 商店的商品数据（含附魔/NBT）；不依赖容器 */
    private static ItemStack shopItemSample(Object shop) {
        if (shop == null) return null;
        try {
            Object item = QuickShopBridge.unwrap(QuickShopBridge.call(shop, "getItem"));
            if (item instanceof ItemStack) return ((ItemStack) item).clone();
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 样品物品：容器实物（NBT 最完整）→ QuickShop 商品数据（不依赖容器）→ 基础材质。
     * 玩家头缺皮肤时两个来源相互补齐；仍缺则记日志便于排查。
     */
    private ItemStack resolveSample(ShopEntry e, Object shop, Inventory chestInv) {
        ItemStack fromChest = findSample(chestInv);
        ItemStack fromShop = shopItemSample(shop);
        ItemStack sample = fromChest != null ? fromChest : fromShop;
        boolean fallback = false;
        if (sample == null) {
            sample = fallbackItem(e);
            fallback = true;
        }
        if (sample == null) return null;
        // 玩家头：缺皮肤时用有皮肤的另一个来源
        if (isHead(sample) && !hasSkullOwner(sample)) {
            ItemStack alt = (sample == fromChest) ? fromShop : fromChest;
            if (alt != null && isHead(alt) && hasSkullOwner(alt)) {
                sample = alt;
                fallback = false;
            }
        }
        // 玩家头完全无皮肤数据：拒绝交易，避免发出错误的“史蒂夫头”
        if (isHead(sample) && !hasSkullOwner(sample)) {
            if (fallback) {
                plugin.getLogger().warning("[样品] 商店 #" + e.shop_id + " 玩家头皮肤数据完全不可用，已拒绝交易（容器不可访问且商店数据读取失败）");
                return null;
            }
            plugin.getLogger().warning("[样品] 商店 #" + e.shop_id + " 的玩家头缺少皮肤数据（来源有物品但无皮肤）");
        }
        return sample;
    }

    private static boolean isHead(ItemStack item) {
        if (item == null) return false;
        String n = item.getType().name();
        return n.contains("HEAD") || n.contains("SKULL");
    }

    private static boolean hasSkullOwner(ItemStack item) {
        try {
            Object meta = item.getItemMeta();
            if (meta == null) return false;
            Object own = QuickShopBridge.call(meta, "getOwningPlayer");
            if (own != null) return true;
            Object own2 = QuickShopBridge.call(meta, "getOwner");
            if (own2 instanceof String) return !((String) own2).isEmpty();
            if (own2 != null) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private Inventory resolveInventory(Object shop) {
        try {
            // 优先使用 QuickShop 提供的库存接口；部分版本的商店方块位置并不是实际容器位置。
            for (String method : new String[]{"getInventory", "getContainerInventory", "getStorageInventory"}) {
                Object direct = QuickShopBridge.unwrap(QuickShopBridge.call(shop, method));
                if (direct instanceof Inventory) return (Inventory) direct;
                if (direct instanceof org.bukkit.inventory.InventoryHolder) {
                    Inventory inv = ((org.bukkit.inventory.InventoryHolder) direct).getInventory();
                    if (inv != null) return inv;
                }
            }
            Object loc = QuickShopBridge.unwrap(QuickShopBridge.call(shop, "getLocation", "bukkitLocation"));
            if (!(loc instanceof Location)) return null;
            Location l = (Location) loc;
            World w = l.getWorld();
            if (w == null) return null;
            // 商店可能在未加载的区块 → 同步加载后再取容器（本方法在主线程执行）
            try {
                int cx = l.getBlockX() >> 4;
                int cz = l.getBlockZ() >> 4;
                if (!w.isChunkLoaded(cx, cz)) {
                    w.getChunkAt(cx, cz); // 未加载时同步加载
                }
            } catch (Throwable ignored) {
            }
            BlockState st = w.getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ()).getState();
            // 箱子（含陷阱箱/双箱）→ 取真实库存（非快照）
            if (st instanceof org.bukkit.block.Chest) {
                try {
                    Inventory inv = ((org.bukkit.block.Chest) st).getBlockInventory();
                    if (inv != null) return inv;
                } catch (Throwable ignored) {
                }
            }
            if (st instanceof Container) {
                Inventory inv = ((Container) st).getInventory();
                if (inv != null) return inv;
            }
            if (st instanceof org.bukkit.inventory.InventoryHolder) {
                return ((org.bukkit.inventory.InventoryHolder) st).getInventory();
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

    // ============================================================
    // 订单记录（P0 订单流水）
    // ============================================================

    /** 记录成功订单 + 经营统计 + 通知；返回订单对象（供调用方复用） */
    private com.google.gson.JsonObject recordSuccess(String type, ShopEntry e, String name,
                                                     int amount, long items, double unit, double total, boolean online) {
        String key = "WEB-" + UUID.randomUUID();
        com.google.gson.JsonObject order = plugin.orders().recordOrder(key, type, "SUCCESS",
                e.shop_id, e.shop_cn_name, e.material, amount, items, unit, total, name, e.owner_name, online, null);
        // 经营统计：失败不计入
        plugin.stats().record(type, amount, total);
        // 通知（若配置了 Webhook）
        if (plugin.notifications().available()) {
            plugin.notifications().order(type, e.shop_cn_name, amount, total, name);
        }
        return order;
    }

    /** 记录失败订单并返回失败响应（幂等键为 null：每次失败都记一条，便于审计失败原因） */
    private JsonObject failRecord(String type, ShopEntry e, String name, int amount, String reason) {
        try {
            long items = (long) amount * Math.max(1, e.stacking_amount);
            plugin.orders().recordOrder(null, type, "FAILED",
                    e.shop_id, e.shop_cn_name, e.material, amount, items, e.price, round2(e.price * amount),
                    name, e.owner_name, false, reason);
        } catch (Throwable ignored) {
        }
        return err(reason);
    }

    /** 读取某商店容器内该物品的件数（null = 容器不可访问 / 系统商店）；用于网页「监控」页实时库存 */
    public Long stockOf(cn.aqcraft.data.ShopEntry e) {
        try {
            if (e == null || e.system_shop) return null;
            long id = parseLong(e.shop_id);
            Object shop = id > 0 ? plugin.bridge().getShopById(id) : null;
            org.bukkit.inventory.Inventory chestInv = shop == null ? null : resolveInventory(shop);
            if (chestInv == null) return null;
            ItemStack sample = resolveSample(e, shop, chestInv);
            if (sample == null) return null;
            return (long) countItems(chestInv, sample);
        } catch (Throwable t) {
            return null;
        }
    }
}
