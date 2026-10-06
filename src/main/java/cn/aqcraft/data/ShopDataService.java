package cn.aqcraft.data;

import cn.aqcraft.QShopWebUIPlugin;
import cn.aqcraft.bridge.QuickShopBridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 商店数据服务：维护 QuickShop 数据的只读快照（带 TTL 缓存）。
 * <p>所有 HTTP 请求都读取此快照，避免频繁跨线程访问 QuickShop 内部结构。</p>
 */
public final class ShopDataService {

    private final QShopWebUIPlugin plugin;
    private final QuickShopBridge bridge;

    private final Object lock = new Object();
    private volatile List<ShopEntry> snapshot = Collections.emptyList();
    private volatile long snapshotAt = 0;
    private volatile ShopStats stats = new ShopStats();
    private final java.util.Deque<com.google.gson.JsonObject> syncHistory = new java.util.ArrayDeque<>();

    // ---- 事件驱动刷新（QuickShop 数据变更 → 尾部防抖自动刷新快照）----
    private final java.util.concurrent.ScheduledExecutorService refreshExec =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "QShopWebUI-Snapshot");
                t.setDaemon(true);
                return t;
            });
    private final java.util.concurrent.atomic.AtomicLong lastShopEventAt = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicBoolean refreshLoopActive = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean closed;

    public ShopDataService(QShopWebUIPlugin plugin, QuickShopBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    /** 插件停用时调用：停止后台刷新线程，避免停用后再向调度器注册任务。 */
    public void shutdown() {
        closed = true;
        refreshExec.shutdownNow();
    }

    private boolean canRefresh() {
        return !closed && plugin.isEnabled();
    }

    /** 获取当前快照（必要时自动刷新） */
    public List<ShopEntry> shops() {
        if (!canRefresh()) return snapshot;
        long ttl = plugin.config().snapshotTtlMs;
        if (snapshotAt > 0 && System.currentTimeMillis() - snapshotAt <= ttl) {
            return snapshot;
        }
        synchronized (lock) {
            if (snapshotAt > 0 && System.currentTimeMillis() - snapshotAt <= ttl) {
                return snapshot;
            }
            refreshLocked();
            return snapshot;
        }
    }

    /** 强制刷新快照（同步执行） */
    public void refresh() {
        if (!canRefresh()) return;
        synchronized (lock) {
            refreshLocked();
        }
    }

    /** 使快照过期（下次请求时重建） */
    public void invalidate() {
        this.snapshotAt = 0;
    }

    // ============================================================
    // 事件驱动刷新（ShopDataListener → 商店变更 → 自动刷新快照）
    // ============================================================

    /** QuickShop 商店数据发生变化（创建/删除/改价/库存等）→ 稍后自动刷新快照 */
    public void onShopChanged() {
        if (!canRefresh()) return;
        lastShopEventAt.set(System.currentTimeMillis());
        if (refreshLoopActive.compareAndSet(false, true)) {
            refreshExec.schedule(this::debounceTick, 1000L, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    private void debounceTick() {
        try {
            if (!canRefresh()) {
                refreshLoopActive.set(false);
                return;
            }
            long now = System.currentTimeMillis();
            // 事件仍在持续（如批量删除）→ 继续等待，合并为一轮刷新
            if (now - lastShopEventAt.get() < 1500L) {
                refreshExec.schedule(this::debounceTick, 800L, java.util.concurrent.TimeUnit.MILLISECONDS);
                return;
            }
            // 距上次实际刷新太近 → 稍后补一轮（防止高频全量刷新）
            long sinceRefresh = now - snapshotAt;
            if (sinceRefresh < 3000L) {
                refreshExec.schedule(this::debounceTick, 3000L - sinceRefresh + 100L,
                        java.util.concurrent.TimeUnit.MILLISECONDS);
                return;
            }
            refreshLoopActive.set(false);
            refresh();
        } catch (Throwable t) {
            refreshLoopActive.set(false);
        }
    }

    private void refreshLocked() {
        long t0 = System.currentTimeMillis();
        long now = t0;
        List<ShopEntry> list = buildSnapshot(now);
        this.snapshot = list;
        this.snapshotAt = now;
        this.stats = computeStats(list, now);
        com.google.gson.JsonObject h = new com.google.gson.JsonObject();
        h.addProperty("time", now);
        h.addProperty("source", "quickshop");
        h.addProperty("success", bridge.isAvailable());
        h.addProperty("shop_count", list.size());
        h.addProperty("duration_ms", System.currentTimeMillis() - t0);
        synchronized (syncHistory) {
            syncHistory.addFirst(h);
            while (syncHistory.size() > 20) syncHistory.removeLast();
        }
        if (plugin.config().debug) {
            plugin.getLogger().info("[Data] 快照刷新完成: " + list.size() + " 家商店");
        }
    }

    public java.util.List<com.google.gson.JsonObject> syncHistory() {
        synchronized (syncHistory) {
            return new java.util.ArrayList<>(syncHistory);
        }
    }

    private List<ShopEntry> buildSnapshot(long now) {
        if (!bridge.isAvailable()) return Collections.emptyList();
        if (!plugin.isEnabled()) return this.snapshot;
        try {
            List<Object> raw = bridge.getAllShopsOnMainThread();
            List<ShopEntry> list = new ArrayList<>(raw.size());
            for (Object o : raw) {
                try {
                    ShopEntry e = QuickShopBridge.toEntry(o, now);
                    e.activity_score = plugin.store().viewsOf(e.material);
                    list.add(e);
                } catch (Throwable ignored) {
                    // 单店读取失败不影响整体
                }
            }
            return list;
        } catch (Throwable t) {
            plugin.getLogger().warning("[Data] 读取 QuickShop 商店失败: " + t.getMessage());
            return this.snapshot; // 保留旧快照
        }
    }

    private static ShopStats computeStats(List<ShopEntry> list, long now) {
        ShopStats s = new ShopStats();
        Set<String> mats = new HashSet<>();
        Set<String> owners = new HashSet<>();
        Set<String> worlds = new HashSet<>();
        long activity = 0;
        for (ShopEntry e : list) {
            s.total++;
            if (e.isSelling()) s.selling++;
            else if (e.isBuying()) s.buying++;
            if (e.material != null) mats.add(e.material);
            if (e.owner_name != null) owners.add(e.owner_name);
            if (e.world != null) worlds.add(e.world);
            activity += e.activity_score;
        }
        s.materials = mats.size();
        s.owners = owners.size();
        s.worlds = worlds.size();
        s.totalActivity = activity;
        s.lastUpdateAt = now;
        s.lastSyncAt = now;
        return s;
    }

    public ShopStats stats() {
        // 保证至少有一次快照
        shops();
        return stats;
    }

    public long snapshotAt() {
        return snapshotAt;
    }
}
