package cn.aqcraft.auth;

import cn.aqcraft.QShopWebUIPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 游戏内生成、网页一次性使用的交易验证码。验证码只保存在内存中。 */
public final class GameCodeService {
    private final QShopWebUIPlugin plugin;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    public GameCodeService(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
    }

    public String issue(Player player) {
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        entries.put(player.getUniqueId(), new Entry(code, System.currentTimeMillis()
                + plugin.config().gameCodeTtlSeconds * 1000L,
                plugin.config().gameCodeMaxAttempts));
        return code;
    }

    public boolean verifyAndConsume(String playerName, String code) {
        if (playerName == null || code == null) return false;
        Entry entry = null;
        UUID matched = null;
        for (Map.Entry<UUID, Entry> candidate : entries.entrySet()) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(candidate.getKey());
            if (player.getName() != null && player.getName().equalsIgnoreCase(playerName.trim())) {
                entry = candidate.getValue();
                matched = candidate.getKey();
                break;
            }
        }
        if (entry == null || System.currentTimeMillis() > entry.expiresAt) {
            if (matched != null) entries.remove(matched);
            return false;
        }
        if (--entry.attempts < 0) {
            entries.remove(matched);
            return false;
        }
        if (!entry.code.equals(code.trim())) return false;
        entries.remove(matched);
        return true;
    }

    /** 只校验验证码是否有效且属于该玩家，不消费（用于订单查询等只读场景）。校验失败不扣尝试次数。 */
    public boolean check(String playerName, String code) {
        if (playerName == null || code == null) return false;
        for (Map.Entry<UUID, Entry> candidate : entries.entrySet()) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(candidate.getKey());
            if (player.getName() != null && player.getName().equalsIgnoreCase(playerName.trim())) {
                Entry entry = candidate.getValue();
                if (entry != null && System.currentTimeMillis() <= entry.expiresAt
                        && entry.code.equals(code.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class Entry {
        private final String code;
        private final long expiresAt;
        private int attempts;

        private Entry(String code, long expiresAt, int attempts) {
            this.code = code;
            this.expiresAt = expiresAt;
            this.attempts = attempts;
        }
    }
}
