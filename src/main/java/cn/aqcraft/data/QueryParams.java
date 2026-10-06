package cn.aqcraft.data;

/** /api/shops 系列查询参数（由 API 层从 HttpRequest 填充） */
public final class QueryParams {
    public String keyword = "";
    public String material = "";
    public String shopType = "";
    public String owner = "";
    public String world = "";
    public Double minPrice = null;
    public Double maxPrice = null;
    public String sort = "";
    /** 商店类型筛选：""=全部，infinite=无限商店，system=管理员(系统)商店，player=玩家商店 */
    public String filter = "";
    public int page = 1;
    public int pageSize = 30;
    /** null = 未传（显示所有）；false = 仅显示价格合理的 */
    public Boolean showAll = null;
    /** 管理端高级筛选 */
    public Integer minActivity = null;
    public Boolean reasonableOnly = null;
}
