// 深挖：今天的操作痕迹 + Jinx 店铺现状 + 最近删除
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

// 1) log_others 里今天(10-02)的所有日志
console.log('=== LOG_OTHERS 今天 (2026-10-02) 的所有记录 ===');
const orow = parseCsv(read('log_others.csv'));
const head = orow[0];
const iId = head.indexOf('ID'), iTime = head.indexOf('TIME'), iType = head.indexOf('TYPE'), iData = head.indexOf('DATA');
let todayCount = 0;
for (let i = 1; i < orow.length; i++) {
  const r = orow[i];
  if (!r[iTime] || !r[iTime].trim().startsWith('2026-10-02')) continue;
  todayCount++;
  console.log('  #' + r[iId] + ' ' + r[iTime].trim() + ' ' + (r[iType] || '').split('.').pop());
  if (todayCount <= 10) console.log('      ' + String(r[iData] || '').slice(0, 260).replace(/\n/g, ' | '));
}
console.log('  今天日志总数: ' + todayCount);

// 2) 最近 15 条删除日志（含 8-24 的最后删除）
console.log('\n=== LOG_OTHERS 最近 15 条 ShopRemoveLog ===');
let n = 0;
for (let i = orow.length - 1; i >= 1 && n < 15; i--) {
  const r = orow[i];
  if (!(r[iType] || '').endsWith('ShopRemoveLog')) continue;
  n++;
  let d = {};
  try { d = JSON.parse(r[iData]); } catch (e) { }
  const pos = (d.shop || {}).position || {};
  console.log('  #' + r[iId] + ' ' + r[iTime].trim() + ' | ' + (d.player || '?') + ' | ' + (d.reason || '?') + ' | ' + (pos.world || '?') + ' ' + pos.x + ',' + pos.y + ',' + pos.z);
}

// 3) LOG_PURCHASE：今天有没有 DELETE / 操作
console.log('\n=== LOG_PURCHASE 今天 (10-02) 的记录 ===');
const lrow = parseCsv(read('log_purchase.csv'));
const lh = lrow[0];
const li = lh.indexOf('ID'), lt = lh.indexOf('TIME'), ls = lh.indexOf('SHOP'), lty = lh.indexOf('TYPE'), lb = lh.indexOf('BUYER'), lm = lh.indexOf('MONEY');
let today2 = 0;
for (let i = 1; i < lrow.length; i++) {
  const r = lrow[i];
  if (!r[lt] || !r[lt].trim().startsWith('2026-10-02')) continue;
  today2++;
  if (today2 <= 20) console.log('  #' + r[li] + ' ' + r[lt].trim() + ' 店=' + r[ls] + ' ' + r[lty] + ' 买家=' + String(r[lb]).slice(0, 8) + ' 金额=' + r[lm]);
}
console.log('  今天交易表记录数: ' + today2);

// 4) Jinx 店铺现状：状态/价格/创建时间/位置（取样）
console.log('\n=== Jinx 175 家店铺现状抽样 ===');
const drow = parseCsv(read('data.csv'));
const dh = drow[0];
const d_id = dh.indexOf('ID'), d_owner = dh.indexOf('OWNER'), d_price = dh.indexOf('PRICE'), d_state = dh.indexOf('SHOP_STATE'), d_type = dh.indexOf('TYPE'), d_pos = dh.indexOf('INV_SYMBOL_LINK'), d_time = dh.indexOf('CREATE_TIME');
let cnt = 0;
const states = {};
for (let i = 1; i < drow.length; i++) {
  const r = drow[i];
  if (String(r[d_owner] || '').toLowerCase() !== '7abaa3a9-882d-355d-a82c-d89b8821ff08') continue;
  cnt++;
  const st = r[d_state] || '?';
  states[st] = (states[st] || 0) + 1;
  if (cnt <= 6) console.log('  #' + r[d_id] + ' 价格=' + r[d_price] + ' 状态=' + st + ' 类型=' + r[d_type] + ' ' + r[d_pos] + ' 建于 ' + r[d_time]);
}
console.log('  Jinx 商店总数: ' + cnt + '，状态分布: ' + JSON.stringify(states));

// 5) 全服商店总数
console.log('\n全服商店总数: ' + (drow.length - 1));
