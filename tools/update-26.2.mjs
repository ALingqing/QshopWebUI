// 用 Minecraft 26.2 更新材质中文表 + 补全物品贴图
// 1) 26.2 en_us.json → 材质全集（对比现有表找新增）
// 2) 新增材质从 zh_cn.json(用户语言文件) 查中文，查不到用英文
// 3) 全量检查 webroot/item 缺图 → 从 26.2 贴图 + 映射规则补
import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import os from 'os';

const root = path.resolve(import.meta.dirname, '..');
const itemDir = path.join(root, 'webroot', 'item');
const resDir = path.join(root, 'src', 'main', 'resources');
const tmp = path.join(os.tmpdir(), 'qsw-tex26');
const jar = path.join(process.env.TEMP, 'mc-26.2-client.jar');
const zhPath = 'g:/p/cache/leaves/26.1.2/lang/zh_cn.json';
const read = (p) => fs.readFileSync(p, 'utf8').replace(/^\uFEFF/, '');

// ---- 解压 en_us.json ----
const langDir = path.join(tmp, 'assets', 'minecraft', 'lang');
if (!fs.existsSync(path.join(langDir, 'en_us.json'))) {
  fs.mkdirSync(langDir, { recursive: true });
  execSync(`tar -xf "${jar}" -C "${tmp}" assets/minecraft/lang/en_us.json`);
}
const en = JSON.parse(read(path.join(langDir, 'en_us.json')));
const zh = JSON.parse(read(zhPath));

// ---- 1. 26.2 材质全集 ----
const enMats = {}; // MATERIAL -> en name
for (const prefix of ['block', 'item']) {
  for (const [key, value] of Object.entries(en)) {
    const m = key.match(new RegExp(`^${prefix}\\.minecraft\\.([a-z0-9_]+)$`));
    if (!m || typeof value !== 'string' || !value) continue;
    enMats[m[1].toUpperCase()] = value;
  }
}
console.log('26.2 材质总数:', Object.keys(enMats).length);

const zhOf = (id) => zh['block.minecraft.' + id] || zh['item.minecraft.' + id] || null;

const current = JSON.parse(read(path.join(resDir, 'material_zh_cn.json')));
console.log('现有中文表:', Object.keys(current).length, '条');

const merged = { ...current };
let added = 0, addedWithZh = 0;
for (const [mat, enName] of Object.entries(enMats)) {
  if (merged[mat]) continue;
  const id = mat.toLowerCase();
  const zhName = zhOf(id);
  merged[mat] = zhName || enName;
  added++;
  if (zhName) addedWithZh++;
}
const sorted = {};
for (const k of Object.keys(merged).sort()) sorted[k] = merged[k];
fs.writeFileSync(path.join(resDir, 'material_zh_cn.json'), JSON.stringify(sorted), 'utf8');
console.log('新增材质:', added, '（其中', addedWithZh, '条有中文翻译），总表:', Object.keys(sorted).length);
const newNoZh = Object.entries(enMats).filter(([m]) => !current[m] && !zhOf(m.toLowerCase())).map(([m, e]) => m + '=' + e);
console.log('无中文的新材质(前 30):', newNoZh.slice(0, 30).join(', '));

// ---- 2. 附魔/药水 检查 ----
const enEnch = Object.keys(en).filter(k => /^enchantment\.minecraft\.[a-z0-9_]+$/.test(k)).map(k => k.split('.').pop());
const curEnch = JSON.parse(read(path.join(resDir, 'enchantment_zh_cn.json')));
const newEnch = enEnch.filter(e => !curEnch[e]);
console.log('\n附魔: 26.2 共', enEnch.length, ', 现有表', Object.keys(curEnch).length, ', 新增', newEnch.length, newEnch.slice(0, 10).join(', '));
if (newEnch.length) {
  for (const id of newEnch) curEnch[id] = zh['enchantment.minecraft.' + id] || id;
  fs.writeFileSync(path.join(resDir, 'enchantment_zh_cn.json'), JSON.stringify(curEnch), 'utf8');
  console.log('附魔表已更新');
}

// ---- 3. 贴图索引 ----
const texRoot = path.join(tmp, 'assets', 'minecraft', 'textures');
const byRel = new Map();
const walk = (d) => {
  for (const f of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, f.name);
    if (f.isDirectory()) walk(p);
    else if (f.name.endsWith('.png')) byRel.set(path.relative(texRoot, p).replace(/\\/g, '/').toLowerCase(), p);
  }
};
walk(texRoot);

const have = new Set(fs.readdirSync(itemDir).filter(f => f.endsWith('.png')).map(f => f.replace(/\.png$/, '').toLowerCase()));

