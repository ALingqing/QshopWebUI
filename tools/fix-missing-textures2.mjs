// 第二轮修复：多面方块 + 特化映射 + 前缀兜底
import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import os from 'os';

const root = path.resolve(import.meta.dirname, '..');
const itemDir = path.join(root, 'webroot', 'item');
const jar = path.join(process.env.TEMP, 'mc-1.21.11-client.jar');
const tmp = path.join(os.tmpdir(), 'qsw-tex');
const read = (p) => fs.readFileSync(p, 'utf8').replace(/^\uFEFF/, '');

if (!fs.existsSync(path.join(tmp, 'assets', 'minecraft', 'textures'))) {
  console.log('解压 textures ...');
  fs.rmSync(tmp, { recursive: true, force: true });
  fs.mkdirSync(tmp, { recursive: true });
  execSync(`tar -xf "${jar}" -C "${tmp}" assets/minecraft/textures`);
}

const texRoot = path.join(tmp, 'assets', 'minecraft', 'textures');
const byRel = new Map();   // rel(小写) -> 绝对路径
const byName = new Map();  // basename(小写) -> 绝对路径 (item>block>other)
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
console.log('索引:', byRel.size, '贴图');

const stillMissing = JSON.parse(read(path.join(root, 'tools', 'still-missing.json')));
const have = new Set(fs.readdirSync(itemDir).filter(f => f.endsWith('.png')).map(f => f.toLowerCase()));

function findPath(name) {
  const n = name.toLowerCase();
  const inItem = byRel.get('item/' + n + '.png');
  if (inItem) return inItem;
  const inBlock = byRel.get('block/' + n + '.png');
  if (inBlock) return inBlock;
  // 多面方块: side > front > top > 前缀兜底
  for (const suf of ['_side', '_front', '_top', '_end', '_stage0']) {
    const p = byRel.get('block/' + n + suf + '.png');
    if (p) return p;
  }
  // 前缀匹配（block 下） —— 从 byRel 迭代
  let best = null;
  for (const [rel, p] of byRel) {
    if (rel.startsWith('block/' + n + '_')) {
      if (!best || rel < best.rel) best = { rel, p };
    }
  }
  if (best) return best.p;
  for (const [rel, p] of byRel) {
    if (rel.startsWith('item/' + n + '_')) {
      if (!best || rel < best.rel) best = { rel, p };
    }
  }
  return best ? best.p : null;
}

function special(name) {
  let m;
  if ((m = name.match(/^(.+)_carpet$/))) return [m[1] + '_wool'];
  if ((m = name.match(/^(.+)_stained_glass_pane$/))) return [m[1] + '_stained_glass'];
  if ((m = name.match(/^(.+)_candle_cake$/))) return [m[1] + '_candle', 'cake'];
  if (name === 'candle_cake') return ['cake'];
  if ((m = name.match(/^(.+)_wall_fan$/))) return [m[1] + '_fan'];
  if ((m = name.match(/^(.+)_wall_banner$/))) return [m[1] + '_banner'];
  if (name === 'banner' || /^(.+)_banner$/.test(name)) return null; // 交给 entity 特判
  return [];
}

let fixed = 0;
const still = [];
for (const entry of stillMissing) {
  const img = entry.img;
  if (have.has(img.toLowerCase())) continue;
  let src = null;

  // 特化（床 → entity/bed/x.png；旗帜 → entity/banner/base.png）
  const bedM = img.match(/^(.+)_bed$/);
  if (bedM) src = byRel.get('entity/bed/' + bedM[1] + '.png') || null;
  if (!src && (/^(.+)_banner$/.test(img) || img === 'banner')) src = byRel.get('entity/banner/base.png') || byRel.get('entity/banner_base.png') || null;

  if (!src) {
    const sp = special(img);
    if (sp) for (const s of sp) { src = findPath(s); if (src) break; }
  }
  if (!src) src = findPath(img);

  if (src) {
    fs.copyFileSync(src, path.join(itemDir, img + '.png'));
    fixed++;
  } else {
    still.push(entry);
  }
}

console.log('\n第二轮修复:', fixed, '  仍缺:', still.length);
fs.writeFileSync(path.join(root, 'tools', 'still-missing2.json'), JSON.stringify(still, null, 1), 'utf8');
console.log('仍缺:', still.map(x => x.img).join(', '));
