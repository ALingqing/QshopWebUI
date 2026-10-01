// 下载指定版本的 Minecraft client.jar
import https from 'https';
import fs from 'fs';
import path from 'path';

const version = process.argv[2] || '26.2';
const outPath = path.join(process.env.TEMP, `mc-${version}-client.jar`);

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

const download = (url, dest) => new Promise((resolve, reject) => {
  https.get(url, (res) => {
    if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
      return download(res.headers.location, dest).then(resolve, reject);
    }
    if (res.statusCode !== 200) return reject(new Error('HTTP ' + res.statusCode));
    const total = parseInt(res.headers['content-length'] || '0', 10);
    let got = 0;
    const f = fs.createWriteStream(dest);
    res.on('data', (c) => {
      got += c.length;
      if (total) process.stdout.write(`\r下载中: ${(got / 1048576).toFixed(1)} / ${(total / 1048576).toFixed(1)} MB`);
    });
    res.pipe(f);
    f.on('finish', () => { f.close(() => { console.log('\n完成:', dest); resolve(dest); }); });
    f.on('error', reject);
  }).on('error', reject);
});

// 1. manifest → version.json
console.log('查询版本', version, '...');
const man = JSON.parse((await get('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json')).body);
const v = man.versions.find(x => x.id === version);
if (!v) { console.error('找不到版本', version); process.exit(1); }
console.log('版本 URL:', v.url);

// 2. version.json → client 下载地址
const vj = JSON.parse((await get(v.url)).body);
const clientUrl = vj.downloads && vj.downloads.client && vj.downloads.client.url;
if (!clientUrl) { console.error('无 client 下载地址'); process.exit(1); }
console.log('client.jar:', clientUrl, '(' + (vj.downloads.client.size / 1048576).toFixed(1) + ' MB)');

// 3. 下载
if (fs.existsSync(outPath)) {
  console.log('已存在，跳过下载:', outPath, (fs.statSync(outPath).size / 1048576).toFixed(1), 'MB');
} else {
  await download(clientUrl, outPath);
}
console.log('文件就绪:', outPath, (fs.statSync(outPath).size / 1048576).toFixed(1), 'MB');
