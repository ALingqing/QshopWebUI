package cn.aqcraft.data;

import cn.aqcraft.QShopWebUIPlugin;
import cn.aqcraft.util.JsonUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家商店收藏：每个玩家可收藏若干 shop_id（喜欢/关注的商店），
 * 网页端可快捷查看收藏商店的当前价格与库存。
 *
 * <p>存储于 {@code data/favorites.json}：{@code { "player": [shop_id,...] }}。</p>
 */
public final class FavoritesStore {

    private final QShopWebUIPlugin plugin;
    private final File file;
    private final Map<String, Set<String>> data = new ConcurrentHashMap<>();

    public FavoritesStore(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/favorites.json");
        load();
    }

    private void load() {
        try {
            if (!file.isFile()) return;
            JsonObject root = JsonParser.parseString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                Set<String> set = new LinkedHashSet<>();
                if (e.getValue().isJsonArray()) {
                    for (JsonElement s : e.getValue().getAsJsonArray()) set.add(s.getAsString());
                }
                data.put(e.getKey().toLowerCase(), set);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[收藏] 读取 favorites.json 失败: " + e.getMessage());
        }
    }

    private void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Set<String>> e : data.entrySet()) {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (String s : e.getValue()) arr.add(s);
            root.add(e.getKey(), arr);
        }
        try {
            Files.write(file.toPath(), JsonUtil.toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("[收藏] 保存 favorites.json 失败: " + e.getMessage());
        }
    }

    public void flush() { save(); }

    public List<String> favorites(String player) {
        Set<String> s = data.get(player == null ? "" : player.toLowerCase());
        return s == null ? new ArrayList<>() : new ArrayList<>(s);
    }

    public void add(String player, String shopId) {
        if (player == null || shopId == null) return;
        data.computeIfAbsent(player.toLowerCase(), k -> new LinkedHashSet<>()).add(shopId);
        save();
    }

    public void remove(String player, String shopId) {
        Set<String> s = data.get(player == null ? "" : player.toLowerCase());
        if (s != null) {
            s.remove(shopId);
            save();
        }
    }

    public boolean isFavorite(String player, String shopId) {
        Set<String> s = data.get(player == null ? "" : player.toLowerCase());
        return s != null && s.contains(shopId);
    }

    /** 切换收藏状态，返回切换后是否已收藏 */
    public boolean toggle(String player, String shopId) {
        if (isFavorite(player, shopId)) {
            remove(player, shopId);
            return false;
        }
        add(player, shopId);
        return true;
    }

    public int count(String player) {
        Set<String> s = data.get(player == null ? "" : player.toLowerCase());
        return s == null ? 0 : s.size();
    }
}