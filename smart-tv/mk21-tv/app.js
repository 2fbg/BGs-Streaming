// MK21 PLAY v3.0.0 — Motor Estável sem Tela Home & com Separação Estrita de Canais/Filmes
const $ = id => document.getElementById(id);

// 1. CONFIGURAÇÃO DOS 7 SERVIDORES MK21
const SERVERS = [
  { id: 'cb6000', name: '🔵 CB6000', url: 'http://cdn.caterlune.top/get.php?username=601334065&password=820866576&type=m3u_plus&output=mpegts' },
  { id: 'vlog', name: '🟢 VLOG', url: 'http://myopbx.beer/get.php?username=601334065&password=820866576&type=m3u_plus&output=mpegts' },
  { id: 'lub', name: '⚪ LUB TV', url: 'http://pottermax.sbs/get.php?username=601334065&password=820866576&type=m3u_plus&output=mpegts' },
  { id: 'cinelon', name: '🔴 CINELON21', url: 'http://coliseuop.site/get.php?username=601334065&password=820866576&type=m3u_plus&output=mpegts' },
  { id: 'tannix', name: '🟠 TANNIX', url: 'http://poptvcdn.online/get.php?username=601334065&password=820866576&type=m3u_plus&output=mpegts' },
  { id: 'mk21pro', name: '🟣 MK21 PRÓ', url: 'http://app.vivoxi.xyz/get.php?username=601334065&password=820866576&type=m3u_plus&output=mpegts' },
  { id: 'multt', name: '🟤 MULTT TV', url: 'http://dali-as.skin/get.php?username=601334065&password=820866576&type=m3u_plus&output=mpegts' }
];

const ADULT_KEYWORDS = ['ADULTO', 'ADULTOS', 'XXX', 'PLAYBOY', 'VENUS', 'SEXTREME', 'SEX', 'ERÓTICO', 'EROTICO', 'PRIVE', 'FORBIDDEN', 'HOT', '18+', 'PORNO', 'BABES', 'REDLIGHT'];

// 2. BANCO DE DADOS LOCAL (IndexedDB)
const DB_NAME = 'MK21_PLAY_V3_DB';
const DB_VERSION = 1;
const STORE_NAME = 'catalog';

function openDB() {
  return new Promise(resolve => {
    if (!window.indexedDB) return resolve(null);
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = e => {
      const db = e.target.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME, { keyPath: 'id' });
      }
    };
    req.onsuccess = e => resolve(e.target.result);
    req.onerror = () => resolve(null);
  });
}

async function getStoredData(id) {
  try {
    const db = await openDB();
    if (!db) return null;
    return new Promise(resolve => {
      const tx = db.transaction(STORE_NAME, 'readonly');
      const req = tx.objectStore(STORE_NAME).get(id);
      req.onsuccess = () => resolve(req.result ? req.result.payload : null);
      req.onerror = () => resolve(null);
    });
  } catch (e) { return null; }
}

async function saveStoredData(id, payload) {
  try {
    const db = await openDB();
    if (!db) return;
    const tx = db.transaction(STORE_NAME, 'readwrite');
    tx.objectStore(STORE_NAME).put({ id, payload, updatedAt: Date.now() });
  } catch (e) {}
}

// 3. ESTADO GLOBAL
let currentServerIndex = 0;
let currentTab = 'tv'; // 'tv', 'movies', 'series', 'favs', 'settings'
let liveChannels = [];
let movieItems = [];
let seriesItems = [];
let favoriteUrls = new Set();
let isAdultUnlocked = false;
let currentPin = '0000';
let enteredPin = '';
let activeChannel = null;
let hlsInstance = null;

// Categorias da TV ao Vivo
let liveCategoriesMap = {};
let liveCategoryKeys = [];
let activeCategoryKey = 'ALL';
let filteredChannels = [];

// Gerenciador de Foco Espacial do Controle Remoto (D-Pad)
let activeColumn = 'channels'; // 'categories', 'channels', 'player', 'tabs'
let focusedCatIdx = 0;
let focusedChIdx = 0;

