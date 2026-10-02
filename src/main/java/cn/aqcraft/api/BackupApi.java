package cn.aqcraft.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cn.aqcraft.QShopWebUIPlugin;
import cn.aqcraft.http.HttpRequest;
import cn.aqcraft.http.HttpResponse;
import cn.aqcraft.util.JsonUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /api/backup 系列：Web 侧数据备份（设置/公告/活动/港口/用户） */
public final class BackupApi extends ApiBase {

    private static final long CLEANUP_RETENTION_MS = 30L * 24 * 3600 * 1000;

    private final Object backupLock = new Object();
    private volatile boolean running = false;
    private volatile long lastBackupAt = 0;
    private volatile boolean lastBackupOk = false;
    private volatile String lastBackupFile = null;

    public BackupApi(QShopWebUIPlugin plugin) {
        super(plugin);
    }

    private File backupDir() {
        File dir = new File(plugin.getDataFolder(), "data/backups");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("[Backup] 备份目录创建失败: " + dir);
        }
        return dir;
    }

    // ============================================================
    // GET /api/backup/status
    // ============================================================

    public HttpResponse status(HttpRequest req) {
        JsonObject config = obj();
        put(config, "enabled", false);
        put(config, "schedule", "manual");
        put(config, "backup_time", "");
        put(config, "backup_interval_minutes", 0);
        put(config, "backup_dir", backupDir().getAbsolutePath());
        put(config, "retention_days", 30);
        put(config, "min_keep", 10);
        put(config, "cleanup_enabled", true);

        JsonObject status = obj();
        put(status, "is_running", running);
        if (lastBackupAt > 0) put(status, "last_backup_at", lastBackupAt);
        else put(status, "last_backup_at", null);
        put(status, "last_backup_ok", lastBackupOk);
        put(status, "last_backup_file", lastBackupFile);
        status.add("history", new JsonArray());
        put(status, "note", "插件版备份 Web 侧数据（设置/公告/活动/用户）；商店数据由 QuickShop 自行持久化");

        JsonObject o = obj();
        put(o, "success", true);
        o.add("config", config);
        o.add("status", status);
        return HttpResponse.json(o);
    }

    // ============================================================
    // GET /api/backup/list
    // ============================================================

    public HttpResponse list(HttpRequest req) {
        File dir = backupDir();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) files = new File[0];
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());

        JsonArray arr = new JsonArray();
        for (File f : files) {
            JsonObject x = obj();
            put(x, "file_name", f.getName());
            put(x, "file_path", f.getAbsolutePath());
            put(x, "size_bytes", f.length());
            put(x, "size_mb", Math.round(f.length() / 1024.0 / 1024.0 * 100.0) / 100.0);
            put(x, "created_at", f.lastModified());
            put(x, "created_at_display", JsonUtil.iso(f.lastModified()));
            String type = "manual";
            String operator = "system";
            Integer shopCount = null;
            try {
                JsonObject meta = JsonUtil.parseObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8))
                        .getAsJsonObject("meta");
                if (meta != null) {
                    type = meta.has("backup_type") ? meta.get("backup_type").getAsString() : type;
                    operator = meta.has("operator") ? meta.get("operator").getAsString() : operator;
                    if (meta.has("shop_count")) shopCount = meta.get("shop_count").getAsInt();
                }
            } catch (Exception ignored) {
            }
            put(x, "backup_type", type);
            put(x, "operator", operator);
            put(x, "shop_count", shopCount);
            arr.add(x);
        }

        JsonObject o = obj();
        put(o, "success", true);
        put(o, "backup_dir", dir.getAbsolutePath());
        o.add("files", arr);
        put(o, "total", arr.size());
        return HttpResponse.json(o);
    }

    // ============================================================
    // POST /api/backup/now
    // ============================================================

    public HttpResponse now(HttpRequest req) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        JsonObject b = body(req);
        String operator = jstr(b, "operator", "admin");

        synchronized (backupLock) {
            if (running) return HttpResponse.error(429, "已有备份任务进行中");
            running = true;
        }
        long t0 = System.currentTimeMillis();
        try {
            JsonObject meta = obj();
            put(meta, "backup_type", "manual");
            put(meta, "operator", operator);
            put(meta, "time", JsonUtil.isoNow());
            put(meta, "shop_count", plugin.shopData().stats().total);
            put(meta, "plugin_version", plugin.getDescription().getVersion());

            JsonObject data = obj();
            data.add("meta", meta);
            data.add("data", plugin.store().exportAll());

            String fileName = "qshop-manual-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date())
                    + "-" + UUID.randomUUID().toString().substring(0, 4) + ".json";
            File out = new File(backupDir(), fileName);
            Files.write(out.toPath(), JsonUtil.toJson(data).getBytes(StandardCharsets.UTF_8));

            lastBackupAt = System.currentTimeMillis();
            lastBackupOk = true;
            lastBackupFile = out.getAbsolutePath();

            String msg = "备份完成: " + fileName + " (" + (out.length() / 1024) + " KB)";
            plugin.getLogger().info("[Backup] " + msg);
            plugin.store().addFetchLog(0, 0, plugin.shopData().stats().total, "backup", msg);

            JsonObject o = obj();
            put(o, "success", true);
            put(o, "file", fileName);
            put(o, "path", out.getAbsolutePath());
            put(o, "size_bytes", out.length());
            put(o, "duration_ms", System.currentTimeMillis() - t0);
            put(o, "backup_dir", backupDir().getAbsolutePath());
            return HttpResponse.json(o);
        } catch (Exception e) {
            lastBackupAt = System.currentTimeMillis();
            lastBackupOk = false;
            plugin.getLogger().warning("[Backup] 备份失败: " + e.getMessage());
            return HttpResponse.error(500, "备份失败: " + e.getMessage());
        } finally {
            running = false;
        }
    }

    // ============================================================
    // POST /api/backup/restore
    // ============================================================

    public HttpResponse restore(HttpRequest req) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        JsonObject b = body(req);
        String file = jstr(b, "file", "");
        if (file.isEmpty() || file.contains("..") || file.contains("/") || file.contains("\\")) {
            return HttpResponse.error(400, "非法文件名");
        }
        File f = new File(backupDir(), file);
        if (!f.isFile()) return HttpResponse.error(404, "备份文件不存在: " + file);
        try {
            JsonObject root = JsonUtil.parseObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            if (!root.has("data")) return HttpResponse.error(400, "备份文件格式不正确");
            plugin.store().importAll(root.getAsJsonObject("data"));
            String operator = jstr(b, "operator", "admin");
            plugin.getLogger().info("[Backup] " + operator + " 已从 " + file + " 恢复 Web 数据");
            plugin.store().addFetchLog(0, 0, plugin.shopData().stats().total, "restore", "已恢复: " + file);
            JsonObject o = obj();
            put(o, "success", true);
            put(o, "file", file);
            put(o, "message", "已恢复 Web 侧数据（设置/公告/活动/用户）");
            return HttpResponse.json(o);
        } catch (Exception e) {
            return HttpResponse.error(500, "恢复失败: " + e.getMessage());
        }
    }

    // ============================================================
    // DELETE /api/backup/:file 与 POST /api/backup/cleanup
    // ============================================================

    public HttpResponse delete(HttpRequest req, String file) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        if (file == null || file.isEmpty() || file.contains("..") || file.contains("/") || file.contains("\\")) {
            return HttpResponse.error(400, "非法文件名");
        }
        File f = new File(backupDir(), file);
        if (!f.isFile()) return HttpResponse.error(404, "备份文件不存在");
        if (!f.delete()) return HttpResponse.error(500, "删除失败");
        JsonObject o = obj();
        put(o, "success", true);
        put(o, "deleted", file);
        return HttpResponse.json(o);
    }

    public HttpResponse cleanup(HttpRequest req) {
        HttpResponse deny = adminOnly(req);
        if (deny != null) return deny;
        long cutoff = System.currentTimeMillis() - CLEANUP_RETENTION_MS;
        File[] files = backupDir().listFiles((d, name) -> name.endsWith(".json"));
        List<String> deleted = new ArrayList<>();
        int kept = 0;
        if (files != null) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified)); // 旧的在前
            int remaining = files.length;
            for (File f : files) {
                if (f.lastModified() < cutoff && remaining > 10) {
                    if (f.delete()) deleted.add(f.getName());
                    remaining--;
                } else {
                    kept++;
                }
            }
        }
        JsonObject o = obj();
        put(o, "success", true);
        put(o, "deleted_count", deleted.size());
        o.add("deleted", toArray(deleted));
        put(o, "kept", kept);
        return HttpResponse.json(o);
    }

    private static JsonArray toArray(List<String> list) {
        JsonArray a = new JsonArray();
        for (String s : list) a.add(s);
        return a;
    }
}
