// 诊断剩余缺失纹理在 client.jar 中的真实名称
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
console.log('总贴图:', rels.length);

const probe = (label, keyword) => {
  const hits = rels.filter(r => r.includes(keyword)).slice(0, 12);
  console.log('\n[' + label + '] 含 "' + keyword + '": ' + (hits.length ? '\n  ' + hits.join('\n  ') : '无'));
};

probe('cinnabar', 'cinnabar');
probe('sulfur', 'sulfur');
probe('copper_chest', 'chest/copper');
probe('chest 目录', 'entity/chest/');
probe('copper_golem', 'copper_golem');
probe('music_disc_bounce', 'music_disc_bounce');
probe('music_disc_ 全部(前12)', 'music_disc_');
probe('hyphae', 'hyphae');
probe('smooth_quartz', 'smooth_quartz');
probe('quartz_block', 'quartz_block');
probe('smooth_sandstone', 'smooth_sandstone');
probe('sandstone_top', 'sandstone_top');
probe('scute', 'scute');
probe('harness', 'harness');
probe('set_spawn', 'set_spawn');
probe('bamboo_sapling', 'bamboo_sapling');
probe('end_gateway', 'end_gateway');
probe('decorated_pot', 'decorated_pot');
probe('torch 相关', 'torch.png');
probe('copper_torch', 'copper_torch');
probe('copper_lantern', 'copper_lantern');
probe('copper_bars', 'copper_bars');
probe('copper_chain', 'copper_chain');
probe('copper_grate', 'copper_grate');
probe('bounce', 'bounce');
probe('skull', 'skull');
probe('skeleton 实体', 'entity/skeleton/skeleton.png');
probe('dragon', 'entity/enderdragon/dragon.png');
probe('banner base', 'banner/base');
probe('moss_carpet', 'moss_carpet');
probe('piston', 'piston_top');
probe('cauldron', 'cauldron.png');
