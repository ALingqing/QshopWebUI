# 功能清单（P0 / P1 / P2）

所有功能均按 ROADMAP 实现，全部已接入 HTTP 前端（webroot）与公开扩展 API。

## P0 交易核心

| 功能 | 说明 | 服务 |
|------|------|------|
| 订单流水 | 每笔成功/失败交易写入独立订单流水（ORD-序号），独立于 trades.json | `OrderStore` |
| 玩家订单查询 | 玩家凭游戏内一次性验证码（或登录会话 token）查询自己的买卖记录 | `EnhancedApi.myOrders` |
| 玩家登录（免重复验证码） | 游戏内验证码 或 AuthMe 密码登录一次，TTL 内交易/查询免重复验证 | `PlayerAuthService` / `/api/player/login` |
| 网页购买 / 出售 | 玩家在线或允许离线时通过网页交易 | `PurchaseService` |
| 配置自动同步 | 启动/重载时把新版本新增的配置键追加到旧 config.yml（保留已有值与注释，只增不改） | `ConfigMerger` |

## P1 经营增强

| 功能 | 说明 | 服务 |
|------|------|------|
| 库存提醒 | 出售店低库存/售罄、收购店收购上限告警；支持实时读取容器（live） | `StockAlertService` |
| 比价 | 同物品各商店价格排序、最优价、均价 | `ComparisonService` |
| 收藏 | 玩家收藏常用商店，一键查看 | `FavoritesStore` |
| 批量预览 + 审计 | 管理端审计日志（actor/action/detail），带清空 | `AuditService` |
| 熔断 | 交易连续失败自动熔断，防止恶意刷失败 | `ShopStatusService` |

## P2 运营能力

| 功能 | 说明 | 服务 |
|------|------|------|
| 经营统计 | 按天汇总买/卖金额、数量、订单数，仪表盘 | `BusinessStatsService` |
| 营业状态 | 开/关业、维护模式、隐藏商店、关闭原因 | `ShopStatusService` |
| 通知 | 交易/订单实时推送到企业微信 webhook | `NotificationService` |

## HTTP 端点（前端已接入）

- 订单流水：`/api/orders`（管理）、`/api/orders/me`（玩家）、`/api/orders/{id}`、`/api/orders/clear`
- 比价：`/api/compare`、`/api/compare/all`
- 收藏：`/api/favorites`、`/api/favorites/toggle`
- 统计：`/api/stats/business`
- 审计：`/api/audit`、`/api/audit/clear`
- 营业：`/api/status`、`/api/status/open`、`/api/status/maintenance`
- 库存监控：`/api/monitor/stock`
- 通知：`/api/notify/status`

详见 [API.md](API.md)。