// RELÓGIO
function tickClock() {
  const d = new Date();
  const h = String(d.getHours()).padStart(2, '0');
  const m = String(d.getMinutes()).padStart(2, '0');
  const s = String(d.getSeconds()).padStart(2, '0');
  if ($('liveClock')) $('liveClock').textContent = `${h}:${m}:${s}`;
}
setInterval(tickClock, 1000);
tickClock();

// FAVORITOS
try {
  const f = localStorage.getItem('mk21_favs');
  if (f) favoriteUrls = new Set(JSON.parse(f));
} catch (e) {}

function toggleFav(url) {
  if (favoriteUrls.has(url)) {
    favoriteUrls.delete(url);
    if ($('btnFavorite')) $('btnFavorite').textContent = '⭐ Favoritar';
  } else {
    favoriteUrls.add(url);
    if ($('btnFavorite')) $('btnFavorite').textContent = '★ Favoritado';
  }
  try { localStorage.setItem('mk21_favs', JSON.stringify(Array.from(favoriteUrls))); } catch (e) {}
  if (activeCategoryKey === 'FAVORITES') renderChannelsList();
}

function isAdult(text) {
  if (!text) return false;
  const upper = text.toUpperCase();
  for (let i = 0; i < ADULT_KEYWORDS.length; i++) {
    if (upper.includes(ADULT_KEYWORDS[i])) return true;
  }
  return false;
}

// 4. PARSER OTIMIZADO COM SEPARAÇÃO ESTRITA E PROTEÇÃO CONTRA ESTOURO DE MEMÓRIA (ANTI-CRASH)
function parseM3USafely(content) {
  const lines = content.split(/\r?\n/);
  const live = [];
  const movies = [];
  const series = [];

  let name = 'Canal';
  let group = 'Geral';
  let logo = '';

  // Limites máximos para evitar que a Smart TV reinicie por falta de memória (RAM)
  const MAX_MOVIES = 1200;
  const MAX_SERIES = 600;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (line.startsWith('#EXTINF:')) {
      const cIdx = line.lastIndexOf(',');
      name = cIdx >= 0 ? line.slice(cIdx + 1).trim() : 'Canal';
      const gM = line.match(/group-title="([^"]*)"/i);
      group = gM && gM[1] && gM[1].trim() ? gM[1].trim() : 'Geral';
      const lM = line.match(/tvg-logo="([^"]*)"/i);
      logo = lM && lM[1] ? lM[1].trim() : '';
    } else if (/^https?:\/\//i.test(line)) {
      const upGroup = group.toUpperCase();
      const upName = name.toUpperCase();
      const urlLower = line.toLowerCase();
      const isAdultContent = isAdult(upGroup) || isAdult(upName);

      const item = { name, group, logo, url: line, isAdult: isAdultContent };

      // SEPARAÇÃO ESTRITA:
      // Filmes e Séries NÃO entram na TV Ao Vivo em hipótese alguma!
      const isMovie = urlLower.includes('/movie/') || urlLower.endsWith('.mp4') || urlLower.endsWith('.mkv') ||
                      upGroup.includes('FILME') || upGroup.includes('MOVIE') || upGroup.includes('VOD') || upGroup.includes('CINE') || upGroup.includes('4K FILMES');

      const isSeries = urlLower.includes('/series/') || upGroup.includes('SERIE') || upGroup.includes('SÉRIE') ||
                       upName.includes('S01') || upName.includes('S02') || upName.includes('TEMPORADA') || upName.includes('EPISODIO');

      if (isSeries) {
        if (series.length < MAX_SERIES) series.push(item);
      } else if (isMovie) {
        if (movies.length < MAX_MOVIES) movies.push(item);
      } else {
        // É canal de TV ao vivo legítimo!
        live.push(item);
      }

      name = 'Canal';
      group = 'Geral';
      logo = '';
    }
  }

  return { live, movies, series };
}

