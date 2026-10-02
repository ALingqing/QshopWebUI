// 模拟 Java 端"移除商店记录"导入逻辑，用真实导出包验证
import { execSync } from 'child_process';

const zip = 'G:/p/plugins/QuickShop-Hikari/export-1772019745453.zip';
const csv = execSync('tar -xOf "' + zip + '" log_others.csv', { encoding: 'utf8' });
const playersCsv = execSync('tar -xOf "' + zip + '" players.csv', { encoding: 'utf8' });

function parseCsv(text) {
  const rows = []; let cur = [], field = '', inQ = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (inQ) {
      if (c === '"') { if (text[i + 1] === '"') { field += '"'; i++; } else inQ = false; }
      else field += c;
    } else {
      if (c === '"') inQ = true;
      else if (c === ',') { cur.push(field); field = ''; }
      else if (c === '\n') { cur.push(field); field = ''; rows.push(cur); cur = []; }
      else if (c !== '\r') field += c;
    }
  }
  if (field.length > 0 || cur.length > 0) { cur.push(field); rows.push(cur); }
  return rows;
}

const nameBy = {};
for (const l of parseCsv(playersCsv).slice(1)) {
  if (l.length >= 3) nameBy[l[0].trim().toLowerCase()] = l[2].trim();
}
const ITEM_ID = /^[ \t]*id:[ \t]*([^\s]+)/m;

const rows = parseCsv(csv);
const head = rows[0];
const iId = head.indexOf('ID'), iTime = head.indexOf('TIME'), iType = head.indexOf('TYPE'), iData = head.indexOf('DATA');
console.log('表头:', head.join(' | '));

const merged = new Map();
const lastByPos = new Map();
let total = 0, removeLogs = 0;
function parseTime(s) { return new Date(s.replace(' ', 'T')).getTime(); }
for (let i = 1; i < rows.length; i++) {
  const r = rows[i];
  if (r.length <= Math.max(iData, Math.max(iId, iTime))) continue;
  total++;
  const type = (r[iType] || '').trim();
  if (!type.endsWith('ShopRemoveLog')) continue;
  removeLogs++;
  let data;
  try { data = JSON.parse(r[iData]); } catch (e) { continue; }
  const player = String(data.player || '');
  const reason = String(data.reason || '');
  const shop = data.shop || {};
  const pos = shop.position || {};
  const world = pos.world || '';
  const posKey = world + '|' + (pos.x || 0) + '|' + (pos.y || 0) + '|' + (pos.z || 0);
  const ts = parseTime(r[iTime]);
  const isUser = player && !player.startsWith('[') && !/^system$|^console$/i.test(player);
  let cur = lastByPos.get(posKey);
  if (cur && ts - cur.ts > 10000) cur = null;
  if (!cur) {
    const res = { ts, time: r[iTime], player, reason, world, x: pos.x, y: pos.y, z: pos.z, owner: shop.owner, price: shop.price };
    const m = ITEM_ID.exec(shop.item || '');
    let mat = m ? m[1] : '';
    const colon = mat.indexOf(':'); if (colon >= 0) mat = mat.substring(colon + 1);
    res.material = mat.toUpperCase().replace(/-/g, '_');
    merged.set(ts + '|' + posKey, res);
    lastByPos.set(posKey, res);
  } else if (isUser) {
    const curIsUser = cur.player && !cur.player.startsWith('[') && !/^系统$/.test(cur.player);
    if (!curIsUser) { cur.player = player; cur.reason = reason; }
  }
}

console.log('\n总行数:', total, '，ShopRemoveLog 条数:', removeLogs);
console.log('合并后移除记录数:', merged.size);
console.log('\n=== 合并结果:');
for (const [k, v] of merged) {
  const name = nameBy[(v.player || '').toLowerCase()] || v.player || '';
  console.log('  ' + v.time + ' | 操作者: ' + (name || '(空)') + ' | 原因: ' + (v.reason || '-') + ' | ' + v.material + ' @ ' + (v.price ?? '?') + '元 | ' + v.world + ' ' + v.x + ' ' + v.y + ' ' + v.z + ' | 店主: ' + (nameBy[(v.owner || '').toLowerCase()] || v.owner));
}
