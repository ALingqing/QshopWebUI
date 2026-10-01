// 列出 26.2 jar 内 lang 文件
import { execSync } from 'child_process';
import path from 'path';

const jar = path.join(process.env.TEMP, 'mc-26.2-client.jar');
const out = execSync('tar -tf "' + jar + '"', { encoding: 'buffer', maxBuffer: 64 * 1024 * 1024 }).toString('utf8');
const lines = out.split(/\r?\n/);
console.log('总条目:', lines.length);
console.log('\nlang 相关:');
lines.filter(l => l.includes('lang/') && l.endsWith('.json')).slice(0, 10).forEach(l => console.log(' ', l));
console.log('\nzh 相关:');
lines.filter(l => l.toLowerCase().includes('zh')).slice(0, 10).forEach(l => console.log(' ', l));
console.log('\n语言文件都放哪了? 所有 .lang 或 lang json:');
lines.filter(l => /lang|\.lang$/.test(l)).slice(0, 30).forEach(l => console.log(' ', l));