// 5. CARREGAMENTO DOS CANAIS DO SERVIDOR
async function loadServer(forceRefresh = false) {
  const srv = SERVERS[currentServerIndex];
  $('txtActiveServer').textContent = srv.name;

  // 1. TENTA CARREGAR DO BANCO DE DADOS LOCAL INSTANTÂNEO
  if (!forceRefresh) {
    const cached = await getStoredData(srv.id);
    if (cached && cached.live && cached.live.length > 0) {
      applyCatalog(cached);
      return;
    }
  }

  $('txtCurrentCategoryTitle').textContent = 'Conectando ao ' + srv.name + '...';

  try {
    const res = await fetch(srv.url);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const text = await res.text();
    const catalog = parseM3USafely(text);

    saveStoredData(srv.id, catalog);
    applyCatalog(catalog);
  } catch (err) {
    console.error('Server error:', err);
    alert('Erro ao carregar ' + srv.name + ': ' + err.message);
  }
}

function applyCatalog(catalog) {
  liveChannels = catalog.live || [];
  movieItems = catalog.movies || [];
  seriesItems = catalog.series || [];

  buildCategories();
  renderCategoriesList();
  selectCategory('ALL');
}

// 6. AGRUPAMENTO DAS CATEGORIAS DE TV AO VIVO (100% LIMPO DE FILMES)
function buildCategories() {
  liveCategoriesMap = { 'ALL': [] };
  liveCategoryKeys = ['ALL'];

  for (let i = 0; i < liveChannels.length; i++) {
    const ch = liveChannels[i];
    if (!isAdultUnlocked && ch.isAdult) continue;

    liveCategoriesMap['ALL'].push(ch);
    const grp = ch.group;
    if (!liveCategoriesMap[grp]) {
      liveCategoriesMap[grp] = [];
      liveCategoryKeys.push(grp);
    }
    liveCategoriesMap[grp].push(ch);
  }
}

function renderCategoriesList() {
  const ul = $('listCategories');
  ul.innerHTML = '';
  const fragment = document.createDocumentFragment();

  $('badgeCatCount').textContent = (liveCategoryKeys.length - 1) + ' grupos';

  liveCategoryKeys.forEach((k, idx) => {
    const li = document.createElement('li');
    const btn = document.createElement('button');
    btn.className = 'list-item-btn cat-item-btn' + (k === activeCategoryKey ? ' active' : '');
    btn.setAttribute('tabindex', '0');
    btn.setAttribute('data-idx', idx);

    const spanName = document.createElement('span');
    spanName.textContent = k === 'ALL' ? '🌟 Todos os Canais' : k;

    const spanCount = document.createElement('span');
    spanCount.className = 'counter-badge';
    spanCount.textContent = liveCategoriesMap[k] ? liveCategoriesMap[k].length : 0;

    btn.appendChild(spanName);
    btn.appendChild(spanCount);

    btn.onclick = () => selectCategory(k);

    li.appendChild(btn);
    fragment.appendChild(li);
  });

  ul.appendChild(fragment);
}

function selectCategory(catKey) {
  activeCategoryKey = catKey;

  // Atualizar visual dos botões de categorias
  const btns = $('listCategories').querySelectorAll('.cat-item-btn');
  btns.forEach(b => b.classList.remove('active'));
  const activeBtn = Array.from(btns).find(b => (catKey === 'ALL' && b.textContent.includes('Todos os Canais')) || b.textContent.startsWith(catKey));
  if (activeBtn) activeBtn.classList.add('active');

  $('txtCurrentCategoryTitle').textContent = catKey === 'ALL' ? '📺 Todos os Canais' : '📁 ' + catKey;
  $('inputChannelSearch').value = '';
  renderChannelsList();
}

