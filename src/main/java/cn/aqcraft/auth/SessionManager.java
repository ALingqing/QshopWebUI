package cn.aqcraft.auth;

import cn.aqcraft.QShopWebUIPlugin;
import cn.aqcraft.http.HttpRequest;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 内存会话管理（重启后失效，需重新登录） */
public final class SessionManager {

    public static final class Session {
        public final String sessionId;
        public final String username;
        public final String role;
        public final long expiresAt;

        Session(String sessionId, String username, String role, long expiresAt) {
            this.sessionId = sessionId;
            this.username = username;
            this.role = role;
            this.expiresAt = expiresAt;
        }

        public boolean isAdmin() {
            return "admin".equals(role);
        }

        public boolean isValid() {
            return System.currentTimeMillis() < expiresAt;
        }
    }

    private final QShopWebUIPlugin plugin;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    public SessionManager(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
    }

    public Session create(String username, String role) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        String sid = sb.toString();
        Session s = new Session(sid, username, role,
                System.currentTimeMillis() + plugin.config().sessionTimeout * 1000L);
        sessions.put(sid, s);
        return s;
    }

    /** 取会话（自动清理过期） */
    public Session get(String sid) {
        if (sid == null || sid.isEmpty()) return null;
        Session s = sessions.get(sid);
        if (s == null) return null;
        if (!s.isValid()) {
            sessions.remove(sid);
            return null;
        }
        return s;
    }

    public Session fromRequest(HttpRequest req) {
        return get(req.header("x-session"));
    }

    /** 玩家会话的玩家名（非玩家会话/未登录返回 null） */
    public String playerOf(HttpRequest req) {
        Session s = fromRequest(req);
        if (s == null) return null;
        return "player".equals(s.role) ? s.username : null;
    }

    public void remove(String sid) {
        if (sid != null) sessions.remove(sid);
    }

    public int size() {
        return sessions.size();
    }
}
