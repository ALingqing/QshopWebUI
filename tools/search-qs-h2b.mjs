// 在 H2 数据库中搜索小写 SNBT 材质名与皮肤字段
import fs from 'fs';

const buf = fs.readFileSync('g:/p/plugins/QuickShop-Hikari/shops.mv.db');

const search = (label, keyword, showCtx = false) => {
  const k = Buffer.from(keyword, 'utf8');
  let count = 0, firstIdx = -1, i = 0;
  while ((i = buf.indexOf(k, i)) !== -1) {
    if (count === 0) firstIdx = i;
    count++;
    i += k.length;
    if (count > 1000) break;
  }
  console.log(`[${label}] "${keyword}": ${count} 处` + (firstIdx >= 0 ? ` 首次@${firstIdx}` : ''));
  if (showCtx && firstIdx >= 0) {
    console.log('  上下文:', buf.slice(firstIdx - 100, firstIdx + 500).toString('utf8').replace(/[\x00-\x1f]/g, '·').substring(0, 550));
  }
  return firstIdx;
};

search('小写材质', 'minecraft:player_head', true);
search('皮肤1', 'SkullOwner', true);
search('皮肤2', 'skull_owner', false);
search('皮肤3', 'profile', false);
search('皮肤4', 'textures', false);
search('属性', 'Properties', false);
search('minecraft:head', 'minecraft:head', false);
