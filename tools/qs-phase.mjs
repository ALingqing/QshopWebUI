import { execSync } from 'child_process';
const jar = 'G:/p/plugins/QuickShop-Hikari-6.3.0.0-SNAPSHOT-4.jar';
// Phase 枚举的取值
const buf = execSync('tar -xOf "' + jar + '" com/ghostchu/quickshop/api/event/Phase.class', { encoding: 'buffer', maxBuffer: 8 * 1024 * 1024 });
const t = buf.toString('latin1');
const strings = [...new Set((t.match(/[ -~]{2,}/g) || []))];
console.log('=== Phase 枚举字符串:', strings.filter(s => /PRE|POST|phase/i.test(s)).join(', '));
