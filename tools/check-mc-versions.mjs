// 查看 Minecraft 版本清单，找 26.2
import https from 'https';

const get = (url) => new Promise((resolve, reject) => {
  https.get(url, (res) => {
    if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
      return get(res.headers.location).then(resolve, reject);
    }
    let data = '';
    res.on('data', (c) => data += c);
    res.on('end', () => resolve({ status: res.statusCode, body: data }));
  }).on('error', reject);
});

const r = await get('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json');
console.log('HTTP', r.status);
const m = JSON.parse(r.body);
console.log('latest:', JSON.stringify(m.latest));
console.log('\n最近 25 个版本:');
for (const v of m.versions.slice(0, 25)) {
  console.log(' ', v.id.padEnd(14), v.type, v.releaseTime.substring(0, 10));
}
const target = m.versions.find(v => v.id === '26.2');
console.log('\n26.2 存在:', !!target, target ? target.url : '');
const snap = m.versions.filter(v => v.id.includes('26.') || v.id.includes('25.')).slice(0, 15);
console.log('含 26./25. 的版本(前15):', snap.map(v => v.id + '(' + v.type + ')').join(', '));
