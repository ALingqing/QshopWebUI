// 同步 d:/QshopWebUI/item → webroot/item
import fs from 'fs';
import path from 'path';

const root = path.resolve(import.meta.dirname, '..');
const src = 'd:/QshopWebUI/item';
const dst = path.join(root, 'webroot', 'item');

let copied = 0;
for (const f of fs.readdirSync(src)) {
  if (!f.endsWith('.png')) continue;
  const s = path.join(src, f);
  const d = path.join(dst, f);
  if (!fs.existsSync(d)) {
    fs.copyFileSync(s, d);
    copied++;
  }
}
console.log('已复制', copied, '张缺失图片到 webroot/item');
console.log('webroot/item 现在共有:', fs.readdirSync(dst).filter(f => f.endsWith('.png')).length, '张 PNG');
