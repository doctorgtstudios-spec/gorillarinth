'use strict';
const api = window.driftrinth;
const $ = (id) => document.getElementById(id);
const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
const size = (n) => (n >= 1073741824 ? (n / 1073741824).toFixed(2) + ' GB' : n >= 1048576 ? (n / 1048576).toFixed(1) + ' MB' : Math.max(1, Math.round(n / 1024)) + ' KB');
const mb = (n) => (n / 1048576).toFixed(1);
const plural = (n, w) => `${n} ${w}${n === 1 ? '' : 's'}`;
const clean = (e) => (e && e.message ? e.message : String(e));
function ago(t) {
  const when = typeof t === 'number' ? t : Date.parse(t);
  if (!when) return '';
  const m = Math.round((Date.now() - when) / 60000);
  if (m < 2) return 'just now';
  if (m < 60) return `${m} minutes ago`;
  if (m < 1440) return plural(Math.round(m / 60), 'hour') + ' ago';
  if (m < 43200) return plural(Math.round(m / 1440), 'day') + ' ago';
  if (m < 525600) return plural(Math.round(m / 43200), 'month') + ' ago';
  return plural(Math.round(m / 525600), 'year') + ' ago';
}

const ICONS = {
  play: '<polygon points="6 3 20 12 6 21 6 3"/>',
  compass: '<circle cx="12" cy="12" r="10"/><polygon points="16.24 7.76 14.12 14.12 7.76 16.24 9.88 9.88 16.24 7.76"/>',
  gear: '<path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/><circle cx="12" cy="12" r="3"/>',
  checks: '<path d="m3 17 2 2 4-4"/><path d="m3 7 2 2 4-4"/><path d="M13 6h8"/><path d="M13 12h8"/><path d="M13 18h8"/>',
  heart: '<path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"/>',
  palette: '<circle cx="13.5" cy="6.5" r=".5"/><circle cx="17.5" cy="10.5" r=".5"/><circle cx="8.5" cy="7.5" r=".5"/><circle cx="6.5" cy="12.5" r=".5"/><path d="M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.93 0 1.65-.75 1.65-1.69 0-.44-.18-.84-.44-1.13-.29-.29-.44-.65-.44-1.12a1.64 1.64 0 0 1 1.67-1.67h1.99c3.05 0 5.56-2.5 5.56-5.55C21.97 6.01 17.46 2 12 2z"/>',
  plus: '<path d="M5 12h14"/><path d="M12 5v14"/>',
  search: '<circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>',
  trash: '<path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><path d="M10 11v6"/><path d="M14 11v6"/>',
  folder: '<path d="m6 14 1.5-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.54 6a2 2 0 0 1-1.95 1.5H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.69.9l.81 1.2a2 2 0 0 0 1.67.9H18a2 2 0 0 1 2 2v2"/>',
  refresh: '<path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/><path d="M8 16H3v5"/>',
  download: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" x2="12" y1="15" y2="3"/>',
  left: '<path d="m15 18-6-6 6-6"/>',
  right: '<path d="m9 18 6-6-6-6"/>',
  down: '<path d="m6 9 6 6 6-6"/>',
  x: '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>',
  check: '<path d="M20 6 9 17l-5-5"/>',
  dots: '<circle cx="12" cy="5" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="12" cy="19" r="1"/>',
  tag: '<path d="M12.59 2.59A2 2 0 0 0 11.17 2H4a2 2 0 0 0-2 2v7.17a2 2 0 0 0 .59 1.42l8.7 8.7a2.43 2.43 0 0 0 3.42 0l6.58-6.58a2.43 2.43 0 0 0 0-3.42z"/><circle cx="7.5" cy="7.5" r=".5"/>',
  clock: '<circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>',
  box: '<path d="M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z"/><path d="m3.3 7 8.7 5 8.7-5"/><path d="M12 22V12"/>',
  layers: '<path d="m12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83Z"/><path d="m22 17.65-9.17 4.16a2 2 0 0 1-1.66 0L2 17.65"/><path d="m22 12.65-9.17 4.16a2 2 0 0 1-1.66 0L2 12.65"/>',
  stop: '<rect x="5" y="5" width="14" height="14" rx="2"/>',
  wifi: '<path d="M12 20h.01"/><path d="M2 8.82a15 15 0 0 1 20 0"/><path d="M5 12.86a10 10 0 0 1 14 0"/><path d="M8.5 16.43a5 5 0 0 1 7 0"/>',
  alert: '<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3"/><path d="M12 9v4"/><path d="M12 17h.01"/>',
  info: '<circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/>',
  drive: '<line x1="22" x2="2" y1="12" y2="12"/><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"/><line x1="6" x2="6.01" y1="16" y2="16"/><line x1="10" x2="10.01" y1="16" y2="16"/>',
  store: '<path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>',
};
const ic = (n) => `<svg class="i" viewBox="0 0 24 24" aria-hidden="true">${ICONS[n]}</svg>`;
const MARK = document.querySelector('.mark').outerHTML;

// Mods without artwork get a tinted cube, picked from the name so it stays the same between runs.
const TINTS = ['#2d4a66', '#3d3a5c', '#2f5349', '#5b4632', '#51344d', '#2f4f5c', '#44503a'];
function modIcon(m, cls = 'ic') {
  if (m.thumb) return `<img class="${cls}" src="${esc(m.thumb)}" data-fb="${esc(m.name)}" alt="" loading="lazy">`;
  return fallbackIcon(m.name, cls);
}
function fallbackIcon(name, cls = 'ic') {
  let h = 0; for (const c of String(name)) h = (h * 31 + c.charCodeAt(0)) | 0;
  return `<div class="${cls}" style="background:${TINTS[Math.abs(h) % TINTS.length]};color:#ffffffb0">${ic('box')}</div>`;
}
document.addEventListener('error', (e) => {
  const t = e.target;
  if (t && t.tagName === 'IMG' && t.dataset.fb !== undefined) t.outerHTML = fallbackIcon(t.dataset.fb, t.className);
}, true);

function toast(msg) {
  const t = document.createElement('div');
  t.className = 'toast'; t.textContent = msg;
  $('toasts').appendChild(t);
  setTimeout(() => t.remove(), 5000);
}
const fail = (e) => toast(clean(e));

