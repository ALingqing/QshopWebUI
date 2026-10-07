package cn.aqcraft.api;

import cn.aqcraft.QShopWebUIPlugin;
import cn.aqcraft.data.ShopEntry;
import cn.aqcraft.service.ComparisonService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** {@link QShopWebUIAPI} 的默认实现，桥接插件内部服务。 */
public class QShopWebUIImpl implements QShopWebUIAPI {

    private final QShopWebUIPlugin plugin;
    private final Gson gson = new Gson();

    public QShopWebUIImpl(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
    }

    private JsonObject toJson(ShopEntry e) {
        return e == null ? null : (JsonObject) gson.toJsonTree(e);
    }

    private List<JsonObject> toJsonList(List<ShopEntry> list) {
        List<JsonObject> out = new ArrayList<>();
        if (list != null) for (ShopEntry e : list) if (e != null) out.add(toJson(e));
        return out;
    }

    @Override
    public JsonObject shop(String shopId) {
        if (shopId == null) return null;
        for (ShopEntry e : plugin.shopData().shops()) {
            if (shopId.equals(e.shop_id)) return toJson(e);
        }
        return null;
    }

    @Override
    public List<JsonObject> shopsByOwner(String ownerName) {
        if (ownerName == null) return new ArrayList<>();
        List<ShopEntry> out = new ArrayList<>();
        String owner = ownerName.trim().toLowerCase();
        for (ShopEntry e : plugin.shopData().shops()) {
            if (e.owner_name != null && e.owner_name.toLowerCase().equals(owner)) out.add(e);
        }
        return toJsonList(out);
    }

    @Override
    public List<JsonObject> shopsByMaterial(String material) {
        if (material == null) return new ArrayList<>();
        List<ShopEntry> out = new ArrayList<>();
        for (ShopEntry e : plugin.shopData().shops()) {
            if (e.material != null && e.material.equalsIgnoreCase(material)) out.add(e);
        }
        return toJsonList(out);
    }

    @Override
    public JsonObject compare(String material) {
        if (material == null) return new JsonObject();
        return plugin.comparison().compare(material, plugin.shopData().shops());
    }

    @Override
    public JsonObject recordOrder(String idempotencyKey, String type, String status,
                                  String shopId, String itemName, String material,
                                  int amount, long items, double unitPrice, double total,
                                  String player, String owner, boolean online, String reason) {
        return plugin.orders().recordOrder(idempotencyKey, type, status, shopId, itemName,
                material, amount, items, unitPrice, total, player, owner, online, reason);
    }

    @Override
    public List<JsonObject> ordersByPlayer(String player, String type, int limit, int offset) {
        List<JsonObject> list = plugin.orders().byPlayer(player, type, limit, offset);
        return list == null ? new ArrayList<>() : list;
    }

    @Override
    public JsonObject order(String orderId) {
        return plugin.orders().byId(orderId);
    }

    @Override
    public boolean isFavorite(String player, String shopId) {
        return plugin.favorites().isFavorite(player, shopId);
    }

    @Override
    public void addFavorite(String player, String shopId) {
        plugin.favorites().add(player, shopId);
    }

    @Override
    public void removeFavorite(String player, String shopId) {
        plugin.favorites().remove(player, shopId);
    }

    @Override
    public List<String> favoriteIds(String player) {
        List<String> l = plugin.favorites().favorites(player);
        return l == null ? new ArrayList<>() : l;
    }

    @Override
    public boolean isOpen() {
        return plugin.shopStatus().snapshot().get("open").getAsBoolean();
    }

    @Override
    public boolean isMaintenance() {
        JsonObject s = plugin.shopStatus().snapshot();
        return s.has("maintenance") && s.get("maintenance").getAsBoolean();
    }

    @Override
    public JsonArray businessStats(int daysN) {
        return plugin.stats().snapshot(daysN);
    }

    @Override
    public JsonObject businessTotals() {
        return plugin.stats().totals();
    }

    @Override
    public void audit(String actor, String action, String detail) {
        plugin.audit().log(actor, action, detail);
    }

    @Override
    public JsonObject purchase(String shopId, String playerName, int amount) {
        return plugin.purchases().purchaseInternal(shopId, playerName, amount);
    }

    @Override
    public JsonObject sell(String shopId, String playerName, int amount) {
        return plugin.purchases().sellInternal(shopId, playerName, amount);
    }
}