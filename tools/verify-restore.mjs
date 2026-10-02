// 验证：被删商店的数据是否可从日志/快照恢复
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

// 1) LOG_OTHERS 里 ShopCreationLog 的分布
const orow = parseCsv(read('log_others.csv'));
const oh = orow[0];
const ot = oh.indexOf('TIME'), oty = oh.indexOf('TYPE'), od = oh.indexOf('DATA');
const cre = [];
for (let i = 1; i < orow.length; i++) {
  const r = orow[i];
  if (!(r[oty] || '').endsWith('ShopCreationLog')) continue;
  cre.push({ t: r[ot].trim(), data: r[od] });
}
console.log('LOG_OTHERS ShopCreationLog 总数:', cre.length);
if (cre.length) {
  console.log('最早:', cre[0].t, ' 最新:', cre[cre.length - 1].t);
  const byDay = {};
  for (const c of cre) byDay[c.t.slice(0, 7)] = (byDay[c.t.slice(0, 7)] || 0) + 1;
  console.log('按月份分布:', JSON.stringify(byDay));
}

// 2) 其中与 Jinx (7abaa3a9) 相关的
const jinxCre = cre.filter(c => c.data.includes('7abaa3a9'));
console.log('\nJinx 的建店日志（ShopCreationLog）:', jinxCre.length, '条');
for (const c of jinxCre.slice(0, 3)) {
  const d = JSON.parse(c.data);
  const pos = (d.shop || d).position || (d.location || {});
  console.log('  ' + c.t + ' creator=' + (d.creator || '').slice(0, 8) + ' 位置=' + JSON.stringify(pos).slice(0, 120));
}

// 3) 今天被删的 68 家（LOG_PURCHASE.DELETE 的 SHOP 编号）能不能对上任何创建日志
const lrow = parseCsv(read('log_purchase.csv'));
const lh = lrow[0];
const lt = lh.indexOf('TIME'), lty = lh.indexOf('TYPE'), lbs = lh.indexOf('SHOP');
const todayDel = [];
for (let i = 1; i < lrow.length; i++) {
  const r = lrow[i];
  if ((r[lty] || '').trim() !== 'DELETE') continue;
  if (!r[lt].trim().startsWith('2026-10-02')) continue;
  todayDel.push(r[lbs].trim());
}
console.log('\n今天删除的店编号(runtime):', todayDel.length, '个; 样例:', todayDel.slice(0, 10).join(', '));

// 4) 这些编号在 LOG_PURCHASE 里对应的 CREATE 记录（建店时的操作）
let creHits = 0;
const delSet = new Set(todayDel);
for (let i = 1; i < lrow.length; i++) {
  const r = lrow[i];
  if ((r[lty] || '').trim() !== 'CREATE') continue;
  if (delSet.has(r[lbs].trim())) creHits++;
}
console.log('其中能在交易日志里找到 CREATE 记录的:', creHits, '家');
