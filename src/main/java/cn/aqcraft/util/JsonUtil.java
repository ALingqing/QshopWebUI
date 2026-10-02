package cn.aqcraft.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * JSON 工具：用服务端自带的 Gson。
 * <p>统一开启 serializeNulls —— 与原 Node 版 JSON.stringify 行为一致（null 字段保留）。</p>
 */
public final class JsonUtil {

    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();

    private static final DateTimeFormatter ISO = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private JsonUtil() {
    }

    public static Gson gson() {
        return GSON;
    }

    public static String toJson(Object o) {
        return GSON.toJson(o);
    }

    public static JsonObject parseObject(String s) {
        JsonElement e = JsonParser.parseString(s);
        return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    public static JsonElement parse(String s) {
        return JsonParser.parseString(s);
    }

    /** epoch 毫秒 → ISO-8601 UTC 字符串（与 PostgreSQL TIMESTAMPTZ 的 JSON 序列化一致） */
    public static String iso(long epochMillis) {
        return ISO.format(Instant.ofEpochMilli(epochMillis));
    }

    public static String isoNow() {
        return iso(System.currentTimeMillis());
    }
}
