'use strict';
// Loaded before the stylesheet, so the saved mode and theme apply before anything is drawn.
(() => {
  const MODES = { dark: 'Dark', light: 'Light', oled: 'OLED' };
  const THEMES = {
    blue: { name: 'Blue', hue: 215 }, cyan: { name: 'Cyan', hue: 190 }, purple: { name: 'Purple', hue: 270 },
    pink: { name: 'Pink', hue: 325 }, red: { name: 'Red', hue: 350 }, orange: { name: 'Orange', hue: 28 }, green: { name: 'Green', hue: 145 },
    rgb: { name: 'RGB', hue: 215 },
  };
  const root = document.documentElement;
  const load = (key, fallback, known) => {
    try { const v = localStorage.getItem(key); return v && known[v] ? v : fallback; } catch (e) { return fallback; }
  };
  const save = (key, v) => { try { localStorage.setItem(key, v); } catch (e) { /* still applies until the app closes */ } };
  const hex = (h, s, l) => {
    const a = (s / 100) * Math.min(l / 100, 1 - l / 100);
    const part = (n) => { const k = (n + h / 30) % 12; const v = l / 100 - a * Math.max(-1, Math.min(k - 3, 9 - k, 1)); return Math.round(v * 255).toString(16).padStart(2, '0'); };
    return `#${part(0)}${part(8)}${part(4)}`;
  };
  let mode = load('driftrinth.mode', 'dark', MODES);
  let theme = load('driftrinth.theme', 'blue', THEMES);
  root.dataset.mode = mode;
  root.dataset.theme = theme;
  // RGB drifts through every colour, once a minute by default. Each step restyles the page, so it takes 10 steps a second and only runs while visible.
  const loadSpeed = () => { try { const v = Number(localStorage.getItem('driftrinth.rgbSpeed')); return v >= 1 && v <= 36 ? v : 6; } catch (e) { return 6; } };
  let rgbSpeed = loadSpeed(); // degrees of hue per second
  let rgbHue = 215, rgbTimer = 0;
  const syncRgb = () => {
    const run = theme === 'rgb' && !document.hidden && !matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (run && !rgbTimer) rgbTimer = setInterval(() => { rgbHue = (rgbHue + rgbSpeed / 10) % 360; root.style.setProperty('--rgb-h', rgbHue.toFixed(1)); }, 100);
    else if (!run && rgbTimer) { clearInterval(rgbTimer); rgbTimer = 0; }
  };
  document.addEventListener('visibilitychange', syncRgb);
  syncRgb();
  window.appearance = {
    MODES, THEMES,
    get mode() { return mode; },
    get theme() { return theme; },
    get rgbSpeed() { return rgbSpeed; },
    setRgbSpeed(v) { if (v >= 1 && v <= 36) { rgbSpeed = v; save('driftrinth.rgbSpeed', String(v)); } },
    setMode(v) { if (MODES[v]) { mode = v; root.dataset.mode = v; save('driftrinth.mode', v); } },
    setTheme(v) { if (THEMES[v]) { theme = v; root.dataset.theme = v; save('driftrinth.theme', v); syncRgb(); } },
    // The seven colours of the liquid background, as shades of the current theme.
    pools() {
      const h = THEMES[theme].hue;
      if (theme === 'rgb') return [0, 1, 2, 3, 4, 5, 6].map((i) => hex((i * 360) / 7, 95, 56)); // a full rainbow
      return [[0, 85, 53], [0, 100, 65], [-15, 100, 55], [10, 75, 40], [0, 97, 60], [10, 70, 32], [-5, 100, 60]].map(([d, s, l]) => hex((h + d + 360) % 360, s, l));
    },
  };
})();
