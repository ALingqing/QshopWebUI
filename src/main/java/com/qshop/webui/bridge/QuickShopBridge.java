package com.qshop.webui.bridge;

import com.qshop.webui.QShopWebUIPlugin;
import com.qshop.webui.data.ShopEntry;
import com.qshop.webui.util.JsonUtil;
import com.qshop.webui.util.Materials;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * QuickShop-Hikari 反射桥。
 * <p>全部通过反射调用 QuickShop API，避免对 QuickShop 具体版本的编译绑定，
 * 同时兼容 5.x / 6.x 的方法命名差异（多候选方法名依次尝试）。</p>
 */
public final class QuickShopBridge {

    private final QShopWebUIPlugin plugin;

    private volatile boolean available = false;
    private volatile String status = "未检测";
    private volatile String qsVersion = "";

    private Object shopManager;
    private Method mGetAllShops;
    private Method mGetShopById;
    private Method mDeleteShop;

    private static final Map<Class<?>, Map<String, List<Method>>> METHOD_INDEX = new ConcurrentHashMap<>();

    public QuickShopBridge(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
    }

    // ============================================================
    // 初始化 / 状态
    // ============================================================

    public void reload() {
        available = false;
        shopManager = null;
        mGetAllShops = null;
        mGetShopById = null;
        mDeleteShop = null;
        try {
            Plugin qs = Bukkit.getPluginManager().getPlugin("QuickShop");
            if (qs == null) qs = Bukkit.getPluginManager().getPlugin("QuickShop-Hikari");
            if (qs == null) {
                status = "未安装 QuickShop-Hikari";
                return;
            }
            if (!qs.isEnabled()) {
                status = "QuickShop-Hikari 未启用";
                return;
            }
            qsVersion = String.valueOf(qs.getDescription().getVersion());

            ClassLoader cl = qs.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("com.ghostchu.quickshop.api.QuickShopAPI", true, cl);
            Object api = apiClass.getMethod("getInstance").invoke(null);
            if (api == null) {
                status = "QuickShopAPI.getInstance() 返回空";
                return;
            }
            Object mgr = apiClass.getMethod("getShopManager").invoke(api);
            if (mgr == null) {
                status = "QuickShop getShopManager() 返回空";
                return;
            }
            this.shopManager = mgr;

            mGetAllShops = findMethod(mgr.getClass(), "getAllShops", 0);
            if (mGetAllShops == null) {
                status = "QuickShop API 缺少 getAllShops()";
                return;
            }
            mGetShopById = findMethod(mgr.getClass(), "getShop", 1);
            mDeleteShop = findMethod(mgr.getClass(), "deleteShop", 1);

            available = true;
            status = "已连接 QuickShop " + qsVersion;
            plugin.getLogger().info("[QuickShop] " + status);
        } catch (Throwable t) {
            status = "初始化失败: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            plugin.getLogger().warning("[QuickShop] " + status);
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String getStatus() {
        return status;
    }

    public String getQuickShopVersion() {
        return qsVersion;
    }

    // ============================================================
    // 数据读取（必须在主线程调用；对外统一走 getAllShopsOnMainThread）
    // ============================================================

    @SuppressWarnings("unchecked")
    public List<Object> getAllShopsRaw() throws Exception {
        if (!available || mGetAllShops == null) return Collections.emptyList();
        Object r = mGetAllShops.invoke(shopManager);
        if (r instanceof Collection) {
            return new ArrayList<>((Collection<Object>) r);
        }
        return Collections.emptyList();
    }

    /** 线程安全：调度到主线程取商店列表（防止并发遍历 QuickShop 内部集合） */
    public List<Object> getAllShopsOnMainThread() throws Exception {
        if (!available) return Collections.emptyList();
        if (Bukkit.isPrimaryThread()) {
            return getAllShopsRaw();
        }
        Future<List<Object>> f = Bukkit.getScheduler().callSyncMethod(
                plugin, (Callable<List<Object>>) this::getAllShopsRaw);
        try {
            return f.get(20, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new Exception("获取 QuickShop 数据超时（服务器是否卡顿？）");
        }
    }

    public Object getShopById(long id) {
        if (!available) return null;
        // 1) 按方法名 + 参数类型精确调用（避免匹配到 getShop(UUID) 等其他重载）
        try {
            for (Method m : methods(shopManager, "getShop", "getShopById")) {
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length != 1) continue;
                try {
                    Object r;
                    if (pt[0] == long.class || pt[0] == Long.class) r = m.invoke(shopManager, id);
                    else if (pt[0] == int.class || pt[0] == Integer.class) r = m.invoke(shopManager, (int) id);
                    else continue;
                    if (r != null) return r;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        // 2) 回退：遍历所有商店按 getShopId() 匹配（与数据快照的 shop_id 来源一致）
        try {
            for (Object shop : getAllShopsRaw()) {
                try {
                    Object sid = call(shop, "getShopId");
                    if (sid instanceof Number && ((Number) sid).longValue() == id) return shop;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 把任意任务调度到主线程执行（操作 QuickShop 对象必须如此） */
    public <T> T runOnMain(Callable<T> task) throws Exception {
        if (Bukkit.isPrimaryThread()) return task.call();
        Future<T> f = Bukkit.getScheduler().callSyncMethod(plugin, task);
        return f.get(20, TimeUnit.SECONDS);
    }

    /**
     * 在主线程对指定商店执行操作。
     *
     * @param action 返回 null 表示成功；否则为错误信息
     * @return null = 成功；否则为错误信息
     */
    public String mutateShop(long id, java.util.function.Function<Object, String> action) {
        if (!available) return "QuickShop 未连接";
        try {
            return runOnMain(() -> {
                Object shop = getShopById(id);
                if (shop == null) return "商店不存在: " + id;
                return action.apply(shop);
            });
        } catch (Throwable t) {
            return "操作失败: " + rootMessage(t);
        }
    }

    /** 读取某商店当前类型（SELLING / BUYING） */
    public String resolveShopType(Object shop) {
        return resolveType(shop);
    }

    // ============================================================
    // 写操作（尽力而为，兼容多版本）
    // ============================================================

    /** @return null=成功；否则为错误信息 */
    public String deleteShop(Object shop) {
        if (!available || mDeleteShop == null) return "当前 QuickShop 版本不支持删除操作";
        try {
            runOnMain(() -> {
                mDeleteShop.invoke(shopManager, shop);
                return null;
            });
            return null;
        } catch (Throwable t) {
            return "删除失败: " + rootMessage(t);
        }
    }

    /** @return null=成功；否则为错误信息 */
    public String setShopPrice(Object shop, double price) {
        return invokeSingleArg(shop, price, "setPrice", "setShopPrice");
    }

    /** @return null=成功；否则为错误信息 */
    public String setShopType(Object shop, String type) {
        if (!available) return "QuickShop 未连接";
        try {
            // 1) 从 shopManager 找 IShopType 对象
            Object typeObj = null;
            for (String cand : new String[]{type, type.toLowerCase(Locale.ROOT)}) {
                Object o = call(shopManager, "shopType", "shopTypeOrDefault");
                if (o == null) {
                    // 带参数调用：shopManager.shopType(String)
                    for (Method m : methods(shopManager, "shopType")) {
                        if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                            try {
                                o = unwrap(m.invoke(shopManager, cand));
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
                if (o != null) {
                    typeObj = o;
                    break;
                }
            }
            final Object finalType = typeObj;
            if (finalType == null) {
                // 5.x 可能支持字符串直接设置
                String r = invokeSingleArg(shop, type, "setShopType");
                return r == null ? null : "当前 QuickShop 版本不支持修改商店类型";
            }
            runOnMain(() -> {
                for (Method m : methods(shop, "shopType", "setShopType")) {
                    if (m.getParameterCount() == 1) {
                        try {
                            m.invoke(shop, finalType);
                            return null;
                        } catch (Throwable ignored) {
                        }
                    }
                }
                return null;
            });
            return null;
        } catch (Throwable t) {
            return "修改类型失败: " + rootMessage(t);
        }
    }

    private String invokeSingleArg(Object target, Object arg, String... methodNames) {
        if (target == null) return "目标不存在";
        for (String name : methodNames) {
            for (Method m : methods(target, name)) {
                if (m.getParameterCount() != 1) continue;
                Class<?> pt = m.getParameterTypes()[0];
                try {
                    Object converted = convertArg(arg, pt);
                    if (converted == null && !arg.getClass().isInstance(converted)) continue;
                    final Method fm = m;
                    final Object fa = converted;
                    runOnMain(() -> {
                        fm.invoke(target, fa);
                        return null;
                    });
                    return null;
                } catch (Throwable ignored) {
                }
            }
        }
        return "当前 QuickShop 版本不支持该操作（" + methodNames[0] + "）";
    }

    private static Object convertArg(Object arg, Class<?> targetType) {
        if (targetType.isInstance(arg)) return arg;
        if (arg instanceof Double) {
            double d = (Double) arg;
            if (targetType == double.class || targetType == Double.class) return d;
            if (targetType == float.class || targetType == Float.class) return (float) d;
        }
        if (targetType == String.class) return String.valueOf(arg);
        return arg;
    }

    // ============================================================
    // 反射工具
    // ============================================================

    public static Method findMethod(Class<?> c, String name, int argCount) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == argCount) return m;
        }
        return null;
    }

    /** 获取目标对象上所有指定名字的 public 方法（支持多个候选名） */
    public static List<Method> methods(Object target, String... names) {
        if (target == null || names == null || names.length == 0) return Collections.emptyList();
        List<Method> out = new ArrayList<>(4);
        for (String name : names) {
            Map<String, List<Method>> idx = METHOD_INDEX.computeIfAbsent(target.getClass(), c -> {
                Map<String, List<Method>> m = new HashMap<>();
                for (Method mm : c.getMethods()) {
                    m.computeIfAbsent(mm.getName(), k -> new ArrayList<>(2)).add(mm);
                }
                return m;
            });
            List<Method> r = idx.get(name);
            if (r != null) out.addAll(r);
        }
        return out;
    }

    /** 依次尝试多个候选方法名（均要求无参），返回第一个成功的结果 */
    public static Object call(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            for (Method m : methods(target, name)) {
                if (m.getParameterCount() != 0) continue;
                try {
                    return m.invoke(target);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    public static Object unwrap(Object o) {
        if (o instanceof Optional) return ((Optional<?>) o).orElse(null);
        if (o instanceof OptionalInt) return ((OptionalInt) o).isPresent() ? ((OptionalInt) o).getAsInt() : null;
        if (o instanceof OptionalLong) return ((OptionalLong) o).isPresent() ? ((OptionalLong) o).getAsLong() : null;
        if (o instanceof OptionalDouble) return ((OptionalDouble) o).isPresent() ? ((OptionalDouble) o).getAsDouble() : null;
        return o;
    }

    public static String asString(Object o) {
        o = unwrap(o);
        if (o == null) return null;
        if (o instanceof String) return (String) o;
        if (o instanceof Enum) return ((Enum<?>) o).name();
        return String.valueOf(o);
    }

    public static long asLong(Object o, long def) {
        o = unwrap(o);
        if (o instanceof Number) return ((Number) o).longValue();
        if (o instanceof String) {
            try {
                return Long.parseLong(((String) o).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public static int asInt(Object o, int def) {
        o = unwrap(o);
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) {
            try {
                return (int) Double.parseDouble(((String) o).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public static double asDouble(Object o, double def) {
        o = unwrap(o);
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof String) {
            try {
                return Double.parseDouble(((String) o).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getMessage() == null ? cur.toString() : cur.getMessage();
    }

    // ============================================================
    // Shop → ShopEntry
    // ============================================================

    public static ShopEntry toEntry(Object shop, long now) {
        ShopEntry e = new ShopEntry();

        Object id = call(shop, "getShopId");
        e.shop_id = id == null ? "unknown" : String.valueOf(asLong(id, 0));

        // 物品
        Object item = call(shop, "getItem", "getItemStack");
        String material = "UNKNOWN";
        if (item != null) {
            Object type = call(item, "getType");
            String t = asString(type);
            if (t != null && !t.isEmpty()) material = t;
        }
        String baseMaterial = material.toUpperCase(Locale.ROOT);

        // 物品自定义显示名（命名物品 / 玩家头名字等）
        String itemDisplayName = readItemDisplayName(item, baseMaterial);

        // 附魔（附魔书 stored 附魔 / 普通装备附魔）
        List<ShopEntry.Enchant> enchants = readEnchants(item);
        String suffix = enchantSuffix(enchants);

        // 药水类（药水 / 喷溅 / 滞留 / 药箭）：[container, effectId, 中文名]
        String[] potion = readPotion(item, baseMaterial);

        // 玩家头头像（API）
        String skullAvatar = isSkull(baseMaterial) ? readSkullAvatar(item) : null;

        // 合成分组键：不同附魔 / 不同药水 在列表中独立分类
        if (potion != null) {
            e.material = baseMaterial + "|" + potion[1];
        } else if (!enchants.isEmpty()) {
            // 例：ENCHANTED_BOOK|protection4+unbreaking3
            StringBuilder sig = new StringBuilder();
            for (ShopEntry.Enchant en : enchants) {
                if (sig.length() > 0) sig.append('+');
                sig.append(en.id).append(en.level);
            }
            e.material = baseMaterial + "|" + sig;
            e.enchants = enchants;
        } else {
            e.material = baseMaterial;
        }

        // 名称：商店自定义名 > 物品显示名 > 药水名 > 材质中文名；再加附魔后缀
        String custom = asString(call(shop, "getName", "getShopName"));
        if (custom != null) {
            custom = custom.trim();
            if (custom.isEmpty() || custom.contains("Component@") || custom.startsWith("Component{")) custom = null;
        }
        String displayName;
        if (custom != null) {
            displayName = custom;
        } else if (itemDisplayName != null) {
            displayName = itemDisplayName;
        } else if (potion != null) {
            displayName = potion[2];
        } else {
            displayName = Materials.cn(baseMaterial);
        }
        if (potion == null && !suffix.isEmpty()) {
            boolean has = false;
            for (ShopEntry.Enchant en : enchants) {
                if (displayName.contains(en.name)) {
                    has = true;
                    break;
                }
            }
            if (!has) displayName = displayName + " · " + suffix;
        }
        if (displayName.length() > 120) displayName = displayName.substring(0, 120);
        e.item_name = displayName;
        e.shop_cn_name = displayName;
        e.item_image = skullAvatar != null
                ? skullAvatar
                : "item/" + Materials.imageName(baseMaterial) + ".png";

        // 店主（系统商店 owner 可能为空/控制台）
        Object owner = call(shop, "getOwner");
        if (owner != null) {
            e.owner_name = asString(call(owner, "getUsername"));
            if (e.owner_name == null || e.owner_name.trim().isEmpty()) {
                e.owner_name = asString(call(owner, "getName"));
            }
            Object uuid = unwrap(call(owner, "getUniqueId"));
            if (uuid != null) e.owner_uuid = String.valueOf(uuid);
            Object real = unwrap(call(owner, "isRealPlayer", "isPlayer"));
            if (real instanceof Boolean) e.system_shop = !((Boolean) real);
            Object console = unwrap(call(owner, "isConsole"));
            if (console instanceof Boolean && (Boolean) console) e.system_shop = true;
            if (!e.system_shop && e.owner_uuid != null) {
                String u = e.owner_uuid.toUpperCase(Locale.ROOT);
                if (u.equals("00000000-0000-0000-0000-000000000000") || u.contains("CONSOLE")) {
                    e.system_shop = true;
                }
            }
        }
        if (!e.system_shop) {
            Object unlim = unwrap(call(shop, "isUnlimited"));
            if (unlim instanceof Boolean && (Boolean) unlim) e.system_shop = true;
        }
        // 系统商店的 owner 名是 QuickShop 的 i18n 占位（中文里是「无限」），不作为店主显示
        if (e.system_shop && e.owner_name != null) {
            String on = e.owner_name.trim().toLowerCase(Locale.ROOT);
            if (on.equals("无限") || on.equals("unlimited") || on.equals("console")) {
                e.owner_name = null;
            }
        }
        if (e.owner_name == null || e.owner_name.trim().isEmpty()) {
            e.owner_name = e.system_shop ? "系统商店" : "unknown";
        }

        // 位置
        Object loc = unwrap(call(shop, "getLocation", "bukkitLocation"));
        if (loc != null) {
            Object w = call(loc, "getWorld");
            if (w != null) e.world = asString(call(w, "getName"));
            e.x = asInt(call(loc, "getBlockX"), 0);
            e.y = asInt(call(loc, "getBlockY"), 0);
            e.z = asInt(call(loc, "getBlockZ"), 0);
        }
        if (e.world == null) e.world = "world";

        // 价格 / 堆叠 / 类型
        e.price = asDouble(call(shop, "getPrice", "shopPrice"), 0);
        e.price_raw = e.price;
        e.price_display = priceDisplay(e.price);
        e.stacking_amount = Math.max(1, asInt(call(shop, "getStackAmount", "getShopStackAmount", "stackAmount"), 1));
        e.shop_type = resolveType(shop);
        e.price_reasonable = true;

        // 库存：系统商店无限；玩家商店暂未知
        e.quantity = e.system_shop ? -1 : null;
        e.is_system_shop = e.system_shop ? Boolean.TRUE : null;

        // 时间
        long created = asLong(call(shop, "getCreationTime", "getCreateTime"), 0);
        if (created > 0 && created < 1_000_000_000_000L) created *= 1000; // 秒 → 毫秒
        if (created <= 0) created = now;
        e.created_at_ms = created;
        e.fetched_at = JsonUtil.iso(created);
        e.updated_at = JsonUtil.iso(now);

        return e;
    }

    private static String resolveType(Object shop) {
        Object b = unwrap(call(shop, "isBuying"));
        if (b instanceof Boolean) return ((Boolean) b) ? "BUYING" : "SELLING";
        Object st = call(shop, "shopType", "getShopType");
        if (st != null) {
            Object b2 = unwrap(call(st, "isBuying"));
            if (b2 instanceof Boolean) return ((Boolean) b2) ? "BUYING" : "SELLING";
            String id = asString(call(st, "identifier", "name"));
            if (id != null) {
                String up = id.toUpperCase(Locale.ROOT);
                if (up.contains("BUYING")) return "BUYING";
                if (up.contains("SELLING")) return "SELLING";
            }
        }
        return "SELLING";
    }

    /** 与前端一致的显示格式：0 → "0"；极小/极大 → 科学计数；否则千分位两位小数 */
    public static String priceDisplay(double v) {
        if (!Double.isFinite(v) || v < 0) v = 0;
        if (v == 0) return "0";
        if (v < 0.01 || v >= 1_000_000) {
            String s = String.format(Locale.ROOT, "%.2e", v);
            s = s.replace("e-0", "e-").replace("e+0", "e+");
            return s;
        }
        return String.format(Locale.US, "%,.2f", v);
    }

    /** 读取物品附魔（优先附魔书的 stored 附魔；兼容多版本方法名） */
    private static List<ShopEntry.Enchant> readEnchants(Object item) {
        List<ShopEntry.Enchant> list = new ArrayList<>(4);
        if (item == null) return list;
        Object meta;
        try {
            meta = call(item, "getItemMeta");
        } catch (Throwable t) {
            return list;
        }
        if (meta == null) return list;
        Map<?, ?> map = null;
        for (String cand : new String[]{"getStoredEnchants", "getEnchants", "getEnchantments"}) {
            try {
                Object m = call(meta, cand);
                if (m instanceof Map && !((Map<?, ?>) m).isEmpty()) {
                    map = (Map<?, ?>) m;
                    break;
                }
            } catch (Throwable ignored) {
            }
        }
        if (map == null) return list;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object ench = entry.getKey();
            if (ench == null || !(entry.getValue() instanceof Number)) continue;
            int lvl = Math.max(1, ((Number) entry.getValue()).intValue());
            String id = null;
            Object nsKey = call(ench, "getKey");
            if (nsKey instanceof String) {
                id = (String) nsKey;
            } else if (nsKey != null) {
                id = asString(call(nsKey, "getKey"));
            }
            if (id == null) id = asString(ench);
            if (id == null) continue;
            id = id.toLowerCase(Locale.ROOT);
            if (id.startsWith("minecraft:")) id = id.substring("minecraft:".length());
            if (id.isEmpty() || id.contains(" ") || id.contains("@") || id.length() > 48) continue;
            ShopEntry.Enchant en = new ShopEntry.Enchant();
            en.id = id;
            en.name = Materials.enchantment(id);
            en.level = lvl;
            en.text = en.name + " " + Materials.roman(lvl);
            list.add(en);
        }
        list.sort((a, b) -> a.id.compareTo(b.id));
        return list;
    }

    /** 附魔展示后缀：“锋利 V、保护 IV”（最多展示 3 个） */
    private static String enchantSuffix(List<ShopEntry.Enchant> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i >= 3) {
                sb.append(" 等");
                break;
            }
            if (i > 0) sb.append("、");
            sb.append(list.get(i).text);
        }
        return sb.toString();
    }

    /** 容器类型 → 中文名表前缀；非药水返回 null */
    private static String potionContainer(String material) {
        switch (material) {
            case "POTION": return "potion";
            case "SPLASH_POTION": return "splash";
            case "LINGERING_POTION": return "lingering";
            case "TIPPED_ARROW": return "tipped";
            default: return null;
        }
    }

    /**
     * 读取药水信息。
     *
     * @return [container, effectId, 中文名]；非药水或读取失败返回 null
     */
    private static String[] readPotion(Object item, String baseMaterial) {
        String container = potionContainer(baseMaterial);
        if (container == null || item == null) return null;
        Object meta;
        try {
            meta = call(item, "getItemMeta");
        } catch (Throwable t) {
            return null;
        }
        if (meta == null) return null;

        String effectId = null;

        // 1) 新 API（1.20.5+）：getBasePotionType() → PotionType（带 key，如 healing/strong_healing）
        try {
            Object pt = call(meta, "getBasePotionType");
            if (pt != null) {
                Object ns = call(pt, "getKey");
                if (ns != null) effectId = asString(call(ns, "getKey"));
                if (effectId == null) effectId = asString(pt);
                if (effectId != null) effectId = effectId.toLowerCase(Locale.ROOT);
            }
        } catch (Throwable ignored) {
        }

        // 2) 旧 API：getBasePotionData() → PotionData（枚举 + upgraded/extended）
        if (effectId == null) {
            try {
                Object pd = call(meta, "getBasePotionData");
                if (pd != null) {
                    String t = asString(call(pd, "getType"));
                    if (t != null && !t.isEmpty()) {
                        t = t.toLowerCase(Locale.ROOT);
                        if ("uncraftable".equals(t)) {
                            effectId = "empty";
                        } else {
                            boolean up = Boolean.TRUE.equals(unwrap(call(pd, "isUpgraded")));
                            boolean ext = Boolean.TRUE.equals(unwrap(call(pd, "isExtended")));
                            if (up) effectId = "strong_" + t;
                            else if (ext) effectId = "long_" + t;
                            else effectId = t;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        if (effectId != null && !effectId.isEmpty()) {
            if (effectId.startsWith("minecraft:")) effectId = effectId.substring("minecraft:".length());
            return new String[]{container, effectId, Materials.potionName(container, effectId)};
        }

        // 3) 自定义效果列表 getCustomEffects() → List<PotionEffect>
        try {
            Object custom = call(meta, "getCustomEffects");
            if (custom instanceof Iterable) {
                StringBuilder sig = new StringBuilder();
                StringBuilder name = new StringBuilder();
                int count = 0;
                for (Object eff : (Iterable<?>) custom) {
                    Object type = call(eff, "getType");
                    if (type == null) continue;
                    Object ns = call(type, "getKey");
                    String eid = ns != null ? asString(call(ns, "getKey")) : asString(type);
                    if (eid == null) continue;
                    eid = eid.toLowerCase(Locale.ROOT);
                    if (eid.startsWith("minecraft:")) eid = eid.substring("minecraft:".length());
                    int amp = asInt(call(eff, "getAmplifier"), 0) + 1;
                    if (count > 0) {
                        sig.append('+');
                        name.append('、');
                    }
                    sig.append(eid).append(amp);
                    name.append(Materials.potionName(container, eid)).append(' ').append(Materials.roman(amp));
                    count++;
                    if (count >= 3) break;
                }
                if (count > 0) {
                    return new String[]{container, sig.toString(), name.toString()};
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean isSkull(String material) {
        return "PLAYER_HEAD".equals(material) || "SKULL".equals(material) || "SKULL_ITEM".equals(material);
    }

    /** 玩家头 → 头像 API URL（按头颅主人名字 / UUID） */
    private static String readSkullAvatar(Object item) {
        if (item == null) return null;
        try {
            Object meta = call(item, "getItemMeta");
            if (meta == null) return null;
            String name = null;
            String uuid = null;
            Object owner = call(meta, "getOwningPlayer");
            if (owner != null) {
                name = asString(call(owner, "getName"));
                Object u = unwrap(call(owner, "getUniqueId"));
                if (u != null) uuid = String.valueOf(u);
            }
            if (name == null || name.isEmpty()) {
                Object own = call(meta, "getOwner");
                if (own instanceof String && !((String) own).isEmpty()) {
                    name = (String) own;
                } else if (own != null) {
                    String n2 = asString(call(own, "getName"));
                    if (n2 != null && !n2.isEmpty()) name = n2;
                    Object u2 = unwrap(call(own, "getUniqueId"));
                    if (u2 != null && uuid == null) uuid = String.valueOf(u2);
                }
            }
            String key = (name != null && !name.isEmpty()) ? name : uuid;
            if (key == null || key.isEmpty()) return null;
            key = key.trim();
            if (key.length() > 64 || key.equalsIgnoreCase("unknown")) return null;
            return "https://mc-heads.net/avatar/" + java.net.URLEncoder.encode(key, "UTF-8") + "/64";
        } catch (Throwable t) {
            return null;
        }
    }

    /** 物品自定义显示名（清理颜色代码）；与默认名相同或空时返回 null */
    private static String readItemDisplayName(Object item, String baseMaterial) {
        if (item == null) return null;
        try {
            Object meta = call(item, "getItemMeta");
            if (meta == null) return null;
            String dn = asString(call(meta, "getDisplayName"));
            // Paper 1.20.5+：物品名也可能在 item_name 组件（getDisplayName 读不到）
            if (dn == null || dn.trim().isEmpty()) {
                dn = readItemNameComponent(meta);
            }
            if (dn == null) return null;
            dn = dn.replaceAll("§.", "").trim();
            if (dn.isEmpty() || dn.contains("Component@") || dn.contains("Component{")) return null;
            // 物品名是数据包翻译键（如 item.dnt.cave_chamber_key）→ 查内置数据包翻译表
            if (!hasChinese(dn) && dn.indexOf('.') > 0) {
                String translated = Materials.translateKey(dn);
                if (translated != null) dn = translated.replaceAll("§.", "").trim();
            }
            if (dn.isEmpty() || dn.contains("Component")) return null;
            String readable = baseMaterial.replace('_', ' ');
            if (dn.equalsIgnoreCase(readable)) return null;
            if (dn.equalsIgnoreCase(Materials.cn(baseMaterial))) return null;
            if (dn.length() > 64) dn = dn.substring(0, 64);
            return dn;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 读取 Paper 1.20.5+ 的 item_name 组件（如数据包自定义物品名）。
     * 翻译组件取 key 查表；文本组件取 text；均失败返回 null。
     */
    private static String readItemNameComponent(Object meta) {
        try {
            Object comp = call(meta, "itemName");
            if (comp == null) return null;
            try {
                Object key = call(comp, "key");
                if (key instanceof String) {
                    String t = Materials.translateKey((String) key);
                    return t != null ? t : null;
                }
            } catch (Throwable ignored) {
            }
            try {
                Object txt = call(comp, "text");
                if (txt instanceof String && !((String) txt).isEmpty()) return (String) txt;
            } catch (Throwable ignored) {
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean hasChinese(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FA5) return true;
        }
        return false;
    }
}
