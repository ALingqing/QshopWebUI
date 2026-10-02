import { execSync } from 'child_process';

const jar = 'G:/p/plugins/QuickShop-Hikari-6.3.0.0-SNAPSHOT-4.jar';
// 1) 列出 database 相关类
const out = execSync('tar -tf "' + jar + '"', { encoding: 'buffer', maxBuffer: 512 * 1024 * 1024 }).toString('utf8');
const cls = out.split(/\r?\n/).filter(l => /database/i.test(l) && l.endsWith('.class'));
console.log('=== database 类:');
console.log(cls.join('\n'));

// 2) 提取每个类里的凭据/URL 字符串
for (const c of cls.slice(0, 10)) {
  try {
    const buf = execSync('tar -xOf "' + jar + '" ' + c, { encoding: 'buffer', maxBuffer: 32 * 1024 * 1024 });
    const txt = buf.toString('latin1');
    const strings = txt.match(/[ -~]{2,}/g) || [];
    const interesting = [...new Set(strings.filter(s =>
      /jdbc|h2|sa$|^sa|password|user|PASSWORD|USER|mysql|quickshop|MODE=/i.test(s) && s.length < 60 && !/^\(|^L|^\[|;/.test(s)))];
    if (interesting.length) {
      console.log('\n=== ' + c + ':');
      console.log(interesting.slice(0, 30).join('\n'));
    }
  } catch (e) { /* skip */ }
}
