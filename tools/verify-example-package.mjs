// 验证示例包内容
import fs from 'fs';
const base = 'C:/Users/aqing/Desktop/新建文件夹/清屿服务器示例';
const cfg = fs.readFileSync(base + '/config.yml', 'utf8');
const m1 = cfg.match(/^server-name:\s*"(.*?)"/m);
const m2 = cfg.match(/^server-subtitle:\s*"(.*?)"/m);
console.log('config server-name     :', m1 ? m1[1] : '!!未找到');
console.log('config server-subtitle :', m2 ? m2[1] : '!!未找到');
const html = fs.readFileSync(base + '/web/index.html', 'utf8');
const t = html.match(/<title>(.*?)<\/title>/);
const logo = html.match(/class="topnav-logo">(.*?)</);
const loader = html.match(/class="loader-title">(.*?)</);
console.log('html <title>           :', t ? t[1] : '!!未找到');
console.log('html topnav-logo       :', logo ? logo[1] : '!!未找到');
console.log('html loader-title      :', loader ? loader[1] : '!!未找到');
console.log('web/index.html 大小    :', fs.statSync(base + '/web/index.html').size, '字节');
console.log('\n示例包文件列表:');
const walk = (d, p = '') => {
  for (const f of fs.readdirSync(d)) {
    const full = d + '/' + f;
    if (fs.statSync(full).isDirectory()) { console.log('  ' + p + f + '/'); walk(full, p + '  '); }
    else console.log('  ' + p + f + '  (' + (fs.statSync(full).size / 1024).toFixed(1) + ' KB)');
  }
};
walk(base);
