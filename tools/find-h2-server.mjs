// 验证服务端 jar 是否自带 H2
import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';

const jars = [];
for (const f of fs.readdirSync('g:/p')) {
  if (f.toLowerCase().endsWith('.jar')) jars.push('g:/p/' + f);
}
console.log('服务端根目录 jar:', jars.join(', ') || '无');
for (const sub of ['versions', 'cache', 'libraries']) {
  const d = 'g:/p/' + sub;
  if (!fs.existsSync(d)) { console.log(sub + '/: 不存在'); continue; }
  const walk = (dir, depth) => {
    if (depth > 4) return;
    for (const f of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, f.name);
      if (f.isDirectory()) walk(p, depth + 1);
      else if (f.name.toLowerCase().endsWith('.jar')) jars.push(p);
    }
  };
  walk(d, 0);
}
console.log('\n全部找到的 jar 数:', jars.length);

for (const jar of jars) {
  try {
    const out = execSync(`tar -tf "${jar}"`, { encoding: 'buffer', maxBuffer: 512 * 1024 * 1024 }).toString('utf8');
    const h2 = out.split(/\r?\n/).filter(l => l.startsWith('org/h2/'));
    if (h2.length) {
      console.log('\n>>> ' + jar + ' 内含 org/h2 类:', h2.length);
      console.log('    Driver 示例:', h2.filter(l => l.includes('Driver')).slice(0, 5).join(', '));
    }
  } catch (e) { /* 跳过非 zip */ }
}
console.log('\n扫描完成');
