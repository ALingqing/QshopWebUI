// 校验 QShopWebUI jar 内的购买功能资源
import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import os from 'os';

// 解压 zip：Windows 的 tar 是 bsdtar（支持 zip）；
// Linux/macOS 的 tar 是 GNU tar（不支持 zip），改用 unzip。
function extractZip(archive, dest) {
  const commands = process.platform === 'win32'
    ? [`tar -xf "${archive}" -C "${dest}"`]
    : [`unzip -q -o "${archive}" -d "${dest}"`, `tar -xf "${archive}" -C "${dest}"`];
  let lastError;
  for (const cmd of commands) {
    try {
      execSync(cmd, { stdio: 'pipe' });
      return;
    } catch (e) {
      lastError = e;
    }
  }
  throw lastError;
}

const root = path.resolve(import.meta.dirname, '..');
const targetDir = path.join(root, 'target');
const pickJar = () => {
  if (process.argv[2]) return path.resolve(process.argv[2]);
  const jars = fs.existsSync(targetDir)
    ? fs.readdirSync(targetDir).filter((f) => /^QShopWebUI-.*\.jar$/.test(f))
    : [];
  if (!jars.length) {
    console.error('未找到 target/QShopWebUI-*.jar，请先执行 mvn package');
    process.exit(1);
  }
  jars.sort((a, b) => fs.statSync(path.join(targetDir, b)).mtimeMs - fs.statSync(path.join(targetDir, a)).mtimeMs);
  return path.join(targetDir, jars[0]);
};
const jar = pickJar();
const tmp = path.join(os.tmpdir(), 'qsw-jar-check');
fs.rmSync(tmp, { recursive: true, force: true });
fs.mkdirSync(tmp, { recursive: true });
extractZip(jar, tmp);

const walk = (d, acc = []) => {
  for (const f of fs.readdirSync(d)) {
    const p = path.join(d, f);
    if (fs.statSync(p).isDirectory()) walk(p, acc);
    else acc.push(path.relative(tmp, p).replace(/\\/g, '/'));
  }
  return acc;
};
const all = walk(tmp);

for (const n of ['PurchaseService.class', 'PurchaseApi.class', 'GameCodeService.class', 'EconomyBridge.class', 'AuthMeBridge.class', 'LimitedBridge.class', 'ItemCodec.class', 'PurchaseJoinListener.class']) {
  const hit = all.filter((x) => x.includes(n));
  console.log(n + ': ' + (hit.length ? hit.join(', ') : '!! 缺失'));
}

const check = (rel, needle, label) => {
  const p = path.join(tmp, rel);
  if (!fs.existsSync(p)) return console.log(label + ': !! 文件缺失 (' + rel + ')');
  const txt = fs.readFileSync(p, 'utf8');
  console.log(label + ': ' + (txt.includes(needle) ? '✓' : '!! 未找到内容'));
};

check('web/js/app.js', 'showPurchaseModal', 'app.js 购买弹窗');
check('web/js/app.js', 'showSellModal', 'app.js 出售弹窗');
check('web/js/app.js', 'initSellPage', 'app.js 收购界面');
check('web/index.html', 'data-tab="sell"', 'index.html 收购导航');
check('web/js/db.js', 'purchaseShop', 'db.js purchaseShop API');
check('web/js/db.js', 'sellShop', 'db.js sellShop API');
check('web/js/db.js', 'getWallet', 'db.js getWallet API');
check('web/js/db.js', 'inventoryCheck', 'db.js inventoryCheck API');
check('web/js/db.js', 'getLimit', 'db.js getLimit API');
check('web/css/style.css', '多设备适配', 'style.css 响应式');
check('config.yml', 'purchase:', 'config.yml purchase 段');
check('config.yml', 'game-code-ttl-seconds', 'config.yml 验证码配置');
check('plugin.yml', 'QShopWebUI', 'plugin.yml');
check('plugin.yml', 'code|reload', 'plugin.yml 验证码命令');
check('custom_lang_zh_cn.json', 'item.dnt.', '数据包翻译表');
{
  const txt = fs.readFileSync(path.join(tmp, 'web/js/app.js'), 'utf8');
  console.log('app.js 店主过滤 shopOwnerName: ' + (txt.includes('shopOwnerName') ? '✓' : '!! 未找到'));
  console.log('app.js 旧店主行已清除: ' + (txt.includes("tr('店主', shop.owner_name") ? '!! 仍有残留' : '✓'));
  console.log('app.js 「库存上限:无限」行已清除: ' + (txt.includes("'库存上限'") ? '!! 仍有残留' : '✓'));
  console.log('app.js 游戏登录已移除: ' + (txt.includes('游戏登录') ? '!! 仍有残留' : '✓'));
  console.log('app.js 验证码购买字段: ' + (txt.includes('游戏内验证码') ? '✓' : '!! 未找到'));
  console.log('app.js 图片 CDN 回退: ' + (txt.includes('mcitemgallery.com') ? '✓' : '!! 未找到'));
  console.log('app.js 限购显示: ' + (txt.includes('限购') ? '✓' : '!! 未找到'));
  const dbTxt = fs.readFileSync(path.join(tmp, 'web/js/db.js'), 'utf8');
  console.log('db.js playerLogin 已移除: ' + (dbTxt.includes('playerLogin') ? '!! 仍有残留' : '✓'));
  console.log('db.js 购买携带验证码: ' + (dbTxt.includes("code: code") ? '✓' : '!! 未找到'));
  console.log('db.js 限购 API: ' + (dbTxt.includes('getLimit') ? '✓' : '!! 未找到'));
}
{
  // 检查 class 里是否残留编译错误标记（编辑器增量编译产物被直接打包时会出现）
  const bad = all.filter((f) => f.endsWith('.class') && fs.readFileSync(path.join(tmp, f)).includes('Unresolved compilation'));
  console.log('class 编译错误残留: ' + (bad.length ? ('!! ' + bad.slice(0, 5).join(', ')) : '✓'));
}
console.log('jar 条目总数: ' + all.length + '，大小: ' + (fs.statSync(jar).size / 1024 / 1024).toFixed(2) + ' MB');
fs.rmSync(tmp, { recursive: true, force: true });
