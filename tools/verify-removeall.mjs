// 验证 /qs removeall 在日志中的表现
import { execSync } from 'child_process';
const zip = 'c:/Users/aqing/Downloads/export-1790911368443.zip';
function parseCsv(text) {
  const rows = []; let cur = [], field = '', inQ = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (inQ) { if (c === '"') { if (text[i + 1] === '"') { field += '"'; i++; } else inQ = false; } else field += c; }
    else { if (c === '"') inQ = true; else if (c === ',') { cur.push(field); field = ''; } else if (c === '\n') { cur.push(field); field = ''; rows.push(cur); cur = []; } else if (c !== '\r') field += c; }
  }
  if (field.length > 0 || cur.length > 0) { cur.push(field); rows.push(cur); }
  return rows;
}
const read = (n) => execSync('tar -xOf "' + zip + '" ' + n, { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 });

// 1) LOG_OTHERS 中所有 reason 的分布
const orow = parseCsv(read('log_others.csv'));
const oh = orow[0];
const ot = oh.indexOf('TIME'), oty = oh.indexOf('TYPE'), od = oh.indexOf('DATA');
const reasons = {};
let removeAllHits = [];
for (let i = 1; i < orow.length; i++) {
  const r = orow[i];
  if (!(r[oty] || '').endsWith('ShopRemoveLog')) continue;
  let d; try { d = JSON.parse(r[od]); } catch (e) { continue; }
  const reason = String(d.reason || '?');
  reasons[reason] = (reasons[reason] || 0) + 1;
  if (/removeall|remove all|remove-all/i.test(reason)) removeAllHits.push(r[ot].trim() + ' ' + d.player + ' ' + reason);
}
console.log('=== ShopRemoveLog 的 reason 分布 ===');
for (const [k, v] of Object.entries(reasons).sort((a, b) => b[1] - a[1])) console.log('  ' + v + ' 条: ' + k);
console.log('\n含 "removeall" 字样的记录数:', removeAllHits.length);
removeAllHits.slice(0, 10).forEach(h => console.log('  ' + h));

// 2) LOG_PURCHASE 里的 DELETE 与 ShopRemoveLog 的时间对照（今天早上 68 条）
console.log('\n=== 今天 (10-02) DELETE 的精确时间分布 ===');
const lrow = parseCsv(read('log_purchase.csv'));
const lh = lrow[0];
const lt = lh.indexOf('TIME'), lty = lh.indexOf('TYPE');
const times = {};
for (let i = 1; i < lrow.length; i++) {
  const r = lrow[i];
  if ((r[lty] || '').trim() !== 'DELETE') continue;
  const t = r[lt].trim();
  if (!t.startsWith('2026-10-02')) continue;
  times[t] = (times[t] || 0) + 1;
}
for (const [k, v] of Object.entries(times).sort()) console.log('  ' + k + ' → ' + v + ' 条');

// 3) 检查 LOG_OTHERS 在 10-01/10-02 有没有任何记录
console.log('\n=== LOG_OTHERS 中 10-01 与 10-02 的记录 ===');
let n = 0;
for (let i = 1; i < orow.length; i++) {
  const t = orow[i][ot].trim();
  if (t.startsWith('2026-10-01') || t.startsWith('2026-10-02')) {
    n++;
    console.log('  ' + t + ' ' + (orow[i][oty] || '').split('.').pop());
  }
}
console.log('  共 ' + n + ' 条');
