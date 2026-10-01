package com.qshop.webui.http;

import com.qshop.webui.QShopWebUIPlugin;
import com.qshop.webui.api.ApiRouter;

/** 请求分发：/api/* → API 路由器；其余 → 静态资源 */
public final class Router {

    private final QShopWebUIPlugin plugin;
    private final StaticResources statics;
    private final ApiRouter api;

    public Router(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
        this.statics = new StaticResources(plugin);
        this.api = new ApiRouter(plugin);
    }

    public HttpResponse handle(HttpRequest req) {
        String path = req.path == null ? "/" : req.path;

        if ("OPTIONS".equals(req.method)) {
            HttpResponse r = new HttpResponse();
            r.status = 204;
            return r;
        }

        if (path.equals("/api") || path.startsWith("/api/")) {
            return api.handle(req);
        }

        if (path.equals("/")) {
            path = "/index.html";
        }
        if (!"GET".equals(req.method) && !"HEAD".equals(req.method)) {
            return HttpResponse.error(405, "Method Not Allowed");
        }

        byte[] data = statics.get(path);
        if (data == null) {
            return HttpResponse.notFound();
        }
        HttpResponse r = HttpResponse.bytes(data, StaticResources.contentType(path));
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".ico")
                || lower.endsWith(".woff") || lower.endsWith(".woff2") || lower.endsWith(".ttf")) {
            r.headers.put("Cache-Control", "public, max-age=86400");
        } else {
            r.headers.put("Cache-Control", "no-cache");
        }
        return r;
    }
}
