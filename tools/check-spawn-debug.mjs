// 查证 set_spawn / debug_stick / end_gateway 在 26.2 的确切情况
import fs from 'fs';
import path from 'path';
import os from 'os';
const tmp = path.join(os.tmpdir(), 'qsw-tex26');
const en = JSON.parse(fs.readFileSync(path.join(tmp, 'assets', 'minecraft', 'lang', 'en_us.json'), 'utf8'));

for (const k of Object.keys(en)) {
  if (k.includes('set_spawn') || k.includes('debug_stick') || k === 'item.minecraft.debug_stick') {
    console.log(k, '=>', en[k]);
  }
}

// 搜 spawn 相关贴图
const texRoot = path.join(tmp, 'assets', 'minecraft', 'textures');
const rels = [];
const walk = (d) => {
  for (const f of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, f.name);
    if (f.isDirectory()) walk(p);
    else if (f.name.endsWith('.png')) rels.push(path.relative(texRoot, p).replace(/\\/g, '/').toLowerCase());
  }
};
walk(texRoot);
console.log('\nspawn 相关贴图(前 20):');
rels.filter(r => r.includes('spawn')).slice(0, 20).forEach(r => console.log(' ', r));
console.log('\ndebug 相关贴图:');
rels.filter(r => r.includes('debug')).forEach(r => console.log(' ', r));
