// 对比 材质表(material_zh_cn.json) 与本地 item/ 贴图目录，找出所有缺失贴图
// 用法: node tools/check-missing-textures.mjs
import fs from 'node:fs';

const matPath = 'd:/QshopWebUI/paper-plugin/src/main/resources/material_zh_cn.json';
const itemDir = 'd:/QshopWebUI/item';
const outPath = 'd:/QshopWebUI/paper-plugin/tools/missing-textures.json';

const mats = JSON.parse(fs.readFileSync(matPath, 'utf8'));
const have = new Set(
  fs.readdirSync(itemDir).filter(f => f.toLowerCase().endsWith('.png')).map(f => f.toLowerCase())
);

const missing = [];
for (const key of Object.keys(mats)) {
  const img = key.toLowerCase().replace(/ /g, '_').replace(/[^a-z0-9_-]/g, '');
  if (!img) continue;
  if (!have.has(img + '.png')) missing.push({ material: key, img });
}

console.log('材质表条目:', Object.keys(mats).length, '| 本地贴图:', have.size, '| 缺失:', missing.length);
console.log('--- 缺失前 50 个:');
console.log(missing.slice(0, 50).map(m => m.img).join(', '));

// 关注类别统计
const cate = (p) => missing.filter(m => m.img.includes(p)).length;
console.log('--- 缺失分类统计:');
console.log('锻造模板:', cate('smithing_template'), '| 旗帜图案:', cate('banner_pattern'), '| 刷怪蛋:', cate('spawn_egg'),
  '| 药水:', cate('potion'), '| 染色:', cate('stained_glass') + cate('wool') + cate('_concrete') + cate('_terracotta'),
  '| 珊瑚:', cate('coral'), '| 海晶:', cate('prismarine'), '| 唱片:', cate('music_disc'));

fs.writeFileSync(outPath, JSON.stringify(missing), 'utf8');
console.log('完整缺失列表已写入:', outPath);
