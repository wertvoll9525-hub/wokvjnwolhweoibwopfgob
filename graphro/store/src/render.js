const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const b = await chromium.launch();
  const out = path.resolve(__dirname, '..', 'images');
  const jobs = [
    ...['01','02','03','04','05','06'].map(n => [`shot${n}.html`, `screenshots/phone-${n}.png`, 1080, 1920]),
    ['feature.html', 'feature-graphic-1024x500.png', 1024, 500],
    ['icon.html', 'icon-512.png', 512, 512],
  ];
  for (const [src, dst, w, h] of jobs) {
    const p = await b.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 1 });
    await p.goto('file://' + path.resolve(__dirname, src));
    await p.evaluate(() => document.fonts.ready);
    await p.waitForTimeout(300);
    await p.screenshot({ path: path.join(out, dst), omitBackground: false });
    await p.close();
  }
  await b.close();
  console.log('rendered');
})();