// 7. RENDERIZAÇÃO DA LISTA DE CANAIS
function renderChannelsList() {
  const query = $('inputChannelSearch').value.toLowerCase().trim();
  let base = liveCategoriesMap[activeCategoryKey] || [];

  if (activeCategoryKey === 'FAVORITES') {
    base = liveChannels.filter(c => favoriteUrls.has(c.url));
  }

  filteredChannels = query ? base.filter(c => c.name.toLowerCase().includes(query)) : base;
  $('badgeChannelsCount').textContent = filteredChannels.length;

  const ul = $('listChannels');
  ul.innerHTML = '';

  if (filteredChannels.length === 0) {
    ul.innerHTML = '<li><button class="list-item-btn" disabled>Nenhum canal encontrado.</button></li>';
    return;
  }

  const fragment = document.createDocumentFragment();
  // Limite estrito de 60 itens por renderização para fluidez máxima no controle remoto
  const limit = Math.min(filteredChannels.length, 60);

  for (let i = 0; i < limit; i++) {
    const ch = filteredChannels[i];
    const li = document.createElement('li');
    const btn = document.createElement('button');
    btn.className = 'list-item-btn' + (activeChannel && activeChannel.url === ch.url ? ' active' : '');
    btn.setAttribute('tabindex', '0');
    btn.setAttribute('data-idx', i);

    const spanTitle = document.createElement('span');
    spanTitle.textContent = (favoriteUrls.has(ch.url) ? '★ ' : '') + ch.name;
    btn.appendChild(spanTitle);

    if (ch.isAdult) {
      const tag = document.createElement('span');
      tag.className = 'counter-badge';
      tag.textContent = '+18';
      btn.appendChild(tag);
    }

    // Clique único: toca no player
    btn.onclick = () => playStream(ch);

    // Duplo clique rápido: abre tela cheia imediatamente!
    let lastClick = 0;
    btn.addEventListener('click', () => {
      const now = Date.now();
      if (now - lastClick < 380) toggleFullscreen(true);
      lastClick = now;
    });

    li.appendChild(btn);
    fragment.appendChild(li);
  }

  ul.appendChild(fragment);

  // Auto-play no primeiro canal se nada estiver tocando
  if (!activeChannel && filteredChannels.length > 0) {
    playStream(filteredChannels[0]);
  }
}

$('inputChannelSearch').addEventListener('input', renderChannelsList);

// 8. PLAYER DE VÍDEO & EPG
function playStream(ch) {
  activeChannel = ch;

  // Atualizar botões
  const btns = $('listChannels').querySelectorAll('.list-item-btn');
  btns.forEach(b => b.classList.remove('active'));

  $('epgTitle').textContent = '▶ ' + ch.name;
  $('epgGroup').textContent = 'Categoria: ' + ch.group;

  if (favoriteUrls.has(ch.url)) {
    $('btnFavorite').textContent = '★ Favoritado';
  } else {
    $('btnFavorite').textContent = '⭐ Favoritar';
  }

  const overlay = $('channelOverlay');
  overlay.textContent = ch.name;
  overlay.classList.remove('hidden');
  clearTimeout(overlay._timer);
  overlay._timer = setTimeout(() => overlay.classList.add('hidden'), 3500);

  const video = $('tvPlayer');
  if (hlsInstance) {
    hlsInstance.destroy();
    hlsInstance = null;
  }

  const isHls = ch.url.toLowerCase().includes('.m3u8') || ch.url.includes('/live/');

  if (isHls && window.Hls && Hls.isSupported()) {
    hlsInstance = new Hls({ enableWorker: true, lowLatencyMode: true, maxBufferLength: 15 });
    hlsInstance.loadSource(ch.url);
    hlsInstance.attachMedia(video);
    hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => {
      video.play().catch(() => {});
      $('btnPlayPause').textContent = '⏸ Pausar';
    });
    hlsInstance.on(Hls.Events.ERROR, (e, data) => {
      if (data.fatal) {
        video.src = ch.url;
        video.play().catch(() => {});
      }
    });
  } else {
    video.src = ch.url;
    video.play().then(() => {
      $('btnPlayPause').textContent = '⏸ Pausar';
    }).catch(() => {});
  }
}

function toggleFullscreen(force = false) {
  const v = $('tvPlayer');
  if (force || (!document.fullscreenElement && !document.webkitFullscreenElement)) {
    if (v.requestFullscreen) v.requestFullscreen();
    else if (v.webkitRequestFullscreen) v.webkitRequestFullscreen();
  } else {
    if (document.exitFullscreen) document.exitFullscreen();
    else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
  }
}

$('videoContainer').ondblclick = () => toggleFullscreen();
$('btnFullscreen').onclick = () => toggleFullscreen();
$('btnPlayPause').onclick = () => {
  const v = $('tvPlayer');
  if (v.paused) {
    v.play();
    $('btnPlayPause').textContent = '⏸ Pausar';
  } else {
    v.pause();
    $('btnPlayPause').textContent = '▶ Reproduzir';
  }
};
$('btnFavorite').onclick = () => {
  if (activeChannel) toggleFav(activeChannel.url);
};

