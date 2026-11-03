const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
(async () => {
  const b = await chromium.launch();
  const out = path.resolve(__dirname, '..', 'images');
  const langs = process.argv.slice(2).length ? process.argv.slice(2) : ['ko', 'en', 'ja'];
  for (const lang of langs) {
    fs.mkdirSync(path.join(out, lang, 'screenshots'), { recursive: true });
    const jobs = [1,2,3,4,5,6].map(n => [`shot-${lang}-0${n}.html`, `${lang}/screenshots/phone-0${n}.png`, 1080, 1920]);
    jobs.push([`feature-${lang}.html`, `${lang}/feature-graphic-1024x500.png`, 1024, 500]);
    for (const [src, dst, w, h] of jobs) {
      const p = await b.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
      await p.goto('file://' + path.resolve(__dirname, src));
      await p.evaluate(() => document.fonts.ready);
      await p.waitForTimeout(300);
      await p.screenshot({ path: path.join(out, dst) });
      await p.close();
    }
  }
  await b.close();
  console.log('rendered', langs.join(','));
})();
