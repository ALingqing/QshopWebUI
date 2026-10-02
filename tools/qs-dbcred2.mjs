import { execSync } from 'child_process';
const jar = 'G:/p/plugins/QuickShop-Hikari-6.3.0.0-SNAPSHOT-4.jar';
const targets = [
  'com/ghostchu/quickshop/QuickShop.class',
  'com/ghostchu/quickshop/database/SimpleDatabaseHelperV2.class'
];
for (const c of targets) {
  const buf = execSync('tar -xOf "' + jar + '" ' + c, { encoding: 'buffer', maxBuffer: 64 * 1024 * 1024 });
  const txt = buf.toString('latin1');
  const strings = txt.match(/[ -~]{2,}/g) || [];
  const interesting = [...new Set(strings.filter(s =>
    /jdbc:h2|jdbc:mysql|MODE=|DB_CLOSE|;PASSWORD|H2|setPassword|setUsername|getUsername|getPassword|\.db|shops|SA|sa\b|user|pass/i.test(s)
      && s.length >= 2 && s.length < 130
      && !/^\$|^\(|^L[a-z]|^\[/.test(s)))];
  console.log('\n=== ' + c + ' 可疑字符串:');
  console.log(interesting.join('\n'));
}
