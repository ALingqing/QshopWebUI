// 验证官网 index.html 修改
import fs from 'fs';
const t = fs.readFileSync('C:/Users/aqing/Downloads/index.html', 'utf8');

const menu = (t.match(/<nav id="menu">[\s\S]*?<\/nav>/) || [''])[0];
console.log('菜单含在线商店:', menu.includes('20850') ? '✓' : '✗');
menu.split('\n').filter(l => l.includes('<li>')).forEach(l => console.log('  ', l.trim()));

const tiles = (t.match(/<section id="one" class="tiles">[\s\S]*?<\/section>/) || [''])[0];
console.log('\n磁贴顺序:');
const articles = tiles.match(/<h3><a[^>]*>([^<]+)<\/a><\/h3>/g) || [];
articles.forEach((a, i) => console.log('  ' + (i + 1) + '. ' + a.replace(/<[^>]+>/g, '').trim()));
console.log('\n在线商店磁贴链接:', tiles.includes('20850') ? '✓' : '✗');
console.log('文件总行数:', t.split('\n').length);
