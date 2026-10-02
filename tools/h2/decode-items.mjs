// 解码 Jinx 商店的 base64 物品数据
import zlib from 'zlib';

const samples = {
  '批量店(2元)物品A': 'H4sIAAAAAAAA/+NiYGBm4HZJLEkMSy0qzszPY2AQVudgYMpMYeDPzcxLTS5KTCuxSsvJzCthZmBNzi/NK2FgYGBkAACgpWgMOAAAAA==',
};
// 从结果文件再捞几个不同的物品 base64
import fs from 'fs';
let raw = fs.readFileSync('jinx-result3.txt');
let t;
if (raw[0] === 0xFF && raw[1] === 0xFE) t = raw.toString('utf16le').slice(1).split(/\r?\n/);
else if (raw[0] === 0xFE && raw[1] === 0xFF) t = raw.swap16().toString('utf16le').slice(1).split(/\r?\n/);
else t = raw.toString('utf8').split(/\r?\n/);
const items = new Map();
for (const l of t) {
  const m = l.match(/^\s+(?:ITEM|ENCODED) = (H4sI[A-Za-z0-9+/=]+)/);
  if (m) items.set(m[1], (items.get(m[1]) || 0) + 1);
}
console.log('=== Jinx 商店中的不同物品 base64 数量:', items.size);
const sorted = [...items.entries()].sort((a, b) => b[1] - a[1]);
for (const [b64, count] of sorted.slice(0, 8)) {
  try {
    const buf = zlib.gunzipSync(Buffer.from(b64, 'base64'));
    let txt = buf.toString('utf8');
    if (/[^\x20-\x7E\n]/.test(txt.slice(0, 200))) txt = 'HEX: ' + buf.slice(0, 80).toString('hex');
    console.log('\n=== 出现 ' + count + ' 次的物品 ===');
    console.log(txt.slice(0, 400).replace(/\n/g, ' | '));
  } catch (e) {
    console.log('\n解码失败: ' + e.message);
  }
}
