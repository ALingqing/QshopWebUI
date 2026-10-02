// 深挖 Jinx（f7ec5348-e5f3-303f-9e5d-f0dfd5a60801）的商店与交易
import fs from 'fs';

const p = 'c:/Users/aqing/Downloads/shops.mv.db';
const buf = fs.readFileSync(p);
const txt = buf.toString('latin1');
const UUID = 'f7ec5348-e5f3-303f-9e5d-f0dfd5a60801';

// 全部出现位置
const positions = [];
let idx = -1;
while ((idx = txt.indexOf(UUID, idx + 1)) !== -1) positions.push(idx);
console.log('Jinx UUID 出现次数:', positions.length);

// 逐个看上下文（找商店特征：symbolLink / price / item yaml / stacks / owner）
for (const pos of positions.slice(0, 40)) {
  const start = Math.max(0, pos - 350);
  const end = Math.min(txt.length, pos + 350);
  let ctx = txt.slice(start, end).replace(/[^\x20-\x7E]/g, '·');
  const features = [];
  if (/symbolLink|;-?\d+;-?\d+;-?\d+;/.test(ctx)) features.push('位置');
  if (/price/.test(ctx)) features.push('价格');
  if (/minecraft:/.test(ctx)) features.push('物品');
  if (/BUYING|SELLING/.test(ctx)) features.push('类型');
  if (pos < 1000000) features.push('早期区域');
  console.log('\n[特征: ' + (features.join(',') || '无') + '] @' + pos);
  console.log('  ' + ctx.slice(0, 700));
}
