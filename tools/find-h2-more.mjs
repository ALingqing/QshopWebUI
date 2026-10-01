// 1) libraries 目录树；2) QuickShop 数据库类；3) QuickShop jar 里的 jdbc:h2 字符串
import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';

console.log('=== g:/p 根目录内容:');
for (const f of fs.readdirSync('g:/p')) {
  const st = fs.statSync('g:/p/' + f);
  console.log((st.isDirectory() ? '[D] ' : '    ') + f);
}

for (const sub of ['libraries', 'versions', 'cache']) {
  const d = 'g:/p/' + sub;
  console.log('\n=== ' + sub + ':', fs.existsSync(d) ? '存在' : '不存在');
  if (fs.existsSync(d)) {
    const walk = (dir, depth, prefix) => {
      if (depth > 3) return;
      let items = fs.readdirSync(dir, { withFileTypes: true });
      for (const f of items.slice(0, 40)) {
        console.log(prefix + f.name + (f.isDirectory() ? '/' : ''));
        if (f.isDirectory()) walk(path.join(dir, f.name), depth + 1, prefix + '  ');
      }
    };
    walk(d, 0, '  ');
  }
}

console.log('\n=== QuickShop jar 内 database 相关类:');
const out = execSync('tar -tf "G:/p/plugins/QuickShop-Hikari-6.3.0.0-SNAPSHOT-4.jar"', { encoding: 'buffer', maxBuffer: 512 * 1024 * 1024 }).toString('utf8');
const lines = out.split(/\r?\n/);
console.log(lines.filter(l => /database/i.test(l)).slice(0, 40).join('\n'));
