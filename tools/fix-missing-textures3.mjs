// 第三轮修复：前缀/后缀映射 + 实体贴图特化
import fs from 'fs';
import path from 'path';
import os from 'os';

const root = path.resolve(import.meta.dirname, '..');
const itemDir = path.join(root, 'webroot', 'item');
const tmp = path.join(os.tmpdir(), 'qsw-tex');
const texRoot = path.join(tmp, 'assets', 'minecraft', 'textures');
const read = (p) => fs.readFileSync(p, 'utf8').replace(/^\uFEFF/, '');

const byRel = new Map();
const byName = new Map();
const rank = (r) => (r.startsWith('item/') ? 0 : r.startsWith('block/') ? 1 : 2);
const walk = (d) => {
  for (const f of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, f.name);
    if (f.isDirectory()) walk(p);
    else if (f.name.endsWith('.png')) {
      const rel = path.relative(texRoot, p).replace(/\\/g, '/').toLowerCase();
      byRel.set(rel, p);
      const base = f.name.toLowerCase();
      const prev = byName.get(base);
      if (!prev || rank(rel) < rank(path.relative(texRoot, prev).replace(/\\/g, '/').toLowerCase())) byName.set(base, p);
    }
  }
};
walk(texRoot);

const stillMissing = JSON.parse(read(path.join(root, 'tools', 'still-missing2.json')));
const have = new Set(fs.readdirSync(itemDir).filter(f => f.endsWith('.png')).map(f => f.toLowerCase()));

function findPath(name) {
  const n = name.toLowerCase();
  const hit = byRel.get('item/' + n + '.png') || byRel.get('block/' + n + '.png');
  if (hit) return hit;
  for (const suf of ['_side', '_front', '_top', '_end', '_stage0', '_still', '_0']) {
    const p = byRel.get('block/' + n + suf + '.png') || byRel.get('item/' + n + suf + '.png');
    if (p) return p;
  }
  let best = null;
  for (const [rel, p] of byRel) {
    if (rel.startsWith('block/' + n + '_') && (!best || rel < best.rel)) best = { rel, p };
  }
  if (best) return best.p;
  for (const [rel, p] of byRel) {
    if (rel.startsWith('item/' + n + '_') && (!best || rel < best.rel)) best = { rel, p };
  }
  return best ? best.p : null;
}

// 直接映射表（探明的实体/特殊贴图）
const DIRECT = {
  'enchanted_golden_apple': 'item/golden_apple.png',
  'scute': 'item/turtle_scute.png',
  'sign': 'item/oak_sign.png',
  'lodestone_compass': 'item/compass.png',
  'smithing_template': 'item/netherite_upgrade_smithing_template.png',
  'bubble_column': 'block/water_flow.png',
  'moss_carpet': 'block/moss_block.png',
  'moving_piston': 'block/piston_top.png',
  'piston_head': 'block/piston_top.png',
  'sticky_piston': 'block/piston_top_sticky.png',
  'heavy_weighted_pressure_plate': 'block/iron_block.png',
  'light_weighted_pressure_plate': 'block/gold_block.png',
  'lava_cauldron': 'block/cauldron.png',
  'water_cauldron': 'block/cauldron.png',
  'powder_snow_cauldron': 'block/cauldron.png',
  'decorated_pot': 'entity/decorated_pot/decorated_pot_base.png',
  'trapped_chest': 'entity/chest/trapped.png',
  'ender_chest': 'entity/chest/ender.png',
  'creeper_head': 'entity/creeper/creeper.png',
  'dragon_head': 'entity/enderdragon/dragon.png',
  'piglin_head': 'entity/piglin/piglin.png',
  'skeleton_skull': 'entity/skeleton/skeleton.png',
  'wither_skeleton_skull': 'entity/skeleton/wither_skeleton.png',
  'zombie_head': 'entity/zombie/zombie.png',
  'harness': 'entity/equipment/happy_ghast_body/white_harness.png',
  'bamboo_sapling': 'block/bamboo.png',
  'crimson_hyphae': 'block/crimson_stem.png',
  'warped_hyphae': 'block/warped_stem.png',
  'stripped_crimson_hyphae': 'block/stripped_crimson_stem.png',
  'stripped_warped_hyphae': 'block/stripped_warped_stem.png',
  'golden_dandelion': 'block/dandelion.png',
  'petrified_oak_slab': 'block/oak_planks.png'
};