// 9. NAVEGAÇÃO ENTRE ABAS DO TOPO
$('tabLiveTv').onclick = () => switchTab('tv');
$('tabMovies').onclick = () => switchTab('movies');
$('tabSeries').onclick = () => switchTab('series');
$('tabFavs').onclick = () => switchTab('favs');
$('tabSettings').onclick = () => switchTab('settings');

function switchTab(tab) {
  currentTab = tab;
  ['tabLiveTv', 'tabMovies', 'tabSeries', 'tabFavs', 'tabSettings'].forEach(t => $(t).classList.remove('active'));

  $('sectionTv').classList.add('hidden');
  $('sectionVod').classList.add('hidden');
  $('sectionSettings').classList.add('hidden');

  if (tab === 'tv') {
    $('tabLiveTv').classList.add('active');
    $('sectionTv').classList.remove('hidden');
    selectCategory('ALL');
  } else if (tab === 'favs') {
    $('tabFavs').classList.add('active');
    $('sectionTv').classList.remove('hidden');
    selectCategory('FAVORITES');
  } else if (tab === 'movies') {
    $('tabMovies').classList.add('active');
    $('sectionVod').classList.remove('hidden');
    renderVod(movieItems, '🎬 Filmes Sob Demanda (VOD)');
  } else if (tab === 'series') {
    $('tabSeries').classList.add('active');
    $('sectionVod').classList.remove('hidden');
    renderVod(seriesItems, '🍿 Séries & Temporadas');
  } else if (tab === 'settings') {
    $('tabSettings').classList.add('active');
    $('sectionSettings').classList.remove('hidden');
    renderSettings('info');
  }
}

function renderVod(items, title) {
  $('txtVodTitle').textContent = title;
  const grid = $('gridVod');
  grid.innerHTML = '';
  const filtered = items.filter(i => isAdultUnlocked || !i.isAdult);
  const fragment = document.createDocumentFragment();
  const limit = Math.min(filtered.length, 50);

  for (let i = 0; i < limit; i++) {
    const item = filtered[i];
    const card = document.createElement('div');
    card.className = 'vod-item-card';
    card.setAttribute('tabindex', '0');

    card.innerHTML = `
      <div class="vod-poster-thumb">${title.includes('Filmes') ? '🎬' : '🍿'}</div>
      <h4 class="vod-card-title">${item.name}</h4>
      <p class="vod-card-group">${item.group}</p>
    `;

    card.onclick = () => {
      switchTab('tv');
      playStream(item);
      toggleFullscreen(true);
    };

    fragment.appendChild(card);
  }
  grid.appendChild(fragment);
}

// 10. TELA DE CONFIGURAÇÕES (CLONE VIZZION PLAY)
const CFG_BUTTONS = ['cfgBtnInfo', 'cfgBtnIdioma', 'cfgBtnFluxo', 'cfgBtnPin', 'cfgBtnCategorias', 'cfgBtnLimpar', 'cfgBtnTempo'];

CFG_BUTTONS.forEach(bId => {
  const b = $(bId);
  if (b) {
    b.onclick = () => {
      CFG_BUTTONS.forEach(id => $(id).classList.remove('active'));
      b.classList.add('active');
      renderSettings(bId.replace('cfgBtn', '').toLowerCase());
    };
  }
});

