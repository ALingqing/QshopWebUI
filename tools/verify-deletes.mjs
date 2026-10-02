// 验证：DELETE 记录来源范围 + Jinx 建店/现存对比
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
const read = (name) => execSync('tar -xOf "' + zip + '" ' + name, { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 });

const lrow = parseCsv(read('log_purchase.csv'));
const lh = lrow[0];
const li = lh.indexOf('ID'), lt = lh.indexOf('TIME'), ls = lh.indexOf('SHOP'), lty = lh.indexOf('TYPE'), lb = lh.indexOf('BUYER'), la = lh.indexOf('AMOUNT'), lm = lh.indexOf('MONEY');

// 1) 所有 DELETE 记录（全部时间）
console.log('=== LOG_PURCHASE 中所有 DELETE 记录 ===');
const dels = [];
for (let i = 1; i < lrow.length; i++) {
  const r = lrow[i];
  if ((r[lty] || '').trim() !== 'DELETE') continue;
  dels.push({ id: r[li], t: r[lt].trim(), shop: r[ls], buyer: r[lb], by: String(r[lb]).slice(0, 8) });
}
console.log('DELETE 总数:', dels.length);
const byDay = {};
for (const d of dels) byDay[d.t.slice(0, 10)] = (byDay[d.t.slice(0, 10)] || 0) + 1;
console.log('按日期分布:', JSON.stringify(byDay));
console.log('前 3 条:', dels.slice(0, 3).map(d => d.id + ' ' + d.t + ' 店' + d.shop + ' "买家"' + d.by).join(' | '));
console.log('今天 72 条的 shop id 范围:', (() => {
  const today = dels.filter(d => d.t.startsWith('2026-10-02'));
  const ids = today.map(d => parseInt(d.shop)).sort((a, b) => a - b);
  return ids.length ? (ids[0] + ' ~ ' + ids[ids.length - 1] + ' (共' + ids.length + '个)') : '-';
})());

// 2) 8月23日 silentremove 时段有没有 DELETE（判断两源是否重合）
const aug23 = dels.filter(d => d.t.startsWith('2026-08-23'));
console.log('\n8月23日 DELETE 数:', aug23.length, aug23.slice(0, 5).map(d => d.t).join(', '));

// 3) Jinx 的 CREATE 总数（全部时段）
let jinxCreate = 0, jinxDelete = 0;
const jinx = '7abaa3a9-882d-355d-a82c-d89b8821ff08';
for (let i = 1; i < lrow.length; i++) {
  const r = lrow[i];
  if (String(r[lb] || '').toLowerCase() !== jinx) continue;
  const ty = (r[lty] || '').trim();
  if (ty === 'CREATE') jinxCreate++;
  if (ty === 'DELETE') jinxDelete++;
}
console.log('\n=== Jinx（7abaa3a9）在交易表中: CREATE=' + jinxCreate + ' 次, DELETE=' + jinxDelete + ' 次 ===');

// 4) 今天 DELETE 的 BUYER 分布
const bd = {};
for (const d of dels.filter(x => x.t.startsWith('2026-10-02'))) bd[d.by] = (bd[d.by] || 0) + 1;
console.log('今天 DELETE 的 BUYER 分布:', JSON.stringify(bd));

// 5) 现存 Jinx 店铺的 ID 范围
const drow = parseCsv(read('data.csv'));
const dh = drow[0];
const di = dh.indexOf('ID'), downer = dh.indexOf('OWNER');
const ids = [];
for (let i = 1; i < drow.length; i++) {
  if (String(drow[i][downer] || '').toLowerCase() === jinx) ids.push(parseInt(drow[i][di]));
}
ids.sort((a, b) => a - b);
console.log('现存 Jinx 店铺数:', ids.length, '，DATA.ID 范围:', ids[0], '~', ids[ids.length - 1]);
