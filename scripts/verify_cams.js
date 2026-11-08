// Paso 2: reproduce cada candidata en el reproductor real de MyTV (Chrome) y mide: anuncios (3 intentos),
// que arranque, que avance sin pausarse ni trabarse durante WATCH segundos, y la resolución real del video.
// Uso: node verify_cams.js candidates.json salida.json [WATCH_seg=25] [max_por_ciudad=4]
// Guarda una captura por cámara aprobada en shots/<id>.jpg (para revisar marcas de agua / textos).
const { chromium } = require('playwright-core'); const fs = require('fs');
const [,, inF, outF, W = '25', MAXC = '4'] = process.argv; const WATCH = +W * 1000;
const cand = JSON.parse(fs.readFileSync(inF, 'utf8'));
let done = {}; try { done = JSON.parse(fs.readFileSync(outF, 'utf8')); } catch {}
const jobs = [];
for (const [city, list] of Object.entries(cand)) {
  const ok = list.filter(x => !x.ads).sort((a, b) => (b.hd - a.hd) || (b.views - a.views)).slice(0, +MAXC);
  for (const x of ok) if (!done[x.v]) jobs.push([city, x]);
}
fs.mkdirSync('shots', { recursive: true });
(async () => {
  const b = await chromium.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true, args: ['--autoplay-policy=no-user-gesture-required'] });
  let i = 0;
  async function worker() {
    const p = await (await b.newContext({ viewport: { width: 960, height: 540 } })).newPage();
    await p.goto('https://yoan231291-sketch.github.io/mytv-launcher/?x=' + Date.now(), { waitUntil: 'domcontentloaded', timeout: 60000 });
    await p.waitForFunction(() => typeof playerReady !== 'undefined' && playerReady, null, { timeout: 60000 }).catch(() => {});
    await p.evaluate(() => { window.nextCam = () => {}; window.discover = () => {}; window.skipCity = () => {}; });
    const probe = async () => {
      const f = p.frames().find(f => /embed/.test(f.url()));
      return f ? await f.evaluate(() => { const pl = document.querySelector('.html5-video-player'), v = document.querySelector('video');
        return pl && v ? { ad: pl.classList.contains('ad-showing'), play: pl.classList.contains('playing-mode'), pause: pl.classList.contains('paused-mode'), buf: pl.classList.contains('buffering-mode'), t: v.currentTime, h: v.videoHeight, w: v.videoWidth } : null; }).catch(() => null) : null;
    };
    while (i < jobs.length) {
      const [city, x] = jobs[i++]; let ads = 0, starts = 0, stable = 0, h = 0, bad = '';
      for (let t = 0; t < 3; t++) {
        await p.evaluate(v => { clearTimeout(watchT); player.loadVideoById(v); player.mute(); }, x.v);
        let ad = false, ok = false;
        for (let k = 0; k < 10 && !ad; k++) { await p.waitForTimeout(1000); const r = await probe(); if (r && r.ad) ad = true; if (r && r.play && r.t > 0) ok = true; if (ok && k >= 4) break; }
        if (ad) ads++; if (ok) starts++;
        if (t === 0 && ok && !ad) {   // prueba larga solo en el primer intento
          let last = -1, stalls = 0, pauses = 0, n = 0;
          for (let s = 0; s < WATCH / 1000; s++) { await p.waitForTimeout(1000); const r = await probe(); if (!r) { stalls++; continue; } n++;
            if (r.ad) { ads++; break; } if (r.pause) pauses++; if (r.t <= last) stalls++; last = r.t; h = Math.max(h, r.h); }
          stable = (pauses === 0 && stalls <= 2 && n > WATCH / 1000 * 0.8) ? 1 : 0; if (!stable) bad = `pausas ${pauses} trabas ${stalls}`;
          if (stable) await p.screenshot({ path: `shots/${x.v}.jpg`, type: 'jpeg', quality: 70 });
        }
        if (ad) break;
      }
      done[x.v] = { city, title: x.title, ch: x.ch, ads, starts, stable, h, bad };
      console.log(city, x.v, 'anuncios', ads, 'arranca', starts + '/3', 'estable', stable, 'alto', h, bad);
      fs.writeFileSync(outF, JSON.stringify(done, null, 1));
    }
  }
  await Promise.all([worker(), worker(), worker()]);
  console.log('FIN'); await b.close();
})();
