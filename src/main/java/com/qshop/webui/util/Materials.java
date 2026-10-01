package com.qshop.webui.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Minecraft 材质中文名工具 */
public final class Materials {

    private static final Map<String, String> ZH = new HashMap<>();
    private static final Map<String, String> ENCH = new HashMap<>();
    private static final Map<String, String> POTION = new HashMap<>();
    /** 服务器数据包/资源包的翻译键（如 item.dnt.cave_chamber_key → 洞穴密室钥匙） */
    private static final Map<String, String> CUSTOM_LANG = new HashMap<>();
    private static boolean initialized = false;

    private Materials() {
    }

    public static synchronized void init(Plugin plugin) {
        if (initialized) return;
        loadJson(plugin, "material_zh_cn.json", ZH);
        loadJson(plugin, "enchantment_zh_cn.json", ENCH);
        loadJson(plugin, "potion_zh_cn.json", POTION);
        loadJson(plugin, "custom_lang_zh_cn.json", CUSTOM_LANG);
        plugin.getLogger().info("材质中文表已加载: " + ZH.size() + " 条, 附魔名: " + ENCH.size() + " 条, 药水名: " + POTION.size() + " 条, 数据包翻译: " + CUSTOM_LANG.size() + " 条");
        initialized = true;
    }

    private static void loadJson(Plugin plugin, String resource, Map<String, String> target) {
        try (InputStream is = plugin.getResource(resource)) {
            if (is == null) {
                plugin.getLogger().warning(resource + " 未找到");
                return;
            }
            JsonObject o = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> e : o.entrySet()) {
                target.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Exception e) {
            plugin.getLogger().warning(resource + " 加载失败: " + e);
        }
    }

    /** 数据包翻译键（如 item.dnt.cave_chamber_key / advancement.dnt.xxx）→ 中文名；无映射返回 null */
    public static String translateKey(String key) {
        if (key == null || key.isEmpty()) return null;
        return CUSTOM_LANG.get(key.toLowerCase(Locale.ROOT));
    }

    /** material（DIAMOND_SWORD）→ 中文名；无映射时转可读形式（Diamond Sword） */
    public static String cn(String material) {
        if (material == null || material.isEmpty()) return "未知物品";
        String key = material.toUpperCase(Locale.ROOT);
        String v = ZH.get(key);
        if (v != null) return v;
        return readable(key);
    }

    private static String readable(String key) {
        String[] parts = key.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.length() == 0 ? key : sb.toString();
    }

    /** material → 物品图片文件名（不含扩展名），如 diamond_sword */
    public static String imageName(String material) {
        if (material == null || material.isEmpty()) return null;
        String m = material.toLowerCase(Locale.ROOT).replace(' ', '_');
        StringBuilder sb = new StringBuilder(m.length());
        for (int i = 0; i < m.length(); i++) {
            char c = m.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 附魔 id（sharpness / minecraft:sharpness / dnt:aerials_bane）→ 中文名 */
    public static String enchantment(String id) {
        if (id == null || id.isEmpty()) return "未知附魔";
        String key = id.toLowerCase(Locale.ROOT);
        if (key.startsWith("minecraft:")) key = key.substring("minecraft:".length());
        String v = ENCH.get(key);
        if (v == null && key.contains(":")) v = ENCH.get(key.substring(key.indexOf(':') + 1));
        if (v != null) return v;
        String bare = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        return readable(bare.toUpperCase(Locale.ROOT));
    }

    /** 1-10 → 罗马数字；其他返回数字文本 */
    public static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (n >= 1 && n < r.length) return r[n];
        return String.valueOf(n);
    }

    /** 药水名：container = potion/splash/lingering/tipped, effectId 如 healing / strong_healing / dnt_levitation */
    public static String potionName(String container, String effectId) {
        if (effectId == null || effectId.isEmpty()) return "未知药水";
        String id = effectId.toLowerCase(Locale.ROOT);
        if (id.startsWith("minecraft:")) id = id.substring("minecraft:".length());
        String v = POTION.get(container + ":" + id);
        if (v == null && id.contains(":")) v = POTION.get(container + ":" + id.substring(id.indexOf(':') + 1));
        if (v != null) return v;
        // 长效/强化变体：新版游戏已删除 long_/strong_ 独立翻译键 → 基础名 + 中文修饰词组合
        String modZh = null;
        String baseId = id;
        if (id.startsWith("long_")) {
            modZh = "长效";
            baseId = id.substring("long_".length());
        } else if (id.startsWith("strong_")) {
            modZh = "强化";
            baseId = id.substring("strong_".length());
        }
        if (modZh != null) {
            String bn = POTION.get(container + ":" + baseId);
            if (bn == null) bn = POTION.get("potion:" + baseId);
            if (bn != null) {
                // 药水/药箭：修饰词在最前（长效水肺药水 / 长效水肺之箭）
                if (container.equals("potion") || container.equals("tipped")) return modZh + bn;
                // 喷溅/滞留：插在容器词后（喷溅型长效水肺药水）
                for (String q : new String[]{"喷溅型", "滞留型"}) {
                    if (bn.startsWith(q)) return q + modZh + bn.substring(q.length());
                }
                return modZh + bn;
            }
        }
        // 退回普通药水名
        String base = POTION.get("potion:" + id);
        if (base != null) return base;
        String bare = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return readable(bare.toUpperCase(Locale.ROOT));
    }
}
