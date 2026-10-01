// 1) QuickShop-Hikari 目录全貌；2) config 的 logging 段；3) lib/com 内容
import fs from 'fs';
import path from 'path';

const root = 'g:/p/plugins/QuickShop-Hikari';
console.log('=== QuickShop-Hikari 目录:');
for (const f of fs.readdirSync(root)) {
  const st = fs.statSync(path.join(root, f));
  console.log('  ' + (st.isDirectory() ? '[D] ' : String(st.size).padStart(9) + ' ') + f);
}

console.log('\n=== config.yml logging 相关:');
const cfg = fs.readFileSync(root + '/config.yml', 'utf8');
const ls = cfg.split(/\r?\n/);
let capture = false;
for (let i = 0; i < ls.length; i++) {
  const l = ls[i];
  if (/^logging:|^log-|^logs|^debug|history/i.test(l)) capture = true;
  if (capture) console.log('  ' + l);
  if (capture && l.trim() === '' && i > 3 && ls[i + 1] && /^[a-z]/i.test(ls[i + 1]) && !/^log|^history|^debug/i.test(ls[i + 1])) break;
}

console.log('\n=== lib/com 内容:');
for (const f of fs.readdirSync(root + '/lib/com')) console.log('  ' + f);
