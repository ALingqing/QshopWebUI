// 从 client.jar + 名称映射修复缺失纹理
// 用法: node tools/fix-missing-textures.mjs
import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import os from 'os';

const root = path.resolve(import.meta.dirname, '..');
const itemDir = path.join(root, 'webroot', 'item');
const jar = path.join(process.env.TEMP, 'mc-1.21.11-client.jar');
const tmp = path.join(os.tmpdir(), 'qsw-tex');
const read = (p) => fs.readFileSync(p, 'utf8').replace(/^\uFEFF/, '');

// 1. 解压 textures
fs.rmSync(tmp, { recursive: true, force: true });
fs.mkdirSync(tmp, { recursive: true });
console.log('解压 textures ...');
execSync(`tar -xf "${jar}" -C "${tmp}" assets/minecraft/textures`);

// 2. 索引: basename -> 路径 (item 优先)
const texRoot = path.join(tmp, 'assets', 'minecraft', 'textures');
const index = new Map();
const walk = (d) => {
  for (const f of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, f.name);
    if (f.isDirectory()) walk(p);
    else if (f.name.endsWith('.png')) {
      const key = f.name.toLowerCase();
      const rel = path.relative(texRoot, p).replace(/\\/g, '/');
      const prev = index.get(key);
      // item/ 优先于 block/ 优先于其它
      const rank = (r) => (r.startsWith('item/') ? 0 : r.startsWith('block/') ? 1 : 2);
      if (!prev || rank(rel) < rank(prev)) index.set(key, p);
    }
  }
};
walk(texRoot);
console.log('索引贴图数:', index.size);

// 3. 缺失列表
const missing = JSON.parse(read(path.join(root, 'tools', 'missing-textures.json')));
const have = new Set(fs.readdirSync(itemDir).filter(f => f.endsWith('.png')).map(f => f.toLowerCase()));

// 4. 候选链
const WOODS = ['acacia', 'birch', 'cherry', 'dark_oak', 'jungle', 'mangrove', 'oak', 'pale_oak', 'spruce', 'crimson', 'warped', 'bamboo', 'bamboo_mosaic'];
function candidates(name) {
  const out = [name];
  const push = (s) => { if (s && !out.includes(s)) out.push(s); };

  if (/_pottery_shard$/.test(name)) push(name.replace(/_pottery_shard$/, '_pottery_sherd'));
  if (/_wall_hanging_sign$/.test(name)) push(name.replace(/_wall_hanging_sign$/, '_hanging_sign'));
  if (/_wall_sign$/.test(name)) push(name.replace(/_wall_sign$/, '_sign'));
  if (/_hanging_sign$/.test(name)) push(name.replace(/_hanging_sign$/, '_sign'));
  if (/_wood$/.test(name)) push(name.replace(/_wood$/, '_log'));

  const m = name.match(/^(.*)_(button|fence_gate|fence|pressure_plate|slab|stairs|wall)$/);
  if (m) {
    const base = m[1];
    if (WOODS.includes(base)) {
      push(base + '_planks');
      push(base === 'bamboo_mosaic' ? 'bamboo_planks' : base + '_log');
    }
    push(base);                        // andesite_slab -> andesite
    push(base + '_planks');            // stone_slab -> stone_planks(通常无)
    push(base + '_bricks');
    push('polished_' + base);
    push('cobbled_' + base);
  }
  if (/^bamboo_mosaic_/.test(name)) push('bamboo_planks');
  // 常见材质变体
  push(name.replace(/_block$/, ''));
  return out;
}

// 5. 修复
let fixed = 0, still = [];
for (const entry of missing) {
  const img = entry.img;
  if (have.has(img.toLowerCase())) continue;
  let done = false;
  for (const c of candidates(img)) {
    const key = (c + '.png').toLowerCase();
    const src = index.get(key);
    if (src) {
      fs.copyFileSync(src, path.join(itemDir, img + '.png'));
      fixed++;
      done = true;
      break;
    }
  }
  if (!done) still.push(entry);
}

console.log('\n修复:', fixed, '  仍缺:', still.length);
fs.writeFileSync(path.join(root, 'tools', 'still-missing.json'), JSON.stringify(still, null, 1), 'utf8');
console.log('仍缺清单已写入 tools/still-missing.json');
console.log('样例(仍缺):', still.slice(0, 40).map(x => x.img).join(', '));
fs.rmSync(tmp, { recursive: true, force: true });
