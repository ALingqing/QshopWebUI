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
    private static boolean initialized = false;

    private Materials() {
    }

    public static synchronized void init(Plugin plugin) {
        if (initialized) return;
        try (InputStream is = plugin.getResource("material_zh_cn.json")) {
            if (is != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, com.google.gson.JsonElement> e : o.entrySet()) {
                    ZH.put(e.getKey().toUpperCase(Locale.ROOT), e.getValue().getAsString());
                }
                plugin.getLogger().info("材质中文表已加载: " + ZH.size() + " 条");
            } else {
                plugin.getLogger().warning("material_zh_cn.json 未找到（将使用英文名）");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("材质中文表加载失败: " + e);
        }
        initialized = true;
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
}
