// 校验 QShopWebUI jar 内的购买功能资源
import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import os from 'os';

const root = path.resolve(import.meta.dirname, '..');
const jar = path.join(root, 'target', 'QShopWebUI-1.0.0.jar');
const tmp = path.join(os.tmpdir(), 'qsw-jar-check');
fs.rmSync(tmp, { recursive: true, force: true });
fs.mkdirSync(tmp, { recursive: true });
execSync(`tar -xf "${jar}" -C "${tmp}"`);

const walk = (d, acc = []) => {
  for (const f of fs.readdirSync(d)) {
    const p = path.join(d, f);
    if (fs.statSync(p).isDirectory()) walk(p, acc);
    else acc.push(path.relative(tmp, p).replace(/\\/g, '/'));
  }
  return acc;
};
const all = walk(tmp);

for (const n of ['PurchaseService.class', 'PurchaseApi.class', 'EconomyBridge.class']) {
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
check('web/js/db.js', 'purchaseShop', 'db.js purchaseShop API');
check('web/css/style.css', 'Online purchase (web buy)', 'style.css 购买样式');
check('config.yml', 'purchase:', 'config.yml purchase 段');
check('plugin.yml', 'QShopWebUI', 'plugin.yml');
console.log('jar 条目总数: ' + all.length + '，大小: ' + (fs.statSync(jar).size / 1024 / 1024).toFixed(2) + ' MB');
fs.rmSync(tmp, { recursive: true, force: true });
