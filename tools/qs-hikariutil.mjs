import { execSync } from 'child_process';
const jar = 'G:/p/plugins/QuickShop-Hikari-6.3.0.0-SNAPSHOT-4.jar';
for (const c of ['com/ghostchu/quickshop/database/HikariUtil.class', 'com/ghostchu/quickshop/QuickShop$DatabaseDriverType.class', 'com/ghostchu/quickshop/api/database/DatabaseHelper.class']) {
  const buf = execSync('tar -xOf "' + jar + '" ' + c, { encoding: 'buffer', maxBuffer: 32 * 1024 * 1024 });
  const txt = buf.toString('latin1');
  const strings = txt.match(/[ -~]{2,}/g) || [];
  console.log('\n=== ' + c + '（全部字符串）:');
  console.log([...new Set(strings)].filter(s => s.length >= 2 && s.length < 120).join('\n'));
}
