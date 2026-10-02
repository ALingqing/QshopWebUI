package cn.aqcraft.bridge;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * AuthMe 密码验证桥（全反射，软依赖，不接触数据库）。
 * 通过 AuthMe API v3 的 checkPassword 校验玩家密码；未安装 AuthMe 时 available() = false。
 */
public final class AuthMeBridge {

    private final Plugin plugin;
    private volatile Object api;

    public AuthMeBridge(Plugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        api = null;
        try {
            Plugin authme = Bukkit.getPluginManager().getPlugin("AuthMe");
            if (authme == null || !authme.isEnabled()) return;
            Class<?> apiClass = Class.forName("fr.xephi.authme.api.v3.AuthMeApi", true, authme.getClass().getClassLoader());
            Object inst = apiClass.getMethod("getInstance").invoke(null);
            if (inst != null) {
                api = inst;
                plugin.getLogger().info("[AuthMe] 已连接 AuthMe（玩家可用游戏账号登录网页）");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[AuthMe] 初始化失败: " + t.getMessage());
        }
    }

    public boolean available() {
        return api != null;
    }

    /** 验证玩家密码（玩家不存在 / 密码错误均返回 false） */
    public boolean checkPassword(String player, String password) {
        Object a = api;
        if (a == null || player == null || password == null) return false;
        try {
            Method m = a.getClass().getMethod("checkPassword", String.class, String.class);
            return Boolean.TRUE.equals(m.invoke(a, player, password));
        } catch (Throwable t) {
            return false;
        }
    }
}
