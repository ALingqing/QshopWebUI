package cn.aqcraft.api;

import com.google.gson.JsonObject;
import cn.aqcraft.QShopWebUIPlugin;
import cn.aqcraft.auth.AuthUtil;
import cn.aqcraft.auth.SessionManager;
import cn.aqcraft.http.HttpRequest;
import cn.aqcraft.http.HttpResponse;

/** /api/auth 系列 */
public final class AuthApi extends ApiBase {

    public AuthApi(QShopWebUIPlugin plugin) {
        super(plugin);
    }

    // ============================================================
    // POST /api/auth/login
    // ============================================================

    public HttpResponse login(HttpRequest req) {
        JsonObject b = body(req);
        String username = jstr(b, "username", "").trim();
        String password = jstr(b, "password", "");
        if (username.isEmpty() || password.isEmpty()) {
            return HttpResponse.error(400, "请输入用户名和密码");
        }

        boolean ok = false;
        String role = "user";

        // 1) 内置管理员（config.yml）
        if (username.equalsIgnoreCase(plugin.config().adminUsername)) {
            String stored = plugin.config().adminPassword;
            if (AuthUtil.isBcrypt(stored)) {
                return HttpResponse.error(401,
                        "config.yml 中 admin.password 是旧版 bcrypt 格式，插件无法校验；请改为明文或 sha256:... 后执行 /qshopwebui reload");
            }
            if (AuthUtil.verify(password, stored)) {
                ok = true;
                role = "admin";
            }
        }

        // 2) 注册用户（WebStore）
        if (!ok) {
            JsonObject u = plugin.store().findUser(username);
            if (u != null && jbool(u, "active", true)) {
                String ph = jstr(u, "password_hash", "");
                if (AuthUtil.verify(password, ph)) {
                    ok = true;
                    role = jstr(u, "role", "user");
                    plugin.store().updateUserLastLogin(username);
                }
            }
        }

        if (!ok) {
            return HttpResponse.error(401, "用户名或密码错误");
        }

        SessionManager.Session s = plugin.sessions().create(username, role);
        JsonObject o = obj();
        put(o, "success", true);
        put(o, "session_id", s.sessionId);
        put(o, "expires_in", plugin.config().sessionTimeout);
        put(o, "username", username);
        put(o, "role", role);
        put(o, "is_admin", "admin".equals(role));
        return HttpResponse.json(o);
    }

    // ============================================================
    // POST /api/auth/logout
    // ============================================================

    public HttpResponse logout(HttpRequest req) {
        plugin.sessions().remove(req.header("x-session"));
        JsonObject o = obj();
        put(o, "success", true);
        return HttpResponse.json(o);
    }

    // ============================================================
    // /api/wallet  余额查询（GET ?player=xxx 或 POST {player}）
    // ============================================================

    public HttpResponse wallet(HttpRequest req) {
        String player = null;
        if ("POST".equalsIgnoreCase(req.method)) {
            JsonObject b = body(req);
            player = jstr(b, "player", "");
        }
        if (player == null || player.trim().isEmpty()) player = req.param("player", "");
        final String target = player.trim();
        if (target.isEmpty()) return HttpResponse.error(400, "请提供玩家名");
        JsonObject o = obj();
        put(o, "success", true);
        put(o, "player", target);
        put(o, "economy", plugin.economy().available());
        put(o, "currency", plugin.economy().currencyName());
        put(o, "balance", balanceOf(target));
        put(o, "online", org.bukkit.Bukkit.getPlayerExact(target) != null);
        put(o, "pending_items", plugin.store().pendingCount(target));
        return HttpResponse.json(o);
    }

    private Double balanceOf(String player) {
        try {
            if (!plugin.economy().available()) return null;
            org.bukkit.OfflinePlayer op = cn.aqcraft.purchase.PurchaseService.resolvePlayer(player);
            if (op == null) return null;
            return plugin.economy().balance(op);
        } catch (Throwable t) {
            return null;
        }
    }

