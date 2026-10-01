package com.qshop.webui.http;

import com.qshop.webui.util.JsonUtil;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** HTTP 响应 */
public final class HttpResponse {

    public int status = 200;
    public String contentType = "application/json; charset=utf-8";
    public byte[] body = new byte[0];
    public final Map<String, String> headers = new LinkedHashMap<>();
    /** true = 响应后关闭连接 */
    public boolean close = false;

    public static HttpResponse json(Object o) {
        HttpResponse r = new HttpResponse();
        r.body = JsonUtil.toJson(o).getBytes(StandardCharsets.UTF_8);
        return r;
    }

    public static HttpResponse jsonRaw(String json) {
        HttpResponse r = new HttpResponse();
        r.body = json.getBytes(StandardCharsets.UTF_8);
        return r;
    }

    public static HttpResponse error(int status, String message) {
        HttpResponse r = new HttpResponse();
        r.status = status;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("error", message == null ? "未知错误" : message);
        r.body = JsonUtil.toJson(m).getBytes(StandardCharsets.UTF_8);
        return r;
    }

    public static HttpResponse text(String text, String contentType) {
        HttpResponse r = new HttpResponse();
        r.contentType = contentType;
        r.body = text.getBytes(StandardCharsets.UTF_8);
        return r;
    }

    public static HttpResponse bytes(byte[] data, String contentType) {
        HttpResponse r = new HttpResponse();
        r.contentType = contentType;
        r.body = data == null ? new byte[0] : data;
        return r;
    }

    public static HttpResponse notFound() {
        return error(404, "资源不存在");
    }

    public HttpResponse withHeader(String k, String v) {
        headers.put(k, v);
        return this;
    }
}
