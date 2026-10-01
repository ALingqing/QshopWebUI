# QShopWebUI — QuickShop-Hikari 商店网页管理系统（Paper 插件）

把整站商店网页系统做成了一个 **单个 Paper 插件**：

- 🗄️ **直接读取 QuickShop-Hikari 数据**：商店列表 / 价格 / 店主 / 坐标 / 世界全部实时来自游戏内存，**不需要数据库、不需要额外服务**
- 🌐 **内嵌 Web 服务器**：丢进 `plugins/` 打开浏览器就能看商店
- 🔌 **单端口复用（看家本领）**：服务器只有 1 个端口也能同时开游戏和网页——插件自动识别「浏览器 HTTP 流量」与「Minecraft 游戏流量」并分流
- 🧩 **全版本尽力兼容**：Java 17+（MC 1.18 ~ 最新）；通过反射对接 QuickShop-Hikari（5.x / 6.x 均可），不绑定编译版本
- 🖥️ 保留原 Web 系统全部界面：物品浏览、商店浏览、信息统计、管理后台、公告、备份等

---

## 一、安装要求

| 项 | 要求 |
| --- | --- |
| 服务端 | Paper / Spigot **1.18+**（Java 17 及以上） |
| 前置插件 | [QuickShop-Hikari](https://modrinth.com/plugin/quickshop-hikari) |
| 插件本体 | `QShopWebUI-1.0.0.jar` |

> 插件为 **softdepend**：QuickShop 未安装时也能加载（页面显示空数据）。

---

## 二、快速开始

### 场景 A：服务器有独立端口给网页（最简单）

1. 把 jar 丢进 `plugins/`，启动服务器（会生成 `plugins/QShopWebUI/config.yml`）
2. 编辑配置：

```yaml
port: 23333        # 网页端口（面板放行这个端口）
mode: standalone   # 独立端口模式
```

3. `/qshopwebui reload` 或重启服务器
4. 浏览器访问 `http://服务器IP:23333/` —— 完成 ✅

### 场景 B：只有 1 个端口（被游戏占用）——单端口复用 ⭐

原理：让插件接管「玩家连接用的那个端口」，MC 本体改到一个**容器内部端口**上跑；
插件自动把 HTTP 流量留给自己、把游戏流量原样转发给 MC。

1. 先把 **MC 的 `server.properties` 里 `server-port` 改成另一个端口**（例如 `25565`），重启服务器
   （玩家连接地址**不变**，依然是原来的端口）
2. 编辑 `plugins/QShopWebUI/config.yml`：

```yaml
port: 20130        # 你对外使用的端口（=玩家连接游戏的端口，面板放行的那个）
mode: multiplex    # 单端口复用
mc-host: 127.0.0.1
mc-port: 0         # 0 = 自动读取服务器当前端口（即改了之后的 25565）
```

3. 重启服务器（插件会在 `20130` 上同时提供：游戏连接 + 网页）
4. 玩家连 `IP:20130` 正常游戏；浏览器访问 `http://IP:20130/` 打开网页 ✅

> ⚠️ 注意
> - Pterodactyl 等面板如果启动命令里写死了 `--port ${SERVER_PORT}`，需要把该变量对应的端口改成内部端口（例如 25565），否则 MC 会继续占用对外端口导致插件启动失败。日志会给出中文提示。
> - 插件承接了游戏流量转发（本机环回），对延迟影响极小；但请确保插件随服启动。

### 模式速查

| mode | 行为 |
| --- | --- |
| `standalone` | 插件只监听 `port` 提供网页，游戏走自己的端口 |
| `multiplex` | 插件监听 `port`，自动分流网页 / 游戏流量（MC 需移到内部端口） |
| `auto`（默认） | `port` 等于当前游戏端口 → 自动按 `multiplex`；否则 `standalone` |

启动失败（端口被占用）时控制台会提示处理办法。

---

## 三、配置说明（`plugins/QShopWebUI/config.yml`）

```yaml
port: 20130            # 监听端口
bind: 0.0.0.0          # 绑定网卡
mode: auto             # standalone / multiplex / auto
mc-host: 127.0.0.1     # multiplex 模式下游戏流量转发目标
mc-port: 0             # 0 = 自动

admin:
  username: admin      # 网页后台账号
  password: admin123   # 支持明文或 sha256:<hex>（网页改密码后自动写入）

session-timeout: 3600  # 登录有效期（秒）
require-auth: true     # 管理操作是否需要登录
web:
  max-page-size: 500
  default-page-size: 60
  max-body-size: 10485760
  access-log: false    # true = 控制台输出每个网页请求
  snapshot-ttl-ms: 5000 # QuickShop 数据快照缓存（毫秒）
debug: false
```

---

## 四、命令

```
/qshopwebui status         # 查看运行状态（端口 / 模式 / QuickShop 连接）
/qshopwebui reload         # 重新读取配置（改端口后无需重启）
/qshopwebui port <端口>     # 快速修改并重绑端口
```

权限：`qshopwebui.admin`（默认 OP）。

---

## 五、网页功能

- **首页 / 物品浏览 / 商店浏览 / 信息统计**：所有人可看
- **管理后台**（需登录，默认 `admin / admin123`）：
  - 商店管理：搜索、批量改价、批量改类型、删除（**直接作用于游戏内 QuickShop 商店**）
  - 公告管理、系统设置、备份/恢复（备份 Web 侧数据：设置 / 公告 / 访问计数 / 港口 / 用户）
  - 导出 CSV / JSON

> 插件版直接操作真实商店：**删除 / 改价会立即在游戏内生效**，请谨慎操作。
> 为防止误删，网页不提供「一键清空全部商店」。

---

## 六、构建

```bash
mvn package
# 产物: target/QShopWebUI-1.0.0.jar
```

- JDK 17+，Maven 3.6+
- 依赖：`spigot-api 1.17.1`（provided），编译期仅用基础 Bukkit API
- QuickShop-Hikari 通过**反射**调用，无需编译依赖

### 目录结构

```
paper-plugin/
├── pom.xml
├── src/main/java/com/qshop/webui/
│   ├── QShopWebUIPlugin.java      # 主类
│   ├── PluginConfig.java
│   ├── bridge/QuickShopBridge.java # QuickShop 反射桥（读数据 + 改价/删除）
│   ├── data/                       # 快照 / 查询 / 持久化（settings/公告/备份…）
│   ├── http/                       # 手写 HTTP + 端口嗅探 + MC 转发
│   └── api/                        # 全部 REST API
├── src/main/resources/
│   ├── plugin.yml / config.yml
│   └── material_zh_cn.json         # 物品中文名表（由官方语言文件生成）
└── webroot/                        # Web 前端（打包进 jar）
```

---

## 七、FAQ

**Q：页面一片空白 / MC 状态显示未连接？**
A：确认 QuickShop-Hikari 已安装并启用；执行 `/qshopwebui status` 查看连接状态。

**Q：密码提示 bcrypt 无法校验？**
A：从旧版迁移时密码可能是 bcrypt 格式。直接编辑 `config.yml` 把 `admin.password` 改回明文，reload 后再到网页里改密码（会自动存成 sha256）。

**Q：multiplex 模式启动失败（端口被占用）？**
A：说明 MC 仍在监听对外端口。把 `server.properties` 的 `server-port` 改成内部端口后重启。

**Q：能不能和网页面板（Dynmap 等）共存？**
A：可以，standalone 模式给不同端口即可。

---

## 八、许可

本插件为个人项目，与 QuickShop-Hikari 官方无关，未包含其代码（仅通过公开 API 反射交互）。
