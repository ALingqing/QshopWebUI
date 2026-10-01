// 1) export zip 内容；2) QS cache 目录；3) versions 下真实服务端 jar 搜 h2；4) QuickShop jar 数据库类里的 h2 引用
import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';

// 1) export zip
console.log('=== export zip 内容:');
try {
  const out = execSync('tar -tf "g:/p/plugins/QuickShop-Hikari/export-1772019745453.zip"', { encoding: 'buffer', maxBuffer: 64 * 1024 * 1024 }).toString('utf8');
  console.log(out.trim());
} catch (e) { console.log('读取失败: ' + e.message); }

// 2) QS cache
console.log('\n=== QS cache 目录:');
const walk = (dir, depth, prefix) => {
  if (depth > 3) return;
  for (const f of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, f.name);
    if (f.isDirectory()) { console.log(prefix + f.name + '/'); walk(p, depth + 1, prefix + '  '); }
    else console.log(prefix + f.name + ' (' + fs.statSync(p).size + ')');
  }
};
walk('g:/p/plugins/QuickShop-Hikari/cache', 0, '  ');

// 3) versions 下 jar 搜 h2（这次不 swallow）
console.log('\n=== versions/ 服务端 jar 搜 org/h2:');
const vdir = 'g:/p/versions';
for (const f of fs.readdirSync(vdir)) {
  const p = vdir + '/' + f;
  if (fs.statSync(p).isDirectory()) {
    for (const g of fs.readdirSync(p)) {
      if (!g.endsWith('.jar')) continue;
      const jar = p + '/' + g;
      try {
        const out = execSync(`tar -tf "${jar}"`, { encoding: 'buffer', maxBuffer: 1024 * 1024 * 1024 }).toString('utf8');
        const h2 = out.split(/\r?\n/).filter(l => l.startsWith('org/h2/')).length;
        console.log('  ' + jar + ' → org/h2 类: ' + h2 + ', 总条目: ' + out.split(/\r?\n/).length);
      } catch (e) { console.log('  ' + jar + ' → 扫描失败: ' + e.message.slice(0, 100)); }
    }
  }
}
