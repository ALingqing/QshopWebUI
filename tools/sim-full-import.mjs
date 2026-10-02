// 模拟完整导入流程：log_others + log_purchase.DELETE 合并去重
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
const nameBy = {};
for (const l of parseCsv(read('players.csv')).slice(1)) {
  if (l.length >= 3) nameBy[l[0].trim().toLowerCase()] = l[2].trim();
}
const name = (u) => u ? (nameBy[String(u).toLowerCase()] || (String(u).length === 36 ? String(u).slice(0, 8) : u)) : '';

// —— 第一部分：log_others ShopRemoveLog（合并，10 秒窗口）——
const orow = parseCsv(read('log_others.csv'));
const oh = orow[0];
const oi = oh.indexOf('ID'), ot = oh.indexOf('TIME'), oty = oh.indexOf('TYPE'), od = oh.indexOf('DATA');
const lastByPos = new Map();
const removals = [];
for (let i = 1; i < orow.length; i++) {
  const r = orow[i];
  if (!(r[oty] || '').endsWith('ShopRemoveLog')) continue;
  let d; try { d = JSON.parse(r[od]); } catch (e) { continue; }
  const pos = (d.shop || {}).position || {};
  const posKey = (pos.world || '') + '|' + (pos.x || 0) + '|' + (pos.y || 0) + '|' + (pos.z || 0);
  const ts = new Date(r[ot].trim().replace(' ', 'T')).getTime();
  let cur = lastByPos.get(posKey);
  if (cur && ts - cur.t > 10000) cur = null;
  if (!cur) {
    const rec = { t: ts, time: r[ot].trim(), player: name(d.player), reason: d.reason || '', from: 'log' };
    removals.push(rec);
    lastByPos.set(posKey, rec);
  } else {
    const isUser = d.player && !String(d.player).startsWith('[');
    if (isUser && (!cur.player || cur.player.length <= 8)) { cur.player = name(d.player); cur.reason = d.reason || cur.reason; }
  }
}
console.log('ShopRemoveLog 合并后:', removals.length, '条');

// —— 第二部分：log_purchase DELETE ——
const lrow = parseCsv(read('log_purchase.csv'));
const lh = lrow[0];
const li = lh.indexOf('ID'), lt = lh.indexOf('TIME'), ls = lh.indexOf('SHOP'), lty = lh.indexOf('TYPE'), lb = lh.indexOf('BUYER');
const tradeDeletes = [];
for (let i = 1; i < lrow.length; i++) {
  const r = lrow[i];
  if ((r[lty] || '').trim() !== 'DELETE') continue;
  tradeDeletes.push({
    t: new Date(r[lt].trim().replace(' ', 'T')).getTime(),
    time: r[lt].trim(), player: name(r[lb]),
    reason: '商店删除（批量命令，编号 ' + r[ls] + '）', from: 'trade', shopRef: r[ls]
  });
}
console.log('交易日志 DELETE:', tradeDeletes.length, '条');

// —— 7.6 去重：DELETE 与 ShopRemoveLog 时间 ±5 秒视为重复 ——
let dup = 0;
const final = [...removals];
for (const d of tradeDeletes) {
  let isDup = false;
  for (const x of removals) {
    if (Math.abs(x.t - d.t) <= 5000) { isDup = true; break; }
  }
  if (isDup) { dup++; continue; }
  final.push(d);
}
console.log('去重跳过:', dup, '条；最终移除记录数:', final.length, '条');
console.log('\n=== 今天的记录（10-02，将出现在移除记录里）===');
const today = final.filter(x => x.time.startsWith('2026-10-02'));
console.log('今天共', today.length, '条:');
for (const t of today.slice(0, 10)) console.log('  ' + t.time + ' | 操作者:' + t.player + ' | ' + t.reason);
console.log('  ...（共 ' + today.length + ' 条）');

// 操作者分布
const byP = {};
for (const t of today) byP[t.player] = (byP[t.player] || 0) + 1;
console.log('操作者分布:', JSON.stringify(byP));
