// 从 MC Item Gallery（mcitemgallery.com）同步物品图片到 webroot/item
//
// 数据源为官方版本压缩包：https://mcitemgallery.com/images-v2/<版本>.zip
// （增强版 v2，压缩包内为 <版本>/<物品名>.png，与本地命名一致）
//
// 用法：
//   node tools/sync-item-images.mjs                    # 下载 26.2 压缩包，更新 / 补齐所有物品图
//   node tools/sync-item-images.mjs --version=1.21.6   # 指定游戏版本
//   node tools/sync-item-images.mjs --missing          # 只补本地缺失的图片
//   node tools/sync-item-images.mjs --zip-file=路径    # 使用本地已有的压缩包
//   node tools/sync-item-images.mjs --dry-run          # 只统计，不写文件
import fs from 'fs';
import os from 'os';
import path from 'path';
import { execSync } from 'child_process';

// 解压 zip：Windows 的 tar 是 bsdtar（支持 zip）；
// Linux/macOS 的 tar 是 GNU tar（不支持 zip），改用 unzip。
function extractZip(archive, dest) {
  const commands = process.platform === 'win32'
    ? [`tar -xf "${archive}" -C "${dest}"`]
    : [`unzip -q -o "${archive}" -d "${dest}"`, `tar -xf "${archive}" -C "${dest}"`];
  let lastError;
  for (const cmd of commands) {
    try {
      execSync(cmd, { stdio: 'pipe' });
      return;
    } catch (e) {
      lastError = e;
    }
  }
  throw lastError;
}

const root = path.resolve(import.meta.dirname, '..');
const args = process.argv.slice(2);
const has = (name) => args.includes('--' + name);
const opt = (name, fallback) => {
  const found = args.find((x) => x.startsWith('--' + name + '='));
  return found ? found.slice(name.length + 3) : fallback;
};
const version = opt('version', '26.2');
const onlyMissing = has('missing');
const dry = has('dry-run');
const zipFile = opt('zip-file', '');
const itemDir = path.join(root, 'webroot', 'item');

const materials = Object.keys(
  JSON.parse(fs.readFileSync(path.join(root, 'src/main/resources/material_zh_cn.json'), 'utf8'))
);
const imageName = (m) => m.toLowerCase().replace(/ /g, '_').replace(/[^a-z0-9_-]/g, '');
const materialNames = new Set(materials.map((m) => imageName(m) + '.png'));
const localNames = new Set(fs.readdirSync(itemDir).filter((f) => f.endsWith('.png')));

// 1) 准备压缩包（--zip-file 用本地包，否则下载到临时目录）
let zipPath = zipFile ? path.resolve(zipFile) : '';
if (!zipPath) {
  zipPath = path.join(os.tmpdir(), `mcitemgallery-${version}.zip`);
  if (!fs.existsSync(zipPath)) {
    const url = `https://mcitemgallery.com/images-v2/${version}.zip`;
    console.log('下载：' + url);
    const res = await fetch(url);
    if (!res.ok) {
      console.error('下载失败：HTTP ' + res.status);
      process.exit(1);
    }
    fs.writeFileSync(zipPath, Buffer.from(await res.arrayBuffer()));
  }
}
console.log('压缩包：' + zipPath + '（' + (fs.statSync(zipPath).size / 1024 / 1024).toFixed(1) + ' MB）');

// 2) 解压到临时目录（tar 可直接解开 zip）
const out = path.join(os.tmpdir(), 'mcitemgallery-' + version);
fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(out, { recursive: true });
extractZip(zipPath, out);

// 3) 在解压结果里找到包含 png 的目录
let pngRoot = out;
const stack = [out];
while (stack.length) {
  const d = stack.pop();
  const entries = fs.readdirSync(d);
  if (entries.some((f) => f.endsWith('.png'))) {
    pngRoot = d;
    break;
  }
  for (const f of entries) {
    const p = path.join(d, f);
    if (fs.statSync(p).isDirectory()) stack.push(p);
  }
}
const zipNames = fs.readdirSync(pngRoot).filter((f) => f.endsWith('.png'));

// 4) 同步：替换本地同名图、补齐缺失图（压缩包没有的本地文件保持不动）
let replaced = 0;
let added = 0;
for (const name of zipNames) {
  const exists = localNames.has(name);
  const wanted = materialNames.has(name) || exists;
  if (!wanted) continue;
  if (onlyMissing && exists) continue;
  if (dry) {
    exists ? replaced++ : added++;
    continue;
  }
  fs.copyFileSync(path.join(pngRoot, name), path.join(itemDir, name));
  exists ? replaced++ : added++;
}

// 5) 报告仍缺的（本地没有、压缩包里也没有）
const zipSet = new Set(zipNames);
const stillMissing = [...materialNames].filter((n) => !localNames.has(n) && !zipSet.has(n));
const verb = dry ? '将' : '';
console.log(`${verb}替换 ${replaced} 张、${verb}新增 ${added} 张（压缩包共 ${zipNames.length} 张）`);
if (stillMissing.length) {
  console.log(`仍缺 ${stillMissing.length} 张（本地与压缩包都没有）：`);
  console.log(stillMissing.slice(0, 50).join('\n') + (stillMissing.length > 50 ? `\n… 其余 ${stillMissing.length - 50} 张` : ''));
}
