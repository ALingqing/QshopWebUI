// 检查 dnt 附魔与现有附魔表冲突
import fs from 'fs';
import path from 'path';
import os from 'os';

const tmp = path.join(os.tmpdir(), 'qsw-dnt');
const dnt = JSON.parse(fs.readFileSync(path.join(tmp, 'assets/dnt/lang/zh_cn.json'), 'utf8'));
const cur = JSON.parse(fs.readFileSync('d:/QshopWebUI/paper-plugin/src/main/resources/enchantment_zh_cn.json', 'utf8'));

const ench = {};
for (const [k, v] of Object.entries(dnt)) {
  const m = k.match(/^enchantment\.dnt\.([a-z0-9_]+)$/);
  if (m) ench[m[1]] = v;
}
console.log('dnt 附魔共', Object.keys(ench).length, '个:');
for (const [k, v] of Object.entries(ench)) {
  const conflict = cur[k] ? '  ⚠原版已有: ' + cur[k] : '';
  console.log('  ' + k.padEnd(24) + ' = ' + v + conflict);
}

// 药水键
const mc = JSON.parse(fs.readFileSync(path.join(tmp, 'assets/minecraft/lang/zh_cn.json'), 'utf8'));
console.log('\n药水键:', Object.keys(mc).length, '个');
