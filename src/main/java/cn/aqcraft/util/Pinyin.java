package cn.aqcraft.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 拼音搜索：中文名 → 全拼 / 首字母（数据由 tools/generate-pinyin.mjs 生成于 pinyin_zh_cn.json）。
 * 匹配规则：原文包含（中文输入）→ 全拼包含（zhizhu）→ 首字母开头（zz）。
 */
public final class Pinyin {

    /** 中文词 → [全拼, 首字母] */
    private static final Map<String, String[]> MAP = new HashMap<>();
    /** 查询缓存：文本 → [全拼, 首字母]（空数组 = 无拼音） */
    private static final Map<String, String[]> CACHE = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    private Pinyin() {
    }

    public static synchronized void init(Plugin plugin) {
        if (initialized) return;
        try (InputStream is = plugin.getResource("pinyin_zh_cn.json")) {
            if (is == null) {
                plugin.getLogger().warning("pinyin_zh_cn.json 未找到（拼音搜索不可用）");
                return;
            }
            JsonObject o = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> e : o.entrySet()) {
                String v = e.getValue().getAsString();
                int idx = v.indexOf('|');
                if (idx <= 0) continue;
                MAP.put(e.getKey(), new String[]{v.substring(0, idx), v.substring(idx + 1)});
            }
            plugin.getLogger().info("拼音表已加载: " + MAP.size() + " 条（支持 zhizhu / zz 搜索）");
            initialized = true;
        } catch (Exception e) {
            plugin.getLogger().warning("pinyin_zh_cn.json 加载失败: " + e);
        }
    }

    private static String[] lookup(String text) {
        String[] v = CACHE.get(text);
        if (v != null) return v.length == 0 ? null : v;
        v = MAP.get(text);
        if (v == null) v = new String[0];
        CACHE.put(text, v);
        return v.length == 0 ? null : v;
    }

    /** 拼音匹配；kw 必须已转小写。text 为原文（中文/含符号均可） */
    public static boolean matches(String kw, String text) {
        if (kw == null || kw.isEmpty() || text == null || text.isEmpty()) return false;
        if (text.toLowerCase(Locale.ROOT).contains(kw)) return true;
        String[] py = lookup(text);
        if (py == null) return false;
        if (py[0] != null && !py[0].isEmpty() && py[0].contains(kw)) return true;
        return py[1] != null && !py[1].isEmpty() && py[1].startsWith(kw);
    }
}
