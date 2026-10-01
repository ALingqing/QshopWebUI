package com.qshop.webui.bridge;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

/**
 * Vault 经济桥（全反射，软依赖）。
 * <p>服务器未安装 Vault / 经济插件时 available() 为 false，在线购买会给出中文提示。</p>
 */
public final class EconomyBridge {

    private final Plugin plugin;
    private volatile Object economy;

    public EconomyBridge(Plugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        economy = null;
        try {
            Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
            if (vault == null || !vault.isEnabled()) return;
            Class<?> ecoClass = Class.forName("net.milkbowl.vault.economy.Economy", true, vault.getClass().getClassLoader());
            @SuppressWarnings("unchecked")
            Iterable<RegisteredServiceProvider<?>> regs = (Iterable<RegisteredServiceProvider<?>>)
                    (Iterable<?>) Bukkit.getServicesManager().getRegistrations((Class) ecoClass);
            for (RegisteredServiceProvider<?> reg : regs) {
                if (reg.getProvider() != null) {
                    economy = reg.getProvider();
                    break;
                }
            }
            if (economy != null) {
                plugin.getLogger().info("[Economy] 已连接经济插件（Vault）");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[Economy] Vault 初始化失败: " + t.getMessage());
        }
    }

    public boolean available() {
        return economy != null;
    }

    public String currencyName() {
        try {
            Object r = callNoArg("currencyNamePlural");
            return r == null ? "金币" : String.valueOf(r);
        } catch (Throwable t) {
            return "金币";
        }
    }

    public double balance(OfflinePlayer player) {
        try {
            Object r = call("getBalance", OfflinePlayer.class, player);
            return r instanceof Number ? ((Number) r).doubleValue() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        try {
            Object resp = call("withdrawPlayer", OfflinePlayer.class, player, double.class, amount);
            if (resp == null) return false;
            Object ok = resp.getClass().getMethod("transactionSuccess").invoke(resp);
            return Boolean.TRUE.equals(ok);
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        try {
            Object resp = call("depositPlayer", OfflinePlayer.class, player, double.class, amount);
            if (resp == null) return false;
            Object ok = resp.getClass().getMethod("transactionSuccess").invoke(resp);
            return Boolean.TRUE.equals(ok);
        } catch (Throwable t) {
            return false;
        }
    }

    private Object callNoArg(String name) throws Exception {
        for (Method m : economy.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 0) {
                return m.invoke(economy);
            }
        }
        throw new NoSuchMethodException(name);
    }

    private Object call(String name, Class<?> p1Type, Object p1) throws Exception {
        return call(name, p1Type, p1, null, null);
    }

    private Object call(String name, Class<?> p1Type, Object p1, Class<?> p2Type, Object p2) throws Exception {
        Throwable lastErr = null;
        for (Method m : economy.getClass().getMethods()) {
            if (!m.getName().equals(name)) continue;
            try {
                if (p2Type == null) {
                    if (m.getParameterCount() != 1) continue;
                    return m.invoke(economy, p1);
                }
                if (m.getParameterCount() != 2) continue;
                return m.invoke(economy, p1, p2);
            } catch (Throwable t) {
                lastErr = t;
            }
        }
        throw new NoSuchMethodException(name + (lastErr == null ? "" : " (" + lastErr.getMessage() + ")"));
    }
}
