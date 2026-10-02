package cn.aqcraft.history;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import cn.aqcraft.QShopWebUIPlugin;
import cn.aqcraft.data.ShopEntry;
import cn.aqcraft.util.Materials;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 导入 QuickShop 历史交易。
 *
 * 本站在本次更新上线后，只记录"更新之后"发生的交易；更早的游戏内购买/收购
 * 只存在于 QuickShop 自己的数据库中。本类通过以下流程把历史数据捞回来：
 *
 * 1. 主线程执行 {@code /qs export}，让 QuickShop 把数据库导出为 export-*.zip；
 * 2. 解析导出包中的 log_purchase.csv（交易流水）、data.csv（商店物品快照）、
 *    players.csv（UUID → 玩家名）；
 * 3. 转换为本站交易记录（source=history）批量写入，并记录已导入进度自动去重。
 *
 * 注意：历史交易的时间点本站无法精确还原到毫秒，取 QuickShop 记录的时间；
 *      历史记录不计入"在线状态"，player 字段为 UUID 对应的缓存名。
 */
public final class HistoryImporter {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]");
    private static final String SETTING_KEY = "history_import_max_id";
    private static final String REMOVALS_KEY = "removals_import_max_id";

    /** 匹配 ItemStack YAML 中的 id 行（行首缩进 + id: material） */
    private static final Pattern ITEM_ID = Pattern.compile("(?m)^[ \\t]*id:[ \\t]*([^\\s]+)");
    private static final Pattern ITEM_COUNT = Pattern.compile("(?m)^[ \\t]*count:[ \\t]*(\\d+)");

    private final QShopWebUIPlugin plugin;

    public HistoryImporter(QShopWebUIPlugin plugin) {
        this.plugin = plugin;
    }

    // ============================================================
    // 入口
    // ============================================================

    public JsonObject run() {
        try {
            return doImport();
        } catch (Throwable e) {
            return fail("导入失败: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : (": " + e.getMessage())));
        }
    }

    private JsonObject doImport() throws Exception {
        Plugin qs = findQuickShop();
        if (qs == null) return fail("未找到 QuickShop 插件（QuickShop-Hikari / QuickShop）");
        File dataFolder = qs.getDataFolder();
        if (!dataFolder.isDirectory()) return fail("QuickShop 数据目录不存在: " + dataFolder.getAbsolutePath());

        // ---- 1) 触发 /qs export 刷新导出（主线程执行命令） ----
        long before = System.currentTimeMillis();
        boolean dispatched;
        try {
            Boolean ok = plugin.bridge().runOnMain(() -> {
                if (Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "qs export")) return true;
                if (Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "quickshop export")) return true;
                return false;
            });
            dispatched = ok != null && ok;
        } catch (Exception e) {
            dispatched = false;
        }

        // ---- 2) 等待新导出文件（最多 20 秒，兜底使用最新已有导出） ----
        File zip = waitForExport(dataFolder, before, 20000L);
        if (zip == null) {
            return fail("未找到 QuickShop 导出文件。请确认服务器中的 QuickShop 已加载且支持 /qs export 命令");
        }
        boolean fresh = tsOf(zip) >= before - 5000L;

        // ---- 3) 解析导出包 ----
        Map<String, String> nameByUuid = new HashMap<>();   // uuid -> 玩家名
        Map<Integer, ShopRef> refById = new HashMap<>();    // shop id -> 物品引用
        List<String[]> rows;
        List<String[]> otherRows = new ArrayList<>();
        try (ZipFile zf = new ZipFile(zip)) {
            String playersCsv = readEntry(zf, "players.csv");
            if (playersCsv != null) parsePlayers(playersCsv, nameByUuid);
            String dataCsv = readEntry(zf, "data.csv");
            if (dataCsv != null) parseData(dataCsv, refById);
            String logCsv = readEntry(zf, "log_purchase.csv");
            if (logCsv == null) return fail("导出包中缺少 log_purchase.csv（QuickShop 交易流水）");
            rows = parseCsv(logCsv);
            String othersCsv = readEntry(zf, "log_others.csv");
            if (othersCsv != null) otherRows = parseCsv(othersCsv);
        }

        // ---- 4) 已导入进度 ----
        long maxId = readMaxId(SETTING_KEY);

        // ---- 5) 当前商店快照（补充物品显示名 / 店主名 / 每份数量） ----
        Map<String, ShopEntry> snap = new HashMap<>();
        for (ShopEntry e : plugin.shopData().shops()) {
            if (e != null && e.shop_id != null) snap.put(e.shop_id, e);
        }

        // ---- 6) 逐行转换 ----
        List<JsonObject> batch = new ArrayList<>();
        List<JsonObject> pendingTradeDeletes = new ArrayList<>(); // 交易日志中的删除记录（批量命令不写 ShopRemoveLog）
        long newMax = maxId;
        int purchases = 0, skipped = 0, removalsSkipped = 0;
        long fromTs = Long.MAX_VALUE, toTs = 0;
        for (String[] row : rows) {
            if (row.length < 9) continue;
            long id;
            try {
                id = Long.parseLong(row[0].trim());
            } catch (Exception e) {
                continue;
            }
            if (id > newMax) newMax = id;
            String type = row[5] == null ? "" : row[5].trim();
            boolean purchase = type.startsWith("PURCHASE_");
            boolean delete = "DELETE".equals(type);
            if (id <= maxId) {
                if (purchase) skipped++;
                if (delete) removalsSkipped++;
                continue;
            }
            if (!purchase && !delete) continue; // CREATE 等事件不计入
            if (delete) {
                // 批量删除命令（如 /qs removeall）只写交易日志、不写 ShopRemoveLog，
                // 这里把 DELETE 记录也转为"移除商店"记录（BUYER 列 = 操作者）
                JsonObject r = new JsonObject();
                r.addProperty("t", parseTime(row[1]));
                r.addProperty("source", "history");
                r.addProperty("player", cleanPlayer(row[4] == null ? "" : row[4].trim(), nameByUuid));
                r.addProperty("reason", "商店删除（批量命令，编号 " + (row[2] == null ? "" : row[2].trim()) + "）");
                pendingTradeDeletes.add(r);
                continue;
            }
            purchases++;

            boolean isBuy = type.contains("SELLING"); // PURCHASE_SELLING_SHOP=玩家买入；PURCHASE_BUYING_SHOP=玩家卖给收购店
            int shopId = (int) parseLongSafe(row[2]);
            String buyer = row[4] == null ? "" : row[4].trim();
            long amount = parseLongSafe(row[6]);
            double money = parseDoubleSafe(row[7]);
            double tax = parseDoubleSafe(row[8]);
            long ts = parseTime(row[1]);
            if (ts > 0) {
                fromTs = Math.min(fromTs, ts);
                toTs = Math.max(toTs, ts);
            }

            ShopRef ref = refById.get(shopId);
            ShopEntry entry = snap.get(String.valueOf(shopId));

            String material = "";
            if (entry != null && entry.material != null && !entry.material.isEmpty()) {
                material = entry.material;
            } else if (ref != null) {
                material = ref.material;
            }
            String item;
            if (entry != null && entry.shop_cn_name != null && !entry.shop_cn_name.isEmpty()) {
                item = entry.shop_cn_name;
            } else if (!material.isEmpty()) {
                item = material;
            } else {
                item = "商店 #" + shopId;
            }

            long perStack = 1;
            if (entry != null && entry.stacking_amount > 0) perStack = entry.stacking_amount;
            else if (ref != null && ref.count > 0) perStack = ref.count;

            String owner = "";
            if (entry != null && entry.owner_name != null && !entry.owner_name.isEmpty()) {
                owner = entry.owner_name;
            } else if (ref != null && ref.ownerUuid != null && !ref.ownerUuid.isEmpty()) {
                owner = resolveName(ref.ownerUuid, nameByUuid);
            }

            JsonObject t = new JsonObject();
            t.addProperty("t", ts);
            t.addProperty("type", isBuy ? "BUY" : "SELL");
            t.addProperty("source", "history");
            t.addProperty("history_id", id);
            t.addProperty("shop_id", String.valueOf(shopId));
            t.addProperty("item", item);
            if (!material.isEmpty()) t.addProperty("material", material.split("\\|")[0]);
            t.addProperty("amount", amount);
            t.addProperty("items", amount * Math.max(1, perStack));
            t.addProperty("unit_price", amount > 0 ? Math.round(money / amount * 100.0) / 100.0 : money);
            t.addProperty("total", Math.round(money * 100.0) / 100.0);
            t.addProperty("tax", Math.round(tax * 100.0) / 100.0);
            t.addProperty("player", resolveName(buyer, nameByUuid));
            if (!owner.isEmpty()) t.addProperty("owner", owner);
            t.addProperty("online", false);
            batch.add(t);
        }

        // ---- 7) 批量写入 + 更新进度 ----
        if (!batch.isEmpty()) {
            plugin.store().addTrades(batch);
        }
        if (newMax > maxId) {
            plugin.store().setSetting(SETTING_KEY, new JsonPrimitive(newMax), "number");
        }

        // ---- 7.5) 移除商店记录（log_others.csv 的 ShopRemoveLog） ----
        long removalsMaxId = readMaxId(REMOVALS_KEY);
        long newRemovalsMax = removalsMaxId;
        List<JsonObject> removalsBatch = new ArrayList<>();
        if (!otherRows.isEmpty()) {
            String[] head = otherRows.get(0);
            int iId = idx(head, "ID"), iTime = idx(head, "TIME"), iType = idx(head, "TYPE"), iData = idx(head, "DATA");
            List<JsonObject> removalsList = new ArrayList<>();
            Map<String, JsonObject> lastByPos = new HashMap<>();
            for (int i = 1; i < otherRows.size(); i++) {
                String[] r = otherRows.get(i);
                if (r.length <= Math.max(iData, Math.max(iId, iTime))) continue;
                long id;
                try {
                    id = Long.parseLong(r[iId].trim());
                } catch (Exception e) {
                    continue;
                }
                if (id > newRemovalsMax) newRemovalsMax = id;
                String type = r[iType] == null ? "" : r[iType].trim();
                if (!type.endsWith("ShopRemoveLog")) continue;
                if (id <= removalsMaxId) {
                    removalsSkipped++;
                    continue;
                }
                JsonObject data;
                try {
                    data = JsonParser.parseString(r[iData]).getAsJsonObject();
                } catch (Exception e) {
                    continue;
                }
                String player = optString(data, "player");
                String reason = optString(data, "reason");
                JsonObject shop = data.has("shop") && data.get("shop").isJsonObject()
                        ? data.getAsJsonObject("shop") : null;
                String world = "";
                long px = 0, py = 0, pz = 0;
                if (shop != null && shop.has("position") && shop.get("position").isJsonObject()) {
                    JsonObject pos = shop.getAsJsonObject("position");
                    world = optString(pos, "world");
                    px = optLong(pos, "x");
                    py = optLong(pos, "y");
                    pz = optLong(pos, "z");
                }
                // 同一删除会产生多条记录（silentremove + 系统 Shop removed，甚至连续几秒的重复命令），
                // 按 位置 + 10 秒时间窗 合并为一条
                String posKey = world + "|" + px + "|" + py + "|" + pz;
                long ts = parseTime(r[iTime]);
                JsonObject cur = lastByPos.get(posKey);
                if (cur != null) {
                    long curTs = cur.has("t") ? cur.get("t").getAsLong() : 0;
                    if (ts - curTs > 10000L) cur = null; // 超过时间窗视为新的删除记录
                }
                boolean isUser = !player.isEmpty() && !player.startsWith("[")
                        && !player.equalsIgnoreCase("system") && !player.equalsIgnoreCase("console");
                if (cur == null) {
                    cur = new JsonObject();
                    cur.addProperty("t", ts);
                    cur.addProperty("source", "history");
                    cur.addProperty("player", cleanPlayer(player, nameByUuid));
                    if (!reason.isEmpty()) cur.addProperty("reason", reason);
                    if (shop != null) {
                        String ownerUuid = optString(shop, "owner");
                        if (!ownerUuid.isEmpty()) {
                            cur.addProperty("owner_uuid", ownerUuid);
                            cur.addProperty("owner", resolveName(ownerUuid, nameByUuid));
                        }
                        String yaml = optString(shop, "item");
                        Matcher m = ITEM_ID.matcher(yaml);
                        String mat = m.find() ? normalizeMaterial(m.group(1)) : "";
                        cur.addProperty("item", mat.isEmpty() ? "未知物品" : Materials.cn(mat));
                        if (!mat.isEmpty()) cur.addProperty("material", mat);
                        cur.addProperty("price", Math.round(optDouble(shop, "price") * 100.0) / 100.0);
                    }
                    if (!world.isEmpty()) {
                        cur.addProperty("world", world);
                        cur.addProperty("x", px);
                        cur.addProperty("y", py);
                        cur.addProperty("z", pz);
                    }
                    removalsList.add(cur);
                    lastByPos.put(posKey, cur);
                } else if (isUser) {
                    // 优先保留带操作者的记录
                    String curPlayer = cur.has("player") ? cur.get("player").getAsString() : "";
                    boolean curIsUser = !curPlayer.isEmpty() && !curPlayer.startsWith("[") && !"系统".equals(curPlayer);
                    if (!curIsUser) {
                        cur.addProperty("player", cleanPlayer(player, nameByUuid));
                        if (!reason.isEmpty()) cur.addProperty("reason", reason);
                    }
                }
            }
            removalsBatch.addAll(removalsList);
        }

        // ---- 7.6) 交易日志中的删除记录（批量命令不写 ShopRemoveLog）----
        // 同一次删除若已由 ShopRemoveLog 记录（±5 秒内），跳过避免重复
        for (JsonObject d : pendingTradeDeletes) {
            long dt = d.has("t") ? d.get("t").getAsLong() : 0;
            boolean dup = false;
            for (JsonObject x : removalsBatch) {
                long xt = x.has("t") ? x.get("t").getAsLong() : 0;
                if (dt > 0 && xt > 0 && Math.abs(xt - dt) <= 5000L) {
                    dup = true;
                    break;
                }
            }
            if (!dup) removalsBatch.add(d);
        }
        if (!removalsBatch.isEmpty()) {
            plugin.store().addRemovals(removalsBatch);
        }
        if (newRemovalsMax > removalsMaxId) {
            plugin.store().setSetting(REMOVALS_KEY, new JsonPrimitive(newRemovalsMax), "number");
        }

        JsonObject o = new JsonObject();
        o.addProperty("success", true);
        o.addProperty("zip", zip.getName());
        o.addProperty("fresh", fresh);
        o.addProperty("exported", dispatched);
        o.addProperty("imported", batch.size());
        o.addProperty("skipped", skipped);
        o.addProperty("purchase_total", purchases + skipped);
        o.addProperty("removals_imported", removalsBatch.size());
        o.addProperty("removals_skipped", removalsSkipped);
        o.addProperty("shops_known", snap.size());
        if (fromTs != Long.MAX_VALUE && toTs > 0) {
            o.addProperty("from", fromTs);
            o.addProperty("to", toTs);
        }
        if (batch.isEmpty() && purchases == 0) {
            o.addProperty("message", "没有新的历史交易需要导入");
        }
        return o;
    }

    // ============================================================
    // QuickShop 定位 / 导出文件等待
    // ============================================================

    private Plugin findQuickShop() {
        for (String n : new String[]{"QuickShop-Hikari", "QuickShop"}) {
            Plugin p = Bukkit.getPluginManager().getPlugin(n);
            if (p != null) return p;
        }
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p.getName() != null && p.getName().toLowerCase(Locale.ROOT).contains("quickshop")) return p;
        }
        return null;
    }

    /** 轮询等待新的 export-*.zip；超时后返回最新的可用导出文件（可能为 null） */
    private File waitForExport(File dataFolder, long since, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        File fallback = null;
        while (true) {
            File[] files = dataFolder.listFiles((d, n) -> n.startsWith("export-") && n.endsWith(".zip"));
            if (files != null && files.length > 0) {
                List<File> sorted = new ArrayList<>();
                for (File f : files) sorted.add(f);
                sorted.sort(Comparator.comparingLong(this::tsOf));
                File latest = sorted.get(sorted.size() - 1);
                // 优先：本轮 dispatch 之后生成的新导出
                for (int i = sorted.size() - 1; i >= 0; i--) {
                    File f = sorted.get(i);
                    if (tsOf(f) >= since - 3000L) {
                        if (isUsable(f)) return f;
                        break; // 新文件可能还在写入，继续等
                    }
                }
                if (isUsable(latest)) fallback = latest;
            }
            if (System.currentTimeMillis() >= deadline) break;
            try {
                Thread.sleep(400L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return fallback;
    }

    private boolean isUsable(File f) {
        if (f == null || f.length() <= 0) return false;
        try (ZipFile zf = new ZipFile(f)) {
            return zf.getEntry("log_purchase.csv") != null;
        } catch (Exception e) {
            return false;
        }
    }

    private long tsOf(File f) {
        String n = f.getName();
        try {
            int a = n.indexOf('-');
            int b = n.lastIndexOf('.');
            if (a > 0 && b > a) return Long.parseLong(n.substring(a + 1, b));
        } catch (Exception ignored) {
        }
        return f.lastModified();
    }

    // ============================================================
    // CSV 解析
    // ============================================================

    /** 解析标准 CSV（支持引号转义、字段内换行、CRLF） */
    static List<String[]> parseCsv(String text) {
        List<String[]> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) return rows;
        List<String> cur = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQ) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQ = false;
                    }
                } else {
                    field.append(c);
                }
            } else {
                if (c == '"') inQ = true;
                else if (c == ',') {
                    cur.add(field.toString());
                    field.setLength(0);
                } else if (c == '\n') {
                    cur.add(field.toString());
                    field.setLength(0);
                    rows.add(cur.toArray(new String[0]));
                    cur.clear();
                } else if (c != '\r') {
                    field.append(c);
                }
            }
        }
        if (field.length() > 0 || !cur.isEmpty()) {
            cur.add(field.toString());
            rows.add(cur.toArray(new String[0]));
        }
        return rows;
    }

    private void parsePlayers(String csv, Map<String, String> out) {
        List<String[]> rows = parseCsv(csv);
        if (rows.size() < 2) return;
        String[] head = rows.get(0);
        int iUuid = idx(head, "UUID"), iName = idx(head, "CACHEDNAME");
        if (iUuid < 0 || iName < 0) return;
        for (int i = 1; i < rows.size(); i++) {
            String[] r = rows.get(i);
            if (r.length <= Math.max(iUuid, iName)) continue;
            String u = r[iUuid].trim().toLowerCase(Locale.ROOT);
            String n = r[iName].trim();
            if (!u.isEmpty() && !n.isEmpty()) out.put(u, n);
        }
    }

    /** data.csv：id -> {material, count, ownerUuid} */
    private void parseData(String csv, Map<Integer, ShopRef> out) {
        List<String[]> rows = parseCsv(csv);
        if (rows.size() < 2) return;
        String[] head = rows.get(0);
        int iId = idx(head, "ID"), iOwner = idx(head, "OWNER"), iItem = idx(head, "ITEM");
        if (iId < 0 || iItem < 0) return;
        for (int i = 1; i < rows.size(); i++) {
            String[] r = rows.get(i);
            if (r.length <= iId || r.length <= iItem) continue;
            int id;
            try {
                id = Integer.parseInt(r[iId].trim());
            } catch (Exception e) {
                continue;
            }
            String yaml = r[iItem];
            String mat = "";
            Matcher m = ITEM_ID.matcher(yaml);
            if (m.find()) mat = m.group(1);
            long count = 1;
            Matcher c = ITEM_COUNT.matcher(yaml);
            if (c.find()) {
                try {
                    count = Long.parseLong(c.group(1));
                } catch (Exception ignored) {
                }
            }
            ShopRef ref = new ShopRef();
            ref.material = normalizeMaterial(mat);
            ref.count = Math.max(1L, count);
            ref.ownerUuid = iOwner >= 0 && r.length > iOwner ? r[iOwner].trim() : "";
            out.put(id, ref);
        }
    }

    /** "minecraft:cobblestone" -> "COBBLESTONE"（与本站商店快照的 material 格式一致） */
    private String normalizeMaterial(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String m = raw.trim();
        int colon = m.indexOf(':');
        if (colon >= 0) m = m.substring(colon + 1);
        return m.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private int idx(String[] head, String name) {
        for (int i = 0; i < head.length; i++) {
            if (head[i] != null && head[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    // ============================================================
    // 小工具
    // ============================================================

    private String resolveName(String uuidOrName, Map<String, String> names) {
        if (uuidOrName == null) return "";
        String v = uuidOrName.trim();
        if (v.isEmpty()) return "";
        String key = v.toLowerCase(Locale.ROOT);
        String n = names.get(key);
        if (n != null && !n.isEmpty()) return n;
        if (key.length() == 36 && key.indexOf('-') > 0) return key.substring(0, 8);
        return v;
    }

    /** 日志中的操作者：UUID → 玩家名；[SYSTEM]/空 → 系统 */
    private String cleanPlayer(String raw, Map<String, String> nameByUuid) {
        if (raw == null || raw.isEmpty()) return "";
        String v = raw.trim();
        if ("[SYSTEM]".equalsIgnoreCase(v)) return "系统";
        if (v.length() == 36 && v.indexOf('-') > 0) return resolveName(v, nameByUuid);
        return v;
    }

    private String optString(JsonObject o, String key) {
        try {
            return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private long optLong(JsonObject o, String key) {
        try {
            return o != null && o.has(key) ? o.get(key).getAsLong() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private double optDouble(JsonObject o, String key) {
        try {
            return o != null && o.has(key) ? o.get(key).getAsDouble() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseTime(String s) {
        if (s == null) return 0;
        String v = s.trim();
        try {
            LocalDateTime dt = LocalDateTime.parse(v, TIME_FMT);
            return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            try {
                return Long.parseLong(v);
            } catch (Exception e2) {
                return 0;
            }
        }
    }

    private long parseLongSafe(String s) {
        try {
            return (long) Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private double parseDoubleSafe(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private String readEntry(ZipFile zf, String name) throws Exception {
        ZipEntry e = zf.getEntry(name);
        if (e == null) return null;
        try (InputStream in = zf.getInputStream(e)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(65536);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private long readMaxId(String key) {
        try {
            JsonObject s = plugin.store().getSetting(key);
            if (s != null && s.has("value") && s.get("value").isJsonPrimitive()) {
                return s.get("value").getAsLong();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private JsonObject fail(String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("success", false);
        o.addProperty("error", msg);
        return o;
    }

    /** 商店物品引用（来自导出包 data.csv） */
    private static final class ShopRef {
        String material = "";
        long count = 1;
        String ownerUuid = "";
    }
}
