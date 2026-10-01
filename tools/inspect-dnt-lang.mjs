// 解出 DnT 的 lang 文件并查看内容
import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import os from 'os';

const zip = 'C:/Users/aqing/Desktop/新建文件夹 (3)/Dungeons and Taverns v5.3.0 (1).zip';
const tmp = path.join(os.tmpdir(), 'qsw-dnt');
fs.rmSync(tmp, { recursive: true, force: true });
fs.mkdirSync(tmp, { recursive: true });
execSync('tar -xf "' + zip + '" -C "' + tmp + '" assets/dnt/lang/zh_cn.json assets/minecraft/lang/zh_cn.json', { stdio: 'inherit' });

for (const f of ['assets/dnt/lang/zh_cn.json', 'assets/minecraft/lang/zh_cn.json']) {
  const p = path.join(tmp, f);
  if (!fs.existsSync(p)) { console.log('\n' + f + ': 不存在'); continue; }
  const data = JSON.parse(fs.readFileSync(p, 'utf8').replace(/^\uFEFF/, ''));
  const keys = Object.keys(data);
  console.log('\n===== ' + f + ' (' + keys.length + ' 键) =====');
  // 按前缀分类
  const groups = {};
  for (const k of keys) {
    const g = k.split('.').slice(0, 3).join('.');
    groups[g] = (groups[g] || 0) + 1;
  }
  console.log('键前缀分布:');
  Object.entries(groups).sort((a, b) => b[1] - a[1]).slice(0, 20).forEach(([g, n]) => console.log('  ' + g + ': ' + n));
  console.log('前 15 条样例:');
  keys.slice(0, 15).forEach(k => console.log('  ' + k + ' = ' + data[k]));
  // 是否有 item/block 名
  const items = keys.filter(k => /^(item|block)\./.test(k));
  console.log('item/block 相关键:', items.length, items.slice(0, 10).join(', '));
}
