// 解压 26.2 客户端并探查关键材质
import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import os from 'os';

const jar = path.join(process.env.TEMP, 'mc-26.2-client.jar');
const tmp = path.join(os.tmpdir(), 'qsw-tex26');

if (!fs.existsSync(path.join(tmp, 'assets', 'minecraft', 'textures'))) {
  console.log('解压 textures ...');
  fs.rmSync(tmp, { recursive: true, force: true });
  fs.mkdirSync(tmp, { recursive: true });
  execSync(`tar -xf "${jar}" -C "${tmp}" assets/minecraft/textures`);
}

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
console.log('26.2 贴图总数:', rels.length);

const probe = (kw) => {
  const hits = rels.filter(r => r.includes(kw)).slice(0, 15);
  console.log('\n[' + kw + '] ' + (hits.length ? '\n  ' + hits.join('\n  ') : '无'));
};
probe('cinnabar');
probe('sulfur');
probe('golden_dandelion');
probe('bounce');
probe('debug_stick');
probe('set_spawn');
probe('harness');
probe('bamboo_sapling');
probe('sulfur_cube');
