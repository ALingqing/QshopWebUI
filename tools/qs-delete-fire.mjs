import { execSync } from 'child_process';
const jar = 'G:/p/plugins/QuickShop-Hikari-6.3.0.0-SNAPSHOT-4.jar';
const names = [
  'com/ghostchu/quickshop/shop/SimpleShopManager.class',
  'com/ghostchu/quickshop/shop/ContainerShop.class',
  'com/ghostchu/quickshop/command/subcommand/SubCommand_RemoveAll.class',
  'com/ghostchu/quickshop/command/subcommand/SubCommand_Remove.class',
  'com/ghostchu/quickshop/util/Util.class'
];
for (const n of names) {
  try {
    const buf = execSync('tar -xOf "' + jar + '" ' + n, { encoding: 'buffer', maxBuffer: 64 * 1024 * 1024 });
    const t = buf.toString('latin1');
    if (t.includes('ShopDeleteEvent')) {
      console.log(n + ': 引用 ShopDeleteEvent ✓');
      let i = -1;
      let count = 0;
      while ((i = t.indexOf('ShopDeleteEvent', i + 1)) !== -1 && count < 3) {
        count++;
        console.log('  ctx' + count + ':', t.slice(Math.max(0, i - 150), i + 150).replace(/[^\x20-\x7E]/g, '.'));
      }
    } else {
      console.log(n + ': 无引用');
    }
  } catch (e) {
    console.log(n + ': 读取失败');
  }
}
