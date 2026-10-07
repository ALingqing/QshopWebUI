package cn.aqcraft.service;

import cn.aqcraft.QShopWebUIPlugin;
import cn.aqcraft.util.JsonUtil;
import com.google.gson.JsonObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 审计日志：记录所有管理端操作（批量购买/出售预览、实际执行、隐藏/显示商店、清空数据、重载等）。
 *
 * <p>存储于 {@code data/audit.json}（最多 {@value MAX_AUDIT} 条，滚动淘汰）。
 * 所有 {@code /api/admin/*}、批量操作、营业状态变更都通过 {@link #log} 记录，
 * 管理端可在网页「审计」页查看谁在什么时间做了什么。</p>
 */
public final class AuditService {

    private final QShopWebUIPlugin plugin;
    private final File file;
    private final List<JsonObject> logs = new ArrayList<>();
    private final AtomicLong seq = new AtomicLong();
    private final AtomicBoolean savePending = new AtomicBoolean();
    private static final int MAX_AUDIT = 5000;

    public AuditService(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/audit.json");
        load();
    }

    private void load() {
        try {
            if (!file.isFile()) return;
            com.google.gson.JsonElement el = JsonUtil.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            if (el.isJsonArray()) {
                for (com.google.gson.JsonElement e : el.getAsJsonArray()) {
                    if (e.isJsonObject()) logs.add(e.getAsJsonObject());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[审计] 读取 audit.json 失败: " + e.getMessage());
        }
    }

    /**
     * 写一条审计日志。
     *
     * @param actor  操作者（如 "admin" / 玩家名）
     * @param action 动作（如 "batch-buy-preview"、"batch-buy-execute"、"shop-hide"、"trade-clear"）
     * @param detail 可读详情
     */
    public synchronized void log(String actor, String action, String detail) {
        JsonObject o = new JsonObject();
        long id = seq.incrementAndGet();
        o.addProperty("id", id);
        o.addProperty("t", System.currentTimeMillis());
        o.addProperty("actor", actor == null ? "" : actor);
        o.addProperty("action", action == null ? "" : action);
        o.addProperty("detail", detail == null ? "" : detail);
        logs.add(o);
        while (logs.size() > MAX_AUDIT) logs.remove(0);
        scheduleSave();
    }

    private void scheduleSave() {
        if (!savePending.compareAndSet(false, true)) return;
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                save();
            } finally {
                savePending.set(false);
            }
        }, 40L);
    }

    public void save() {
        try {
            List<JsonObject> copy;
            synchronized (this) {
                copy = new ArrayList<>(logs);
            }
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (JsonObject o : copy) arr.add(o);
            File tmp = new File(file.getParentFile(), "audit.json.tmp");
            Files.write(tmp.toPath(), JsonUtil.toJson(arr).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            plugin.getLogger().warning("[审计] 保存 audit.json 失败: " + e.getMessage());
        }
    }

    public void flush() {
        save();
    }

    /** 审计日志快照（时间倒序） */
    public synchronized List<JsonObject> snapshot(int limit, int offset) {
        List<JsonObject> out = new ArrayList<>();
        for (int i = logs.size() - 1; i >= 0; i--) out.add(logs.get(i).deepCopy());
        if (offset < 0) offset = 0;
        int end = Math.min(out.size(), offset + Math.max(1, limit));
        if (offset > out.size()) return new ArrayList<>();
        return new ArrayList<>(out.subList(offset, end));
    }

    public synchronized int count() { return logs.size(); }

    public synchronized void clear() {
        logs.clear();
        seq.set(0);
        save();
    }
}