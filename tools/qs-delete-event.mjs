import { execSync } from 'child_process';
const buf = execSync('tar -xOf "G:/p/plugins/QuickShop-Hikari-6.3.0.0-SNAPSHOT-4.jar" com/ghostchu/quickshop/api/event/management/ShopDeleteEvent.class', { encoding: 'buffer', maxBuffer: 8 * 1024 * 1024 });
const txt = buf.toString('latin1');
const strings = txt.match(/[ -~]{3,}/g) || [];
const interesting = [...new Set(strings.filter((s) => /get[A-Z]|player|Player|shop|Shop|reason|Reason|owner|source|who|damage|remove/i.test(s) && !s.startsWith('(') && !s.startsWith('L')))];
console.log(interesting.slice(0, 60).join('\n'));
