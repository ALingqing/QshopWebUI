// 查找 H2 驱动所在位置
import { execSync } from 'child_process';
import fs from 'fs';

const out = execSync('tar -tf "G:/p/plugins/QuickShop-Hikari-6.3.0.0-SNAPSHOT-4.jar"', { encoding: 'buffer', maxBuffer: 512 * 1024 * 1024 }).toString('utf8');
const lines = out.split(/\r?\n/);
console.log('QuickShop jar 内 org/h2/ 类数量:', lines.filter(l => l.startsWith('org/h2/')).length);
console.log('QuickShop jar 内 h2 Driver:', lines.filter(l => /org\/h2\/.*Driver\.class/.test(l)).slice(0, 3).join(', ') || '无');

// 查 jar 内 libraries 声明（Hikari plugin.yml 可能有 libraries:）
const plugYml = execSync('tar -xOf "G:/p/plugins/QuickShop-Hikari-6.3.0.0-SNAPSHOT-4.jar" plugin.yml', { encoding: 'utf8' });
console.log('\n=== plugin.yml:');
console.log(plugYml.slice(0, 1500));
