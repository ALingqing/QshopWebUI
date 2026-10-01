// 查看数据包 zip 内容结构
import { execSync } from 'child_process';
import fs from 'fs';

const files = [
  'C:/Users/aqing/Desktop/新建文件夹 (3)/Dungeons and Taverns v5.3.0 (1).zip',
  'C:/Users/aqing/Desktop/新建文件夹 (3)/Structory_v1.3.7.zip',
  'C:/Users/aqing/Desktop/新建文件夹 (3)/Trek 1.21-26.2 B0.6.2.zip'
];

for (const f of files) {
  console.log('\n========== ' + f.split('/').pop() + ' ==========');
  try {
    const out = execSync('tar -tf "' + f + '"', { encoding: 'buffer', maxBuffer: 64 * 1024 * 1024 }).toString('utf8');
    const lines = out.split(/\r?\n/).filter(l => l.trim());
    console.log('总条目:', lines.length);
    // 找 lang / json 重点文件
    const lang = lines.filter(l => /lang|zh_cn|en_us/i.test(l));
    console.log('语言文件:', lang.length ? '\n  ' + lang.slice(0, 10).join('\n  ') : '无');
    const pack = lines.find(l => l.endsWith('pack.mcmeta'));
    console.log('pack.mcmeta:', pack || '无');
    console.log('一级目录样例(前 15):');
    const top = new Set(lines.map(l => l.split('/')[0] + (l.includes('/') ? '/...' : '')));
    [...top].slice(0, 15).forEach(t => console.log(' ', t));
  } catch (e) {
    console.log('读取失败:', e.message);
  }
}
