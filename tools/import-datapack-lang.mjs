// 导入数据包翻译：assets/*/lang/zh_cn.json → 插件内置翻译
// 用法: node tools/import-datapack-lang.mjs "<数据包zip>"
// 产物:
//   src/main/resources/custom_lang_zh_cn.json   全部翻译键（用于解析自定义物品名）
//   src/main/resources/enchantment_zh_cn.json   并入自定义附魔（不覆盖原版）
//   src/main/resources/potion_zh_cn.json        并入自定义药水（不覆盖原版）
import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import os from 'os';

const root = path.resolve(import.meta.dirname, '..');
const resDir = path.join(root, 'src', 'main', 'resources');
const zips = process.argv.slice(2);
if (!zips.length) { console.error('用法: node tools/import-datapack-lang.mjs "<zip路径>" ...'); process.exit(1); }

const read = (p) => JSON.parse(fs.readFileSync(p, 'utf8').replace(/^\uFEFF/, ''));
const customPath = path.join(resDir, 'custom_lang_zh_cn.json');
const custom = fs.existsSync(customPath) ? read(customPath) : {};
const ench = read(path.join(resDir, 'enchantment_zh_cn.json'));
const potion = read(path.join(resDir, 'potion_zh_cn.json'));

let totalKeys = 0, newKeys = 0, newEnch = 0, newPotion = 0;
const tmp = path.join(os.tmpdir(), 'qsw-datapack-lang');
fs.rmSync(tmp, { recursive: true, force: true });
fs.mkdirSync(tmp, { recursive: true });

for (const zip of zips) {
  console.log('\n===== ' + path.basename(zip) + ' =====');
  let fileList;
  try {
    fileList = execSync('tar -tf "' + zip + '"', { encoding: 'buffer', maxBuffer: 64 * 1024 * 1024 }).toString('utf8').split(/\r?\n/);
  } catch (e) { console.log('读取失败:', e.message); continue; }

  const langFiles = fileList.filter(f => /^assets\/.+\/lang\/zh_cn\.json$/.test(f.trim()));
  if (!langFiles.length) { console.log('无 zh_cn.json 翻译，跳过'); continue; }
  console.log('翻译文件:', langFiles.join(', '));

  execSync('tar -xf "' + zip + '" -C "' + tmp + '" ' + langFiles.map(f => '"' + f.trim() + '"').join(' '));

  // 先 dnt 命名空间后 minecraft（minecraft 放最后，避免覆盖？实际键不冲突）
  langFiles.sort((a, b) => a.includes('/minecraft/') ? 1 : b.includes('/minecraft/') ? -1 : 0);
  for (const lf of langFiles) {
    const data = read(path.join(tmp, lf.trim()));
    for (const [k, v] of Object.entries(data)) {
      if (typeof v !== 'string') continue;
      totalKeys++;
      if (!(k in custom)) { custom[k] = v; newKeys++; }
    }

    // 自定义附魔：enchantment.<ns>.<id>（读取端不带命名空间/不带子后缀）
    for (const [k, v] of Object.entries(data)) {
      const m = k.match(/^enchantment\.([a-z0-9_]+)\.([a-z0-9_]+)$/);
      if (!m || m[1] === 'minecraft') continue;
      if (ench[m[2]] === undefined) { ench[m[2]] = v; newEnch++; }
    }
    // 自定义药水
    const groups = [
      ['potion', 'item.minecraft.potion.effect.'],
      ['splash', 'item.minecraft.splash_potion.effect.'],
      ['lingering', 'item.minecraft.lingering_potion.effect.'],
      ['tipped', 'item.minecraft.tipped_arrow.effect.'],
    ];
    for (const [prefix, langPrefix] of groups) {
      for (const [k, v] of Object.entries(data)) {
        if (!k.startsWith(langPrefix)) continue;
        const id = k.substring(langPrefix.length);
        if (!/^[a-z0-9_]+$/.test(id)) continue;
        const key = prefix + ':' + id;
        if (potion[key] === undefined) { potion[key] = v; newPotion++; }
      }
    }
  }
}

const sortedCustom = {};
for (const k of Object.keys(custom).sort()) sortedCustom[k] = custom[k];
fs.writeFileSync(customPath, JSON.stringify(sortedCustom), 'utf8');
fs.writeFileSync(path.join(resDir, 'enchantment_zh_cn.json'), JSON.stringify(ench), 'utf8');
fs.writeFileSync(path.join(resDir, 'potion_zh_cn.json'), JSON.stringify(potion), 'utf8');

console.log('\n导入完成:');
console.log('  custom_lang_zh_cn.json:', Object.keys(sortedCustom).length, '条 (+' + newKeys + ')');
console.log('  enchantment_zh_cn.json:', Object.keys(ench).length, '条 (+' + newEnch + ')');
console.log('  potion_zh_cn.json:', Object.keys(potion).length, '条 (+' + newPotion + ')');
fs.rmSync(tmp, { recursive: true, force: true });
