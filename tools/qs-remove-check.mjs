// 提取 QuickShop remove/removeall 子命令类的可见字符串
import { execSync } from 'child_process';

const jar = 'G:/p/plugins/QuickShop-Hikari-6.3.0.0-SNAPSHOT-4.jar';
for (const cls of ['SubCommand_RemoveAll', 'SubCommand_Remove', 'SubCommand_RemoveWorld']) {
  try {
    const buf = execSync(`tar -xOf "${jar}" com/ghostchu/quickshop/command/subcommand/${cls}.class`, { encoding: 'buffer', maxBuffer: 32 * 1024 * 1024 });
    const txt = buf.toString('latin1');
    // 提取所有长度 >= 4 的可见 ASCII 串
    const strings = txt.match(/[ -~]{4,}/g) || [];
    const interesting = strings.filter((s) =>
      /remove|world|player|usage|other|permission|quickshop\.|command|arg|name/i.test(s) && !/^\(|^L|^\[/.test(s)
    );
    console.log('=== ' + cls + ':');
    console.log([...new Set(interesting)].slice(0, 40).join('\n'));
    console.log('');
  } catch (e) {
    console.log(cls + ': 提取失败 ' + e.message.slice(0, 80));
  }
}

// 找语言文件里的 removeall 提示
try {
  const out = execSync(`tar -tf "${jar}"`, { encoding: 'buffer', maxBuffer: 1024 * 1024 * 1024 }).toString('utf8');
  const lines = out.split(/\r?\n/);
  const cand = lines.filter((l) => /lang|message|i18n/i.test(l) && !l.endsWith('/'));
  console.log('=== 语言资源候选（前 20）:');
  console.log(cand.slice(0, 20).join('\n') || '无');
} catch (e) { console.log('列包失败: ' + e.message.slice(0, 80)); }