function findPath(name) {
  const n = name.toLowerCase();
  const hit = byRel.get('item/' + n + '.png') || byRel.get('block/' + n + '.png');
  if (hit) return hit;
  for (const suf of ['_side', '_front', '_top', '_end', '_stage0', '_still', '_0', '_up_middle']) {
    const p = byRel.get('block/' + n + suf + '.png') || byRel.get('item/' + n + suf + '.png');
    if (p) return p;
  }
  let best = null;
  for (const [rel, p] of byRel) {
    if ((rel.startsWith('block/' + n + '_') || rel.startsWith('item/' + n + '_')) && (!best || rel < best.rel)) best = { rel, p };
  }
  return best ? best.p : null;
}

const SPECIAL_26 = {
  'cinnabar_slab': 'block/cinnabar.png',
  'cinnabar_stairs': 'block/cinnabar.png',
  'cinnabar_wall': 'block/cinnabar.png',
  'sulfur_slab': 'block/sulfur.png',
  'sulfur_stairs': 'block/sulfur.png',
  'sulfur_wall': 'block/sulfur.png',
  'sulfur_spike': 'block/sulfur_spike_up_middle.png',
  'sulfur_cube_bucket': 'item/sulfur_cube_bucket.png',
  'sulfur_cube_spawn_egg': 'item/sulfur_cube_spawn_egg.png',
  'potted_golden_dandelion': 'block/golden_dandelion.png',
  'harness': 'entity/equipment/happy_ghast_body/white_harness.png',
  'debug_stick': null // 26.2 也没有贴图
};

function resolveName(img) {
  let n = img.toLowerCase();
  const m0 = n.match(/^waxed_(.+)$/); if (m0) n = m0[1];
  let m;
  if ((m = n.match(/^polished_(cinnabar|sulfur)_(slab|stairs|wall)$/))) n = 'polished_' + m[1];
  if ((m = n.match(/^(cinnabar|sulfur)_brick_(slab|stairs|wall)$/))) n = m[1] + '_bricks';
  if (SPECIAL_26[n] !== undefined) return SPECIAL_26[n] ? (byRel.get(SPECIAL_26[n]) || null) : null;
  if ((m = n.match(/^(.+)_brick_(slab|stairs|wall)$/))) n = m[1] + '_bricks';
  if ((m = n.match(/^(?:(exposed|oxidized|weathered)_)?copper_chest$/))) return byRel.get('entity/chest/' + (m[1] ? 'copper_' + m[1] : 'copper') + '.png') || null;
  if ((m = n.match(/^(?:(exposed|oxidized|weathered)_)?copper_golem_statue$/))) return byRel.get('entity/copper_golem/' + (m[1] ? m[1] + '_copper_golem' : 'copper_golem') + '.png') || null;
  n = n.replace(/_wall_head$/, '_head').replace(/_wall_skull$/, '_skull').replace(/_wall_torch$/, '_torch');
  if (/^smooth_quartz/.test(n)) return byRel.get('block/quartz_block_bottom.png') || null;
  if (/^smooth_red_sandstone/.test(n)) return byRel.get('block/red_sandstone_top.png') || null;
  if (/^smooth_sandstone/.test(n)) return byRel.get('block/sandstone_top.png') || null;
  if ((m = n.match(/^potted_(.+)$/))) n = m[1];
  n = n.replace(/^infested_/, '');
  if ((m = n.match(/^pottery_shard_(.+)$/))) n = m[1] + '_pottery_sherd';
  const p = findPath(n);
  if (p) return p;
  if ((m = n.match(/^(.+)_(slab|stairs|wall|button|fence_gate|fence|pressure_plate|door|trapdoor)$/))) return findPath(m[1]);
  return null;
}

// ---- 4. 全量补图 ----
let fixed = 0;
const missing = [];
for (const mat of Object.keys(sorted)) {
  const img = mat.toLowerCase().replace(/[^a-z0-9_-]/g, '');
  if (have.has(img)) continue;
  const src = resolveName(img);
  if (src) {
    fs.copyFileSync(src, path.join(itemDir, img + '.png'));
    have.add(img);
    fixed++;
  } else {
    missing.push(mat);
  }
}
console.log('\n补图:', fixed, '  无图材质:', missing.length);
console.log('无图列表:', missing.join(', '));
fs.writeFileSync(path.join(root, 'tools', 'no-image-materials.json'), JSON.stringify(missing, null, 1), 'utf8');
console.log('\nwebroot/item 最终 PNG 数:', fs.readdirSync(itemDir).filter(f => f.endsWith('.png')).length);
