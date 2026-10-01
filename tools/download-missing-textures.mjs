// 从 Mojang 官方资源 CDN 补齐缺失贴图
// 用法: node tools/download-missing-textures.mjs
import fs from 'node:fs';

const missingPath = 'd:/QshopWebUI/paper-plugin/tools/missing-textures.json';
const failedPath = 'd:/QshopWebUI/paper-plugin/tools/failed-textures.json';
const outDir = 'd:/QshopWebUI/item';
const CDN = 'https://resources.download.minecraft.net/';
const VERSION = process.argv[2] || '1.21.11';

function fetchJson(url, timeout = 30000) {
  return fetch(url, { signal: AbortSignal.timeout(timeout) }).then((r) => {
    if (!r.ok) throw new Error(url + ' → HTTP ' + r.status);
    return r.json();
  });
}

// 1) 版本清单 → 1.21.11 的版本 json → 资源索引
const manifest = await fetchJson('https://launchermeta.mojang.com/mc/game/version_manifest_v2.json');
const ver = manifest.versions.find((v) => v.id === VERSION);
if (!ver) {
  console.log('未找到版本', VERSION, '，可用版本前 20：');
  console.log(manifest.versions.slice(0, 20).map((v) => v.id).join(', '));
  process.exit(1);
}
const verJson = await fetchJson(ver.url);
const index = await fetchJson(verJson.assetIndex.url);
const objects = index.objects;
console.log('资源索引条目:', Object.keys(objects).length);

const missing = JSON.parse(fs.readFileSync(missingPath, 'utf8'));

// 2) 候选贴图路径（含历史命名差异：shard→sherd 等）
function candidateKeys(img) {
  const names = new Set([img]);
  if (img.includes('_shard')) names.add(img.replace('_shard', '_sherd'));
  const keys = [];
  for (const name of names) {
    keys.push(`minecraft/textures/item/${name}.png`);
    keys.push(`minecraft/textures/block/${name}.png`);
    keys.push(`minecraft/textures/block/${name}_inventory.png`);
    keys.push(`minecraft/textures/block/${name}_top.png`);
    keys.push(`minecraft/textures/block/${name}_front.png`);
  }
  return keys;
}

async function downloadOne(m) {
  for (const key of candidateKeys(m.img)) {
    const obj = objects[key];
    if (!obj) continue;
    try {
      const r = await fetch(CDN + obj.hash.slice(0, 2) + '/' + obj.hash, {
        signal: AbortSignal.timeout(20000),
      });
      if (!r.ok) continue;
      const buf = Buffer.from(await r.arrayBuffer());
      if (buf.length < 40) continue;
      fs.writeFileSync(`${outDir}\\${m.img}.png`, buf);
      return true;
    } catch (e) {
      // 试下一个候选
    }
  }
  return false;
}

// 3) 并发下载
let done = 0, ok = 0;
const failed = [];
const queue = [...missing];
async function worker() {
  while (queue.length) {
    const m = queue.shift();
    const success = await downloadOne(m);
    done++;
    if (success) ok++;
    else failed.push(m.img);
    if (done % 50 === 0) console.log(`进度 ${done}/${missing.length}  成功 ${ok}  失败 ${failed.length}`);
  }
}
await Promise.all(Array.from({ length: 8 }, worker));

console.log(`\n完成: ${ok} 成功 / ${failed.length} 失败 / 共 ${missing.length}`);
fs.writeFileSync(failedPath, JSON.stringify(failed), 'utf8');
if (failed.length) {
  console.log('失败清单（前 100）:', failed.slice(0, 100).join(', '));
}
