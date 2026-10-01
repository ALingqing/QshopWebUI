// 解压 export zip 并查看 CSV 结构
import { execSync } from 'child_process';
import fs from 'fs';

const tmp = 'd:/QshopWebUI/paper-plugin/tools/qs-export-tmp';
fs.rmSync(tmp, { recursive: true, force: true });
fs.mkdirSync(tmp, { recursive: true });
execSync('tar -xf "g:/p/plugins/QuickShop-Hikari/export-1772019745453.zip" -C "' + tmp + '"');
console.log('解压完成:', fs.readdirSync(tmp).join(', '));

for (const f of ['log_purchase.csv', 'data.csv', 'shops.csv', 'log_transaction.csv']) {
  const p = tmp + '/' + f;
  if (!fs.existsSync(p)) { console.log('\n=== ' + f + ': 不存在'); continue; }
  const txt = fs.readFileSync(p, 'utf8');
  const lines = txt.split(/\r?\n/);
  console.log('\n=== ' + f + ' (' + lines.length + ' 行):');
  for (const l of lines.slice(0, 6)) console.log('  ' + l.slice(0, 400));
}
