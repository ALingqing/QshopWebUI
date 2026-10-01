// 分析 LOG_TRANSACTION 表结构与数据样例
import fs from 'fs';

const buf = fs.readFileSync('g:/p/plugins/QuickShop-Hikari/shops.mv.db');
const txt = buf.toString('latin1');

// 1. 完整建表语句
let idx = txt.indexOf('LOG_TRANSACTION');
console.log('=== LOG_TRANSACTION 建表语句附近:');
console.log(txt.slice(idx - 200, idx + 1200).replace(/[^\x20-\x7E]/g, '·'));

// 2. 交易类型值样例（搜常见枚举）
for (const kw of ['PURCHASE', 'SELL', 'BUY', 'TRADE', '"TYPE"']) {
  let count = 0, i = 0;
  while ((i = txt.indexOf(kw, i)) !== -1 && count < 3) {
    console.log('\n=== "' + kw + '" @' + i + ':');
    console.log(txt.slice(i - 80, i + 120).replace(/[^\x20-\x7E]/g, '·'));
    count++;
    i += kw.length;
  }
}
