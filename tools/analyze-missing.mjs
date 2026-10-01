// 分析所有缺失纹理的真实状态
import fs from 'fs';
import path from 'path';

const root = path.resolve(import.meta.dirname, '..');
const read = (p) => fs.readFileSync(p, 'utf8').replace(/^\uFEFF/, '');

const missing = JSON.parse(read(path.join(root, 'tools', 'missing-textures.json')));
const failed = JSON.parse(read(path.join(root, 'tools', 'failed-textures.json')));

console.log('missing-textures.json 类型:', Array.isArray(missing) ? 'array' : typeof missing, '条目:', Array.isArray(missing) ? missing.length : Object.keys(missing).length);
console.log('missing 样例:', JSON.stringify(Array.isArray(missing) ? missing.slice(0, 3) : Object.entries(missing).slice(0, 3)));
console.log('failed-textures.json 类型:', Array.isArray(failed) ? 'array' : typeof failed, '条目:', Array.isArray(failed) ? failed.length : Object.keys(failed).length);
console.log('failed 样例:', JSON.stringify(Array.isArray(failed) ? failed.slice(0, 3) : Object.entries(failed).slice(0, 3)));

// webroot/item 现有文件
const have = new Set(fs.readdirSync(path.join(root, 'webroot', 'item')).filter(f => f.endsWith('.png')).map(f => f.toLowerCase()));

// failed 列表里现在还有多少真的缺
const failedArr = Array.isArray(failed) ? failed.map(x => typeof x === 'string' ? x : (x.name || x.file || x.material || '')).filter(Boolean) : [];
const stillMissing = failedArr.filter(n => !have.has(n.toLowerCase()));
console.log('\nfailed 列表中仍然缺失:', stillMissing.length, '/', failedArr.length);
console.log('样例(仍缺):', stillMissing.slice(0, 20));

// client.jar 是否存在
const jar = path.join(process.env.TEMP, 'mc-1.21.11-client.jar');
console.log('\nclient.jar 存在:', fs.existsSync(jar), fs.existsSync(jar) ? (fs.statSync(jar).size / 1024 / 1024).toFixed(1) + ' MB' : '');
