package cn.aqcraft.api;

import com.google.gson.JsonObject;
import cn.aqcraft.QShopWebUIPlugin;
import cn.aqcraft.http.HttpRequest;
import cn.aqcraft.http.HttpResponse;

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
        String code = jstr(b, "code", "");
        String password = jstr(b, "password", "");
        String token = jstr(b, "token", "");
        int amount = jint(b, "amount", 1);
        JsonObject result = plugin.purchases().purchase(shopId, player, amount, code, password, token);
        return HttpResponse.json(result);
    }

    public HttpResponse sell(HttpRequest req) {
        if (!plugin.config().purchaseEnabled) {
            return HttpResponse.error(403, "在线收购功能已关闭（config.yml purchase.enabled）");
        }
        JsonObject b = body(req);
        String shopId = jstr(b, "shop_id", "");
        String player = jstr(b, "player", "");
        String code = jstr(b, "code", "");
        String password = jstr(b, "password", "");
        String token = jstr(b, "token", "");
        int amount = jint(b, "amount", 1);
        JsonObject result = plugin.purchases().sell(shopId, player, amount, code, password, token);
        return HttpResponse.json(result);
    }

    /**
     * POST /api/player/login 玩家登录（获取交易会话 token，免重复验证）。
     * body: player + （code 游戏内验证码 | password AuthMe 密码）
     */
    public HttpResponse playerLogin(HttpRequest req) {
        JsonObject b = body(req);
        String player = jstr(b, "player", "");
        String code = jstr(b, "code", "");
        String password = jstr(b, "password", "");
        cn.aqcraft.auth.PlayerAuthService.Result auth =
                plugin.playerAuth().authenticate(player, code, password, "");
        JsonObject o = new JsonObject();
        if (!auth.ok) {
            o.addProperty("success", false);
            o.addProperty("error", auth.reason);
            return HttpResponse.json(o);
        }
        o.addProperty("success", true);
        o.addProperty("token", auth.token);
        o.addProperty("method", auth.method);
        o.addProperty("ttl", plugin.config().playerSessionTtlSeconds);
        o.addProperty("player", player.trim());
        return HttpResponse.json(o);
    }

    /** POST /api/player/logout 退出登录（清除会话）body: player + token */
    public HttpResponse playerLogout(HttpRequest req) {
        JsonObject b = body(req);
        plugin.playerAuth().logout(jstr(b, "player", ""), jstr(b, "token", ""));
        JsonObject o = new JsonObject();
        o.addProperty("success", true);
        return HttpResponse.json(o);
    }

    /** GET /api/player/session?player=X&token=Y 检查会话是否仍有效 */
    public HttpResponse playerSession(HttpRequest req) {
        String player = req.param("player", "");
        String token = req.param("token", "");
        JsonObject o = new JsonObject();
        o.addProperty("success", true);
        o.addProperty("logged_in", plugin.playerAuth().isLoggedIn(player, token));
        o.addProperty("authme_available", plugin.authme().available());
        o.addProperty("allow_password_login", plugin.config().allowAuthmeLogin);
        return HttpResponse.json(o);
    }

    /** POST /api/inventory-check：查询在线玩家背包中该商店物品数量（收购「最大」按钮） */
    public HttpResponse inventoryCheck(HttpRequest req) {
        JsonObject b = body(req);
        String shopId = jstr(b, "shop_id", "");
        String player = jstr(b, "player", "");
        JsonObject result = plugin.purchases().inventoryCheck(shopId, player);
        return HttpResponse.json(result);
    }

    /** POST /api/limit：查询该玩家在该商店的限购剩余额度（购买弹窗用） */
    public HttpResponse limit(HttpRequest req) {
        JsonObject b = body(req);
        String shopId = jstr(b, "shop_id", "");
        String player = jstr(b, "player", "");
        JsonObject result = plugin.purchases().limitInfo(shopId, player);
        return HttpResponse.json(result);
    }
}
