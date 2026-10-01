package com.qshop.webui.data;

/**
 * 商店数据模型——字段名与前端期望的 JSON 完全一致（snake_case）。
 */
public final class ShopEntry {

    public String shop_id;
    public String material;
    public String item_name;
    public String owner_name;
    public String owner_uuid;
    public String world;
    public int x;
    public int y;
    public int z;
    public double price;
    public int stacking_amount = 1;
    public String shop_type = "SELLING";
    public boolean price_reasonable = true;
    public String nbt;
    /** 系统商店 -1（无限）；玩家商店 null（未知） */
    public Integer quantity = null;
    public int activity_score = 0;
    public String fetched_at;
    public String updated_at;

    // ==== 响应附加字段（与原后端一致） ====
    public String shop_cn_name;
    public String item_image;
    public String price_display;
    public double price_raw;
    /** 系统（管理员）商店标记；普通商店为 null */
    public Boolean is_system_shop;

    // ==== 内部字段（不序列化） ====
    public transient boolean system_shop;
    public transient long created_at_ms;
    public transient int views;

    public boolean isSelling() {
        return "SELLING".equalsIgnoreCase(shop_type);
    }

    public boolean isBuying() {
        return "BUYING".equalsIgnoreCase(shop_type);
    }
}
