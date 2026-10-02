package cn.aqcraft.http;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 解析后的 HTTP 请求 */
public final class HttpRequest {

    /** GET / POST / PUT / DELETE / HEAD / OPTIONS ... */
    public String method = "GET";
    /** 已 URL 解码的路径，如 /api/shops */
    public String path = "/";
    /** 原始目标（含 query），如 /api/shops?page=1 */
    public String target = "/";
    /** 原始 query 串（不含 ?），可能为 null */
    public String rawQuery = null;
    /** header 名统一小写 */
    public final Map<String, String> headers = new HashMap<>();
    /** 请求体（可能为 null / 空数组） */
    public byte[] body = new byte[0];
    /** 客户端地址 */
    public String clientIp = "unknown";
    /** 客户端是否要求保持连接 */
    public boolean keepAlive = true;

    private Map<String, String> queryCache;

    public String header(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    /** 查询参数（首个值） */
    public String param(String name) {
        return queryParams().get(name);
    }

    public String param(String name, String def) {
        String v = param(name);
        return v == null ? def : v;
    }

    public int intParam(String name, int def) {
        try {
            String v = param(name);
            if (v == null || v.isEmpty()) return def;
            return (int) Double.parseDouble(v);
        } catch (Exception e) {
            return def;
        }
    }

    public double doubleParam(String name, double def) {
        try {
            String v = param(name);
            if (v == null || v.isEmpty()) return def;
            return Double.parseDouble(v);
        } catch (Exception e) {
            return def;
        }
    }

    public Map<String, String> queryParams() {
        if (queryCache == null) {
            queryCache = new LinkedHashMap<>();
            if (rawQuery != null && !rawQuery.isEmpty()) {
                for (String pair : rawQuery.split("&")) {
                    if (pair.isEmpty()) continue;
                    int eq = pair.indexOf('=');
                    String k, v;
                    if (eq < 0) {
                        k = decode(pair);
                        v = "";
                    } else {
                        k = decode(pair.substring(0, eq));
                        v = decode(pair.substring(eq + 1));
                    }
                    // 同名参数保留第一个（与原 URLSearchParams.get 行为一致）
                    queryCache.putIfAbsent(k, v);
                }
            }
        }
        return queryCache;
    }

    public String bodyString() {
        try {
            return new String(body, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        } catch (IllegalArgumentException e) {
            return s;
        }
    }
}
