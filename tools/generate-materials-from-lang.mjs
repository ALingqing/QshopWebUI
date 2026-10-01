// 从 Minecraft 服务端语言文件 zh_cn.json 生成 material_zh_cn.json
// 用法: node tools/generate-materials-from-lang.mjs [语言文件路径]
import fs from 'node:fs';

const langPath = process.argv[2] || 'g:/p/cache/leaves/1.21.11/lang/zh_cn.json';
const outPath = 'd:/QshopWebUI/paper-plugin/src/main/resources/material_zh_cn.json';

const lang = JSON.parse(fs.readFileSync(langPath, 'utf8'));
const map = {};

// 先 block 后 item（item 覆盖同名）
for (const prefix of ['block', 'item']) {
  for (const [key, value] of Object.entries(lang)) {
    const m = key.match(new RegExp(`^${prefix}\\.minecraft\\.([a-z0-9_]+)$`));
    if (!m) continue;
    if (typeof value !== 'string' || value.length === 0) continue;
    map[m[1].toUpperCase()] = value;
  }
}

// 按键排序输出，便于 diff
const sorted = {};
for (const k of Object.keys(map).sort()) sorted[k] = map[k];

fs.writeFileSync(outPath, JSON.stringify(sorted), 'utf8');
console.log('生成条目数:', Object.keys(sorted).length);
console.log('抽查:', sorted['DIAMOND'], '/', sorted['DIAMOND_SWORD'], '/', sorted['COPPER_CHEST'], '/', sorted['SULFUR']);
