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
    private static boolean initialized = false;

    private Materials() {
    }

    public static synchronized void init(Plugin plugin) {
        if (initialized) return;
        loadJson(plugin, "material_zh_cn.json", ZH);
        loadJson(plugin, "enchantment_zh_cn.json", ENCH);
        loadJson(plugin, "potion_zh_cn.json", POTION);
        plugin.getLogger().info("材质中文表已加载: " + ZH.size() + " 条, 附魔名: " + ENCH.size() + " 条, 药水名: " + POTION.size() + " 条");
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

    /** 附魔 id（sharpness / minecraft:sharpness）→ 中文名 */
    public static String enchantment(String id) {
        if (id == null || id.isEmpty()) return "未知附魔";
        String key = id.toLowerCase(Locale.ROOT);
        if (key.startsWith("minecraft:")) key = key.substring("minecraft:".length());
        String v = ENCH.get(key);
        if (v != null) return v;
        return readable(key.toUpperCase(Locale.ROOT));
    }

    /** 1-10 → 罗马数字；其他返回数字文本 */
    public static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (n >= 1 && n < r.length) return r[n];
        return String.valueOf(n);
    }

    /** 药水名：container = potion/splash/lingering/tipped, effectId 如 healing / strong_healing */
    public static String potionName(String container, String effectId) {
        if (effectId == null || effectId.isEmpty()) return "未知药水";
        String id = effectId.toLowerCase(Locale.ROOT);
        if (id.startsWith("minecraft:")) id = id.substring("minecraft:".length());
        String v = POTION.get(container + ":" + id);
        if (v != null) return v;
        // 退回普通药水名
        String base = POTION.get("potion:" + id);
        if (base != null) return base;
        return readable(id.toUpperCase(Locale.ROOT));
    }
}
