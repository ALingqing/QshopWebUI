// 从 QuickShop-Hikari jar 里查找交易事件类路径与方法
import { execSync } from 'child_process';

const jar = 'G:/p/plugins/QuickShop-Hikari-6.3.0.0-SNAPSHOT-4.jar';
const out = execSync('tar -tf "' + jar + '"', { encoding: 'buffer', maxBuffer: 128 * 1024 * 1024 }).toString('utf8');
const lines = out.split(/\r?\n/);

console.log('=== 事件类（含 Purchase/Success 的 class）:');
lines.filter(l => /event/i.test(l) && /\.class$/.test(l) && /purchase|success|economy/i.test(l))
  .slice(0, 40).forEach(l => console.log(' ', l));

console.log('\n=== 所有 economy 事件:');
lines.filter(l => l.includes('/event/economy/')).slice(0, 30).forEach(l => console.log(' ', l));

console.log('\n=== ShopSuccessPurchaseEvent 精确搜索:');
lines.filter(l => l.includes('ShopSuccessPurchaseEvent')).forEach(l => console.log(' ', l));
