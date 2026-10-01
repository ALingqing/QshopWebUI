// 在 QuickShop H2 数据库中搜索玩家头皮肤数据特征
import fs from 'fs';

const f = 'g:/p/plugins/QuickShop-Hikari/shops.mv.db';
const buf = fs.readFileSync(f);
console.log('文件大小:', (buf.length / 1048576).toFixed(2), 'MB');

const search = (label, keyword) => {
  const k = Buffer.from(keyword, 'utf8');
  let count = 0, firstIdx = -1, i = 0;
  while ((i = buf.indexOf(k, i)) !== -1) {
    if (count === 0) firstIdx = i;
    count++;
    i += k.length;
    if (count > 500) break;
  }
  console.log(`[${label}] "${keyword}": ${count} 处` + (firstIdx >= 0 ? ` 首次@${firstIdx}` : ''));
  return { count, firstIdx };
};

search('材质', 'PLAYER_HEAD');
search('材质2', 'PLAYERHEAD');
search('皮肤', 'SkullOwner');
search('皮肤2', 'skull_owner');
search('profile', 'profile');
search('textures', 'textures');
search('mineskin', 'mineskin');
search('Value', 'Value');
search('DataVersion', 'DataVersion');

// 如果有 SkullOwner，打印一处上下文
const idx = buf.indexOf(Buffer.from('SkullOwner'));
if (idx >= 0) {
  console.log('\nSkullOwner 上下文（前 400 字节）:');
  console.log(buf.slice(Math.max(0, idx - 200), idx + 600).toString('latin1').replace(/[^\x20-\x7E]/g, '.'));
}
