import { execSync } from 'child_process';
const y = execSync('tar -xOf "G:/p/plugins/QuickShop-Hikari-6.3.0.0-SNAPSHOT-4.jar" plugin.yml', { encoding: 'utf8' });
const i = y.indexOf('\ncommands:');
console.log(i >= 0 ? y.slice(i, i + 700) : '未找到 commands 段');
