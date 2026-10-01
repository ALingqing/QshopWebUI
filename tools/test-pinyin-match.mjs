// 模拟 Java Pinyin.matches 逻辑验证
import fs from 'fs';

const py = JSON.parse(fs.readFileSync('d:/QshopWebUI/paper-plugin/src/main/resources/pinyin_zh_cn.json', 'utf8'));

function matches(kw, text) {
  if (!kw || !text) return false;
  if (text.toLowerCase().includes(kw)) return true;
  const v = py[text];
  if (!v) return false;
  const [fp, ini] = v.split('|');
  if (fp && fp.includes(kw)) return true;
  if (ini && ini.startsWith(kw)) return true;
  return false;
}

const names = ['蜘蛛眼', '铁剑', '钻石', '金合欢木台阶', '附魔金苹果', '火把', '面包', '末影珍珠', '下界合金锭'];
const tests = ['zhizhu', 'zz', 'tiejian', 'tj', 'zuanshi', 'zs', 'jhhmtj', 'jinhehuan', 'mianbao', 'mb', 'huoba', 'hb', 'mo ying', 'moying', 'mysj'];
console.log('物品名 → 拼音:', names.map(n => n + '=' + (py[n] || '?')).join('\n  '));
console.log('\n搜索测试:');
for (const kw of tests) {
  const hit = names.filter(n => matches(kw, n));
  console.log('  "' + kw + '" →', hit.length ? hit.join(', ') : '（无）');
}
