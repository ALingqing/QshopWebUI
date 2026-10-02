// 1) log_changes.csv 内容与 schema；2) log_others；3) 事件类
import { execSync } from 'child_process';
import fs from 'fs';

const zip = 'G:/p/plugins/QuickShop-Hikari/export-1772019745453.zip';
for (const f of ['log_changes.csv', 'log_changes.schema.json', 'log_others.csv', 'log_others.schema.json', 'log_purchase.schema.json']) {
  try {
    const txt = execSync('tar -xOf "' + zip + '" ' + f, { encoding: 'utf8' });
    console.log('=== ' + f + ':');
    console.log(txt.split(/\r?\n/).slice(0, 25).join('\n'));
    console.log('');
  } catch (e) { console.log(f + ': 读取失败\n'); }
}

// QuickShop jar 里的 Shop 事件类
const out = execSync('tar -tf "G:/p/plugins/QuickShop-Hikari-6.3.0.0-SNAPSHOT-4.jar"', { encoding: 'buffer', maxBuffer: 512 * 1024 * 1024 }).toString('utf8');
const evs = out.split(/\r?\n/).filter(l => /api\/event\/.*\.class$/.test(l) && !l.includes('$')).map(l => l.replace(/.*api\/event\//, '').replace('.class', ''));
console.log('=== QuickShop 事件类:');
console.log(evs.join('\n'));
