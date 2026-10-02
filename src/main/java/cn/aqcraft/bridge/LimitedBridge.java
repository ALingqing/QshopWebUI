package cn.aqcraft.bridge;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QuickShop-Hikari「Limited」限购扩展桥（全反射，软依赖）。
 *
 * <p>扩展把限购数据存在商店的 extra data 中，键命名空间与扩展一致（qssuite-limited）：
 * <ul>
 *   <li>{@code limit} —— 每个玩家每周期最多购买数量，小于 1 表示未限购</li>
 *   <li>{@code data.&lt;玩家UUID&gt;} —— 该玩家本周期已购买数量，扩展会在周期切换时自动清零</li>
 *   <li>{@code period} —— 清零周期（CalendarTriggerType 名称：HOUR / DAY / WEEK / MONTH / YEAR 等）</li>
 * </ul>
 * 网页购买会先检查剩余额度，成功后写入同样的计数，与游戏内购买共用。</p>
 */
public final class LimitedBridge {

    private final Plugin plugin;
    private volatile boolean available;
    private volatile Plugin addon;
    private NamespacedKey keyLimit;
    private NamespacedKey keyPeriod;
    private String status = "未安装";

    private static final Map<Class<?>, Method> GET_INT = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> SET_INT = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> GET_STR = new ConcurrentHashMap<>();

    public LimitedBridge(Plugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        available = false;
        addon = null;
        keyLimit = null;
        keyPeriod = null;
        status = "未安装";
        try {
            Plugin found = Bukkit.getPluginManager().getPlugin("qssuite-limited");
            if (found == null) found = Bukkit.getPluginManager().getPlugin("Limited");
            if (found == null || !found.isEnabled()) {
                status = "未安装限购扩展（qssuite-limited）";
                return;
            }
            addon = found;
            keyLimit = new NamespacedKey(found, "limit");
            keyPeriod = new NamespacedKey(found, "period");
            available = true;
            status = "已连接限购扩展 v" + found.getDescription().getVersion();
            plugin.getLogger().info("[Limited] " + status);
        } catch (Throwable t) {
            status = "初始化失败: " + t.getMessage();
            plugin.getLogger().warning("[Limited] " + status);
        }
    }

    public boolean available() {
        return available;
    }

    public String getStatus() {
        return status;
    }

    /** 商店每人限购数量；小于 1 表示未限购 */
    public int limitOf(Object shop) {
        if (!available || shop == null) return 0;
        return getExtraInt(shop, keyLimit, 0);
    }

    /** 玩家本周期已购买数量 */
    public int usedOf(Object shop, UUID playerId) {
        if (!available || shop == null || playerId == null) return 0;
        return getExtraInt(shop, dataKey(playerId), 0);
    }

    /** 清零周期（CalendarTriggerType 名称，如 DAY） */
    public String periodOf(Object shop) {
        if (!available || shop == null) return "";
        try {
            Method m = methodFor(shop, GET_STR, "getExtra", NamespacedKey.class, String.class);
            if (m == null) return "";
            Object r = m.invoke(shop, keyPeriod, "");
            return r == null ? "" : String.valueOf(r);
        } catch (Throwable t) {
            return "";
        }
    }

    /** 检查能否购买：返回 null = 可以；否则返回拒绝原因 */
    public String checkTrade(Object shop, UUID playerId, int amount) {
        if (!available || shop == null || playerId == null) return null;
        try {
            int limit = limitOf(shop);
            if (limit < 1) return null;
            int used = usedOf(shop, playerId);
            int remaining = limit - used;
            if (amount > remaining) {
                return "该商店限购：每" + periodLabel(periodOf(shop)) + "最多 " + limit
                        + " 份，你已购买 " + used + " 份（剩余 " + Math.max(0, remaining) + " 份）";
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 记录成功购买的数量（与游戏内共用同一计数） */
    public void addUsed(Object shop, UUID playerId, int amount) {
        if (!available || shop == null || playerId == null || amount <= 0) return;
        try {
            int limit = limitOf(shop);
            if (limit < 1) return;
            Method m = methodFor(shop, SET_INT, "setExtra", NamespacedKey.class, int.class);
            if (m == null) return;
            m.invoke(shop, dataKey(playerId), usedOf(shop, playerId) + amount);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Limited] 记录限购用量失败: " + t.getMessage());
        }
    }

    /** 周期名称 → 中文（每天 / 每周 / 每月 …） */
    public static String periodLabel(String period) {
        if (period == null || period.isEmpty()) return "周期";
        switch (period.toUpperCase(Locale.ROOT)) {
            case "SECOND": return "秒";
            case "MINUTE": return "分钟";
            case "HOUR": return "小时";
            case "DAY": return "天";
            case "WEEK": return "周";
            case "MONTH": return "月";
            case "YEAR": return "年";
            default: return "周期";
        }
    }

    // ============================================================
    // 反射工具
    // ============================================================

    private NamespacedKey dataKey(UUID playerId) {
        return new NamespacedKey(addon, "data." + playerId);
    }

    private int getExtraInt(Object shop, NamespacedKey key, int def) {
        try {
            Method m = methodFor(shop, GET_INT, "getExtra", NamespacedKey.class, int.class);
            if (m == null) return def;
            Object r = m.invoke(shop, key, def);
            return r instanceof Number ? ((Number) r).intValue() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private static Method methodFor(Object shop, Map<Class<?>, Method> cache, String name, Class<?>... types) {
        Class<?> cls = shop.getClass();
        Method cached = cache.get(cls);
        if (cached != null) return cached;
        try {
            Method m = cls.getMethod(name, types);
            m.setAccessible(true);
            cache.put(cls, m);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }
}
