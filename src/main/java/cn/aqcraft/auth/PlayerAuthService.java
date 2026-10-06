package cn.aqcraft.auth;

import cn.aqcraft.QShopWebUIPlugin;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网页玩家登录会话。
 * <p>玩家可用「游戏内一次性验证码」或「AuthMe 密码」登录一次，登录后在 TTL 内交易/查询免重复验证，
 * 避免每次都输入验证码。会话仅保存在内存中，服务器重启后失效。</p>
 */
public final class PlayerAuthService {

    private final QShopWebUIPlugin plugin;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    public PlayerAuthService(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
    }

    /** 认证结果。 */
    public static final class Result {
        public final boolean ok;
        public final String reason;
        public final String token;
        public final String method;

        private Result(boolean ok, String reason, String token, String method) {
            this.ok = ok;
            this.reason = reason;
            this.token = token;
            this.method = method;
        }
    }

    private static Result ok(String token, String method) {
        return new Result(true, null, token, method);
    }

    private static Result fail(String reason) {
        return new Result(false, reason, null, null);
    }

    /**
     * 交易认证：已登录会话有效 → 直接通过；否则用「验证码」或「AuthMe 密码」验证。
     * 任一方式通过都会（重新）签发 token。
     */
    public Result authenticate(String playerName, String code, String password, String token) {
        String name = playerName == null ? "" : playerName.trim();
        if (name.isEmpty()) return fail("请输入你的游戏 ID");

        // 1) 已有有效会话（免重复验证）
        if (token != null && !token.isEmpty()) {
            Entry e = sessions.get(key(name));
            if (e != null && e.token.equals(token) && System.currentTimeMillis() <= e.expiresAt) {
                return ok(e.token, "session");
            }
        }
        // 2) 游戏内一次性验证码
        if (code != null && !code.trim().isEmpty()) {
            if (plugin.gameCodes().verifyAndConsume(name, code)) {
                return ok(issue(name), "code");
            }
            return fail("验证码无效、已过期或玩家不在线，请在游戏内执行 /qshopwebui code");
        }
        // 3) AuthMe 密码
        if (password != null && !password.isEmpty()) {
            if (!plugin.config().allowAuthmeLogin) {
                return fail("服务器未开启 AuthMe 密码登录（config.yml purchase.allow-authme-login）");
            }
            if (!plugin.authme().available()) {
                return fail("服务器未安装 AuthMe，无法使用密码登录，请改用游戏内验证码");
            }
            if (plugin.authme().checkPassword(name, password)) {
                return ok(issue(name), "password");
            }
            return fail("AuthMe 密码错误");
        }
        return fail("请提供游戏内验证码或 AuthMe 密码进行验证");
    }

    /** 只读校验（订单/收藏查询等）：有效会话 或 验证码有效即可（不消费验证码）。 */
    public boolean verify(String playerName, String code, String token) {
        String name = playerName == null ? "" : playerName.trim();
        if (name.isEmpty()) return false;
        if (token != null && !token.isEmpty()) {
            Entry e = sessions.get(key(name));
            if (e != null && e.token.equals(token) && System.currentTimeMillis() <= e.expiresAt) return true;
        }
        return plugin.gameCodes().check(name, code);
    }

    /** 指定玩家当前是否有有效登录会话。 */
    public boolean isLoggedIn(String playerName, String token) {
        String name = playerName == null ? "" : playerName.trim();
        if (name.isEmpty() || token == null || token.isEmpty()) return false;
        Entry e = sessions.get(key(name));
        return e != null && e.token.equals(token) && System.currentTimeMillis() <= e.expiresAt;
    }

    /** 退出登录：清除该玩家的会话（token 为空则强制清除）。 */
    public void logout(String playerName, String token) {
        String name = playerName == null ? "" : playerName.trim();
        if (name.isEmpty()) return;
        Entry e = sessions.get(key(name));
        if (e != null && (token == null || token.isEmpty() || e.token.equals(token))) {
            sessions.remove(key(name));
        }
    }

    /** 会话数量（诊断用）。 */
    public int size() {
        return sessions.size();
    }

    private String issue(String name) {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(48);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        String token = sb.toString();
        long ttl = Math.max(60, plugin.config().playerSessionTtlSeconds) * 1000L;
        sessions.put(key(name), new Entry(token, System.currentTimeMillis() + ttl));
        return token;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static final class Entry {
        private final String token;
        private final long expiresAt;

        private Entry(String token, long expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }
}
