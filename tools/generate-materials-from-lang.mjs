// 从 Minecraft 服务端语言文件 zh_cn.json 生成：
//   material_zh_cn.json     物体中文名表（block + item）
//   enchantment_zh_cn.json  附魔中文名表
// 用法: node tools/generate-materials-from-lang.mjs [语言文件路径]
import fs from 'node:fs';

const langPath = process.argv[2] || 'g:/p/cache/leaves/1.21.11/lang/zh_cn.json';
const outMaterials = 'd:/QshopWebUI/paper-plugin/src/main/resources/material_zh_cn.json';
const outEnchants = 'd:/QshopWebUI/paper-plugin/src/main/resources/enchantment_zh_cn.json';
const outPotions = 'd:/QshopWebUI/paper-plugin/src/main/resources/potion_zh_cn.json';

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
fs.writeFileSync(outMaterials, JSON.stringify(sorted), 'utf8');
console.log('物体中文表:', Object.keys(sorted).length, '条');

// ---- 附魔名 ----
const ench = {};
for (const [key, value] of Object.entries(lang)) {
  const m = key.match(/^enchantment\.minecraft\.([a-z0-9_]+)$/);
  if (!m) continue;
  if (typeof value !== 'string' || value.length === 0) continue;
  ench[m[1]] = value;
}
const sortedEnch = {};
for (const k of Object.keys(ench).sort()) sortedEnch[k] = ench[k];
fs.writeFileSync(outEnchants, JSON.stringify(sortedEnch), 'utf8');
console.log('附魔中文表:', Object.keys(sortedEnch).length, '条');

// ---- 药水 / 喷溅 / 滞留 / 药箭 名称（键：容器:effectId） ----
const potion = {};
const potionGroups = [
  ['potion', 'item.minecraft.potion.effect.'],
  ['splash', 'item.minecraft.splash_potion.effect.'],
  ['lingering', 'item.minecraft.lingering_potion.effect.'],
  ['tipped', 'item.minecraft.tipped_arrow.effect.'],
];
for (const [prefix, langPrefix] of potionGroups) {
  for (const [key, value] of Object.entries(lang)) {
    if (!key.startsWith(langPrefix)) continue;
    const id = key.substring(langPrefix.length);
    if (!/^[a-z0-9_]+$/.test(id)) continue;
    if (typeof value !== 'string' || value.length === 0) continue;
    potion[prefix + ':' + id] = value;
  }
}
const sortedPotion = {};
for (const k of Object.keys(potion).sort()) sortedPotion[k] = potion[k];
fs.writeFileSync(outPotions, JSON.stringify(sortedPotion), 'utf8');
console.log('药水名称表:', Object.keys(sortedPotion).length, '条');

console.log('抽查:', sorted['ENCHANTED_BOOK'], '/', sortedEnch['sharpness'], '/', sortedPotion['potion:healing'], '/', sortedPotion['splash:strong_healing']);
