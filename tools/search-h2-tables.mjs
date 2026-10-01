// 查 QuickShop H2 数据库的表结构（找是否有交易日志表）
import fs from 'fs';

const buf = fs.readFileSync('g:/p/plugins/QuickShop-Hikari/shops.mv.db');
const txt = buf.toString('latin1');

// H2 表名/SQL 明文搜索
const probes = ['QUICKSHOP', 'LOG', 'TRANSACTION', 'HISTORY', 'RECORD', 'CREATE TABLE', 'PUBLIC.', 'TABLE'];
for (const p of probes) {
  const re = new RegExp(p.replace('.', '\\.'), 'gi');
  const found = new Set();
  let m;
  while ((m = re.exec(txt)) && found.size < 20) {
    const start = Math.max(0, m.index - 40);
    const ctx = txt.slice(start, m.index + 60).replace(/[^\x20-\x7E]/g, '.').trim();
    found.add(ctx);
  }
  console.log('\n=== "' + p + '" 上下文(前 12):');
  [...found].slice(0, 12).forEach(c => console.log(' ', c));
}