// 确认放弃（客户端无任何相关贴图，等待更高版本）
const GIVE_UP = /^(cinnabar|sulfur|chiseled_cinnabar|chiseled_sulfur|polished_cinnabar|polished_sulfur|potent_sulfur|sulfur_spike|sulfur_cube|.*cinnabar.*|.*sulfur.*|cave_air|void_air|end_gateway|music_disc_bounce|set_spawn|player_head|player_wall_head)$/;

function resolveName(img) {
  let n = img.toLowerCase();
  // waxed_ 去前缀
  const unwax = n.replace(/^waxed_/, '');
  // 铜箱
  let m;
  if ((m = unwax.match(/^(?:(exposed|oxidized|weathered)_)?copper_chest$/))) {
    const st = m[1] ? 'copper_' + m[1] : 'copper';
    return byRel.get('entity/chest/' + st + '.png') || null;
  }
  if ((m = unwax.match(/^(?:(exposed|oxidized|weathered)_)?copper_golem_statue$/))) {
    const st = m[1] ? m[1] + '_copper_golem' : 'copper_golem';
    return byRel.get('entity/copper_golem/' + st + '.png') || null;
  }
  n = unwax;
  // 墙头/墙骷髅
  n = n.replace(/_wall_head$/, '_head').replace(/_wall_skull$/, '_skull');
  // 墙火把
  n = n.replace(/_wall_torch$/, '_torch').replace(/^wall_torch$/, 'torch');
  // 砖类 slab/stairs/wall
  if ((m = n.match(/^(.+)_brick_(slab|stairs|wall)$/))) n = m[1] + '_bricks';
  // smooth 系列
  if (/^smooth_quartz/.test(n)) return byRel.get('block/quartz_block_bottom.png') || null;
  if (/^smooth_red_sandstone/.test(n)) return byRel.get('block/red_sandstone_top.png') || null;
  if (/^smooth_sandstone/.test(n)) return byRel.get('block/sandstone_top.png') || null;
  // potted_ 去前缀
  if ((m = n.match(/^potted_(.+)$/))) n = m[1];
  // infested_ 去前缀
  n = n.replace(/^infested_/, '');
  // pottery_shard_X -> X_pottery_sherd
  if ((m = n.match(/^pottery_shard_(.+)$/))) n = m[1] + '_pottery_sherd';
  // 直接映射
  if (DIRECT[n]) return byRel.get(DIRECT[n]) || null;
  // 精确 + 多面 + 前缀
  let p = findPath(n);
  if (p) return p;
  // 通用后缀回退：X_slab/stairs/wall/button/fence... -> X
  if ((m = n.match(/^(.+)_(slab|stairs|wall|button|fence_gate|fence|pressure_plate|door|trapdoor)$/))) {
    p = findPath(m[1]);
    if (p) return p;
  }
  return null;
}

let fixed = 0;
const still = [];
const given = [];
for (const entry of stillMissing) {
  const img = entry.img;
  if (have.has(img.toLowerCase())) continue;
  if (GIVE_UP.test(img)) { given.push(entry); continue; }
  const src = resolveName(img);
  if (src) {
    fs.copyFileSync(src, path.join(itemDir, img + '.png'));
    fixed++;
  } else {
    still.push(entry);
  }
}

console.log('第三轮修复:', fixed, '  放弃(版本不存在):', given.length, '  仍缺:', still.length);
fs.writeFileSync(path.join(root, 'tools', 'still-missing3.json'), JSON.stringify(still, null, 1), 'utf8');
fs.writeFileSync(path.join(root, 'tools', 'give-up-textures.json'), JSON.stringify(given, null, 1), 'utf8');
console.log('\n仍缺:', still.map(x => x.img).join(', '));
console.log('\nwebroot/item 现有 PNG:', fs.readdirSync(itemDir).filter(f => f.endsWith('.png')).length);
