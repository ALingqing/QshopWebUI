// 诊断最后 10 个
import fs from 'fs';
import path from 'path';
import os from 'os';
const tmp = path.join(os.tmpdir(), 'qsw-tex');
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
const probe = (kw) => {
  const hits = rels.filter(r => r.includes(kw)).slice(0, 10);
  console.log('[' + kw + '] ' + (hits.length ? hits.join(', ') : '无'));
};
probe('bamboo');
probe('debug');
probe('tiles');
probe('cauldron');
probe('compass');
probe('redstone_dust');
probe('redstone_wire');
probe('item/stick');