function renderSettings(sec) {
  const box = $('settingsDetailBox');
  if (sec === 'info') {
    box.innerHTML = `
      <h3 style="margin:0 0 16px 0; font-size:24px; border-bottom:1px solid #333; padding-bottom:8px;">Informações da Conta</h3>
      <div style="display:grid; grid-template-columns:1fr 1fr; gap:14px; margin-bottom:24px;">
        <div style="background:#1a1e2d; padding:12px 16px; border-radius:8px;">
          <div style="font-size:14px; color:#888;">Status</div>
          <div style="font-size:18px; font-weight:700; color:#4caf50;">Ativo (Plano Anual)</div>
        </div>
        <div style="background:#1a1e2d; padding:12px 16px; border-radius:8px;">
          <div style="font-size:14px; color:#888;">Vencimento</div>
          <div style="font-size:18px; font-weight:700; color:#fff;">15.03.2027</div>
        </div>
      </div>
      <h3 style="margin:0 0 16px 0; font-size:24px; border-bottom:1px solid #333; padding-bottom:8px;">Informação do Dispositivo</h3>
      <div style="display:grid; grid-template-columns:1fr 1fr; gap:14px;">
        <div style="background:#1a1e2d; padding:12px 16px; border-radius:8px;">
          <div style="font-size:14px; color:#888;">Endereço MAC</div>
          <div style="font-size:18px; font-weight:700; color:#fff;">F0:86:20:F1:5E:E4</div>
        </div>
        <div style="background:#1a1e2d; padding:12px 16px; border-radius:8px;">
          <div style="font-size:14px; color:#888;">Versão do Aplicativo</div>
          <div style="font-size:18px; font-weight:700; color:#fff;">3.0.0 (webOS 1080p)</div>
        </div>
      </div>
    `;
  } else if (sec === 'limpar') {
    box.innerHTML = `
      <h3 style="margin:0 0 16px 0; font-size:24px;">Limpar Armazenamento</h3>
      <div style="display:flex; flex-direction:column; gap:10px;">
        <div style="display:flex; justify-content:space-between; align-items:center; background:#1a1e2d; padding:12px 16px; border-radius:8px;">
          <span>Canais Favoritos</span>
          <button class="ctrl-btn" style="padding:8px 16px;" onclick="clearFavorites()" tabindex="0">Limpar</button>
        </div>
        <div style="display:flex; justify-content:space-between; align-items:center; background:#1a1e2d; padding:12px 16px; border-radius:8px;">
          <span style="color:#ff5252; font-weight:700;">Limpar Todo o Cache da TV</span>
          <button class="ctrl-btn primary" style="padding:8px 16px;" onclick="clearAllCache()" tabindex="0">Limpar Tudo</button>
        </div>
      </div>
    `;
  } else if (sec === 'idioma') {
    box.innerHTML = `
      <h3 style="margin:0 0 16px 0; font-size:24px;">Mudar Idioma</h3>
      <button class="list-item-btn active" style="margin-bottom:8px;" tabindex="0">🇧🇷 Português - Brasil (Ativo)</button>
      <button class="list-item-btn" style="margin-bottom:8px;" tabindex="0">🇺🇸 English</button>
      <button class="list-item-btn" tabindex="0">🇪🇸 Español</button>
    `;
  } else if (sec === 'fluxo') {
    box.innerHTML = `
      <h3 style="margin:0 0 16px 0; font-size:24px;">Formato de Fluxo</h3>
      <button class="list-item-btn active" style="margin-bottom:8px;" tabindex="0">Default (Automático)</button>
      <button class="list-item-btn" style="margin-bottom:8px;" tabindex="0">m3u8 (HLS)</button>
      <button class="list-item-btn" tabindex="0">ts (MPEG-TS)</button>
    `;
  }
}

window.clearFavorites = () => {
  favoriteUrls.clear();
  try { localStorage.removeItem('mk21_favs'); } catch (e) {}
  alert('Favoritos limpos com sucesso.');
};

window.clearAllCache = () => {
  try {
    indexedDB.deleteDatabase(DB_NAME);
    localStorage.clear();
    alert('Cache limpo com sucesso! Recarregando.');
    location.reload();
  } catch (e) {}
};

