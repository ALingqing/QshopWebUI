// 深入 AuthMe：配置 + 数据库结构 + 哈希算法
import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import os from 'os';

const tmp = path.join(os.tmpdir(), 'qsw-authme');
fs.rmSync(tmp, { recursive: true, force: true });
fs.mkdirSync(tmp, { recursive: true });
execSync('tar -xf "C:/Users/aqing/Desktop/AuthMe-6.0.0配置包-v3.zip" -C "' + tmp + '" AuthMe/config.yml AuthMe/authme.db', { stdio: 'inherit' });

// 1. config.yml 数据库部分
const cfg = fs.readFileSync(path.join(tmp, 'AuthMe/config.yml'), 'utf8');
const lines = cfg.split(/\r?\n/);
let inDb = false;
console.log('===== config.yml 数据库配置 =====');
for (let i = 0; i < lines.length; i++) {
  const l = lines[i];
  if (/^DataSource:/.test(l)) { inDb = true; }
  else if (inDb && /^[A-Za-z]/.test(l)) { inDb = false; }
  if (inDb) console.log(l);
}

// 2. authme.db 表结构（SQLite 文件内搜索明文）
const db = fs.readFileSync(path.join(tmp, 'AuthMe/authme.db'));
const txt = db.toString('latin1');
console.log('\n===== authme.db 表结构（CREATE TABLE 片段）=====');
const re = /CREATE TABLE[^;]{0,400}/g;
let m, count = 0;
while ((m = re.exec(txt)) && count < 10) {
  console.log(m[0].replace(/\0/g, ' ').substring(0, 300));
  console.log('---');
  count++;
}
// 密码行样例（username + $SHA$...）
console.log('\n===== 数据库中的密码哈希样例（前几个 $ 开头的字符串）=====');
const hashes = txt.match(/\$[A-Z0-9]+\$[A-Za-z0-9+/=]+\$[a-f0-9]+/g) || [];
console.log('找到', hashes.length, '个哈希样例:', hashes.slice(0, 5));

// 3. 源码哈希算法文档
const doc = 'C:/Users/aqing/Desktop/AuthMeReReloaded/docs/hash_algorithms.md';
if (fs.existsSync(doc)) {
  console.log('\n===== hash_algorithms.md =====');
  console.log(fs.readFileSync(doc, 'utf8').substring(0, 3000));
}
