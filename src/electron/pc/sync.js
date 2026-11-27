// 把安卓同一份前端 index.html 同步到电脑版 app 目录
const fs = require('fs');
const path = require('path');

const src = path.join(__dirname, '..', 'project', 'assets', 'index.html');
const dstDir = path.join(__dirname, 'app');
const dst = path.join(dstDir, 'index.html');

if (!fs.existsSync(src)) {
  console.error('找不到前端：' + src);
  process.exit(1);
}
if (!fs.existsSync(dstDir)) fs.mkdirSync(dstDir, { recursive: true });
fs.copyFileSync(src, dst);
const size = fs.statSync(dst).size;
console.log('前端已同步 → app/index.html (' + size + ' bytes)');
