import { execSync } from 'child_process';
const zip = 'G:/p/plugins/QuickShop-Hikari/export-1772019745453.zip';
const csv = execSync('tar -xOf "' + zip + '" players.csv', { encoding: 'utf8' });
const hits = csv.split(/\r?\n/).filter(l => /jinx/i.test(l));
console.log('players.csv 中匹配 jinx:', hits.length ? hits.join(' | ') : '（无）');

// 商店数据里也找找（OWNER 列）
const data = execSync('tar -xOf "' + zip + '" data.csv', { encoding: 'utf8' });
const ownerUuid = new Map();
for (const l of data.split(/\r?\n/).slice(1)) {
  const m = l.match(/^(\d+),([0-9a-f-]{36}),/);
  if (m) ownerUuid.set(m[2], (ownerUuid.get(m[2]) || 0) + 1);
}
console.log('商店所有者数:', ownerUuid.size);
// 用 players.csv 映射看有没有 jinx 拥有商店
const nameBy = {};
for (const l of csv.split(/\r?\n/).slice(1)) {
  const p = l.split(',');
  if (p.length >= 3) nameBy[p[0].toLowerCase()] = p[2];
}
for (const [u, n] of Object.entries(nameBy)) {
  if (/jinx/i.test(n)) console.log('玩家', n, '(' + u + ') 拥有商店数:', ownerUuid.get(u) || 0);
}
