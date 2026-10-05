// 制作「清屿服务器示例」压缩包文件夹 → 桌面\新建文件夹\清屿服务器示例
import fs from 'fs';
import path from 'path';

const root = path.resolve(import.meta.dirname, '..');
const dst = 'C:/Users/aqing/Desktop/新建文件夹/清屿服务器示例';
fs.rmSync(dst, { recursive: true, force: true });
fs.mkdirSync(dst, { recursive: true });

// 1. jar
const jarSrc = path.join(root, 'target', 'QShopWebUI-1.0.1.jar');
fs.copyFileSync(jarSrc, path.join(dst, 'QShopWebUI-1.0.1.jar'));
console.log('✓ jar:', (fs.statSync(jarSrc).size / 1024 / 1024).toFixed(2), 'MB');

// 2. config.yml（预填清屿在线商店）
let cfg = fs.readFileSync(path.join(root, 'src', 'main', 'resources', 'config.yml'), 'utf8');
cfg = cfg.replace('server-name: ""', 'server-name: "清屿在线商店"');
cfg = cfg.replace('server-subtitle: ""', 'server-subtitle: "游戏购买"');
fs.writeFileSync(path.join(dst, 'config.yml'), cfg, 'utf8');
console.log('✓ config.yml（server-name: 清屿在线商店 / server-subtitle: 游戏购买）');

// 2.5 web/index.html 定制覆盖（磁盘优先于 jar 内置页面）
let html = fs.readFileSync(path.join(root, 'webroot', 'index.html'), 'utf8');
html = html.replace('<title>Qshop — Minecraft 商店管理系统</title>', '<title>清屿在线商店</title>');
html = html.replace('<div class="loader-title">Qshop WebUI</div>', '<div class="loader-title">清屿在线商店</div>');
html = html.replace('<div class="topnav-logo">Qshop WebUI</div>', '<div class="topnav-logo">清屿在线商店</div>');
fs.mkdirSync(path.join(dst, 'web'), { recursive: true });
fs.writeFileSync(path.join(dst, 'web', 'index.html'), html, 'utf8');
console.log('✓ web/index.html（定制版首页：标题/Logo = 清屿在线商店）');

// 3. README
fs.copyFileSync(path.join(root, 'README.md'), path.join(dst, 'README.md'));
console.log('✓ README.md');

// 4. 安装说明
const readmeTxt = `========================================
 QShopWebUI · 清屿服务器 示例包
========================================

【这是什么？】
QuickShop-Hikari 商店的网页系统（Paper 插件）：
- 玩家：浏览全服商店、搜索价格、在线购买、在线收购（把物品卖给收购商店）
- 管理员：网页后台管理商店（批量改价/删除）、公告、备份、页面可见性等
- 网页直接读取游戏内 QuickShop 数据，无需数据库，单端口即可运行

【文件说明】
  QShopWebUI-1.0.1.jar  插件本体（放进服务器 plugins 文件夹）
  config.yml            已填好「清屿在线商店」的配置示例
  web/index.html        定制版首页（标题/Logo = 清屿在线商店，磁盘覆盖用）
  README.md             完整功能与配置文档

【安装步骤】
1. 要求：服务器的 Java 17+，已安装 QuickShop-Hikari
   （在线购买/收购还需要 Vault + 经济插件，如 EssentialsX）
2. 把 QShopWebUI-1.0.1.jar 放进服务器 plugins/ 目录，重启服务器
3. 首次启动会生成 plugins/QShopWebUI/config.yml
   （想用「清屿在线商店」名称：把本包里的 config.yml 复制过去覆盖即可）
4. 【可选】自定义网站：把本包的 web 文件夹（整个）复制到
   plugins/QShopWebUI/ 下，即 plugins/QShopWebUI/web/index.html
   →　浏览器标题/加载动画/导航 Logo 会直接显示「清屿在线商店」
5. 浏览器访问：http://服务器IP:20130/
6. 管理员默认账号 admin / admin123 → 登录后请尽快修改密码

【示例配置要点】（config.yml）
  server-name: "清屿在线商店"  网页标题/导航栏/首页大标题
  server-subtitle: "游戏购买"   首页大标题下方的副标题
  port: 20130                  网页端口（与游戏端口一致时自动单端口复用）
  mode: auto                   自动判断端口模式
  purchase.enabled: true       在线购买 + 在线收购 总开关
  purchase.max-amount: 64      单次最多交易份数
  pages.hide-*: false          页面可见性（也可在网页后台里切换）

【网页页面】
  首页          公告
  购买界面      所有出售商店 → 在线购买（玩家需在游戏中）
  收购界面      所有收购商店 → 把背包物品卖给它们（玩家需在游戏中）
  物品浏览      按物品聚合看全服价格
  商店浏览      全部商店总览
  信息统计      数据总览
  数据管理(管理) 页面可见性开关 / 实时统计 / 清空数据等
  公告管理(管理)、备份恢复(管理)、导入导出(管理)

【常用命令】
  /qshopwebui status      查看运行状态（端口/模式/QuickShop 连接）
  /qshopwebui reload      重载配置
  /qshopwebui port <端口>  快速修改网页端口

【在线交易规则】
  · 玩家必须正在游戏中才能购买/收购（网页不能操作离线玩家）
  · 购买：买家扣款 → 实物从商店箱子取出进背包（装不下掉落脚下）
  · 收购：物品从背包扣除进商店箱子 → 报酬到账
  · 玩家商店：买→钱给店主；卖→店主付款（店主余额不足会拒绝）
  · 系统商店（无限商店）：购买只扣买家；收购由系统无限付款

【备注】
  · 更新插件：替换 jar 后 /plugman reload QShopWebUI 或重启
  · 自定义网页外观：plugins/QShopWebUI/web/ 放同名文件会覆盖插件内置网页
    （本包已带 index.html 示例；还可以放 css/style.css、js/app.js 等）
  · 只想改名字不想动网页文件：只改 config.yml 的 server-name / server-subtitle 即可
  · 忘记管理员密码：删除 config.yml 中 admin.password 或直接改明文后 reload

打包时间：2026-10-01
`;
fs.writeFileSync(path.join(dst, '安装说明.txt'), readmeTxt, 'utf8');
console.log('✓ 安装说明.txt');

console.log('\n输出目录:', dst);
for (const f of fs.readdirSync(dst)) {
  console.log('  ', f, (fs.statSync(path.join(dst, f)).size / 1024).toFixed(1) + ' KB');
}
