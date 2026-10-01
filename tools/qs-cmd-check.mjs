import { execSync } from 'child_process';
const out = execSync('tar -tf "G:/p/plugins/QuickShop-Hikari-6.3.0.0-SNAPSHOT-4.jar"', { encoding: 'buffer', maxBuffer: 512 * 1024 * 1024 }).toString('utf8');
const lines = out.split(/\r?\n/);
console.log('=== export 相关类:');
console.log(lines.filter(l => /export/i.test(l) && l.endsWith('.class')).join('\n') || '无');
console.log('\n=== command 子命令类（前30）:');
console.log(lines.filter(l => /\/command\/.*SubCommand.*\.class$/.test(l)).slice(0, 40).join('\n'));

// 日志里玩家/控制台使用 qs 的记录
import fs from 'fs';
const logs = ['g:/p/logs/latest.log'];
for (const lp of logs) {
  if (!fs.existsSync(lp)) { console.log('\n' + lp + ' 不存在'); continue; }
  const t = fs.readFileSync(lp, 'utf8').split(/\r?\n/);
  console.log('\n=== ' + lp + ' 中 /qs 命令记录（前15）:');
  let n = 0;
  for (const l of t) {
    if (/issued server command: \/qs\b|issued server command: \/quickshop\b/.test(l)) {
      console.log('  ' + l.slice(0, 180));
      if (++n >= 15) break;
    }
  }
  if (!n) console.log('  （无）');
}