function rich(text) {
  const inline = (s) => esc(s).replace(/\*\*(.+?)\*\*/g, '<b>$1</b>').replace(/`(.+?)`/g, '<code>$1</code>');
  return String(text || '').replace(/\r/g, '').split(/\n{2,}/).map((block) => {
    const lines = block.split('\n').filter((l) => l.trim());
    if (!lines.length) return '';
    if (lines.every((l) => /^\s*[-*] /.test(l))) return '<ul>' + lines.map((l) => `<li>${inline(l.replace(/^\s*[-*] /, ''))}</li>`).join('') + '</ul>';
    if (lines.length === 1 && /^#{1,3}\s/.test(lines[0])) return `<h4>${inline(lines[0].replace(/^#{1,3}\s+/, ''))}</h4>`;
    return `<p>${lines.map(inline).join('<br>')}</p>`;
  }).join('');
}

// ---- State ----------------------------------------------------------------------
let S = null;
let route = { name: 'home' };
const backStack = [], fwdStack = [];
const lib = { q: '', filter: 'all', sort: 'name', selected: new Set() };
const store = { items: null, loading: false, error: '', configured: true, offline: false, offlineReason: '', name: '', q: '', sort: 'new', tag: '', hideInstalled: false };
const installing = {};
let task = null;          // progress of a game file copy
let applyProg = null;     // { step, fraction }
let detailRef = null;     // { src, id }
let settingsTab = 'connection';
let lastKey = '';

function setHtml(el, html) { if (el && el._h !== html) { el._h = html; el.innerHTML = html; } }
const enabledMods = () => S.library.filter((m) => m.enabled);
const storeItem = (id) => (id && store.items ? store.items.find((x) => x.id === id) || null : null);
const connected = () => S.device.state === 'ready';

function go(r) {
  if (r.name === route.name && r.tab === route.tab) return;
  backStack.push(route); fwdStack.length = 0;
  route = r; mount();
}
function hop(from, to) {
  if (!from.length) return;
  to.push(route); route = from.pop(); mount();
}

// ---- Shared pieces --------------------------------------------------------------
function playBtn(big) {
  const g = S.game, cls = `btn primary play${big ? ' big' : ''}`;
  if (!g) return `<button class="${cls}" disabled>${ic('play')}Not installed</button>`;
  if (g.running) return `<button class="btn danger play${big ? ' big' : ''}" data-stop="1">${ic('stop')}Stop</button>`;
  return `<button class="${cls}" data-launch="1"${S.busy === 'apply' ? ' disabled' : ''}>${ic('play')}Play</button>`;
}
function gameMeta() {
  const n = enabledMods().length;
  return [
    S.game ? `<span>${ic('tag')}v${esc(S.game.versionName)}</span>` : '',
    `<span>${ic('layers')}${plural(n, 'mod')} enabled</span>`,
    S.appliedAt ? `<span>${ic('clock')}Applied ${esc(ago(S.appliedAt))}</span>` : '',
  ].join('');
}
const needsSetup = () => S.device.state === 'none' || S.device.state === 'lost' || !(S.base && S.base.usable);

function setupCard() {
  if (!needsSetup()) return '';
  const st = S.device.state, dev = st === 'ready', pulling = S.busy === 'base';
  const a = st === 'lost'
    ? `<button class="btn line small" data-pair="1">Pair again</button><button class="btn primary small" data-reconnect="1">Try again</button>`
    : st === 'none' ? '<button class="btn primary" data-pair="1">Pair headset</button>' : '';
  const b = S.base && S.base.usable ? '' : pulling ? '<span class="spin"></span>'
    : dev && S.game ? '<button class="btn primary" data-pull="1">Copy game file</button>' : '<button class="btn line" data-choose="1">Choose file</button>';
  const devText = dev ? 'Connected over wireless ADB.'
    : st === 'reconnecting' ? 'Reconnecting automatically…'
    : st === 'lost' ? 'Could not reconnect. Check that Wireless debugging is on.'
    : 'One time only. GorillaRinth reconnects on its own after this.';
  return `<div class="card setup">
    <div class="step ${dev ? 'done' : ''}"><span class="num">${dev ? ic('check') : st === 'reconnecting' ? '<span class="spin"></span>' : '1'}</span><div class="t"><b>Pair your headset</b><small>${devText}</small></div><div class="row">${a}</div></div>
    <div class="step ${S.base && S.base.usable ? 'done' : ''}"><span class="num">${S.base && S.base.usable ? ic('check') : '2'}</span><div class="t"><b>Save a clean copy of the game file</b><small>${S.base && S.base.usable ? esc(S.base.name) : pulling ? 'Copying. You can keep using the app.' : 'Mods are built from this copy, even if the game is already modded. It takes about 3 GB of space.'}</small>${pulling && task ? `<div class="bar"><i style="width:${Math.round(task.fraction * 100)}%"></i></div>` : ''}</div>${b}</div>
  </div>`;
}

function issueBanners() {
  return S.checks.map((c, i) => {
    return `<div class="banner ${c.lvl}">${ic('alert')}<div class="t">${esc(c.text)}</div>${c.action ? `<button class="btn small line" data-action="${i}">${esc(c.action.label)}</button>` : ''}</div>`;
  }).join('');
}

function tile(m) {
  return `<button class="tile card" data-detail="${esc(m.id)}" data-src="lib" title="${esc(m.name)}">${modIcon(m)}<div class="n">${esc(m.name)}</div><div class="s"><span class="dot ${m.enabled ? 'on' : ''}"></span>${m.enabled ? 'Enabled' : 'Disabled'}</div></button>`;
}

const libFiltered = () => {
  const q = lib.q.trim().toLowerCase();
  let l = S.library.filter((m) => (lib.filter === 'all' || (lib.filter === 'on') === m.enabled) && (!q || [m.name, m.creator, m.summary].join(' ').toLowerCase().includes(q)));
  if (lib.sort === 'size') l = l.slice().sort((a, b) => b.bytes - a.bytes);
  if (lib.sort === 'new') l = l.slice().sort((a, b) => (b.added || 0) - (a.added || 0));
  return l;
};

// ---- Home -----------------------------------------------------------------------
function mountHome() {
  return `<div class="inner">
    <h1 class="h">Jump in</h1>
    <div class="card jump" id="jump"></div>
    <div id="setup"></div>
    <div class="section">
      <h1 class="h">Library</h1>
      <div class="toolbar" style="margin-top:0">
        <div class="search">${ic('search')}<input id="lib-q" type="search" placeholder="Search your mods" value="${esc(lib.q)}" autocomplete="off"></div>
        <button class="btn line" data-upload="1">${ic('folder')}Upload mods</button>
        <button class="btn primary" data-go="discover">${ic('compass')}Browse mods</button>
      </div>
      <div id="home-grid"></div>
    </div>
  </div>`;
}
function updateHome() {
  setHtml($('jump'), `<div class="art"></div><img class="ic" src="orion-icon.png" alt=""><div><h2>Orion Drift</h2><div class="meta">${gameMeta()}</div>${playBtn(true)}</div>`);
  setHtml($('setup'), setupCard() || issueBanners());
  const l = libFiltered();
  setHtml($('home-grid'), l.length ? `<div class="grid">${l.map(tile).join('')}</div>`
    : S.library.length ? '<div class="empty">No mods match your search.</div>'
    : emptyLibrary());
}
function emptyLibrary() {
  return `<div class="empty">${ic('box').replace('class="i"', 'class="i big"')}<h3>No mods yet</h3><p>Upload a mod archive from this headset, or browse a mod store.</p></div>`;
}

// ---- Orion Drift ----------------------------------------------------------------
function mountGame() {
  const tab = route.tab || 'content';
  return `<div class="inner">
    <div class="head" id="game-head"></div>
    <div class="tabs" role="tablist" style="margin-top:18px">
      <button role="tab" data-gtab="content" aria-selected="${tab === 'content'}">${ic('layers')}Content</button>
      <button role="tab" data-gtab="device" aria-selected="${tab === 'device'}">${ic('wifi')}Device</button>
    </div>
    ${tab === 'content' ? `
    <div id="issues"></div>
    <div class="toolbar">
      <div class="search">${ic('search')}<input id="lib-q" type="search" placeholder="Search mods" value="${esc(lib.q)}" autocomplete="off"></div>
      <button class="btn line" data-upload="1">${ic('folder')}Upload mods</button>
      <button class="btn primary" data-go="discover">${ic('compass')}Browse mods</button>
    </div>
    <div class="row wrap" style="margin-top:12px">
      <div class="select"><select id="lib-sort" aria-label="Sort"><option value="name">Name (A-Z)</option><option value="new">Recently added</option><option value="size">Largest</option></select>${ic('down')}</div>
      <span class="vline"></span>
      <span id="lib-chips" class="row"></span>
      <span class="spacer"></span>
      <span id="lib-bulk" class="row"></span>
    </div>
    <div id="lib-table"></div>` : '<div id="device-tab"></div>'}
  </div>`;
}
function modRow(m) {
  const sel = lib.selected.has(m.id), sm = storeItem(m.modId), id = esc(m.id);
  const changed = m.enabled !== m.applied;
  return `<div class="tr ${m.enabled ? '' : 'off'}">
    <button class="box" role="checkbox" aria-checked="${sel}" data-select="${id}" aria-label="Select ${esc(m.name)}">${ic('check')}</button>
    <button class="proj" data-detail="${id}" data-src="lib">${modIcon(m)}<span class="x"><b>${esc(m.name)}${changed ? `<span class="tag-new">${m.enabled ? 'Not applied' : 'Still in game'}</span>` : ''}</b><small>${esc(m.creator || 'Unknown')}</small></span></button>
    <div class="ver"><b>${m.version ? 'v' + esc(m.version) : 'Local file'}</b><small>${esc(m.file)}${m.bytes ? ' · ' + size(m.bytes) : ''}</small></div>
    <div class="acts">
      ${sm && sm.update ? `<button class="icon-btn brand" data-install="${esc(sm.id)}" aria-label="Update ${esc(m.name)}" title="Update">${ic('download')}</button>` : ''}
      <button class="switch" role="switch" aria-checked="${m.enabled}" data-toggle="${id}" aria-label="Enable ${esc(m.name)}"></button>
      <button class="icon-btn" data-remove="${id}" aria-label="Remove ${esc(m.name)}" title="Remove">${ic('trash')}</button>
      <button class="icon-btn" data-detail="${id}" data-src="lib" aria-label="Details for ${esc(m.name)}" title="Details">${ic('dots')}</button>
    </div>
  </div>`;
}
function updateGame() {
  setHtml($('game-head'), `<img class="ic" src="orion-icon.png" alt=""><div class="spacer"><h1>Orion Drift</h1><div class="meta">${gameMeta()}</div></div>${playBtn(false)}<button class="round" style="width:48px;height:48px" data-settings="game" aria-label="Game settings">${ic('gear')}</button>`);
  if ($('device-tab')) { setHtml($('device-tab'), deviceTab()); return; }
  setHtml($('issues'), issueBanners());
  const on = enabledMods().length, all = S.library.length;
  setHtml($('lib-chips'), [['all', `All ${all}`], ['on', `Enabled ${on}`], ['off', `Disabled ${all - on}`]].map(([k, t]) => `<button class="chip" data-filter="${k}" aria-pressed="${lib.filter === k}">${t}</button>`).join(''));
  $('lib-sort').value = lib.sort;
  const live = new Set(S.library.map((m) => m.id));
  lib.selected.forEach((id) => { if (!live.has(id)) lib.selected.delete(id); });
  setHtml($('lib-bulk'), lib.selected.size
    ? `<span class="muted" style="font-size:14px">${lib.selected.size} selected</span><button class="btn small" data-bulk="on">Enable</button><button class="btn small" data-bulk="off">Disable</button><button class="btn small danger" data-bulk="rm">Remove</button>`
    : '');
  const l = libFiltered();
  if (!all) { setHtml($('lib-table'), `<div class="card table">${emptyLibrary()}</div>`); return; }
  const allSel = l.length && l.every((m) => lib.selected.has(m.id));
  setHtml($('lib-table'), `<div class="card table"><div class="tr th"><button class="box" role="checkbox" aria-checked="${!!allSel}" data-selectall="1" aria-label="Select all">${ic('check')}</button><span>Mod</span><span class="ver">Version</span><span class="acts">Actions</span></div>${l.length ? l.map(modRow).join('') : '<div class="empty">No mods match.</div>'}</div>`);
}
function deviceTab() {
  const d = S.device, g = S.game, st = d.state, b = S.base;
  const over = S.capacity && S.used > S.capacity;
  const label = { ready: 'Connected', reconnecting: 'Reconnecting…', lost: 'Could not reconnect', none: 'Not paired' }[st];
  return `<div class="dtwo">
    <div class="card pane"><h3>${ic('wifi')}Connection</h3>
      <div class="kv"><span>Headset</span><span>${esc(d.model)}</span></div>
      <div class="kv"><span>Wireless ADB</span><span>${label}</span></div>
      <div class="kv"><span>Reconnect automatically</span><span>${d.autoReconnect ? (d.automatic ? 'On' : st === 'ready' ? 'Setting up…' : 'Waiting for first connection') : 'Off'}</span></div>
      <div class="row">${st !== 'ready' ? `<button class="btn primary small" data-reconnect="1">Reconnect</button>` : ''}<button class="btn line small" data-pair="1">${d.paired ? 'Pair again' : 'Pair headset'}</button></div>
    </div>
    <div class="card pane"><h3>${ic('box')}Game</h3>
      <div class="kv"><span>Installed version</span><span>${g ? esc(g.versionName) : 'Not installed'}</span></div>
      <div class="kv"><span>Status</span><span>${g && g.running ? 'Running' : 'Not running'}</span></div>
      <div class="kv"><span>Mods on the headset</span><span>${S.library.filter((m) => m.applied).length}</span></div>
    </div>
    <div class="card pane"><h3>${ic('drive')}Original game file</h3>
      <div class="kv"><span>Saved copy</span><span>${b ? esc(b.name) : 'None'}</span></div>
      <div class="kv"><span>Size</span><span>${b && b.size ? size(b.size) : '–'}</span></div>
      ${b && b.fromModded ? '<p class="muted" style="font-size:14px">Copied from a modded game. Its mods are in your library, and optional content removed earlier stays removed until the game is reinstalled.</p>' : ''}
      ${S.busy === 'base' && task ? `<div class="bar" style="margin-top:8px"><i style="width:${Math.round(task.fraction * 100)}%"></i></div>` : ''}
      <div class="row">${connected() && g ? `<button class="btn line small" data-pull="1"${S.busy ? ' disabled' : ''}>Copy from Quest</button>` : ''}<button class="btn line small" data-choose="1"${S.busy ? ' disabled' : ''}>Choose file</button></div>
    </div>
    <div class="card pane"><h3>${ic('layers')}Mod space</h3>
      <div class="bar ${over ? 'over' : ''}" style="margin:12px 0 8px"><i style="width:${S.capacity ? Math.min(100, S.used / S.capacity * 100) : 0}%"></i></div>
      <div class="kv"><span>Used by enabled mods</span><span>${mb(S.used)} of ${S.capacity ? mb(S.capacity) + ' MB' : '–'}</span></div>
      <p class="muted" style="font-size:14px">The game file must keep its size, so optional content is dropped to make room.</p>
    </div>
    ${S.checks.length ? `<div class="pane full" style="padding:0">${issueBanners()}</div>` : ''}
  </div>`;
}

// ---- Discover -------------------------------------------------------------------
function mountDiscover() {
  return `<div class="inner"><div class="disc">
    <div>
      <div class="search">${ic('search')}<input id="store-q" type="search" placeholder="Search mods" value="${esc(store.q)}" autocomplete="off"></div>
      <div class="row wrap" style="margin-top:12px">
        <div class="select"><select id="store-sort" aria-label="Sort by"><option value="new">Sort by: Newest</option><option value="name">Sort by: A to Z</option><option value="size">Sort by: Largest</option></select>${ic('down')}</div>
        <span class="muted" id="store-count" style="font-size:14px;margin-left:6px"></span>
        <span class="spacer"></span>
        <button class="btn ghost small" data-refresh="1">${ic('refresh')}Refresh</button>
      </div>
      <div id="store-list"></div>
    </div>
    <aside class="card filters" id="store-filters"></aside>
  </div></div>`;
}
function storeFiltered() {
  let items = store.items.slice();
  const q = store.q.trim().toLowerCase();
  if (q) items = items.filter((m) => [m.name, m.creator, m.summary, m.description, (m.tags || []).join(' ')].join(' ').toLowerCase().includes(q));
  if (store.tag) items = items.filter((m) => (m.tags || []).includes(store.tag));
  if (store.hideInstalled) items = items.filter((m) => !m.installed);
  const when = (m) => Date.parse(m.updated || m.created) || 0;
  const by = { new: (a, b) => when(b) - when(a), name: (a, b) => a.name.localeCompare(b.name), size: (a, b) => b.bytes - a.bytes };
  return items.sort(by[store.sort] || by.new);
}
function storeBtn(m) {
  const p = installing[m.id];
  if (p) return `<button class="btn outline" disabled><span class="spin"></span>${p.phase === 'download' && p.fraction ? Math.round(p.fraction * 100) + '%' : 'Installing'}</button>`;
  if (m.update) return `<button class="btn outline" data-install="${esc(m.id)}">${ic('download')}Update</button>`;
  if (m.installed) return `<button class="btn line" disabled>${ic('check')}Installed</button>`;
  return `<button class="btn outline" data-install="${esc(m.id)}">${ic('plus')}Install</button>`;
}
function result(m) {
  const id = esc(m.id);
  const tags = (m.tags || []).slice(0, 4).map((t) => `<span class="tag">${esc(t)}</span>`).join('');
  return `<div class="card result">
    <div data-detail="${id}" data-src="store">${modIcon(m)}</div>
    <div style="min-width:0"><h3 data-detail="${id}" data-src="store"><em>${esc(m.name)}</em><span>by ${esc(m.creator)}</span></h3><p>${esc(m.summary || '')}</p>${tags ? `<div class="tags">${tags}</div>` : ''}</div>
    <div class="res-side">${storeBtn(m)}<span>${m.bytes ? `${ic('drive')}${size(m.bytes)}` : ''}</span><span class="muted">${m.updated ? esc(ago(m.updated)) : ''}</span></div>
  </div>`;
}
function updateDiscover() {
  $('store-sort').value = store.sort;
  const list = $('store-list'), count = $('store-count'), filters = $('store-filters');
  filters.hidden = !store.configured;
  if (!store.configured) {
    count.textContent = '';
    setHtml(list, `<div class="card empty" style="margin-top:14px">${ic('store').replace('class="i"', 'class="i big"')}<h3>No mod store added</h3><p>Add the address of a store's manifest in Settings to browse its mods here. You can always upload mod archives directly.</p><div class="row"><button class="btn line" data-upload="1">${ic('folder')}Upload mods</button><button class="btn primary" data-settings="store">Add a store</button></div></div>`);
    return;
  }
  if (store.error) { count.textContent = ''; setHtml(list, `<div class="card empty" style="margin-top:14px"><h3>Could not load the store</h3><p>${esc(store.error)}</p><div class="row"><button class="btn line" data-settings="store">Store settings</button><button class="btn primary" data-refresh="1">Try again</button></div></div>`); return; }
  if (!store.items) { count.textContent = ''; setHtml(list, '<div class="empty"><span class="spin"></span></div>'); return; }
  const items = storeFiltered();
  count.textContent = `${items.length} of ${plural(store.items.length, 'mod')}${store.name ? ' · ' + store.name : ''}`;
  const note = store.offline ? `<div class="banner">${ic('info')}<div class="t">Showing the last saved list. ${esc(store.offlineReason)}</div></div>` : '';
  setHtml(list, note + (items.length ? items.map(result).join('') : `<div class="empty">${store.items.length ? 'No mods match your search.' : 'This store is connected, but it has no mods yet.'}</div>`));
  const tags = [...new Set(store.items.flatMap((m) => m.tags || []))].sort((a, b) => a.localeCompare(b));
  setHtml(filters, `<div class="opt"><span>Hide installed</span><button class="switch" role="switch" aria-checked="${store.hideInstalled}" data-hide="1" aria-label="Hide already installed"></button></div>`
    + (tags.length ? `<h3>Category</h3><button class="f ${store.tag ? '' : 'on'}" data-tag="">All</button>${tags.map((t) => `<button class="f ${store.tag === t ? 'on' : ''}" data-tag="${esc(t)}">${esc(t)}</button>`).join('')}` : ''));
}

// ---- Setup ------------------------------------------------------------------------
let setupStep = 0;
function setupSteps() {
  const base = !!(S.base && S.base.usable), mods = S.library.length, on = enabledMods().length;
  return [
    { t: 'Find Orion Drift', s: 'Check the game is installed', done: !!S.game },
    { t: 'Connect your headset', s: 'Pair once, then it is automatic', done: connected() },
    { t: 'Save the game file', s: 'The copy mods are built from', done: base },
    { t: 'Add your mods', s: 'Upload or browse', done: mods > 0, optional: true },
    { t: 'Apply your mods', s: 'Build them into the game', done: on > 0 && !S.pending, optional: true },
    { t: 'Play', s: 'Launch Orion Drift', done: false },
  ];
}
function mountSetup() {
  return `<div class="inner"><h1 class="h">Setup</h1><div class="wiz">
    <div class="card wiz-steps" id="wiz-steps"></div>
    <div class="card wiz-panel"><div class="wiz-body" id="wiz-body"></div><div class="wiz-foot" id="wiz-foot"></div></div>
  </div></div>`;
}
const wizOk = (t) => `<div class="wiz-ok">${ic('check')}<span>${t}</span></div>`;
const wizWait = (t) => `<div class="wiz-wait"><span class="spin"></span><span>${t}</span></div>`;
function setupBody(i) {
  const d = S.device, st = d.state, g = S.game, b = S.base;
  if (i === 0) return `<h2>Find Orion Drift</h2><p>GorillaRinth manages mods for the copy of Orion Drift on this headset.</p>`
    + (g ? wizOk(`Orion Drift ${esc(g.versionName)} is installed.`) : wizWait('Install Orion Drift from the Meta Horizon Store. This page updates when it is ready.'));
  if (i === 1) {
    if (st === 'ready') return `<h2>Connect your headset</h2><p>GorillaRinth uses wireless ADB on this headset to read and replace the game file.</p>${wizOk(`Connected to ${esc(d.model)}. It will reconnect by itself from now on.`)}`;
    if (st === 'reconnecting') return `<h2>Connect your headset</h2><p>This headset is already paired, so there is nothing to type.</p>${wizWait('Reconnecting automatically…')}`;
    if (st === 'lost') return `<h2>Connect your headset</h2><p>This headset is paired, but GorillaRinth could not reach it. Check that Wireless debugging is on, then try again.</p><div class="row"><button class="btn primary" data-reconnect="1">Try again</button><button class="btn line" data-devset="1">Open Settings</button><button class="btn line" data-pair="1">Pair again</button></div>`;
    return `<h2>Connect your headset</h2><p>You only do this once.</p><ol class="numbered">
      <li>Turn on <b>Developer Mode</b> for this headset in the Meta Horizon phone app.</li>
      <li>Open Settings, press <b>Open</b>, then go to <b>Developer options &gt; Wireless debugging</b> and turn it on.<br><button class="btn" data-devset="1">Open Settings</button></li>
      <li>Choose <b>Pair device with pairing code</b>, keep that window open, and enter the code here.<br><button class="btn primary" data-pair="1">Enter pairing code</button></li>
    </ol>`;
  }
  if (i === 2) {
    const copying = S.busy === 'base';
    const intro = '<h2>Save the game file</h2><p>Mods are built from a saved copy of the game file, which needs about 3 GB of free space. If the game is already modded, GorillaRinth keeps those mods and adds them to your library.</p>';
    if (b && b.usable) return intro + wizOk(`Saved ${esc(b.name)}.`);
    if (copying) return intro + wizWait('Copying the game file. This takes a minute or two.') + (task ? `<div class="bar wiz-bar"><i style="width:${Math.round(task.fraction * 100)}%"></i></div>` : '');
    return intro + `<div class="row">${connected() && g ? '<button class="btn primary" data-pull="1">Copy from this headset</button>' : '<button class="btn primary" disabled>Connect your headset first</button>'}<button class="btn line" data-choose="1">Choose a file</button></div>`;
  }
  if (i === 3) {
    const list = S.library.length ? `<div class="wiz-mods">${S.library.map((m) => `<div class="wiz-mod">${modIcon(m)}<span>${esc(m.name)}</span><button class="switch" role="switch" aria-checked="${m.enabled}" data-toggle="${esc(m.id)}" aria-label="Enable ${esc(m.name)}"></button></div>`).join('')}</div>` : '';
    return `<h2>Add your mods</h2><p>Upload a zip, 7z or rar from this headset, or browse a mod store if you have added one. Switch on the mods you want to use.</p>${list}<div class="row"><button class="btn primary" data-upload="1">${ic('folder')}Upload mods</button><button class="btn line" data-go="discover">${ic('compass')}Browse mods</button></div>`;
  }
  if (i === 4) {
    const on = enabledMods();
    const intro = '<h2>Apply your mods</h2><p>This builds your enabled mods into the game file and sends it to the game. It takes a few minutes and closes the game while it runs.</p>';
    if (applyProg) {
      const STEPS = ['Building the patched game file', 'Checking size and checksums', 'Sending to Quest', 'Checking the copy on the Quest', 'Done'];
      return intro + wizWait(STEPS[applyProg.step] || STEPS[0]) + `<div class="bar wiz-bar"><i style="width:${Math.round(((applyProg.step + (applyProg.fraction || 0)) / 4) * 100)}%"></i></div>`;
    }
    if (!on.length) return intro + `<div class="wiz-wait">${ic('info')}<span>No mods are switched on, so there is nothing to apply.</span></div>`;
    if (!S.pending) return intro + wizOk(`${plural(on.length, 'mod')} in the game.`);
    return intro + (S.canApply ? '' : `<div class="wiz-wait">${ic('info')}<span>${!connected() ? 'Waiting for the headset connection.' : !(b && b.usable) ? 'Save the game file first.' : S.busy ? 'Waiting for the current task to finish.' : 'There is a problem to fix on the Orion Drift page.'}</span></div>`)
      + `<div class="row"><button class="btn primary" id="go"${S.canApply ? '' : ' disabled'}>Apply ${plural(on.length, 'mod')}</button></div>`;
  }
  return `<h2>You're all set</h2><p>Press Play to launch Orion Drift. You can add more mods whenever you like: switch them on, press Apply, and your earlier mods stay in.</p><div class="row">${playBtn(true)}</div>`;
}
function updateSetup() {
  const steps = setupSteps();
  setHtml($('wiz-steps'), steps.map((s, i) => `<button class="wiz-step ${i === setupStep ? 'on' : ''} ${s.done ? 'done' : ''}" data-sstep="${i}" aria-current="${i === setupStep}"><span class="wiz-num">${s.done ? ic('check') : i + 1}</span><span>${esc(s.t)}<small>${esc(s.s)}</small></span></button>`).join(''));
  setHtml($('wiz-body'), `<div class="wiz-eyebrow">Step ${setupStep + 1} of ${steps.length}</div>` + setupBody(setupStep));
  const c = steps[setupStep], last = setupStep === steps.length - 1;
  const next = last ? '<button class="btn line" data-go="home">Go to Home</button>'
    : c.done ? '<button class="btn primary" data-snext="1">Continue</button>'
    : c.optional ? '<button class="btn line" data-snext="1">Skip for now</button>'
    : '<button class="btn primary" disabled>Continue</button>';
  setHtml($('wiz-foot'), `<button class="btn ghost" data-sback="1"${setupStep ? '' : ' disabled'}>${ic('left')}Back</button><small>${!c.done && !c.optional && !last ? 'Finish this step to continue' : ''}</small>${next}`);
}

// ---- Top bar, apply bar, dialogs -------------------------------------------------
function updateChrome() {
  const d = S.device, g = S.game;
  const conn = d.state === 'ready' ? `<span class="pill on"><span class="dot on"></span>${esc(d.model || 'Quest')} connected</span>`
    : d.state === 'reconnecting' ? '<span class="pill"><span class="spin"></span>Reconnecting…</span>'
    : d.state === 'lost' ? `<button class="pill warn" data-reconnect="1"><span class="dot warn"></span>Reconnect</button>`
    : `<button class="pill warn" data-pair="1"><span class="dot warn"></span>Not paired</button>`;
  const run = g && g.running
    ? `<span class="pill on"><span class="dot on"></span>Orion Drift running<button class="stop" data-stop="1" aria-label="Stop game">${ic('stop')}</button></span>`
    : '<span class="pill"><span class="dot"></span>Not running</span>';
  const copy = S.busy === 'base' && task ? `<span class="pill">${ic('download')}<span class="bar"><i style="width:${Math.round(task.fraction * 100)}%"></i></span></span>` : '';
  setHtml($('status'), copy + run + conn);
  const crumbs = { home: `${ic('play')}Home`, discover: `${ic('compass')}Discover mods`, game: '<img src="orion-icon.png" alt="">Orion Drift', setup: `${ic('checks')}Setup` };
  setHtml($('crumb'), crumbs[route.name]);
  $('nav-back').disabled = !backStack.length; $('nav-fwd').disabled = !fwdStack.length;
  document.querySelectorAll('.rail-btn[data-go]').forEach((b) => b.classList.toggle('active', b.dataset.go === route.name));

  const bar = $('applybar');
  const STEPS = ['Building the patched game file', 'Checking size and checksums', 'Sending to Quest', 'Checking the copy on the Quest', 'Done'];
  let html = '';
  if (route.name === 'setup') html = '';
  else if (applyProg) {
    html = `<span class="spin"></span><div class="t">${STEPS[applyProg.step] || STEPS[0]}${applyProg.fraction && applyProg.step < 4 ? ` · ${Math.round(applyProg.fraction * 100)}%` : ''}<div class="bar"><i style="width:${Math.round(((applyProg.step + (applyProg.fraction || 0)) / 4) * 100)}%"></i></div></div>`;
  } else if (S.pending) {
    const n = enabledMods().length;
    const hint = S.canApply ? 'Takes a few minutes. The game closes while it runs.' : S.busy ? 'Waiting for the current task to finish.' : !connected() ? 'Waiting for the headset connection.' : !(S.base && S.base.usable) ? 'Save the original game file first.' : 'Fix the problems shown on this page first.';
    html = `<div class="t">${n ? 'Changes are not in the game yet' : 'Mods are still in the game'}<small>${hint}</small></div><button class="btn primary" id="go"${S.canApply ? '' : ' disabled'}>${n ? 'Apply to game' : 'Remove from game'}</button>`;
  }
  bar.hidden = !html;
  setHtml(bar, html);
  $('page').classList.toggle('has-bar', !!html);
}

function renderDetail() {
  if (!detailRef) return;
  const isStore = detailRef.src === 'store';
  const m = isStore ? storeItem(detailRef.id) : S.library.find((x) => x.id === detailRef.id);
  if (!m) { $('detail').close(); return; }
  const sm = isStore ? m : storeItem(m.modId);
  const status = isStore ? (m.update ? 'Update available' : m.installed ? 'Installed' : 'Not installed') : (m.enabled ? 'Enabled' : 'Disabled');
  const facts = [['Creator', m.creator], ['Version', m.version ? 'v' + m.version : ''], ['Size', m.bytes ? size(m.bytes) : ''], ['Updated', m.updated ? ago(m.updated) : ''], ['Status', status]]
    .filter((f) => f[1]).map((f) => `<div><span>${f[0]}</span><b>${esc(f[1])}</b></div>`).join('');
  const btns = isStore ? storeBtn(m)
    : `<button class="switch" role="switch" aria-checked="${m.enabled}" data-toggle="${esc(m.id)}" aria-label="Enabled"></button>${sm && sm.update ? storeBtn(sm) : ''}`;
  setHtml($('detail-body'),
    ((m.screenshots || []).length ? `<div class="shots">${m.screenshots.map((s) => `<img src="${esc(s)}" alt="" loading="lazy">`).join('')}</div>` : '')
    + `<div class="dhead">${modIcon(m)}<div class="spacer"><h2>${esc(m.name)}</h2>${m.summary ? `<div class="muted">${esc(m.summary)}</div>` : ''}</div>${btns}<button class="round" data-close="detail" aria-label="Close">${ic('x')}</button></div>`
    + ((m.tags || []).length ? `<div class="tags">${m.tags.map((t) => `<span class="tag">${esc(t)}</span>`).join('')}</div>` : '')
    + `<div class="facts">${facts}</div>`
    + (m.description ? `<div class="sect">About</div><div class="desc">${rich(m.description)}</div>` : '')
    + (m.changelog ? `<div class="sect">What's new</div><div class="desc">${rich(m.changelog)}</div>` : '')
    + ((m.files || []).length ? `<div class="sect">Files</div><ul class="files">${m.files.map((f) => `<li><span>${esc(f.name)}</span><span>${f.bytes ? size(f.bytes) : ''}</span></li>`).join('')}</ul>` : ''));
}
function openDetail(src, id) {
  detailRef = { src, id }; renderDetail();
  const d = $('detail'); if (!d.open) d.showModal();
}
$('detail').addEventListener('close', () => { detailRef = null; });

const CREDITS = [
  ['Stripes', 'GorillaRinth developer and original UI contributor'],
  ['MeiMei', 'GorillaRinth contributor and mod creator'],
  ['Claude', 'AI Coding Assistant'],
  ['Driftrinth', 'Original renderer, layout and UI source'],
];
// The slider's markup never changes, so background refreshes can't replace it while it is being dragged.
function syncSpeedSlider() {
  const r = $('rgb-speed');
  if (!r) return;
  r.value = window.appearance.rgbSpeed;
  const secs = Math.round(360 / window.appearance.rgbSpeed);
  $('rgb-speed-label').textContent = secs >= 60 ? `One cycle per ${+(secs / 60).toFixed(1)} min` : `One cycle per ${secs} sec`;
}
const SETTINGS_TABS = { connection: ['wifi', 'Connection'], store: ['store', 'Mod store'], game: ['box', 'Game'], appearance: ['palette', 'Appearance'], about: ['info', 'About'], credits: ['heart', 'Credits'] };
function renderSettings() {
  for (const [k, [i, t]] of Object.entries(SETTINGS_TABS)) {
    const b = $('st-' + k); b.classList.toggle('on', settingsTab === k); setHtml(b, ic(i) + t);
  }
  const d = S.device, el = $('settings-body');
  if (settingsTab === 'connection') {
    setHtml(el, `<section><h3>Reconnect automatically</h3><div class="opt"><p>After pairing once, GorillaRinth turns Wireless debugging back on when it is off and reconnects by itself, including after a restart. This gives the app permission to change that one Android setting.</p><button class="switch" role="switch" aria-checked="${d.autoReconnect}" data-autore="1" aria-label="Reconnect automatically"></button></div></section>
      <section><h3>Headset</h3><p>${d.state === 'ready' ? 'Connected to ' + esc(d.model) + '.' : 'Not connected right now.'}</p><div class="row">${d.state !== 'ready' ? '<button class="btn primary small" data-reconnect="1">Reconnect</button>' : ''}<button class="btn line small" data-pair="1">${d.paired ? 'Pair again' : 'Pair headset'}</button></div></section>`);
  } else if (settingsTab === 'store') {
    if (!el.querySelector('#cdn-url')) {
      el._h = null;
      el.innerHTML = `<section><h3>Mod store</h3><p>The address of a folder or manifest.json that lists mods. Leave it empty to only use mods you upload.</p><label class="field" style="margin-top:12px">Store address<input type="text" id="cdn-url" placeholder="https://example.com/mods/" spellcheck="false" autocomplete="off"></label><p class="err" id="cdn-msg" hidden></p><div class="row"><button class="btn primary small" id="cdn-save">Save and load</button><button class="btn line small" id="cdn-default">Use default</button></div></section>`;
      api.storeConfig().then((c) => { if ($('cdn-url')) $('cdn-url').value = c.url; }).catch(fail);
    }
  } else if (settingsTab === 'game') {
    const can = connected() && S.base && S.base.usable && !S.busy;
    setHtml(el, `<section><h3>Remove all mods</h3><p>${S.base && S.base.fromModded ? 'Rebuilds the game file with no mods, using your saved copy.' : 'Puts your saved original game file back on the headset.'} Your library stays as it is.</p><div class="row"><button class="btn danger small" id="restore-base"${can ? '' : ' disabled'}>Remove all mods</button></div></section>
      <section><h3>Installed game</h3><p>${S.game ? `Orion Drift ${esc(S.game.versionName)} (${esc(S.game.versionCode)})` : 'Orion Drift is not installed.'}</p></section>`);
  } else if (settingsTab === 'appearance') {
    const A = window.appearance;
    const swatch = { blue: '#4f9cff', cyan: '#22d3ee', purple: '#c78aff', pink: '#ff7ab8', red: '#ff6984', orange: '#ffa347', green: '#1bd96a', rgb: 'conic-gradient(#ff3b3b, #ffd23b, #3bff6a, #3bd5ff, #5a3bff, #ff3bd5, #ff3b3b)' };
    setHtml(el, `<section><h3>Mode</h3><div class="mode-grid">${Object.entries(A.MODES).map(([k, n]) => `<button class="mode-card" data-setmode="${k}" aria-pressed="${A.mode === k}"><div class="mode-prev ${k}"><i></i><div class="pr"><div class="pc"></div><div class="pb"></div></div></div><div class="mode-label"><span>${n}</span>${A.mode === k ? ic('check') : ''}</div></button>`).join('')}</div></section>
      <section><h3>Theme</h3><div class="theme-grid">${Object.entries(A.THEMES).map(([k, t]) => `<button class="theme-chip" data-settheme="${k}" aria-pressed="${A.theme === k}"><span class="theme-dot" style="background:${swatch[k]}"></span>${t.name}</button>`).join('')}</div>${A.theme === 'rgb' ? '<div class="speed"><label class="speed-head" for="rgb-speed"><span>RGB speed</span><span id="rgb-speed-label"></span></label><div class="speed-row"><span>Slow</span><input type="range" id="rgb-speed" min="1" max="36" step="1" aria-label="RGB speed"><span>Fast</span></div></div>' : ''}</section>`);
    syncSpeedSlider();
  } else if (settingsTab === 'credits') {
    setHtml(el, `<section><h3>Credits</h3><div class="credits">${CREDITS.map(([n, r]) => `<div class="credit"><b>${esc(n)}</b><span>${esc(r)}</span></div>`).join('')}</div><p style="margin-top:14px">Font: Inter, under the SIL Open Font License.</p></section>`);
  } else {
    setHtml(el, `<section><div class="about-mark">${MARK}GorillaRinth</div><p>Version ${esc(S.version)}. A Gorilla Tag companion and mod manager.</p><p>UI source: GorillaRinth. Credits to its contributors.</p></section>`);
  }
}

// ---- Liquid dialog backgrounds -----------------------------------------------------
// Soft blue pools pour in from every edge on open, then lean toward the cursor or controller pointer.
// Springs give the lag and overshoot that make it read as liquid. Gradients moved by transform stay cheap on Quest.
let pointerX = innerWidth / 2, pointerY = innerHeight / 2;
const trackPointer = (e) => { pointerX = e.clientX; pointerY = e.clientY; };
addEventListener('pointermove', trackPointer, { passive: true, capture: true });
addEventListener('pointerdown', trackPointer, { passive: true, capture: true });

function makeAura(box, { home = 0.46, pullScale = 1 } = {}) {
  const paint = () => window.appearance.pools().forEach((c, i) => {
    blobs[i].el.style.background = `radial-gradient(closest-side, ${c}, ${c}b0 25%, ${c}66 50%, ${c}24 72%, ${c}0a 88%, transparent)`;
  });
  const blobs = window.appearance.pools().map((c, i, all) => {
    const el = document.createElement('i');
    box.appendChild(el);
    return { el, angle: (i / all.length) * Math.PI * 2 + 0.3, pull: (0.16 + (i % 3) * 0.12) * pullScale, k: 0.01 + (i % 4) * 0.003, phase: i * 1.7, x: 0, y: 0, vx: 0, vy: 0 };
  });
  blobs[1].pull = 0.9 * pullScale; blobs[1].k = 0.035; // this one stays closest to the pointer
  let raf = 0, last = 0, leaving = false;
  const edge = (b, W, H) => { const reach = Math.max(W, H) * 0.42; return [W / 2 + Math.cos(b.angle) * (W / 2 + reach), H / 2 + Math.sin(b.angle) * (H / 2 + reach)]; };
  function frame(now) {
    const W = innerWidth, H = innerHeight, t = now / 1000;
    const steps = Math.min(3, (now - last) / 16.67); last = now;
    const damp = Math.pow(0.91, steps);
    for (const b of blobs) {
      const hx = W / 2 + Math.cos(b.angle) * W * home, hy = H / 2 + Math.sin(b.angle) * H * home;
      let tx = hx + (pointerX - hx) * b.pull + Math.sin(t * 0.7 + b.phase) * W * 0.05;
      let ty = hy + (pointerY - hy) * b.pull + Math.cos(t * 0.9 + b.phase) * H * 0.06;
      if (leaving) [tx, ty] = edge(b, W, H);
      const k = leaving ? 0.0075 : b.k;
      b.vx = (b.vx + (tx - b.x) * k * steps) * damp; b.vy = (b.vy + (ty - b.y) * k * steps) * damp;
      b.x += b.vx * steps; b.y += b.vy * steps;
      const stretch = Math.min(0.45, Math.hypot(b.vx, b.vy) / 60);
      b.el.style.transform = `translate3d(${b.x}px, ${b.y}px, 0) translate(-50%, -50%) rotate(${Math.atan2(b.vy, b.vx)}rad) scale(${1 + stretch}, ${1 - stretch * 0.5})`;
    }
    raf = requestAnimationFrame(frame);
  }
  return {
    paint,
    start() {
      cancelAnimationFrame(raf);
      paint();
      const W = innerWidth, H = innerHeight;
      leaving = false;
      for (const b of blobs) {
        // Starts fully off screen on its own side.
        [b.x, b.y] = edge(b, W, H);
        b.vx = 0; b.vy = 0;
      }
      last = performance.now();
      raf = requestAnimationFrame(frame);
    },
    // Pours back out past the edges, then calls done.
    leave(done) { leaving = true; setTimeout(done, 900); },
    stop() { cancelAnimationFrame(raf); },
  };
}
const aura = makeAura($('settings').querySelector('.aura'));
function closeWithFlow(d, a) {
  if (!d.open || d.classList.contains('leaving')) return;
  d.classList.add('leaving');
  a.leave(() => { d.close(); d.classList.remove('leaving'); });
}
const closeSettings = () => closeWithFlow($('settings'), aura);
$('settings').addEventListener('close', () => aura.stop());
$('settings').addEventListener('cancel', (e) => { e.preventDefault(); closeSettings(); });

// ---- Confirmation ------------------------------------------------------------------
// The pools stay near the edges here, so the page behind it stays easy to see.
const confirmAura = makeAura($('confirm').querySelector('.aura'), { home: 0.66, pullScale: 0.32 });
let confirmDone = null;
function answerConfirm(yes) {
  const done = confirmDone; confirmDone = null;
  if (done) done(yes);
  closeWithFlow($('confirm'), confirmAura);
}
function askRemove(mods) {
  const one = mods.length === 1 ? mods[0] : null;
  const inGame = mods.filter((m) => m.applied).length;
  setHtml($('confirm-body'), `<div class="confirm-head">${one ? modIcon(one) : `<div class="ic confirm-trash">${ic('trash')}</div>`}<h2>Remove ${one ? esc(one.name) : plural(mods.length, 'mod')}?</h2></div>
    <p>This deletes ${one ? 'it' : 'them'} from your library.${inGame ? ` ${inGame === mods.length ? (one ? 'It stays' : 'They stay') : plural(inGame, 'mod') + ' stay'} in the game until you apply again.` : ''}</p>`);
  $('confirm').showModal(); confirmAura.start();
  return new Promise((resolve) => { confirmDone = resolve; });
}
$('confirm').addEventListener('close', () => { confirmAura.stop(); if (confirmDone) answerConfirm(false); });
$('confirm').addEventListener('cancel', (e) => { e.preventDefault(); answerConfirm(false); });

// ---- Refresh and data ------------------------------------------------------------
function update() {
  if (!S) return;
  updateChrome();
  if (route.name === 'home') updateHome();
  else if (route.name === 'game') updateGame();
  else if (route.name === 'setup') updateSetup();
  else updateDiscover();
  if ($('detail').open) renderDetail();
  if ($('settings').open) renderSettings();
}
function mount() {
  const p = $('page');
  p._h = null;
  if (route.name === 'setup' && S) { const u = setupSteps().findIndex((x) => !x.done && !x.optional); setupStep = u < 0 ? 5 : u; }
  p.innerHTML = route.name === 'home' ? mountHome() : route.name === 'game' ? mountGame() : route.name === 'setup' ? mountSetup() : mountDiscover();
  p.scrollTop = 0;
  if (route.name === 'discover') loadStore(false);
  update();
}
async function refresh(force) {
  try {
    const s = await api.state();
    const key = JSON.stringify(s);
    if (!force && key === lastKey) return;
    lastKey = key; S = s; update();
  } catch (e) { /* the backend is still starting; the next poll retries */ }
}
function applyStore(r) {
  store.configured = r.configured; store.items = r.mods; store.name = r.name || '';
  store.offline = !!r.offline; store.offlineReason = r.offlineReason || '';
}
async function loadStore(force) {
  if (store.loading || (store.items && !force)) return;
  store.loading = true; store.error = ''; update();
  try { applyStore(await api.storeList({ refresh: !!force })); } catch (e) { store.error = clean(e); store.items = null; }
  store.loading = false; update();
}
async function refreshStoreFlags() {
  if (!store.items) return;
  try { applyStore(await api.storeList({ refresh: false })); } catch (e) { /* keep what is on screen */ }
}

// ---- Pairing ---------------------------------------------------------------------
let portSearch = 0;
function openPair() {
  $('pair-msg').hidden = true; $('pair-found').hidden = true;
  $('pair-port').value = ''; $('pair-code').value = '';
  if ($('settings').open) $('settings').close();
  $('pair').showModal();
  const mine = ++portSearch;
  (async function look() {
    while (mine === portSearch && $('pair').open) {
      let port = 0;
      try { port = await api.pairingPort(); } catch (e) { /* retried below */ }
      if (mine !== portSearch || !$('pair').open) return;
      if (port > 0) {
        if ($('pair-port').value !== String(port)) { $('pair-port').value = port; $('pair-found').textContent = 'Found the pairing screen. Just enter the code.'; $('pair-found').hidden = false; }
      }
      await new Promise((r) => setTimeout(r, 1500));
    }
  })();
}
$('pair').addEventListener('close', () => { portSearch++; });
$('pair-open').addEventListener('click', async () => { if (!(await api.openDevSettings())) toast('This headset blocked the shortcut to Settings.'); });
$('pair-go').addEventListener('click', async () => {
  const btn = $('pair-go'), msg = $('pair-msg');
  btn.disabled = true; btn.textContent = 'Pairing…'; msg.hidden = true;
  try {
    await api.pair(Number($('pair-port').value), $('pair-code').value, Number($('pair-connect').value) || 0);
    $('pair').close(); toast('Paired. GorillaRinth will reconnect by itself from now on.'); await refresh(true);
  } catch (err) { msg.textContent = clean(err); msg.hidden = false; }
  btn.disabled = false; btn.textContent = 'Pair';
});

// ---- Actions ---------------------------------------------------------------------
async function runApply() {
  applyProg = { step: 0, fraction: 0 }; S.busy = 'apply'; update();
  try {
    const r = await api.apply();
    toast(r.mods ? `${plural(r.mods, 'mod')} applied${r.removed.length ? ` (removed ${r.removed.join(', ')} to make room)` : ''}. Press Play.` : 'Mods removed from the game.');
  } catch (err) { fail(err); }
  applyProg = null; task = null; await refresh(true);
}
async function upload() {
  try {
    const r = await api.installLocal();
    if (r) { toast(`Added ${r.name}. Switch it on to use it.`); if (route.name !== 'game' && route.name !== 'setup') go({ name: 'game' }); }
    await refresh(true);
  } catch (err) { fail(err); }
}

document.addEventListener('click', async (e) => {
  if (e.target.tagName === 'DIALOG') { if (e.target.id === 'settings') closeSettings(); else if (e.target.id === 'confirm') answerConfirm(false); else e.target.close(); return; }
  const el = e.target.closest('button,[data-detail]');
  if (!el) return;
  const d = el.dataset;
  try {
    if (el.id === 'nav-back') hop(backStack, fwdStack);
    else if (el.id === 'nav-fwd') hop(fwdStack, backStack);
    else if (el.id === 'confirm-yes') answerConfirm(true);
    else if (el.id === 'confirm-no') answerConfirm(false);
    else if (el.id === 'go') await runApply();
    else if (el.id === 'cdn-save') await saveStore();
    else if (el.id === 'cdn-default') { $('cdn-url').value = (await api.storeConfig()).fallback; await saveStore(); }
    else if (el.id === 'restore-base') {
      closeSettings(); applyProg = { step: 2, fraction: 0 }; S.busy = 'apply'; update();
      try { await api.restoreBase(); toast('Original restored. No mods are in the game now.'); } catch (err) { fail(err); }
      applyProg = null; task = null; await refresh(true);
    }
    else if (d.go) { go({ name: d.go }); }
    else if (d.sstep !== undefined) { setupStep = Number(d.sstep); update(); }
    else if (d.snext) { setupStep++; update(); $('page').scrollTop = 0; }
    else if (d.sback) { setupStep--; update(); $('page').scrollTop = 0; }
    else if (d.devset) { if (!(await api.openDevSettings())) toast('This headset blocked the shortcut to Settings.'); }
    else if (d.gtab) { route = { name: 'game', tab: d.gtab }; mount(); }
    else if (d.detail) openDetail(d.src, d.detail);
    else if (d.close) { if (d.close === 'settings') closeSettings(); else $(d.close).close(); }
    else if (d.settings) { settingsTab = d.settings; renderSettings(); if (!$('settings').open) { $('settings').showModal(); aura.start(); } }
    else if (d.stab) { settingsTab = d.stab; renderSettings(); }
    else if (d.setmode) { window.appearance.setMode(d.setmode); renderSettings(); }
    else if (d.settheme) { window.appearance.setTheme(d.settheme); aura.paint(); renderSettings(); }
    else if (d.upload) await upload();
    else if (d.pair) openPair();
    else if (d.reconnect) { el.disabled = true; if (!(await api.reconnect())) toast('Still not connected. Check that Wireless debugging is on.'); await refresh(true); }
    else if (d.autore) { await api.setAutoReconnect(el.getAttribute('aria-checked') !== 'true'); await refresh(true); }
    else if (d.launch) { await api.launch(); await refresh(true); }
    else if (d.stop) { await api.stopGame(); await refresh(true); }
    else if (d.pull) { task = { fraction: 0 }; S.busy = 'base'; update(); try { await api.pullBase(); } catch (err) { fail(err); } task = null; await refresh(true); }
    else if (d.choose) { await api.chooseBase(); await refresh(true); }
    else if (d.toggle) { await api.toggle(d.toggle); await refresh(true); }
    else if (d.select) { if (lib.selected.has(d.select)) lib.selected.delete(d.select); else lib.selected.add(d.select); update(); }
    else if (d.selectall) { const l = libFiltered(), all = l.every((m) => lib.selected.has(m.id)); l.forEach((m) => (all ? lib.selected.delete(m.id) : lib.selected.add(m.id))); update(); }
    else if (d.filter) { lib.filter = d.filter; update(); }
    else if (d.bulk) {
      const ids = [...lib.selected];
      if (d.bulk === 'rm') {
        if (!(await askRemove(S.library.filter((m) => lib.selected.has(m.id))))) return;
        for (const id of ids) await api.remove(id);
        lib.selected.clear(); await refreshStoreFlags();
      } else await api.setMany(ids, d.bulk === 'on');
      await refresh(true);
    }
    else if (d.remove) {
      const m = S.library.find((x) => x.id === d.remove);
      if (!m || !(await askRemove([m]))) return;
      await api.remove(d.remove); lib.selected.delete(d.remove); await refreshStoreFlags(); await refresh(true);
    }
    else if (d.refresh) { store.items = null; await loadStore(true); }
    else if (d.hide) { store.hideInstalled = !store.hideInstalled; update(); }
    else if (d.tag !== undefined) { store.tag = d.tag; update(); }
    else if (d.install) {
      const id = d.install, wasUpdate = !!(storeItem(id) && storeItem(id).update);
      installing[id] = { phase: 'download', fraction: 0 }; update();
      try { await api.storeInstall(id); toast(wasUpdate ? 'Updated. Apply to use the new version.' : 'Installed. Switch it on in your library.'); }
      catch (err) { fail(err); } finally { delete installing[id]; await refreshStoreFlags(); await refresh(true); }
    }
    else if (d.action !== undefined) {
      const a = S.checks[Number(d.action)].action;
      if (a.type === 'pair') openPair();
      else if (a.type === 'reconnect') { await api.reconnect(); await refresh(true); }
      else if (a.type === 'base-pull') { task = { fraction: 0 }; try { await api.pullBase(); } catch (err) { fail(err); } task = null; await refresh(true); }
      else if (a.type === 'base-choose') { await api.chooseBase(); await refresh(true); }
      else if (a.type === 'disable') { await api.toggle(a.id); await refresh(true); }
    }
  } catch (err) { fail(err); }
});
async function saveStore() {
  const btn = $('cdn-save'), msg = $('cdn-msg');
  btn.disabled = true; msg.hidden = true;
  try {
    await api.storeSetUrl($('cdn-url').value);
    store.items = null; store.error = '';
    await loadStore(true);
    if (store.error) { msg.textContent = store.error; msg.hidden = false; }
    else { closeSettings(); go({ name: 'discover' }); }
  } catch (err) { msg.textContent = clean(err); msg.hidden = false; }
  btn.disabled = false;
}

document.addEventListener('input', (e) => {
  if (e.target.id === 'lib-q') { lib.q = e.target.value; update(); }
  else if (e.target.id === 'store-q') { store.q = e.target.value; update(); }
  else if (e.target.id === 'rgb-speed') { window.appearance.setRgbSpeed(Number(e.target.value)); syncSpeedSlider(); }
});
document.addEventListener('change', (e) => {
  if (e.target.id === 'lib-sort') { lib.sort = e.target.value; update(); }
  else if (e.target.id === 'store-sort') { store.sort = e.target.value; update(); }
});

api.onProgress((p) => {
  if (p.task === 'notice') { toast(p.text); refresh(true); }
  else if (p.task === 'install') { if (installing[p.gbId]) { installing[p.gbId] = { phase: p.phase, fraction: p.fraction }; update(); } }
  else if (p.task === 'apply') { applyProg = { step: p.step, fraction: p.fraction }; update(); }
  else { task = p; update(); }
});

// ---- Start ------------------------------------------------------------------------
$('nav-back').innerHTML = ic('left'); $('nav-fwd').innerHTML = ic('right');
$('pair-x').innerHTML = ic('x'); $('set-x').innerHTML = ic('x');
document.querySelector('[data-go="home"]').innerHTML = ic('play');
document.querySelector('[data-go="discover"]').innerHTML = ic('compass');
document.querySelector('[data-go="setup"]').innerHTML = ic('checks');
$('rail-add').innerHTML = ic('plus'); $('rail-settings').innerHTML = ic('gear');
// The bar fills as far as it honestly can until the backend answers, then finishes and fades out.
const splashFrom = Date.now();
const splashFill = $('splash-fill');
setTimeout(() => splashFill.classList.add('go'), 30);
refresh(true).then(async () => {
  if (S && S.device.state === 'none' && !S.device.paired) route = { name: 'setup' };
  mount(); document.body.classList.add('ready'); loadStore(false);
  await new Promise((r) => setTimeout(r, Math.max(0, 800 - (Date.now() - splashFrom))));
  splashFill.classList.add('full');
  await new Promise((r) => setTimeout(r, 240));
  $('splash').classList.add('out');
  setTimeout(() => $('splash').remove(), 320);
});
document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(true); });
setInterval(() => refresh(false), 3000);
