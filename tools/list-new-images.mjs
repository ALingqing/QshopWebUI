// 列出本次从 26.2 补的贴图（对比 git）
import { execSync } from 'child_process';
const out = execSync('git status --short webroot/item', { encoding: 'buffer' }).toString('utf8');
const lines = out.split(/\r?\n/).filter(l => l.trim());
console.log('新增/修改的图片:', lines.length);
console.log(lines.map(l => '  ' + l.trim()).join('\n'));
