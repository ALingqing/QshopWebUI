package cn.aqcraft.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 密码工具：sha256:<hex> 或明文；兼容原系统 bcrypt 格式的检测 */
public final class AuthUtil {

    private AuthUtil() {
    }

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(b.length * 2);
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 校验密码。支持：
     * 明文、sha256:&lt;hex&gt;；
     * bcrypt（$2...）无法本地校验 → 返回 false（需在 config.yml 中改用明文/哈希）。
     */
    public static boolean verify(String input, String stored) {
        if (input == null || stored == null) return false;
        if (stored.startsWith("sha256:")) {
            return MessageDigest.isEqual(
                    sha256(input).getBytes(StandardCharsets.UTF_8),
                    stored.substring("sha256:".length()).trim().getBytes(StandardCharsets.UTF_8));
        }
        if (stored.startsWith("$2")) {
            return false; // bcrypt：不支持
        }
        return MessageDigest.isEqual(
                input.getBytes(StandardCharsets.UTF_8),
                stored.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isBcrypt(String stored) {
        return stored != null && stored.startsWith("$2");
    }
}
