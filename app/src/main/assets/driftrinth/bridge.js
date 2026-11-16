'use strict';
// window.driftrinth, backed by the Kotlin side.
(() => {
  let next = 1;
  const waiting = new Map();
  const listeners = new Set();
  const call = (method, ...args) => new Promise((resolve, reject) => {
    const id = next++;
    waiting.set(id, { resolve, reject });
    window.DriftrinthNative.call(id, method, JSON.stringify(args));
  });
  window.__drDone = (id, ok, value) => {
    const w = waiting.get(id);
    if (!w) return;
    waiting.delete(id);
    if (ok) w.resolve(value); else w.reject(new Error(value));
  };
  window.__drProgress = (p) => listeners.forEach((cb) => cb(p));
  window.driftrinth = {
    state: () => call('state'),
    pair: (port, code, connectPort) => call('pair', port, code, connectPort),
    pairingPort: () => call('pairingPort'),
    reconnect: () => call('reconnect'),
    setAutoReconnect: (on) => call('setAutoReconnect', on),
    openDevSettings: () => call('openDevSettings'),
    launch: () => call('launch'),
    stopGame: () => call('stopGame'),
    pullBase: () => call('pullBase'),
    chooseBase: () => call('chooseBase'),
    restoreBase: () => call('restoreBase'),
    toggle: (id) => call('toggle', id),
    setMany: (ids, on) => call('setMany', ids, on),
    remove: (id) => call('remove', id),
    storeConfig: () => call('storeConfig'),
    storeSetUrl: (url) => call('storeSetUrl', url),
    storeList: (opts) => call('storeList', opts),
    storeInstall: (modId) => call('storeInstall', modId),
    installLocal: () => call('installLocal'),
    apply: () => call('apply'),
    onProgress: (cb) => { listeners.add(cb); return () => listeners.delete(cb); },
  };
})();
