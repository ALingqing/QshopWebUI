# QShopWebUI 文档

QShopWebUI 是一个运行于 Paper 服务端的商店网页系统插件：直接读取
QuickShop-Hikari 商店数据，内嵌 Web 服务器（支持与 Minecraft 协议共享同一
TCP 端口分流），并提供一套公开扩展 API 供其他插件开发附属。

- [功能清单](FEATURES.md) —— P0/P1/P2 全部功能说明
- [扩展 API](API.md) —— 供其他插件开发附属的 Java API + HTTP API 文档
- [部署 / 配置](DEPLOY.md) —— 安装、配置文件、命令、发行说明

## 目录速览

| 章节 | 说明 |
|------|------|
| FEATURES.md | 订单流水、玩家订单、库存提醒、比价收藏、批量审计、经营统计、营业状态、通知 |
| API.md | `QShopWebUIAPI` 公开接口 + HTTP 端点 |
| DEPLOY.md | 部署、config.yml 全字段、命令、构建、发行 |

## 项目结构（核心）

```
src/main/java/cn/aqcraft/
├── QShopWebUIPlugin.java     # 主类，暴露 getAPI()
├── PluginConfig.java         # 全部配置项
├── api/
│   ├── ApiRouter.java        # HTTP 路由分发
│   ├── EnhancedApi.java      # P1/P2 增强 HTTP 端点
│   ├── QShopWebUIAPI.java    # 公开扩展 API 接口（其他插件用）
│   └── QShopWebUIImpl.java   # 接口实现
├── order/OrderStore.java     # 订单流水存储
├── service/                  # 比价、审计、营业状态、库存提醒、经营统计
├── notify/NotificationService.java  # 企业微信 webhook 通知
└── data/FavoritesStore.java  # 收藏存储
```

webroot/ 打包进 jar 的 `/web/`，是原生 HTML/CSS/JS 前端（无外部依赖）。