    // ============================================================
    // GET /api/auth/status
    // ============================================================

    public HttpResponse status(HttpRequest req) {
        SessionManager.Session s = plugin.sessions().fromRequest(req);
        JsonObject o = obj();
        put(o, "success", true);
        put(o, "authenticated", s != null);
        put(o, "require_auth", plugin.config().requireAuth);
        put(o, "role", s == null ? "guest" : s.role);
        put(o, "username", s == null ? null : s.username);
        put(o, "is_admin", s != null && (s.isAdmin() || s.username.equalsIgnoreCase(plugin.config().adminUsername)));
        return HttpResponse.json(o);
    }

    // ============================================================
    // GET /api/auth/me
    // ============================================================

    public HttpResponse me(HttpRequest req) {
        SessionManager.Session s = plugin.sessions().fromRequest(req);
        JsonObject o = obj();
        put(o, "success", true);
        put(o, "username", s == null ? null : s.username);
        put(o, "role", s == null ? "guest" : s.role);
        put(o, "is_admin", s != null && (s.isAdmin() || s.username.equalsIgnoreCase(plugin.config().adminUsername)));
        return HttpResponse.json(o);
    }

    // ============================================================
    // POST /api/auth/register
    // ============================================================

    public HttpResponse register(HttpRequest req) {
        JsonObject b = body(req);
        String u = jstr(b, "username", "").trim();
        String password = jstr(b, "password", "");
        String email = jstr(b, "email", "");

        if (u.isEmpty() || password.isEmpty()) return HttpResponse.error(400, "用户名和密码不能为空");
        if (u.length() < 3 || u.length() > 64) return HttpResponse.error(400, "用户名长度 3-64 字符");
        if (password.length() < 6) return HttpResponse.error(400, "密码至少 6 个字符");
        if (!u.matches("^[a-zA-Z0-9_.-]+$")) return HttpResponse.error(400, "用户名只能包含字母、数字、下划线、点、短横");
        if (u.equalsIgnoreCase(plugin.config().adminUsername)) {
            return HttpResponse.error(409, "用户名已被占用");
        }
        if (plugin.store().userExists(u)) {
            return HttpResponse.error(409, "用户名已被占用");
        }

        String hash = "sha256:" + AuthUtil.sha256("qshop:" + password);
        plugin.store().createUser(u, hash, email.isEmpty() ? null : email.substring(0, Math.min(email.length(), 255)));
        JsonObject o = obj();
        put(o, "success", true);
        put(o, "message", "注册成功，请登录");
        put(o, "username", u);
        put(o, "role", "user");
        return HttpResponse.json(o);
    }

    // ============================================================
    // PUT /api/auth/change-password
    // ============================================================

    public HttpResponse changePassword(HttpRequest req) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        JsonObject b = body(req);
        String current = jstr(b, "current_password", "");
        String newPassword = jstr(b, "new_password", "");
        if (newPassword.length() < 6) return HttpResponse.error(400, "新密码至少 6 个字符");

        String stored = plugin.config().adminPassword;
        if (AuthUtil.isBcrypt(stored)) {
            return HttpResponse.error(401,
                    "当前配置为旧版 bcrypt 密码，无法校验；请直接编辑 config.yml 修改 admin.password");
        }
        if (!AuthUtil.verify(current, stored)) {
            return HttpResponse.error(401, "旧密码不正确");
        }

        String hashed = "sha256:" + AuthUtil.sha256("qshop:" + newPassword);
        plugin.getConfig().set("admin.password", hashed);
        plugin.saveConfig();
        plugin.applyAdminPassword(hashed);
        plugin.getLogger().info("[Auth] 管理员密码已更新（sha256）");
        JsonObject o = obj();
        put(o, "success", true);
        return HttpResponse.json(o);
    }
}
