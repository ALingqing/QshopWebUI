// 解析用户上传的导出包：提取今天的删除记录 + 验证 Jinx 商店状态
import { execSync } from 'child_process';
import fs from 'fs';
import zlib from 'zlib';

const zip = 'c:/Users/aqing/Downloads/export-1790911368443.zip';

// ---- CSV 解析（同 Java 端逻辑）----
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

const read = (name) => execSync('tar -xOf "' + zip + '" ' + name, { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 });

// ---- 玩家名映射 ----
const nameBy = {};
for (const l of parseCsv(read('players.csv')).slice(1)) {
  if (l.length >= 3) nameBy[l[0].trim().toLowerCase()] = l[2].trim();
}

// ---- 物品提取（yaml 或 base64-NBT 兼容）----
function extractItem(s) {
  if (!s) return { mat: '' };
  let m = /^\s*id:\s*([^\s]+)/m.exec(s);
  if (m) return { mat: m[1].replace(/^minecraft:/, '').toUpperCase() };
  const b64 = /^(H4sI[A-Za-z0-9+/=]+|rO0AB[A-Za-z0-9+/=]+)/.exec(s);
  if (b64) {
    try {
      let buf = b64[1].startsWith('H4sI') ? zlib.gunzipSync(Buffer.from(b64[1], 'base64')) : Buffer.from(b64[1], 'base64');
      m = /minecraft:[a-z0-9_]+/.exec(buf.toString('latin1'));
      if (m) return { mat: m[0].replace(/^minecraft:/, '').toUpperCase() };
    } catch (e) { }
  }
  return { mat: '' };
}

// ---- 解析 log_others ----
const orow = parseCsv(read('log_others.csv'));
const head = orow[0];
const iId = head.indexOf('ID'), iTime = head.indexOf('TIME'), iType = head.indexOf('TYPE'), iData = head.indexOf('DATA');
console.log('log_others 总行数:', orow.length - 1);

const lastByPos = new Map();
const records = [];
let removeLogs = 0;
for (let i = 1; i < orow.length; i++) {
  const r = orow[i];
  if (r.length <= iData) continue;
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
  const ts = new Date(r[iTime].trim().replace(' ', 'T')).getTime();
  const isUser = player && !player.startsWith('[') && !/^system$|^console$/i.test(player);
  let cur = lastByPos.get(posKey);
  if (cur && ts - cur.ts > 10000) cur = null;
  if (!cur) {
    const it = extractItem(shop.item || '');
    const rec = {
      ts, time: r[iTime].trim(), player, reason, world, x: pos.x, y: pos.y, z: pos.z,
      owner: shop.owner || '', price: shop.price, mat: it.mat
    };
    records.push(rec);
    lastByPos.set(posKey, rec);
  } else if (isUser) {
    const curIsUser = cur.player && !cur.player.startsWith('[');
    if (!curIsUser) { cur.player = player; cur.reason = reason; }
  }
}

// 操作者/店主 名解析
for (const rec of records) {
  rec.byName = rec.player ? (nameBy[rec.player.toLowerCase()] || (rec.player.length === 36 ? rec.player.slice(0, 8) : rec.player)) : '(系统)';
  rec.ownerName = rec.owner ? (nameBy[String(rec.owner).toLowerCase()] || (String(rec.owner).length === 36 ? String(rec.owner).slice(0, 8) : rec.owner)) : '';
}

// ---- 今天（10-02）的记录 ----
const today = records.filter(r => r.time.startsWith('2026-10-02'));
console.log('全部 ShopRemoveLog 原始条数:', removeLogs, '，合并后记录数:', records.length);
console.log('今天 (2026-10-02) 的删除记录数:', today.length);
console.log('最近删除时间:', records.length ? records[records.length - 1].time : '-');

// ---- 今天的按操作者/原因统计 ----
const byReason = {};
const byPlayer = {};
for (const r of today) {
  byReason[r.reason] = (byReason[r.reason] || 0) + 1;
  byPlayer[r.byName] = (byPlayer[r.byName] || 0) + 1;
}
console.log('\n=== 今天删除 · 按原因 ===');
for (const [k, v] of Object.entries(byReason)) console.log('  ' + v + ' 条: ' + k);
console.log('\n=== 今天删除 · 按操作者 ===');
for (const [k, v] of Object.entries(byPlayer)) console.log('  ' + v + ' 条: ' + k);

// ---- 今天的明细（前 30 条 + 各店主统计）----
console.log('\n=== 今天删除记录（前 30 条明细）===');
for (const r of today.slice(0, 30)) {
  console.log('  ' + r.time + ' | 操作者:' + r.byName + ' | ' + (r.mat || '?') + ' | ' + r.price + '元 | 店东:' + r.ownerName + ' | ' + r.world + ' ' + r.x + ',' + r.y + ',' + r.z);
}
const byOwner = {};
for (const r of today) byOwner[r.ownerName] = (byOwner[r.ownerName] || 0) + 1;
console.log('\n=== 今天删除 · 按商店店主统计 ===');
for (const [k, v] of Object.entries(byOwner).sort((a, b) => b[1] - a[1])) console.log('  ' + v + ' 家: ' + (k || '(未知)'));

// ---- 生成 CSV 文件 ----
const esc = (v) => { const s = String(v == null ? '' : v); return /[",\n\r]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s; };
const csvRows = [['时间', '操作者', '原因', '商店物品', '单价', '店主', '世界', 'X', 'Y', 'Z']];
for (const r of today) csvRows.push([r.time, r.byName, r.reason, r.mat, r.price, r.ownerName, r.world, r.x, r.y, r.z]);
fs.writeFileSync('d:/QshopWebUI/Jinx删除记录-今天.csv', '\uFEFF' + csvRows.map(r => r.map(esc).join(',')).join('\r\n'), 'utf8');
console.log('\nCSV 已输出: d:/QshopWebUI/Jinx删除记录-今天.csv (' + today.length + ' 条)');

// ---- 验证：Jinx 当前是否还有商店 ----
const drow = parseCsv(read('data.csv'));
let jinxShops = 0, totalShops = drow.length - 1;
for (let i = 1; i < drow.length; i++) {
  if (String(drow[i][1] || '').toLowerCase() === '7abaa3a9-882d-355d-a82c-d89b8821ff08') jinxShops++;
}
console.log('\n=== 验证 ===');
console.log('当前数据库商店总数:', totalShops, '（上次分析时约 174+ 家 Jinx 的店）');
console.log('Jinx 名下剩余商店:', jinxShops, '(应为 0 表示已删光)');
