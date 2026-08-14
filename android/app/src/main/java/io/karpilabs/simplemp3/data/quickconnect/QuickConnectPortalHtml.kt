package io.karpilabs.simplemp3.data.quickconnect

/**
 * Single-page portal served by [QuickConnectServer].
 * Dark theme to match Simple MP3; works offline on the LAN with no CDN.
 */
object QuickConnectPortalHtml {
    val PAGE: String =
        """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>Simple MP3 · Quick Connect</title>
<style>
  :root {
    --bg: #0a0a0c;
    --card: #1a1a22;
    --elevated: #242430;
    --teal: #00e5c0;
    --teal-dim: #00b89a;
    --text: #f5f5f7;
    --muted: #6e6e7a;
    --secondary: #b0b0bc;
    --coral: #ff6b6b;
    --violet: #a78bfa;
    --radius: 14px;
    --font: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
  }
  * { box-sizing: border-box; }
  body {
    margin: 0; min-height: 100vh;
    font-family: var(--font);
    background: radial-gradient(1200px 600px at 10% -10%, #0d1b1a 0%, var(--bg) 55%),
                radial-gradient(900px 500px at 100% 0%, #1a0a14 0%, transparent 50%),
                var(--bg);
    color: var(--text);
  }
  a { color: var(--teal); }
  .wrap { max-width: 980px; margin: 0 auto; padding: 28px 18px 80px; }
  h1 { font-size: 1.6rem; margin: 0 0 4px; letter-spacing: -0.02em; }
  .sub { color: var(--secondary); margin: 0 0 24px; font-size: 0.95rem; }
  .badge {
    display: inline-flex; align-items: center; gap: 8px;
    background: rgba(0,229,192,0.12); color: var(--teal);
    padding: 6px 12px; border-radius: 999px; font-size: 0.8rem; font-weight: 600;
  }
  .card {
    background: var(--card); border-radius: var(--radius);
    padding: 20px; margin-bottom: 16px;
    border: 1px solid rgba(255,255,255,0.04);
  }
  .hidden { display: none !important; }
  label { display: block; color: var(--secondary); font-size: 0.85rem; margin-bottom: 8px; }
  input[type=text], input[type=password], select {
    width: 100%; padding: 12px 14px; border-radius: 12px;
    border: 1px solid rgba(255,255,255,0.08); background: var(--elevated);
    color: var(--text); font-size: 1.05rem; outline: none;
  }
  input:focus, select:focus { border-color: var(--teal); }
  .code-input {
    letter-spacing: 0.35em; text-align: center; font-size: 1.5rem; font-weight: 700;
  }
  button, .btn {
    appearance: none; border: 0; cursor: pointer;
    background: var(--teal); color: #0a0a0c;
    font-weight: 700; padding: 12px 18px; border-radius: 12px;
    font-size: 0.95rem;
  }
  button.secondary, .btn.secondary {
    background: var(--elevated); color: var(--teal);
  }
  button.danger { background: rgba(255,107,107,0.18); color: var(--coral); }
  button:disabled { opacity: 0.5; cursor: not-allowed; }
  .row { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }
  .grow { flex: 1; min-width: 140px; }
  .err { color: var(--coral); font-size: 0.9rem; margin-top: 10px; white-space: pre-wrap; }
  .ok { color: var(--teal); font-size: 0.9rem; margin-top: 10px; }
  .drop {
    border: 2px dashed rgba(0,229,192,0.35); border-radius: var(--radius);
    padding: 36px 20px; text-align: center; background: rgba(0,229,192,0.04);
    transition: border-color .15s, background .15s;
  }
  .drop.drag { border-color: var(--teal); background: rgba(0,229,192,0.1); }
  .drop h2 { margin: 0 0 8px; font-size: 1.15rem; }
  .drop p { margin: 0; color: var(--secondary); font-size: 0.9rem; }
  .tabs { display: flex; gap: 8px; margin-bottom: 14px; flex-wrap: wrap; }
  .tab {
    background: transparent; color: var(--secondary); border: 1px solid rgba(255,255,255,0.08);
    padding: 8px 14px; border-radius: 999px; font-weight: 600;
  }
  .tab.active { background: rgba(0,229,192,0.15); color: var(--teal); border-color: transparent; }
  table { width: 100%; border-collapse: collapse; font-size: 0.92rem; }
  th, td { text-align: left; padding: 10px 8px; border-bottom: 1px solid rgba(255,255,255,0.06); }
  th { color: var(--muted); font-weight: 600; font-size: 0.78rem; text-transform: uppercase; letter-spacing: .04em; }
  tr:hover td { background: rgba(255,255,255,0.02); }
  .muted { color: var(--muted); }
  .pill {
    display: inline-block; padding: 2px 8px; border-radius: 999px;
    background: var(--elevated); color: var(--secondary); font-size: 0.75rem;
  }
  .pill.lan { background: rgba(0,229,192,0.15); color: var(--teal); }
  .pill.youtube { background: rgba(255,107,107,0.15); color: var(--coral); }
  .pill.jellyfin { background: rgba(167,139,250,0.15); color: var(--violet); }
  .toolbar { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; align-items: center; }
  .progress {
    height: 6px; background: var(--elevated); border-radius: 999px; overflow: hidden; margin-top: 12px;
  }
  .progress > i { display: block; height: 100%; width: 0; background: var(--teal); transition: width .2s; }
  #login-card { max-width: 420px; margin: 12vh auto 0; }
  .stat { color: var(--secondary); font-size: 0.88rem; }
  .actions button { padding: 8px 12px; font-size: 0.8rem; }
  .empty { color: var(--muted); padding: 24px; text-align: center; }
</style>
</head>
<body>
<div class="wrap">
  <div class="badge">Simple MP3 · Quick Connect</div>
  <h1 style="margin-top:12px">LAN library portal</h1>
  <p class="sub">Same Wi‑Fi as your phone. Upload MP3s and manage playlists. Portal stops when you leave the phone screen.</p>

  <div id="login-card" class="card">
    <label for="code">Access code (shown on your phone)</label>
    <input id="code" class="code-input" type="text" inputmode="numeric" maxlength="6" placeholder="000000" autocomplete="one-time-code"/>
    <div class="row" style="margin-top:14px">
      <button id="unlock-btn" class="grow">Unlock</button>
    </div>
    <div id="login-err" class="err hidden"></div>
  </div>

  <div id="app" class="hidden">
    <div class="toolbar">
      <div class="stat" id="status-line">Loading…</div>
      <button class="secondary" id="refresh-btn">Refresh</button>
    </div>

    <div class="card">
      <div class="drop" id="drop">
        <h2>Drop audio files here</h2>
        <p>MP3, M4A, AAC, FLAC, OGG, Opus, WAV · or click to browse</p>
        <input id="file-input" type="file" accept="audio/*,.mp3,.m4a,.aac,.flac,.ogg,.opus,.wav" multiple hidden/>
        <div class="row" style="justify-content:center;margin-top:16px">
          <button type="button" id="pick-btn" class="secondary">Choose files</button>
          <select id="upload-playlist" style="max-width:240px">
            <option value="">Library only (+ LAN Imports)</option>
          </select>
        </div>
        <div class="progress hidden" id="up-progress"><i id="up-bar"></i></div>
        <div id="up-msg" class="ok hidden"></div>
        <div id="up-err" class="err hidden"></div>
      </div>
    </div>

    <div class="tabs">
      <button class="tab active" data-tab="tracks">Library</button>
      <button class="tab" data-tab="playlists">Playlists</button>
    </div>

    <div id="tab-tracks" class="card">
      <div class="toolbar">
        <strong>All tracks</strong>
        <input id="track-filter" type="text" placeholder="Filter…" style="max-width:220px"/>
      </div>
      <div id="tracks-table"></div>
    </div>

    <div id="tab-playlists" class="card hidden">
      <div class="toolbar">
        <strong>Playlists</strong>
        <div class="row">
          <input id="new-pl-name" type="text" placeholder="New playlist name" style="max-width:220px"/>
          <button id="create-pl-btn">Create</button>
        </div>
      </div>
      <div id="playlists-list"></div>
      <div id="playlist-detail" class="hidden" style="margin-top:18px"></div>
    </div>
  </div>
</div>
<script>
(function () {
  const ${'$'} = (id) => document.getElementById(id);
  let authorized = false;
  let tracks = [];
  let playlists = [];
  let openPlaylistId = null;

  function show(el, on) { el.classList.toggle('hidden', !on); }
  function setErr(el, msg) {
    if (!msg) { show(el, false); el.textContent = ''; return; }
    el.textContent = msg; show(el, true);
  }

  async function api(path, opts = {}) {
    const res = await fetch(path, {
      credentials: 'same-origin',
      ...opts,
      headers: {
        ...(opts.body && !(opts.body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}),
        ...(opts.headers || {})
      }
    });
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (_) { data = { error: text }; }
    if (!res.ok) {
      const err = new Error((data && data.error) || res.statusText || 'Request failed');
      err.status = res.status;
      throw err;
    }
    return data;
  }

  async function unlock() {
    setErr(${'$'}('login-err'), '');
    const code = ${'$'}('code').value.trim();
    if (!code) { setErr(${'$'}('login-err'), 'Enter the 6-digit code from your phone'); return; }
    ${'$'}('unlock-btn').disabled = true;
    try {
      await api('/api/auth', { method: 'POST', body: JSON.stringify({ code }) });
      authorized = true;
      show(${'$'}('login-card'), false);
      show(${'$'}('app'), true);
      await refreshAll();
    } catch (e) {
      setErr(${'$'}('login-err'), e.message || 'Invalid code');
    } finally {
      ${'$'}('unlock-btn').disabled = false;
    }
  }

  async function refreshAll() {
    const status = await api('/api/status');
    ${'$'}('status-line').textContent =
      status.trackCount + ' tracks · ' + status.playlistCount + ' playlists · portal active';
    const t = await api('/api/tracks');
    tracks = t.tracks || [];
    const p = await api('/api/playlists');
    playlists = p.playlists || [];
    renderTracks();
    renderPlaylists();
    fillUploadPlaylistSelect();
    if (openPlaylistId != null) await openPlaylist(openPlaylistId);
  }

  function sourcePill(src) {
    const cls = src === 'lan' ? 'lan' : src === 'youtube' ? 'youtube' : src === 'jellyfin' ? 'jellyfin' : '';
    return '<span class="pill ' + cls + '">' + (src || 'local') + '</span>';
  }

  function fmtDur(ms) {
    if (!ms || ms < 0) return '—';
    const s = Math.floor(ms / 1000);
    const m = Math.floor(s / 60);
    const r = s % 60;
    return m + ':' + String(r).padStart(2, '0');
  }

  function renderTracks() {
    const q = (${'$'}('track-filter').value || '').toLowerCase();
    const list = tracks.filter(t => {
      if (!q) return true;
      return (t.title + ' ' + t.artist + ' ' + t.album).toLowerCase().includes(q);
    });
    if (!list.length) {
      ${'$'}('tracks-table').innerHTML = '<div class="empty">No tracks match</div>';
      return;
    }
    let html = '<table><thead><tr><th>Title</th><th>Artist</th><th>Album</th><th>Len</th><th>Src</th><th></th></tr></thead><tbody>';
    for (const t of list) {
      html += '<tr>' +
        '<td>' + esc(t.title) + '</td>' +
        '<td class="muted">' + esc(t.artist) + '</td>' +
        '<td class="muted">' + esc(t.album) + '</td>' +
        '<td class="muted">' + fmtDur(t.duration) + '</td>' +
        '<td>' + sourcePill(t.source) + '</td>' +
        '<td class="actions">' +
          (t.canDelete
            ? '<button class="danger" data-del-track="' + t.id + '">Delete</button>'
            : '') +
        '</td></tr>';
    }
    html += '</tbody></table>';
    ${'$'}('tracks-table').innerHTML = html;
    ${'$'}('tracks-table').querySelectorAll('[data-del-track]').forEach(btn => {
      btn.addEventListener('click', async () => {
        if (!confirm('Delete this LAN upload from the phone?')) return;
        try {
          await api('/api/tracks/' + btn.getAttribute('data-del-track'), { method: 'DELETE' });
          await refreshAll();
        } catch (e) { alert(e.message); }
      });
    });
  }

  function fillUploadPlaylistSelect() {
    const sel = ${'$'}('upload-playlist');
    const cur = sel.value;
    sel.innerHTML = '<option value="">Library only (+ LAN Imports)</option>';
    for (const p of playlists) {
      if (p.isSystem && p.systemType === 'lan_imports') continue;
      const opt = document.createElement('option');
      opt.value = p.id;
      opt.textContent = p.name + ' (' + p.trackCount + ')';
      sel.appendChild(opt);
    }
    if (cur) sel.value = cur;
  }

  function renderPlaylists() {
    if (!playlists.length) {
      ${'$'}('playlists-list').innerHTML = '<div class="empty">No playlists yet</div>';
      return;
    }
    let html = '<table><thead><tr><th>Name</th><th>Tracks</th><th></th></tr></thead><tbody>';
    for (const p of playlists) {
      html += '<tr>' +
        '<td><a href="#" data-open-pl="' + p.id + '">' + esc(p.name) + '</a>' +
          (p.isSystem ? ' <span class="pill">system</span>' : '') + '</td>' +
        '<td class="muted">' + p.trackCount + '</td>' +
        '<td class="actions">' +
          (!p.isSystem
            ? '<button class="danger" data-del-pl="' + p.id + '">Delete</button>'
            : '') +
        '</td></tr>';
    }
    html += '</tbody></table>';
    ${'$'}('playlists-list').innerHTML = html;
    ${'$'}('playlists-list').querySelectorAll('[data-open-pl]').forEach(a => {
      a.addEventListener('click', (ev) => {
        ev.preventDefault();
        openPlaylist(Number(a.getAttribute('data-open-pl')));
      });
    });
    ${'$'}('playlists-list').querySelectorAll('[data-del-pl]').forEach(btn => {
      btn.addEventListener('click', async () => {
        if (!confirm('Delete this playlist?')) return;
        try {
          await api('/api/playlists/' + btn.getAttribute('data-del-pl'), { method: 'DELETE' });
          openPlaylistId = null;
          show(${'$'}('playlist-detail'), false);
          await refreshAll();
        } catch (e) { alert(e.message); }
      });
    });
  }

  async function openPlaylist(id) {
    openPlaylistId = id;
    const pl = playlists.find(p => p.id === id);
    const data = await api('/api/playlists/' + id + '/tracks');
    const pts = data.tracks || [];
    let html = '<div class="toolbar"><strong>' + esc(pl ? pl.name : 'Playlist') + '</strong>' +
      '<button class="secondary" id="close-pl">Close</button></div>';
    if (!pts.length) {
      html += '<div class="empty">Empty playlist — upload with this playlist selected, or add tracks below.</div>';
    } else {
      html += '<table><thead><tr><th>Title</th><th>Artist</th><th></th></tr></thead><tbody>';
      for (const t of pts) {
        html += '<tr><td>' + esc(t.title) + '</td><td class="muted">' + esc(t.artist) + '</td>' +
          '<td class="actions"><button class="danger" data-rm="' + t.id + '">Remove</button></td></tr>';
      }
      html += '</tbody></table>';
    }
    html += '<div class="row" style="margin-top:14px">' +
      '<select id="add-track-sel" class="grow"><option value="">Add existing track…</option></select>' +
      '<button id="add-track-btn" class="secondary">Add</button></div>';
    const detail = ${'$'}('playlist-detail');
    detail.innerHTML = html;
    show(detail, true);
    const sel = ${'$'}('add-track-sel');
    const inPl = new Set(pts.map(t => t.id));
    for (const t of tracks) {
      if (inPl.has(t.id)) continue;
      const opt = document.createElement('option');
      opt.value = t.id;
      opt.textContent = t.title + ' — ' + t.artist;
      sel.appendChild(opt);
    }
    ${'$'}('close-pl').onclick = () => { openPlaylistId = null; show(detail, false); };
    ${'$'}('add-track-btn').onclick = async () => {
      const tid = Number(sel.value);
      if (!tid) return;
      try {
        await api('/api/playlists/' + id + '/tracks', {
          method: 'POST', body: JSON.stringify({ trackId: tid })
        });
        await openPlaylist(id);
        await refreshAll();
      } catch (e) { alert(e.message); }
    };
    detail.querySelectorAll('[data-rm]').forEach(btn => {
      btn.addEventListener('click', async () => {
        try {
          await api('/api/playlists/' + id + '/tracks/' + btn.getAttribute('data-rm'), { method: 'DELETE' });
          await openPlaylist(id);
          await refreshAll();
        } catch (e) { alert(e.message); }
      });
    });
  }

  function esc(s) {
    return String(s ?? '').replace(/[&<>"']/g, c => ({
      '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
    })[c]);
  }

  async function uploadFiles(fileList) {
    const files = Array.from(fileList || []);
    if (!files.length) return;
    setErr(${'$'}('up-err'), '');
    setErr(${'$'}('up-msg'), '');
    const bar = ${'$'}('up-bar');
    const prog = ${'$'}('up-progress');
    show(prog, true);
    bar.style.width = '5%';
    let done = 0;
    const errors = [];
    for (const file of files) {
      const fd = new FormData();
      fd.append('file', file, file.name);
      const pl = ${'$'}('upload-playlist').value;
      if (pl) fd.append('playlistId', pl);
      try {
        const res = await fetch('/api/upload', { method: 'POST', body: fd, credentials: 'same-origin' });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Upload failed');
        if (data.errors && data.errors.length) errors.push(...data.errors);
      } catch (e) {
        errors.push(file.name + ': ' + (e.message || 'failed'));
      }
      done++;
      bar.style.width = Math.round((done / files.length) * 100) + '%';
    }
    await refreshAll();
    if (errors.length) setErr(${'$'}('up-err'), errors.join('\n'));
    else {
      ${'$'}('up-msg').textContent = 'Uploaded ' + files.length + ' file' + (files.length === 1 ? '' : 's');
      show(${'$'}('up-msg'), true);
    }
    setTimeout(() => show(prog, false), 800);
  }

  // Tabs
  document.querySelectorAll('.tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      const name = tab.getAttribute('data-tab');
      show(${'$'}('tab-tracks'), name === 'tracks');
      show(${'$'}('tab-playlists'), name === 'playlists');
    });
  });

  ${'$'}('unlock-btn').onclick = unlock;
  ${'$'}('code').addEventListener('keydown', e => { if (e.key === 'Enter') unlock(); });
  ${'$'}('refresh-btn').onclick = () => refreshAll().catch(e => alert(e.message));

  // QR / deep link: http://phone:port/?code=123456 → unlock automatically
  (function tryAutoUnlock() {
    try {
      const params = new URLSearchParams(window.location.search || '');
      const fromQuery = (params.get('code') || '').trim();
      if (fromQuery) {
        ${'$'}('code').value = fromQuery;
        unlock();
        // Clean code out of the address bar without reloading
        if (window.history && window.history.replaceState) {
          window.history.replaceState({}, '', window.location.pathname || '/');
        }
      }
    } catch (_) { /* ignore */ }
  })();
  ${'$'}('track-filter').oninput = renderTracks;
  ${'$'}('create-pl-btn').onclick = async () => {
    const name = ${'$'}('new-pl-name').value.trim();
    if (!name) return;
    try {
      await api('/api/playlists', { method: 'POST', body: JSON.stringify({ name }) });
      ${'$'}('new-pl-name').value = '';
      await refreshAll();
    } catch (e) { alert(e.message); }
  };

  const drop = ${'$'}('drop');
  const fileInput = ${'$'}('file-input');
  ${'$'}('pick-btn').onclick = () => fileInput.click();
  drop.addEventListener('click', (e) => {
    if (e.target === drop || e.target.tagName === 'H2' || e.target.tagName === 'P') fileInput.click();
  });
  fileInput.onchange = () => uploadFiles(fileInput.files);
  ;['dragenter','dragover'].forEach(ev => drop.addEventListener(ev, e => {
    e.preventDefault(); drop.classList.add('drag');
  }));
  ;['dragleave','drop'].forEach(ev => drop.addEventListener(ev, e => {
    e.preventDefault(); drop.classList.remove('drag');
  }));
  drop.addEventListener('drop', e => {
    uploadFiles(e.dataTransfer.files);
  });
})();
</script>
</body>
</html>
        """.trimIndent()
}
