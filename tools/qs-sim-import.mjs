// 模拟 Java 端导入逻辑，用真实导出包验证
import fs from 'fs';
const tmp = 'd:/QshopWebUI/paper-plugin/tools/qs-export-tmp';

// —— 与 Java 相同的 CSV parser ——
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

// players
const playersCsv = fs.readFileSync(tmp + '/players.csv', 'utf8');
const pRows = parseCsv(playersCsv);
const nameByUuid = {};
{
  const head = pRows[0];
  const iU = head.indexOf('UUID'), iN = head.indexOf('CACHEDNAME');
  for (let i = 1; i < pRows.length; i++) {
    const r = pRows[i];
    if (r.length <= Math.max(iU, iN)) continue;
    nameByUuid[r[iU].trim().toLowerCase()] = r[iN].trim();
  }
}
console.log('玩家名表:', Object.keys(nameByUuid).length, '条');

// data.csv -> refById
const dataCsv = fs.readFileSync(tmp + '/data.csv', 'utf8');
const dRows = parseCsv(dataCsv);
console.log('data.csv 解析行数:', dRows.length - 1);
const refById = {};
{
  const head = dRows[0];
  const iId = 0, iOwner = 1, iItem = 2;
  const ITEM_ID = /^[ \t]*id:[ \t]*([^\s]+)/m;
  const ITEM_COUNT = /^[ \t]*count:[ \t]*(\d+)/m;
  for (let i = 1; i < dRows.length; i++) {
    const r = dRows[i];
    if (r.length <= iItem) continue;
    const id = parseInt(r[iId], 10);
    const m = r[iItem].match(ITEM_ID);
    const c = r[iItem].match(ITEM_COUNT);
    let mat = m ? m[1] : '';
    const colon = mat.indexOf(':');
    if (colon >= 0) mat = mat.substring(colon + 1);
    refById[id] = { material: mat.toUpperCase().replace(/-/g, '_'), count: c ? parseInt(c[1], 10) : 1, owner: r[iOwner].trim() };
  }
}
console.log('商店物品引用:', Object.keys(refById).length, '条，样例:', JSON.stringify(refById[94] || refById[3]));

// log_purchase.csv -> 交易
const logCsv = fs.readFileSync(tmp + '/log_purchase.csv', 'utf8');
const lRows = parseCsv(logCsv);
console.log('log_purchase 行数:', lRows.length - 1);

const batch = [];
let skipped = 0, purchases = 0;
const maxId = 0; // 模拟首次导入
let newMax = maxId;
for (let i = 1; i < lRows.length; i++) {
  const row = lRows[i];
  if (row.length < 9) continue;
  const id = parseInt(row[0], 10);
  if (isNaN(id)) continue;
  if (id > newMax) newMax = id;
  const type = (row[5] || '').trim();
  const purchase = type.startsWith('PURCHASE_');
  if (id <= maxId) { if (purchase) skipped++; continue; }
  if (!purchase) continue;
  purchases++;
  const isBuy = type.includes('SELLING');
  const shopId = parseInt(row[2], 10);
  const buyer = (row[4] || '').trim();
  const amount = parseInt(row[6], 10) || 0;
  const money = parseFloat(row[7]) || 0;
  const tax = parseFloat(row[8]) || 0;
  const ref = refById[shopId];
  const player = nameByUuid[buyer.toLowerCase()] || (buyer.length === 36 ? buyer.slice(0, 8) : buyer);
  const owner = ref ? (nameByUuid[ref.owner.toLowerCase()] || '') : '';
  batch.push({
    time: row[1], type: isBuy ? 'BUY' : 'SELL', shop_id: shopId,
    item: ref ? ref.material : ('商店 #' + shopId), amount, items: amount * (ref ? ref.count : 1),
    unit: amount > 0 ? Math.round(money / amount * 100) / 100 : money, total: money, tax, player, owner,
    mapOk: !!ref, playerOk: !!nameByUuid[buyer.toLowerCase()]
  });
}
console.log('\n=== 转换结果: 新导入', batch.length, '条, 跳过', skipped, ', 采购行合计', purchases + skipped);
console.log('max id =', newMax);
console.log('解析映射: 商店物品命中', batch.filter(t => t.mapOk).length, '/', batch.length,
  '; 玩家名命中', batch.filter(t => t.playerOk).length, '/', batch.length);
console.log('\n样例 8 条:');
batch.slice(0, 8).forEach(t => console.log('  ', JSON.stringify(t)));
console.log('\n最后 3 条:');
batch.slice(-3).forEach(t => console.log('  ', JSON.stringify(t)));
