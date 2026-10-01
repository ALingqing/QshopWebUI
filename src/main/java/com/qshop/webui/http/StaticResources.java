package com.qshop.webui.http;

import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * jar 内置静态资源（/web/ 目录）读取 + 内存缓存。
 * 若插件数据目录下存在 web/ 覆盖目录，则优先使用磁盘文件（方便用户自行换 UI）。
 */
public final class StaticResources {

    private static final long MAX_CACHE_BYTES = 64L * 1024 * 1024;

    private final Plugin plugin;
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong cacheBytes = new java.util.concurrent.atomic.AtomicLong();

    public StaticResources(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @param path 请求路径（如 /css/style.css），会自动去掉前导斜杠
     * @return 文件内容，不存在返回 null
     */
    public byte[] get(String path) {
        String p = norm(path);
        if (p == null) return null;
        byte[] cached = cache.get(p);
        if (cached != null) return cached == EMPTY ? null : cached;

        byte[] data = readFromDisk(p);
        if (data == null) data = readFromJar(p);
        if (data == null) {
            cache.put(p, EMPTY);
            return null;
        }
        if (cacheBytes.get() + data.length <= MAX_CACHE_BYTES) {
            cache.put(p, data);
            cacheBytes.addAndGet(data.length);
        }
        return data;
    }

    private static final byte[] EMPTY = new byte[0];

    private byte[] readFromDisk(String p) {
        try {
            java.io.File f = new java.io.File(plugin.getDataFolder(), "web/" + p);
            if (f.isFile()) {
                return java.nio.file.Files.readAllBytes(f.toPath());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private byte[] readFromJar(String p) {
        try (InputStream is = plugin.getResource("web/" + p)) {
            if (is == null) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream(8192);
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) >= 0) bos.write(buf, 0, r);
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** 规范化路径：禁止目录穿越；空路径 → index.html */
    private static String norm(String path) {
        if (path == null) return null;
        String p = path.replace('\\', '/');
        while (p.startsWith("/")) p = p.substring(1);
        if (p.isEmpty() || p.endsWith("/")) p = p + "index.html";
        if (p.contains("..") || p.contains("\0")) return null;
        return p;
    }

    public static String contentType(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        int q = p.indexOf('?');
        if (q >= 0) p = p.substring(0, q);
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html; charset=utf-8";
        if (p.endsWith(".css")) return "text/css; charset=utf-8";
        if (p.endsWith(".js") || p.endsWith(".mjs")) return "application/javascript; charset=utf-8";
        if (p.endsWith(".json")) return "application/json; charset=utf-8";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".gif")) return "image/gif";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".ico")) return "image/x-icon";
        if (p.endsWith(".woff")) return "font/woff";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".ttf")) return "font/ttf";
        if (p.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }
}
