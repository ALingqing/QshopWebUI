// 对比 webroot/item 与 d:\QshopWebUI\item 的图片差异
import fs from 'fs';
import path from 'path';

const root = path.resolve(import.meta.dirname, '..');
const a = path.join(root, 'webroot', 'item');
const b = 'd:/QshopWebUI/item';

const list = (d) => fs.existsSync(d) ? new Set(fs.readdirSync(d).filter(f => f.endsWith('.png'))) : new Set();

const A = list(a);
const B = list(b);
console.log('webroot/item PNG 数:', A.size);
console.log('d:/QshopWebUI/item PNG 数:', B.size);

const onlyB = [...B].filter(f => !A.has(f));
const onlyA = [...A].filter(f => !B.has(f));
console.log('\n仅存在于 d:/QshopWebUI/item (webroot 缺):', onlyB.length);
console.log(onlyB.slice(0, 30).join(', ') + (onlyB.length > 30 ? ' ...' : ''));
console.log('\n仅存在于 webroot/item:', onlyA.length);
console.log(onlyA.slice(0, 30).join(', ') + (onlyA.length > 30 ? ' ...' : ''));

// 检查 failed-textures.json（之前 PowerShell 误报的 509 个）到底哪些真的不在 webroot
const failedPath = path.join(root, 'tools', 'failed-textures.json');
if (fs.existsSync(failedPath)) {
  const failed = JSON.parse(fs.readFileSync(failedPath, 'utf8'));
  const arr = Array.isArray(failed) ? failed : (failed.failed || failed.items || []);
  console.log('\nfailed-textures.json 条目数:', arr.length);
  const sample = arr.slice(0, 5);
  console.log('样例:', JSON.stringify(sample));
}
