# 扩展 API

QShopWebUI 提供两层扩展能力：

1. **公开 Java API（`QShopWebUIAPI`）** —— 供其他插件开发附属插件。
2. **HTTP API** —— 供前端、脚本或外部系统调用。

---

## 一、公开 Java API（开发附属插件）

### 获取实例

```java
import cn.aqcraft.api.QShopWebUIAPI;
import cn.aqcraft.QShopWebUIPlugin;

QShopWebUIAPI api =
    ((QShopWebUIPlugin) Bukkit.getPluginManager().getPlugin("QShopWebUI")).getAPI();
```

把 `QShopWebUI-<ver>.jar` 放进你插件 `pom.xml` 的依赖，并在 `plugin.yml`
里 `depend: [QShopWebUI]`（或 `softdepend`）即可。

### 接口方法

| 方法 | 返回 | 说明 |
|------|------|------|
| `shop(String shopId)` | `JsonObject` | 商店快照；不存在返回 null |
| `shopsByOwner(String name)` | `List<JsonObject>` | 某店主名下商店 |
| `shopsByMaterial(String material)` | `List<JsonObject>` | 某材质商店 |
| `compare(String material)` | `JsonObject` | 同物品比价结果 |
| `recordOrder(key,type,status,shopId,item,material,amount,items,unit,total,player,owner,online,reason)` | `JsonObject` | 写入订单流水（幂等） |
| `ordersByPlayer(player,type,limit,offset)` | `List<JsonObject>` | 玩家订单 |
| `order(String orderId)` | `JsonObject` | 单笔订单 |
| `isFavorite / addFavorite / removeFavorite` | `boolean/void` | 收藏管理 |
| `favoriteIds(player)` | `List<String>` | 玩家收藏的商店 ID |
| `isOpen()` / `isMaintenance()` | `boolean` | 营业状态 |
| `businessStats(int days)` | `JsonArray` | 按天经营统计 |
| `businessTotals()` | `JsonObject` | 累计总量 |
| `audit(actor,action,detail)` | `void` | 写审计日志 |
| `purchase(shopId,player,amount)` | `JsonObject` | 程序化购买（跳过验证码，信任调用方） |
| `sell(shopId,player,amount)` | `JsonObject` | 程序化出售 |

### 注意

- 所有返回均为 Gson `JsonObject`/`JsonArray`，字段与 HTTP API 返回一致。
- `purchase`/`sell` 跳过游戏内验证码校验：调用方必须已自行校验玩家身份。
- 扩展插件需要与主插件同 JVM（常规插件均为同一服务器 JVM，天然满足）。

---

## 二、HTTP API

基础 URL：`http://<host>:<port>/api/...`。管理端端点需要 `requireAuth` 开启时
携带会话 Cookie；玩家自助端点使用 `player + code`（游戏内 `/qshopwebui code`
生成的一次性验证码）鉴权。

### 订单

| 端点 | 方法 | 鉴权 | 说明 |
|------|------|------|------|
| `/api/orders?type=&player=&limit=&offset=` | GET | 管理 | 全部订单分页（时间倒序） |
| `/api/orders/me?player=&code=&type=&limit=&offset=` | GET | 玩家 | 我的订单 |
| `/api/orders/{id}` | GET | 管理 | 单笔订单 |
| `/api/orders/clear` | POST | 管理 | 清空订单 |

### 比价

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/compare?material=minecraft:diamond` | GET | 同物品比价 |
| `/api/compare/all` | GET | 全物品聚合（最低卖出/最高收购） |

### 收藏

| 端点 | 方法 | 鉴权 | 说明 |
|------|------|------|------|
| `/api/favorites?player=&code=` | GET | 玩家 | 收藏列表 |
| `/api/favorites/toggle` | POST | 玩家 | body: `{player,code,shop_id}` |

### 经营统计 / 审计

| 端点 | 方法 | 鉴权 | 说明 |
|------|------|------|------|
| `/api/stats/business?days=30` | GET | 公开 | 经营统计仪表盘 |
| `/api/audit?limit=&offset=` | GET | 管理 | 审计日志 |
| `/api/audit/clear` | POST | 管理 | 清空审计 |

### 营业状态 / 监控 / 通知

| 端点 | 方法 | 鉴权 | 说明 |
|------|------|------|------|
| `/api/status` | GET | 公开 | 营业状态 |
| `/api/status/open` | POST | 公开 | body: `{open:true/false}` 开/关业 |
| `/api/status/maintenance` | POST | 管理 | body: `{on:true/false}` 维护模式 |
| `/api/monitor/stock?live=true` | GET | 公开 | 库存告警；`live=true` 实时读容器 |
| `/api/notify/status` | GET | 公开 | 通知是否已配置 |

---

## 版本兼容性

- Java API 为稳定契约：方法签名向后兼容，底层实现可演进。
- 建议在扩展插件里对 `getAPI()` 判空（主插件可能未加载）。