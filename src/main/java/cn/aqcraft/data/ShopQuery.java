package cn.aqcraft.data;

import java.util.ArrayList;
import java.util.List;

/** 商店筛选 / 排序 / 分页（与原 Node 后端 queryShops 行为对齐） */
public final class ShopQuery {

    public static final class Result {
        public final List<ShopEntry> shops;
        public final int total;
        public final int page;
        public final int limit;
        public final int totalPages;

        Result(List<ShopEntry> shops, int total, int page, int limit) {
            this.shops = shops;
            this.total = total;
            this.page = page;
            this.limit = limit;
            this.totalPages = Math.max(1, (int) Math.ceil(total / (double) limit));
        }
    }

    private ShopQuery() {
    }

    public static Result query(List<ShopEntry> all, QueryParams p) {
        String kw = p.keyword == null ? "" : p.keyword.trim().toLowerCase(java.util.Locale.ROOT);
        String mat = p.material == null || p.material.isEmpty() ? "" : p.material.trim().toUpperCase(java.util.Locale.ROOT);
        String st = p.shopType == null || p.shopType.isEmpty() ? "" : p.shopType.trim().toUpperCase(java.util.Locale.ROOT);
        String ow = p.owner == null || p.owner.isEmpty() ? "" : p.owner.trim().toLowerCase(java.util.Locale.ROOT);
        String wd = p.world == null ? "" : p.world.trim();
        String flt = p.filter == null ? "" : p.filter.trim().toLowerCase(java.util.Locale.ROOT);

        List<ShopEntry> results = new ArrayList<>(all.size());
        for (ShopEntry s : all) {
            if (!st.isEmpty() && !st.equalsIgnoreCase(s.shop_type)) continue;
            if ("infinite".equals(flt) && !isInfinite(s)) continue;
            if ("system".equals(flt) && !isSystem(s)) continue;
            if ("player".equals(flt) && isSystem(s)) continue;
            if (!mat.isEmpty() && (s.material == null || !mat.equals(s.material.toUpperCase(java.util.Locale.ROOT)))) continue;
            if (!ow.isEmpty() && (s.owner_name == null || !s.owner_name.toLowerCase(java.util.Locale.ROOT).contains(ow))) continue;
            if (!wd.isEmpty() && (s.world == null || !s.world.contains(wd))) continue;
            if (p.minPrice != null && !(s.price >= p.minPrice)) continue;
            if (p.maxPrice != null && !(s.price <= p.maxPrice)) continue;
            if (p.minActivity != null && s.activity_score < p.minActivity) continue;
            if (!kw.isEmpty()) {
                boolean inItem = s.item_name != null && s.item_name.toLowerCase(java.util.Locale.ROOT).contains(kw);
                boolean inMat = s.material != null && s.material.toLowerCase(java.util.Locale.ROOT).contains(kw);
                boolean inOwner = s.owner_name != null && s.owner_name.toLowerCase(java.util.Locale.ROOT).contains(kw);
                // 拼音 / 首字母搜索（蜘蛛 → zhizhu / zz）
                if (!inItem && s.item_name != null) inItem = cn.aqcraft.util.Pinyin.matches(kw, s.item_name);
                if (!inItem && s.shop_cn_name != null) inItem = cn.aqcraft.util.Pinyin.matches(kw, s.shop_cn_name);
                if (!inItem && !inMat && !inOwner) continue;
            }
            results.add(s);
        }

        // show_all 显式 false → 仅价格合理；reasonableOnly 为管理端参数
        boolean filterReasonable = Boolean.FALSE.equals(p.showAll) || Boolean.TRUE.equals(p.reasonableOnly);
        if (filterReasonable) {
            List<ShopEntry> filtered = new ArrayList<>(results.size());
            for (ShopEntry s : results) if (s.price_reasonable) filtered.add(s);
            results = filtered;
        }

        // 排序
        String sort = p.sort == null ? "" : p.sort.toLowerCase(java.util.Locale.ROOT);
        switch (sort) {
            case "price_asc":
                results.sort((a, b) -> Double.compare(a.price, b.price));
                break;
            case "price_desc":
                results.sort((a, b) -> Double.compare(b.price, a.price));
                break;
            case "amount":
                results.sort((a, b) -> Integer.compare(b.stacking_amount, a.stacking_amount));
                break;
            case "shop_id":
                results.sort((a, b) -> Long.compare(parseId(a.shop_id), parseId(b.shop_id)));
                break;
            default:
                // ratio / 未知：保持原顺序（与原后端一致）
                break;
        }

        int total = results.size();
        int limit = Math.max(1, Math.min(p.pageSize <= 0 ? 30 : p.pageSize, 500));
        int page = Math.max(1, p.page);
        int start = (page - 1) * limit;
        List<ShopEntry> pageItems;
        if (start >= total) {
            pageItems = new ArrayList<>();
        } else {
            pageItems = new ArrayList<>(results.subList(start, Math.min(start + limit, total)));
        }
        return new Result(pageItems, total, page, limit);
    }

    /** 系统（管理员）商店 */
    private static boolean isSystem(ShopEntry s) {
        return s.system_shop || Boolean.TRUE.equals(s.is_system_shop);
    }

    /** 无限商店（无限库存 / 系统商店） */
    private static boolean isInfinite(ShopEntry s) {
        return s.system_shop || (s.quantity != null && s.quantity < 0);
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(String.valueOf(id));
        } catch (Exception e) {
            return 0;
        }
    }
}