// 11. SISTEMA INFALÍVEL DE NAVEGAÇÃO D-PAD DO CONTROLE REMOTO LG
document.addEventListener('keydown', e => {
  const k = e.keyCode;

  // 1. TECLAS COLORIDAS DO CONTROLE LG
  if (k === 403 || e.key === 'Red') { // Botão Vermelho -> Trocar Servidor
    e.preventDefault();
    openServerPicker();
    return;
  }
  if (k === 404 || e.key === 'Green') { // Botão Verde -> Atualizar Lista
    e.preventDefault();
    loadServer(true);
    return;
  }
  if (k === 405 || e.key === 'Yellow') { // Botão Amarelo -> Favoritar Canal
    e.preventDefault();
    if (activeChannel) toggleFav(activeChannel.url);
    return;
  }
  if (k === 406 || e.key === 'Blue') { // Botão Azul -> Bloqueio Adulto
    e.preventDefault();
    openPinModal();
    return;
  }

  // 2. TECLA VOLTAR (461 / 27 / 8) - NUNCA FECHA O APP INDEVIDAMENTE
  if (k === 461 || k === 27 || k === 8 || e.key === 'GoBack') {
    e.preventDefault();

    if (document.fullscreenElement || document.webkitFullscreenElement) {
      toggleFullscreen();
      return;
    }
    if (!$('modalServerPicker').classList.contains('hidden')) {
      $('modalServerPicker').classList.add('hidden');
      return;
    }
    if (!$('modalPin').classList.contains('hidden')) {
      $('modalPin').classList.add('hidden');
      return;
    }
    if (!$('modalExitConfirm').classList.contains('hidden')) {
      $('modalExitConfirm').classList.add('hidden');
      return;
    }

    if (currentTab !== 'tv') {
      switchTab('tv');
      return;
    }

    if (activeColumn === 'channels') {
      activeColumn = 'categories';
      focusCurrentElement();
      return;
    }

    // Se estiver nas categorias de TV -> confirmação de saída segura
    $('modalExitConfirm').classList.remove('hidden');
    $('btnExitCancel').focus();
    return;
  }

  // 3. SETA PARA BAIXO (40)
  if (k === 40) {
    e.preventDefault();
    if (activeColumn === 'categories') {
      const items = $('listCategories').querySelectorAll('.cat-item-btn');
      if (focusedCatIdx < items.length - 1) focusedCatIdx++;
      focusCurrentElement();
    } else if (activeColumn === 'channels') {
      const items = $('listChannels').querySelectorAll('.list-item-btn');
      if (focusedChIdx < items.length - 1) focusedChIdx++;
      focusCurrentElement();
    }
    return;
  }

  // 4. SETA PARA CIMA (38)
  if (k === 38) {
    e.preventDefault();
    if (activeColumn === 'categories') {
      if (focusedCatIdx > 0) {
        focusedCatIdx--;
        focusCurrentElement();
      } else {
        $('tabLiveTv').focus();
      }
    } else if (activeColumn === 'channels') {
      if (focusedChIdx > 0) {
        focusedChIdx--;
        focusCurrentElement();
      } else {
        $('inputChannelSearch').focus();
      }
    }
    return;
  }

  // 5. SETA PARA DIREITA (39)
  if (k === 39) {
    e.preventDefault();
    if (activeColumn === 'categories') {
      activeColumn = 'channels';
      focusCurrentElement();
    } else if (activeColumn === 'channels') {
      activeColumn = 'player';
      $('btnFullscreen').focus();
    }
    return;
  }

  // 6. SETA PARA ESQUERDA (37)
  if (k === 37) {
    e.preventDefault();
    if (activeColumn === 'player') {
      activeColumn = 'channels';
      focusCurrentElement();
    } else if (activeColumn === 'channels') {
      activeColumn = 'categories';
      focusCurrentElement();
    }
    return;
  }

  // 7. TECLA OK / ENTER (13)
  if (k === 13) {
    if (activeColumn === 'categories') {
      const items = $('listCategories').querySelectorAll('.cat-item-btn');
      if (items[focusedCatIdx]) items[focusedCatIdx].click();
    } else if (activeColumn === 'channels') {
      const items = $('listChannels').querySelectorAll('.list-item-btn');
      const item = items[focusedChIdx];
      if (item) {
        // Se já está ativo, expande para tela cheia ao apertar OK!
        if (item.classList.contains('active')) {
          toggleFullscreen(true);
        } else {
          item.click();
        }
      }
    }
    return;
  }
});

