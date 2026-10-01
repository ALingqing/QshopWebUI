package com.qshop.webui.api;

import com.google.gson.JsonObject;
import com.qshop.webui.QShopWebUIPlugin;
import com.qshop.webui.http.HttpRequest;
import com.qshop.webui.http.HttpResponse;

/** POST /api/purchase 网页在线购买；POST /api/sell 玩家出售给收购商店 */
public final class PurchaseApi extends ApiBase {

    public PurchaseApi(QShopWebUIPlugin plugin) {
        super(plugin);
    }

    public HttpResponse purchase(HttpRequest req) {
        if (!plugin.config().purchaseEnabled) {
            return HttpResponse.error(403, "在线购买功能已关闭（config.yml purchase.enabled）");
        }
        JsonObject b = body(req);
        String shopId = jstr(b, "shop_id", "");
        String player = jstr(b, "player", "");
        String password = jstr(b, "password", "");
        int amount = jint(b, "amount", 1);
        String sessionPlayer = plugin.sessions().playerOf(req);
        JsonObject result = plugin.purchases().purchase(shopId, player, amount, sessionPlayer, password);
        return HttpResponse.json(result);
    }

    public HttpResponse sell(HttpRequest req) {
        if (!plugin.config().purchaseEnabled) {
            return HttpResponse.error(403, "在线收购功能已关闭（config.yml purchase.enabled）");
        }
        JsonObject b = body(req);
        String shopId = jstr(b, "shop_id", "");
        String player = jstr(b, "player", "");
        String password = jstr(b, "password", "");
        int amount = jint(b, "amount", 1);
        String sessionPlayer = plugin.sessions().playerOf(req);
        JsonObject result = plugin.purchases().sell(shopId, player, amount, sessionPlayer, password);
        return HttpResponse.json(result);
    }

    /** POST /api/inventory-check：查询在线玩家背包中该商店物品数量（收购「最大」按钮） */
    public HttpResponse inventoryCheck(HttpRequest req) {
        JsonObject b = body(req);
        String shopId = jstr(b, "shop_id", "");
        String player = jstr(b, "player", "");
        String sessionPlayer = plugin.sessions().playerOf(req);
        JsonObject result = plugin.purchases().inventoryCheck(shopId, player, sessionPlayer);
        return HttpResponse.json(result);
    }
}
