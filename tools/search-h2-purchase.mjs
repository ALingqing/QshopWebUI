// 完整查看 LOG_PURCHASE 表结构 + 估算数据量
import fs from 'fs';

const buf = fs.readFileSync('g:/p/plugins/QuickShop-Hikari/shops.mv.db');
const txt = buf.toString('latin1');

// 完整建表语句
let i = txt.indexOf('CREATE CACHED TABLE "PUBLIC"."LOG_PURCHASE"');
if (i < 0) i = txt.indexOf('LOG_PURCHASE"(');
if (i < 0) i = txt.indexOf('LOG_PURCHASE');
console.log('=== LOG_PURCHASE 建表:');
console.log(txt.slice(i, i + 900).replace(/[^\x20-\x7E]/g, '·'));

// 数据量估算：数 PURCHASE_SELLING_SHOP / PURCHASE_BUYING_SHOP 出现次数
for (const kw of ['PURCHASE_SELLING_SHOP', 'PURCHASE_BUYING_SHOP']) {
  let count = 0, j = 0;
  while ((j = txt.indexOf(kw, j)) !== -1) { count++; j += kw.length; if (count > 100000) break; }
  console.log('\n' + kw + ' 出现次数（含索引/元数据重复，粗略）:', count);
}

// LOG_PURCHASE 索引
for (const kw of ['IDX_LOG_PURCHASE_TIME', 'IDX_LOG_PURCHASE_BUYER', 'SYSTEM_SEQUENCE']) {
  const idx = txt.indexOf(kw);
  console.log('\n[' + kw + '] @' + idx + ':', idx >= 0 ? txt.slice(idx, idx + 150).replace(/[^\x20-\x7E]/g, '·') : '无');
}
