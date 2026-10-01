// 生成拼音索引：pinyin_zh_cn.json  {中文: "全拼|首字母"}
// 数据源：material/enchantment/potion/custom_lang 中文表
// 用法: node tools/generate-pinyin.mjs
import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';

const root = path.resolve(import.meta.dirname, '..');
const resDir = path.join(root, 'src', 'main', 'resources');

// 从临时目录加载 pinyin-pro
const tmp = path.join(process.env.TEMP, 'qsw-pinyin');
if (!fs.existsSync(path.join(tmp, 'node_modules', 'pinyin-pro'))) {
  console.log('安装 pinyin-pro ...');
  execSync('npm install pinyin-pro --no-audit --no-fund --loglevel=error', { cwd: tmp, stdio: 'inherit' });
}
const { createRequire } = await import('module');
const require2 = createRequire(path.join(tmp, 'x.js'));
const { pinyin } = require2('pinyin-pro');

const read = (p) => JSON.parse(fs.readFileSync(p, 'utf8').replace(/^\uFEFF/, ''));
const sources = ['material_zh_cn.json', 'enchantment_zh_cn.json', 'potion_zh_cn.json', 'custom_lang_zh_cn.json'];

const words = new Set();
for (const f of sources) {
  const data = read(path.join(resDir, f));
  for (const v of Object.values(data)) {
    if (typeof v !== 'string') continue;
    const clean = v.replace(/§./g, '').trim();
    if (/[\u4e00-\u9fa5]/.test(clean) && clean.length <= 40) words.add(clean);
  }
}
console.log('收集中文词:', words.size);

const out = {};
let skipped = 0;
for (const w of words) {
  try {
    const arr = pinyin(w, { toneType: 'none', type: 'array' });
    const fp = arr.join('').toLowerCase().replace(/[^a-z0-9]/g, '');
    const ini = arr.map(s => (s.toLowerCase().replace(/[^a-z0-9]/g, '')[0] || '')).join('');
    if (!fp) { skipped++; continue; }
    out[w] = fp + '|' + ini;
  } catch (e) {
    skipped++;
  }
}

const sorted = {};
for (const k of Object.keys(out).sort()) sorted[k] = out[k];
fs.writeFileSync(path.join(resDir, 'pinyin_zh_cn.json'), JSON.stringify(sorted), 'utf8');
console.log('已写入 pinyin_zh_cn.json:', Object.keys(sorted).length, '条（跳过', skipped, '）');
console.log('抽查: 蜘蛛 =', sorted['蜘蛛'], '/ 铁剑 =', sorted['铁剑'], '/ 金合欢木台阶 =', sorted['金合欢木台阶']);
