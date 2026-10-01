// 查 logs 里的 h2 加载痕迹 + libraries/com 下 h2database + booster 配置
import fs from 'fs';
import path from 'path';

console.log('=== libraries/com 子目录（找 h2database）:');
const com = 'g:/p/libraries/com';
for (const f of fs.readdirSync(com)) console.log('  ' + f);

console.log('\n=== spigotlibrarybooster.properties:');
console.log(fs.readFileSync('g:/p/spigotlibrarybooster.properties', 'utf8').slice(0, 1200));

console.log('\n=== logs/latest.log 中 h2 相关行:');
const log = fs.readFileSync('g:/p/logs/latest.log', 'utf8');
const lines = log.split(/\r?\n/);
for (const l of lines) {
  if (/h2|quickshop.*(database|h2)|driver/i.test(l)) console.log('  ' + l.slice(0, 200));
}
