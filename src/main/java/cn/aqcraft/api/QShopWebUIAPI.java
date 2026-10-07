package cn.aqcraft.api;

import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * QShopWebUI 公开扩展 API。
 *
 * <p>其他插件可通过 {@code cn.aqcraft.api.QShopWebUIAPI api =
 * ((QShopWebUIPlugin) Bukkit.getPluginManager().getPlugin("QShopWebUI")).getAPI();}
 * 获取实例，用于查询商店、订单、比价、收藏、营业状态、执行购买等。</p>
 *
 * <p>该接口为稳定契约；底层实现可能随版本演进，但方法签名保持向后兼容。</p>
 */
public interface QShopWebUIAPI {

    /**
     * 查询某个商店的快照信息。
     *
     * @param shopId QuickShop 商店 ID
     * @return 商店信息 JSON；不存在返回 null
     */
    JsonObject shop(String shopId);

    /**
     * 查询某玩家名下的全部商店。
     *
     * @param ownerName 店主名（不区分大小写）
     * @return 商店列表 JSON
     */
    List<JsonObject> shopsByOwner(String ownerName);

    /**
     * 查询某种物品当前可用的商店（出售+收购）。
     *
     * @param material 物品材质（如 "minecraft:diamond"）
     * @return 商店列表 JSON
     */
    List<JsonObject> shopsByMaterial(String material);

    /**
     * 同物品比价。
     *
     * @param material 物品材质
     * @return 比价结果 JSON：{sellers:[...], buyers:[...], lowest_sell, highest_buy}
     */
    JsonObject compare(String material);

    /**
     * 记录一条订单（供其他插件把交易写入 QShopWebUI 订单流水）。
     *
     * @param idempotencyKey 幂等键；同键重复调用返回已有订单而非重复记账，可传 null
     * @param type           BUY / SELL
     * @param status         SUCCESS / FAILED / CANCELLED
     * @param shopId         商店 ID
     * @param itemName       物品显示名
     * @param material       物品材质
     * @param amount         份数
     * @param items          物品件数
     * @param unitPrice      单价
     * @param total          总额
     * @param player         交易玩家
     * @param owner          店主
     * @param online         玩家是否在线
     * @param reason         备注（失败原因等，可 null）
     * @return 订单对象
     */
    JsonObject recordOrder(String idempotencyKey, String type, String status,
                           String shopId, String itemName, String material,
                           int amount, long items, double unitPrice, double total,
                           String player, String owner, boolean online, String reason);

    /**
     * 查询某玩家的订单流水。
     *
     * @param player 玩家名
     * @param type   BUY/SELL，可传 null 表示全部
     * @param limit  条数上限
     * @param offset 偏移
     * @return 订单列表（时间倒序）
     */
    List<JsonObject> ordersByPlayer(String player, String type, int limit, int offset);

    /**
     * 按订单号查单笔订单。
     *
     * @param orderId 订单号（如 ORD-123）
     * @return 订单 JSON；不存在返回 null
     */
    JsonObject order(String orderId);

    /**
     * 判断某商店是否已被某玩家收藏。
     */
    boolean isFavorite(String player, String shopId);

    /**
     * 添加收藏。
     */
    void addFavorite(String player, String shopId);

    /**
     * 移除收藏。
     */
    void removeFavorite(String player, String shopId);

    /**
     * 查询玩家收藏的商店列表。
     *
     * @return 商店 ID 列表
     */
    List<String> favoriteIds(String player);

    /**
     * 商店当前是否营业（含维护模式判断）。
     */
    boolean isOpen();

    /**
     * 是否处于维护模式。
     */
    boolean isMaintenance();

    /**
     * 经营统计：最近 daysN 天。
     *
     * @return 按日期数组；每项含 buy/sell 的 amount、total、orders
     */
    com.google.gson.JsonArray businessStats(int daysN);

    /**
     * 经营统计：累计总量。
     */
    JsonObject businessTotals();

    /**
     * 写入一条审计日志。
     *
     * @param actor  操作者（如 "admin"、"web"）
     * @param action 动作标识
     * @param detail 详情
     */
    void audit(String actor, String action, String detail);

    /**
     * 执行一次在线购买（等价于网页购买，需要经济余额；玩家须在线或允许离线购买）。
     *
     * @param shopId   商店 ID
     * @param playerName 玩家名
     * @param amount   份数
     * @return 交易结果 JSON（success 字段标记是否成功）
     */
    JsonObject purchase(String shopId, String playerName, int amount);

    /**
     * 执行一次在线出售（玩家出售给收购商店）。
     *
     * @param shopId   商店 ID
     * @param playerName 玩家名
     * @param amount   份数
     * @return 交易结果 JSON
     */
    JsonObject sell(String shopId, String playerName, int amount_minor);
}