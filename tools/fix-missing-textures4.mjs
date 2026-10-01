// 第四轮：最后 10 个
import fs from 'fs';
import path from 'path';
import os from 'os';

const root = path.resolve(import.meta.dirname, '..');
const itemDir = path.join(root, 'webroot', 'item');
const tmp = path.join(os.tmpdir(), 'qsw-tex');
const texRoot = path.join(tmp, 'assets', 'minecraft', 'textures');

const rel = (r) => path.join(texRoot, r);
const simple = {
  'deepslate_tile_slab': 'block/deepslate_tiles.png',
  'deepslate_tile_stairs': 'block/deepslate_tiles.png',
  'deepslate_tile_wall': 'block/deepslate_tiles.png',
  'lava_cauldron': 'item/cauldron.png',
  'water_cauldron': 'item/cauldron.png',
  'powder_snow_cauldron': 'item/cauldron.png',
  'lodestone_compass': 'item/compass_00.png',
  'redstone_wire': 'block/redstone_dust_dot.png',
  'bamboo_sapling': 'block/bamboo_small_leaves.png'
};

let fixed = 0; const still = [];
for (const [img, src] of Object.entries(simple)) {
  const p = rel(src);
  if (fs.existsSync(p)) {
    fs.copyFileSync(p, path.join(itemDir, img + '.png'));
    fixed++;
    console.log('✓', img, '←', src);
  } else {
    still.push(img);
    console.log('✗', img, '(缺失来源', src + ')');
  }
}
// debug_stick 放弃
still.push('debug_stick');
console.log('\n最终修复:', fixed, ' 仍缺:', still.join(', '));
console.log('webroot/item 现有 PNG:', fs.readdirSync(itemDir).filter(f => f.endsWith('.png')).length);
