// 看 PURCHASE 行样例、players.csv、CREATE_TIME/BENEFIT
import fs from 'fs';
const tmp = 'd:/QshopWebUI/paper-plugin/tools/qs-export-tmp';

const txt = fs.readFileSync(tmp + '/log_purchase.csv', 'utf8');
const lines = txt.split(/\r?\n/);
console.log('=== PURCHASE 行样例:');
let n = 0;
for (const l of lines) {
  if (l.includes('PURCHASE_')) { console.log('  ' + l); if (++n >= 6) break; }
}
console.log('\n=== TYPE 统计:');
const stat = {};
for (const l of lines) {
  const m = l.match(/,(CREATE|PURCHASE_SELLING_SHOP|PURCHASE_BUYING_SHOP),/);
  if (m) stat[m[1]] = (stat[m[1]] || 0) + 1;
}
console.log(' ', JSON.stringify(stat));
console.log('  最后一行:', lines[lines.length - 2]);

console.log('\n=== players.csv:');
const p = fs.readFileSync(tmp + '/players.csv', 'utf8');
console.log(p.split(/\r?\n/).slice(0, 8).join('\n'));

console.log('\n=== data.csv 第一条（前 30 行原始）:');
const d = fs.readFileSync(tmp + '/data.csv', 'utf8');
console.log(d.split(/\r?\n/).slice(0, 30).join('\n').slice(0, 2200));
