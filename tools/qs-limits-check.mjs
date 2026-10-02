import { execSync } from 'child_process';
import fs from 'fs';

const out = execSync('tar -tf "G:/p/plugins/QuickShop-Hikari-6.3.0.0-SNAPSHOT-4.jar"', { encoding: 'buffer', maxBuffer: 512 * 1024 * 1024 }).toString('utf8');
const subs = out.split(/\r?\n/).filter(l => /SubCommand_.*\.class$/.test(l) && !l.includes('$')).map(l => l.replace(/.*SubCommand_/, '').replace('.class', ''));
console.log('=== QuickShop 全部子命令:');
console.log(subs.join(', '));

console.log('\n=== QS config.yml 400-500 行（limit: 10 附近）:');
const cfg = fs.readFileSync('g:/p/plugins/QuickShop-Hikari/config.yml', 'utf8').split(/\r?\n/);
for (let i = 425; i < 500 && i < cfg.length; i++) console.log((i + 1) + ': ' + cfg[i]);

console.log('\n=== 我们插件 config.yml:');
console.log(fs.readFileSync('d:/QshopWebUI/paper-plugin/src/main/resources/config.yml', 'utf8').slice(0, 1500));
