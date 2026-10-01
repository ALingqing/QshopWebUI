// 诊断资源索引的键格式
const manifest = await (await fetch('https://launchermeta.mojang.com/mc/game/version_manifest_v2.json')).json();
const v = manifest.versions.find((x) => x.id === '1.21.11');
console.log('version:', v && v.id, v && v.url);
const vj = await (await fetch(v.url)).json();
console.log('assetIndex:', vj.assetIndex.url);
const idx = await (await fetch(vj.assetIndex.url)).json();
const keys = Object.keys(idx.objects);
console.log('索引键数:', keys.length);
console.log('前 15 个键:');
console.log(keys.slice(0, 15).join('\n'));
const tex = keys.filter((k) => k.includes('textures'));
console.log('含 textures 的键数:', tex.length);
console.log('textures 样本:');
console.log(tex.slice(0, 8).join('\n'));
const af = keys.filter((k) => k.includes('acacia_fence'));
console.log('acacia_fence 相关键:', af.length ? af.slice(0, 6).join('\n') : '(无)');
