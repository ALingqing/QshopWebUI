package com.qshop.webui.api;

import com.google.gson.JsonObject;
import com.qshop.webui.QShopWebUIPlugin;
import com.qshop.webui.http.HttpRequest;
import com.qshop.webui.http.HttpResponse;

/** POST /api/purchase —— 网页在线购买 */
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
        int amount = jint(b, "amount", 1);
        JsonObject result = plugin.purchases().purchase(shopId, player, amount);
        return HttpResponse.json(result);
    }
}
