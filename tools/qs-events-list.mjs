import { execSync } from 'child_process';
const out = execSync('tar -tf "G:/p/plugins/QuickShop-Hikari-6.3.0.0-SNAPSHOT-4.jar"', { encoding: 'buffer', maxBuffer: 512 * 1024 * 1024 }).toString('utf8');
const evs = out.split(/\r?\n/).filter(l => /api\/event\/.*\.class$/.test(l) && !l.includes('$')).map(l => l.replace(/.*api\/event\//, '').replace('.class', ''));
console.log(evs.join('\n'));
