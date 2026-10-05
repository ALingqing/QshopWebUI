# 部署 / 配置 / 构建 / 发行

## 一、环境

- Java 17
- Paper / Spigot 1.17.1+（api-version 1.17）
- QuickShop-Hikari（依赖其数据）
- Maven 3.8+

## 二、安装

1. 把 `target/QShopWebUI-<ver>.jar` 放入 `plugins/`。
2. 重启服务器（或 `/reload`）。
3. 编辑 `plugins/QShopWebUI/config.yml` 设置管理员账号、端口等，再 `/qshopwebui reload`。

## 三、config.yml 字段

### 服务端

| 键 | 默认 | 说明 |
|----|------|------|
| `server.port` | 20130 | HTTP 监听端口 |
| `server.bind` | `0.0.0.0` | 绑定地址 |
| `server.mode` | `multiplex` | `multiplex` 与 MC 共用端口 / 独立端口 |
| `server.mc-host` / `server.mc-port` | 127.0.0.1 / 25565 | MC 后端地址 |
| `server.server-name` | 清屿 | 服务器名 |
| `server.server-subtitle` | — | 副标题 |
| `server.hidden-pages` | — | 隐藏页签 |
| `server.snapshot-ttl-ms` | — | 快照缓存 TTL |

### 鉴权

| 键 | 默认 | 说明 |
|----|------|------|
| `auth.admin-username` | admin | 管理员账号 |
| `auth.admin-password` | 随机 | 管理员密码（首次启动生成） |
| `auth.session-timeout` | 7200 | 会话超时秒 |
| `auth.require-auth` | true | 是否要求登录 |

### 购买

| 键 | 默认 | 说明 |
|----|------|------|
| `purchase.enabled` | true | 是否允许网页购买 |
| `purchase.max-amount` | 64 | 单次最大份数 |
| `purchase.allow-offline-buy` | false | 是否允许离线购买 |
| `purchase.game-code-ttl-seconds` | 300 | 验证码有效期 |
| `purchase.game-code-max-attempts` | 5 | 验证码最大尝试次数 |

### 商店

| 键 | 默认 | 说明 |
|----|------|------|
| `shop.player-shop-max-buy` | — | 玩家商店单次最大购买 |
| `shop.player-shop-max-stock` | — | 玩家商店最大库存 |
| `shop.player-shop-low-stock-threshold` | — | 低库存阈值 |
| `shop.stock-low-threshold` | 10 | 库存提醒低库存阈值 |
| `shop.alert-cooldown-ms` | 300000 | 库存提醒冷却 |
| `shop.circuit-breaker-threshold` | 8 | 交易失败熔断阈值 |
| `shop.circuit-breaker-duration-ms` | 300000 | 熔断持续时间 |
| `shop.order-max` | 50000 | 订单流水上限 |
| `shop.audit-max` | 5000 | 审计日志上限 |

### 通知

| 键 | 默认 | 说明 |
|----|------|------|
| `notifications.webhook-url` | 空 | 企业微信 webhook，非空才启用通知 |

## 四、命令

| 命令 | 权限 | 说明 |
|------|------|------|
| `/qshopwebui` | 默认 | 主命令 |
| `/qshopwebui code` | 所有玩家 | 生成游戏内一次性验证码（网页交易/查单/收藏用） |
| `/qshopwebui reload` | qshopwebui.reload | 重载配置与数据 |
| `/qshopwebui status` | qshopwebui.reload | 查看运行状态 |

## 五、构建

```bash
mvn clean package
```

产物：`target/QShopWebUI-<ver>.jar`。版本见 `pom.xml`（当前 `1.1.0-beta.1`），
需与 `plugin.yml` 的 `version` 保持同步（通过资源过滤自动完成）。

## 六、发行

1. 修改 `pom.xml` 版本号。
2. 提交并打 tag：`git tag vX.Y.Z && git push origin vX.Y.Z`。
3. CI 自动构建并发布 Release（见 `.github/workflows/release.yml`）。

## 七、常见问题

- **网页打开但登录失败**：检查 `auth.admin-username/password`，或 `/qshopwebui reload`。
- **订单不记录**：确认 `purchase.enabled`、且交易走的是本插件的购买流程。
- **通知没发**：`notifications.webhook-url` 留空则不启用；填错 URL 会每 60s 冷却告警。
- **数据目录**：`plugins/QShopWebUI/data/` 下存有订单（orders.json）、审计（audit.json）、
  经营统计（biz_stats.json）、营业状态（shop_status.json）、收藏（favorites.json）。