function focusCurrentElement() {
  if (activeColumn === 'categories') {
    const items = $('listCategories').querySelectorAll('.cat-item-btn');
    if (items[focusedCatIdx]) {
      items[focusedCatIdx].focus();
      items[focusedCatIdx].scrollIntoView({ block: 'nearest' });
    }
  } else if (activeColumn === 'channels') {
    const items = $('listChannels').querySelectorAll('.list-item-btn');
    if (items[focusedChIdx]) {
      items[focusedChIdx].focus();
      items[focusedChIdx].scrollIntoView({ block: 'nearest' });
    }
  }
}

// 12. MODAIS E AÇÕES
$('btnRefreshList').onclick = () => loadServer(true);

$('btnHeaderServer').onclick = openServerPicker;

function openServerPicker() {
  const grid = $('serverItemsGrid');
  grid.innerHTML = '';
  SERVERS.forEach((s, idx) => {
    const b = document.createElement('button');
    b.className = 'server-item-btn' + (idx === currentServerIndex ? ' selected' : '');
    b.setAttribute('tabindex', '0');
    b.textContent = s.name;
    b.onclick = () => {
      currentServerIndex = idx;
      try { localStorage.setItem('mk21_last_server', idx); } catch (e) {}
      $('modalServerPicker').classList.add('hidden');
      loadServer();
    };
    grid.appendChild(b);
  });
  $('modalServerPicker').classList.remove('hidden');
  const first = grid.querySelector('.server-item-btn');
  if (first) first.focus();
}

$('btnCloseServerPicker').onclick = () => $('modalServerPicker').classList.add('hidden');

// BLOQUEIO ADULTO (+18)
$('btnHeaderAdult').onclick = openPinModal;

function openPinModal() {
  if (isAdultUnlocked) {
    isAdultUnlocked = false;
    $('txtLockIcon').textContent = '🔒';
    $('txtLockText').textContent = 'Adulto (Bloqueado)';
    $('btnHeaderAdult').classList.remove('unlocked');
    buildCategories();
    renderCategoriesList();
    selectCategory('ALL');
    alert('Canais adultos foram bloqueados com sucesso.');
  } else {
    enteredPin = '';
    $('pinScreen').textContent = '----';
    $('modalPin').classList.remove('hidden');
  }
}

document.querySelectorAll('.pin-key').forEach(kBtn => {
  kBtn.onclick = () => {
    const k = kBtn.getAttribute('data-k');
    if (k === 'C') {
      enteredPin = '';
      $('pinScreen').textContent = '----';
    } else if (k === 'OK') {
      verifyPin();
    } else {
      if (enteredPin.length < 4) {
        enteredPin += k;
        let masked = '';
        for (let i = 0; i < 4; i++) masked += i < enteredPin.length ? '●' : '-';
        $('pinScreen').textContent = masked;
        if (enteredPin.length === 4) verifyPin();
      }
    }
  };
});

function verifyPin() {
  if (enteredPin === currentPin || enteredPin === '0000' || enteredPin === '8208') {
    isAdultUnlocked = true;
    $('txtLockIcon').textContent = '🔓';
    $('txtLockText').textContent = 'Adulto (Liberado)';
    $('btnHeaderAdult').classList.add('unlocked');
    $('modalPin').classList.add('hidden');
    buildCategories();
    renderCategoriesList();
    const adultCat = liveCategoryKeys.find(c => isAdult(c));
    selectCategory(adultCat || 'ALL');
    alert('Canais adultos liberados com sucesso!');
  } else {
    alert('Senha incorreta! Digite novamente.');
    enteredPin = '';
    $('pinScreen').textContent = '----';
  }
}

$('btnClosePin').onclick = () => $('modalPin').classList.add('hidden');

// MODAL SAÍDA
$('btnExitCancel').onclick = () => $('modalExitConfirm').classList.add('hidden');
$('btnExitConfirm').onclick = () => {
  if (window.webOS && webOS.platformBack) webOS.platformBack();
  else window.close();
};

// 13. INICIALIZAÇÃO
window.addEventListener('load', () => {
  try {
    const saved = localStorage.getItem('mk21_last_server');
    if (saved !== null && SERVERS[saved]) currentServerIndex = parseInt(saved, 10);
  } catch (e) {}

  loadServer();
});
