// 检查 AuthMe 配置包内容与数据库类型
import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';

const zip = 'C:/Users/aqing/Desktop/AuthMe-6.0.0配置包-v3.zip';
console.log('===== AuthMe 配置包 zip =====');
try {
  const out = execSync('tar -tf "' + zip + '"', { encoding: 'buffer', maxBuffer: 64 * 1024 * 1024 }).toString('utf8');
  const lines = out.split(/\r?\n/).filter(l => l.trim());
  console.log('条目数:', lines.length);
  lines.slice(0, 40).forEach(l => console.log(' ', l));
} catch (e) { console.log('读取失败:', e.message); }

console.log('\n===== AuthMeReReloaded 文件夹 =====');
try {
  const dir = 'C:/Users/aqing/Desktop/AuthMeReReloaded';
  const walk = (d, p = '', depth = 0) => {
    if (depth > 2) return;
    for (const f of fs.readdirSync(d).slice(0, 30)) {
      const full = path.join(d, f);
      const isDir = fs.statSync(full).isDirectory();
      console.log(' ', p + f + (isDir ? '/' : ''));
      if (isDir && depth < 2) walk(full, p + '  ', depth + 1);
    }
  };
  walk(dir);
} catch (e) { console.log('读取失败:', e.message); }
