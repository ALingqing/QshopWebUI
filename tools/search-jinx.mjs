// 在新上传的 shops.mv.db 中搜索 jinx
import fs from 'fs';

const p = 'c:/Users/aqing/Downloads/shops.mv.db';
const buf = fs.readFileSync(p);
console.log('文件大小:', (buf.length / 1024 / 1024).toFixed(2), 'MB');
console.log('文件头 16 字节:', JSON.stringify(buf.slice(0, 16).toString('latin1')));

const txt = buf.toString('latin1');

// 1) 搜 jinx（不区分大小写）
const re = /jinx/gi;
let m, count = 0;
const hits = [];
while ((m = re.exec(txt)) !== null) {
  count++;
  const start = Math.max(0, m.index - 120);
  const ctx = txt.slice(start, m.index + 120).replace(/[^\x20-\x7E]/g, '·');
  hits.push('@' + m.index + ': ' + ctx);
  if (count >= 20) break;
}
console.log('\njinx 出现次数(前20):', count);
hits.forEach(h => console.log('\n' + h));

// 2) 把文件里所有类似玩家名的可见字符串找出来（找与 jinx 相近的名字）
const names = new Set();
for (const mm of txt.matchAll(/[A-Za-z0-9_]{3,16}/g)) {
  const s = mm[0];
  if (/jin|inx/i.test(s)) names.add(s);
}
console.log('\n包含 jin/inx 的可见标识串:', [...names].slice(0, 40).join(', '));
