// 对照验证：LOG_PURCHASE.DELETE 的 BUYER 语义
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

// LOG_PURCHASE 的 DELETE：8月22/24/27/9月5/10月分布样本
const lrow = parseCsv(read('log_purchase.csv'));
const lh = lrow[0];
const li = lh.indexOf('ID'), lt = lh.indexOf('TIME'), ls = lh.indexOf('SHOP'), lty = lh.indexOf('TYPE'), lb = lh.indexOf('BUYER');
console.log('=== LOG_PURCHASE DELETE 样本（8月22日~9月5日）===');
for (let i = 1; i < lrow.length; i++) {
  const r = lrow[i];
  if ((r[lty] || '').trim() !== 'DELETE') continue;
  const t = r[lt].trim();
  if (!/^2026-08-2[2-7]|^2026-09-05/.test(t)) continue;
  console.log('  #' + r[li] + ' ' + t + ' 店=' + r[ls] + ' BUYER=' + r[lb]);
}

// LOG_OTHERS 对应时段的 ShopRemoveLog（操作者）
const orow = parseCsv(read('log_others.csv'));
const oh = orow[0];
const oi = oh.indexOf('ID'), ot = oh.indexOf('TIME'), oty = oh.indexOf('TYPE'), od = oh.indexOf('DATA');
console.log('\n=== LOG_OTHERS ShopRemoveLog 样本（8月22日~9月5日）===');
for (let i = 1; i < orow.length; i++) {
  const r = orow[i];
  if (!(r[oty] || '').endsWith('ShopRemoveLog')) continue;
  const t = r[ot].trim();
  if (!/^2026-08-2[2-7]|^2026-09-05/.test(t)) continue;
  let d = {};
  try { d = JSON.parse(r[od]); } catch (e) { }
  const pos = (d.shop || {}).position || {};
  console.log('  #' + r[oi] + ' ' + t + ' 操作者=' + d.player + ' 原因=' + d.reason + ' ' + pos.world + ' ' + pos.x + ',' + pos.y + ',' + pos.z);
}

// 玩家名对照
const nameBy = {};
for (const l of parseCsv(read('players.csv')).slice(1)) {
  if (l.length >= 3) nameBy[l[0].trim().toLowerCase()] = l[2].trim();
}
console.log('\n=== 名字对照 ===');
for (const u of ['85d0d6a1-28fe-3161-880e-898e4cf9fd15', '7abaa3a9-882d-355d-a82c-d89b8821ff08', '9eb8ec34-9a31-3540-9259-30cff3b3f9ce', '7f78a593']) {
  const hit = Object.entries(nameBy).find(([k]) => k.startsWith(u));
  console.log('  ' + u + ' → ' + (hit ? hit[1] : '(未找到)'));
}
