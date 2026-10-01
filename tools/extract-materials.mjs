// 从 server.js 提取 MATERIAL_ZH_CN 映射表 → material_zh_cn.json
import fs from 'node:fs';

const srcPath = 'd:/QshopWebUI/server.js';
const outPath = 'd:/QshopWebUI/paper-plugin/src/main/resources/material_zh_cn.json';

const src = fs.readFileSync(srcPath, 'utf8');
const lines = src.split(/\r?\n/);

let start = -1, end = -1;
for (let i = 0; i < lines.length; i++) {
  if (/const\s+MATERIAL_ZH_CN\s*=\s*\{/.test(lines[i])) { start = i; break; }
}
if (start < 0) throw new Error('未找到 MATERIAL_ZH_CN');

for (let i = start; i < lines.length; i++) {
  if (/^\};/.test(lines[i])) { end = i; break; }
}
if (end < 0) throw new Error('未找到表结束');

const block = lines.slice(start + 1, end).join('\n');
const re = /([A-Z][A-Z0-9_]*)\s*:\s*'((?:[^'\\]|\\.)*)'/g;
const map = {};
let m, n = 0;
while ((m = re.exec(block))) {
  map[m[1]] = m[2].replace(/\\'/g, "'").replace(/\\\\/g, '\\');
  n++;
}

fs.writeFileSync(outPath, JSON.stringify(map), 'utf8');
console.log('提取条目数:', n);
console.log('示例:', map['DIAMOND'], '/', map['DIAMOND_SWORD'], '/', map['GRASS_BLOCK']);
