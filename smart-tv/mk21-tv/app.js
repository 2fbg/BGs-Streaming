// MK21 PLAY — Clone Premium Vizzion Play para LG webOS
const $ = id => document.getElementById(id);

// 1. POLYFILLS PARA NAVEGADORES CHROMIUM ANTIGOS DO WEBOS
if (!Element.prototype.replaceChildren) {
  Element.prototype.replaceChildren = function() {
    while (this.firstChild) {
      this.removeChild(this.firstChild);
    }
    for (var i = 0; i < arguments.length; i++) {
      this.appendChild(arguments[i]);
    }
  };
}

// 2. CONFIGURAÇÃO DOS 7 SERVIDORES MK21
const SERVERS = [
  { id: 'vlog', name: '🟢 VLOG', url: 'http://myopbx.beer/get.php?username=601334065&password=820866576&type=m3u_plus&output=mpegts' },
  { id: 'lub', name: '⚪ LUB TV', url: 'http://pottermax.sbs/get.php?username=601334065&password=820866576&type=m3u_plus&output=mpegts' },
  { id: 'cinelon', name: '🔴 CINELON21', url: 'http://coliseuop.site/get.php?username=601334065&password=820866576&type=m3u_plus&output=mpegts' },
  { id: 'tannix', name: '🟠 TANNIX', url: 'http://poptvcdn.online/get.php?username=601334065&password=820866576&type=m3u_plus&output=mpegts' },
  { id: 'cb6000', name: '🔵 CB6000', url: 'http://cdn.caterlune.top/get.php?username=601334065&password=820866576&type=m3u_plus&output=mpegts' },
  { id: 'mk21pro', name: '🟣 MK21 PRÓ', url: 'http://app.vivoxi.xyz/get.php?username=601334065&password=820866576&type=m3u_plus&output=mpegts' },
  { id: 'multt', name: '🟤 MULTT TV', url: 'http://dali-as.skin/get.php?username=601334065&password=820866576&type=m3u_plus&output=mpegts' }
];

const ADULT_KEYWORDS = ['ADULTO', 'ADULTOS', 'XXX', 'PLAYBOY', 'VENUS', 'SEXTREME', 'SEX', 'ERÓTICO', 'EROTICO', 'PRIVE', 'FORBIDDEN', 'HOT', '18+', 'PORNO', 'BABES', 'REDLIGHT'];
const DEFAULT_PIN = '0000';

// BANCO DE DADOS LOCAL NA TV (IndexedDB)
const DB_NAME = 'MK21_PLAY_CACHE_V2';
const DB_VERSION = 1;
const STORE_NAME = 'playlists';

function openCacheDB() {
  return new Promise(resolve => {
    if (!window.indexedDB) {
      resolve(null);
      return;
    }
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = e => {
      const db = e.target.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME, { keyPath: 'serverId' });
      }
    };
    req.onsuccess = e => resolve(e.target.result);
    req.onerror = () => resolve(null);
  });
}

async function getCachedData(serverId) {
  try {
    const db = await openCacheDB();
    if (!db) return null;
    return new Promise(resolve => {
      const tx = db.transaction(STORE_NAME, 'readonly');
      const store = tx.objectStore(STORE_NAME);
      const req = store.get(serverId);
      req.onsuccess = () => resolve(req.result || null);
      req.onerror = () => resolve(null);
    });
  } catch (e) {
    return null;
  }
}

async function saveCachedData(serverId, data) {
  try {
    const db = await openCacheDB();
    if (!db) return;
    const tx = db.transaction(STORE_NAME, 'readwrite');
    const store = tx.objectStore(STORE_NAME);
    store.put({
      serverId: serverId,
      data: data,
      updatedAt: Date.now()
    });
  } catch (e) {}
}

// ESTADO GLOBAL DO APP
let currentServerIndex = 0;
let currentView = 'home'; // 'home', 'channels', 'vod'
let vodMode = 'movies'; // 'movies' ou 'series'
let allItems = [];
let liveTvItems = [];
let movieItems = [];
let seriesItems = [];
let favoriteUrls = new Set();
let isAdultUnlocked = false;
let enteredPin = '';
let activeChannel = null;
let hlsInstance = null;

// Categorias da TV ao vivo
let liveCategories = {};
let liveCategoryKeys = [];
let selectedLiveCategory = 'ALL';
let currentFilteredChannels = [];

function isAdult(text) {
  if (!text) return false;
  const upper = text.toUpperCase();
  for (let i = 0; i < ADULT_KEYWORDS.length; i++) {
    if (upper.includes(ADULT_KEYWORDS[i])) return true;
  }
  return false;
}

// RELÓGIO DIGITAL
function updateClock() {
  const now = new Date();
  const h = String(now.getHours()).padStart(2, '0');
  const m = String(now.getMinutes()).padStart(2, '0');
  const s = String(now.getSeconds()).padStart(2, '0');
  $('clockDisplay').textContent = `${h}:${m}:${s}`;
}
setInterval(updateClock, 1000);
updateClock();

// FAVORITOS
try {
  const savedFavs = localStorage.getItem('mk21_favs');
  if (savedFavs) favoriteUrls = new Set(JSON.parse(savedFavs));
} catch (e) {}

function toggleFavorite(url) {
  if (favoriteUrls.has(url)) {
    favoriteUrls.delete(url);
    $('btnPlayerFav').textContent = '⭐ Favoritar';
  } else {
    favoriteUrls.add(url);
    $('btnPlayerFav').textContent = '★ Favoritado';
  }
  try {
    localStorage.setItem('mk21_favs', JSON.stringify(Array.from(favoriteUrls)));
  } catch (e) {}
  $('favsCountBadge').textContent = favoriteUrls.size + ' Marcados';
}

// 3. PARSER OTIMIZADO & SEPARAÇÃO AUTOMÁTICA (TV AO VIVO, FILMES E SÉRIES)
function parseAndClassifyM3U(content) {
  const lines = content.split(/\r?\n/);
  const all = [];
  const live = [];
  const movies = [];
  const series = [];

  let name = 'Canal';
  let group = 'Geral';
  let logo = '';

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (line.startsWith('#EXTINF:')) {
      const commaIdx = line.lastIndexOf(',');
      name = commaIdx >= 0 ? line.slice(commaIdx + 1).trim() : 'Canal';

      const groupMatch = line.match(/group-title="([^"]*)"/i);
      group = groupMatch && groupMatch[1] && groupMatch[1].trim() ? groupMatch[1].trim() : 'Geral';

      const logoMatch = line.match(/tvg-logo="([^"]*)"/i);
      logo = logoMatch && logoMatch[1] ? logoMatch[1].trim() : '';
    } else if (/^https?:\/\//i.test(line)) {
      const upperGroup = group.toUpperCase();
      const upperName = name.toUpperCase();
      const isAdultContent = isAdult(upperGroup) || isAdult(upperName);

      const item = {
        name,
        group,
        logo,
        url: line,
        isAdult: isAdultContent
      };

      all.push(item);

      // Classificação automática no padrão Vizzion Play / OTT
      if (upperGroup.includes('SÉRIE') || upperGroup.includes('SERIE') || upperName.includes('S01') || upperName.includes('S02') || upperName.includes('TEMPORADA') || line.includes('/series/')) {
        series.push(item);
      } else if (upperGroup.includes('FILME') || upperGroup.includes('MOVIE') || upperGroup.includes('VOD') || upperGroup.includes('CINE') || upperGroup.includes('4K FILMES') || line.includes('/movie/')) {
        movies.push(item);
      } else {
        live.push(item);
      }

      name = 'Canal';
      group = 'Geral';
      logo = '';
    }
  }

  return { all, live, movies, series };
}

// 4. MUDANÇA DE TELAS (ROTEAMENTO SPA)
function switchView(viewName) {
  currentView = viewName;
  $('viewHome').classList.add('hidden');
  $('viewChannels').classList.add('hidden');
  $('viewVod').classList.add('hidden');

  if (viewName === 'home') {
    $('viewHome').classList.remove('hidden');
    $('cardLiveTv').focus();
    // Pausar vídeo ao voltar para a home
    const video = $('player');
    if (!video.paused) video.pause();
  } else if (viewName === 'channels') {
    $('viewChannels').classList.remove('hidden');
    buildLiveCategories();
    renderLiveCategories();
    selectLiveCategory('ALL');
    setTimeout(() => {
      const firstCh = $('liveChannelsList').querySelector('.ch-item-btn');
      if (firstCh) firstCh.focus();
    }, 200);
  } else if (viewName === 'vod') {
    $('viewVod').classList.remove('hidden');
    renderVodGrid();
    setTimeout(() => {
      const firstVod = $('vodGrid').querySelector('.vod-card');
      if (firstVod) firstVod.focus();
    }, 200);
  }
}

// 5. CONSTRUÇÃO DAS CATEGORIAS DE TV AO VIVO
function buildLiveCategories() {
  liveCategories = { 'ALL': [] };
  liveCategoryKeys = ['ALL'];

  liveTvItems.forEach(ch => {
    if (!isAdultUnlocked && ch.isAdult) return;

    liveCategories['ALL'].push(ch);
    const g = ch.group;
    if (!liveCategories[g]) {
      liveCategories[g] = [];
      liveCategoryKeys.push(g);
    }
    liveCategories[g].push(ch);
  });
}

function renderLiveCategories() {
  const ul = $('liveCategoriesList');
  ul.innerHTML = '';
  const fragment = document.createDocumentFragment();

  $('catCountBadge').textContent = (liveCategoryKeys.length - 1) + ' grupos';

  liveCategoryKeys.forEach(catKey => {
    const li = document.createElement('li');
    const btn = document.createElement('button');
    btn.className = 'cat-item-btn' + (catKey === selectedLiveCategory ? ' active' : '');
    btn.setAttribute('tabindex', '0');

    const label = document.createElement('span');
    label.textContent = catKey === 'ALL' ? '🌟 Todos os Canais' : catKey;

    const count = document.createElement('span');
    count.className = 'pane-badge';
    count.textContent = liveCategories[catKey] ? liveCategories[catKey].length : 0;

    btn.appendChild(label);
    btn.appendChild(count);

    btn.onclick = () => selectLiveCategory(catKey);

    li.appendChild(btn);
    fragment.appendChild(li);
  });

  ul.appendChild(fragment);
}

function selectLiveCategory(catKey) {
  selectedLiveCategory = catKey;
  const btns = $('liveCategoriesList').querySelectorAll('.cat-item-btn');
  btns.forEach(b => b.classList.remove('active'));
  const activeBtn = Array.from(btns).find(b => {
    return (catKey === 'ALL' && b.textContent.includes('Todos os Canais')) || b.textContent.startsWith(catKey);
  });
  if (activeBtn) activeBtn.classList.add('active');

  $('activeCategoryTitle').textContent = catKey === 'ALL' ? '📺 Todos os Canais' : '📁 ' + catKey;
  $('channelSearchInput').value = '';
  renderLiveChannels();
}

function renderLiveChannels() {
  const query = $('channelSearchInput').value.toLowerCase().trim();
  let baseList = liveCategories[selectedLiveCategory] || [];

  if (selectedLiveCategory === 'FAVORITES') {
    baseList = liveTvItems.filter(c => favoriteUrls.has(c.url));
  }

  if (query) {
    currentFilteredChannels = baseList.filter(c => c.name.toLowerCase().includes(query));
  } else {
    currentFilteredChannels = baseList;
  }

  $('channelCountBadge').textContent = currentFilteredChannels.length;

  const ul = $('liveChannelsList');
  ul.innerHTML = '';

  if (currentFilteredChannels.length === 0) {
    const li = document.createElement('li');
    li.innerHTML = '<button class="ch-item-btn" disabled>Nenhum canal encontrado.</button>';
    ul.appendChild(li);
    return;
  }

  const fragment = document.createDocumentFragment();
  const limit = Math.min(currentFilteredChannels.length, 1200);

  for (let i = 0; i < limit; i++) {
    const ch = currentFilteredChannels[i];
    const li = document.createElement('li');
    const btn = document.createElement('button');
    btn.className = 'ch-item-btn' + (activeChannel && activeChannel.url === ch.url ? ' active' : '');
    btn.setAttribute('tabindex', '0');

    const nameSpan = document.createElement('span');
    nameSpan.textContent = (favoriteUrls.has(ch.url) ? '★ ' : '') + ch.name;
    btn.appendChild(nameSpan);

    if (ch.isAdult) {
      const tag = document.createElement('span');
      tag.className = 'pane-badge';
      tag.textContent = '+18';
      btn.appendChild(tag);
    }

    btn.onclick = () => playStream(ch);

    li.appendChild(btn);
    fragment.appendChild(li);
  }

  ul.appendChild(fragment);
}

// 6. RENDERIZAÇÃO DA GRADE DE FILMES / SÉRIES (VOD)
function renderVodGrid() {
  const grid = $('vodGrid');
  grid.innerHTML = '';
  const isMovies = vodMode === 'movies';
  $('vodScreenTitle').textContent = isMovies ? '🎬 Filmes Sob Demanda (VOD)' : '🍿 Séries e Temporadas';

  const list = isMovies ? movieItems : seriesItems;
  const filtered = list.filter(item => isAdultUnlocked || !item.isAdult);

  const fragment = document.createDocumentFragment();
  const limit = Math.min(filtered.length, 60);

  for (let i = 0; i < limit; i++) {
    const item = filtered[i];
    const card = document.createElement('div');
    card.className = 'vod-card';
    card.setAttribute('tabindex', '0');

    const poster = document.createElement('div');
    poster.className = 'vod-poster';
    poster.textContent = isMovies ? '🎬' : '🍿';

    const title = document.createElement('p');
    title.className = 'vod-title';
    title.textContent = item.name;

    const grp = document.createElement('p');
    grp.className = 'vod-group';
    grp.textContent = item.group;

    card.appendChild(poster);
    card.appendChild(title);
    card.appendChild(grp);

    card.onclick = () => {
      // Reproduzir filme ou episódio em tela cheia direto!
      switchView('channels');
      playStream(item);
      toggleFullscreen(true);
    };

    fragment.appendChild(card);
  }

  grid.appendChild(fragment);
}

// 7. PLAYER DE VÍDEO
function playStream(item) {
  activeChannel = item;
  $('currentStreamTitle').textContent = '▶ ' + item.name;
  $('currentStreamCategory').textContent = item.group;

  if (favoriteUrls.has(item.url)) {
    $('btnPlayerFav').textContent = '★ Favoritado';
  } else {
    $('btnPlayerFav').textContent = '⭐ Favoritar';
  }

  const video = $('player');

  if (hlsInstance) {
    hlsInstance.destroy();
    hlsInstance = null;
  }

  const isHls = item.url.toLowerCase().includes('.m3u8') || item.url.includes('/live/');

  if (isHls && window.Hls && Hls.isSupported()) {
    hlsInstance = new Hls({ enableWorker: true, lowLatencyMode: true, maxBufferLength: 20 });
    hlsInstance.loadSource(item.url);
    hlsInstance.attachMedia(video);
    hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => {
      video.play().catch(() => {});
      $('btnPlayerPause').textContent = '⏸ Pausar';
    });
    hlsInstance.on(Hls.Events.ERROR, (event, data) => {
      if (data.fatal) {
        video.src = item.url;
        video.play().catch(() => {});
      }
    });
  } else {
    video.src = item.url;
    video.play().then(() => {
      $('btnPlayerPause').textContent = '⏸ Pausar';
    }).catch(() => {});
  }
}

function toggleFullscreen(force = false) {
  const video = $('player');
  if (force || (!document.fullscreenElement && !document.webkitFullscreenElement)) {
    if (video.requestFullscreen) video.requestFullscreen();
    else if (video.webkitRequestFullscreen) video.webkitRequestFullscreen();
  } else {
    if (document.exitFullscreen) document.exitFullscreen();
    else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
  }
}

$('btnPlayerFullscreen').onclick = () => toggleFullscreen();
$('btnPlayerPause').onclick = () => {
  const video = $('player');
  if (video.paused) {
    video.play();
    $('btnPlayerPause').textContent = '⏸ Pausar';
  } else {
    video.pause();
    $('btnPlayerPause').textContent = '▶ Reproduzir';
  }
};
$('btnPlayerFav').onclick = () => {
  if (activeChannel) toggleFavorite(activeChannel.url);
};

// 8. CARREGAMENTO COM INDEXEDDB CACHE (0.2s INSTANTÂNEO)
async function loadServerData(forceRefresh = false) {
  const srv = SERVERS[currentServerIndex];
  $('headerServerName').textContent = srv.name;

  // 1. TENTA CACHE INSTANTÂNEO
  if (!forceRefresh) {
    const cached = await getCachedData(srv.id);
    if (cached && cached.data) {
      applyData(cached.data);
      $('homeSyncStatus').textContent = '⚡ Carregamento Instantâneo (' + srv.name + ')';
      // Sync em segundo plano
      syncBackground(srv);
      return;
    }
  }

  // 2. DOWNLOAD DA PLAYLIST
  $('homeSyncStatus').textContent = 'Baixando catálogo do ' + srv.name + '...';

  try {
    const res = await fetch(srv.url);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const text = await res.text();
    const data = parseAndClassifyM3U(text);

    saveCachedData(srv.id, data);
    applyData(data);
    $('homeSyncStatus').textContent = '✅ Catálogo pronto (' + srv.name + ')';
  } catch (err) {
    console.error('Download error:', err);
    $('homeSyncStatus').textContent = '⚠️ Falha na rede: ' + err.message;
  }
}

function applyData(data) {
  allItems = data.all || [];
  liveTvItems = data.live || [];
  movieItems = data.movies || [];
  seriesItems = data.series || [];

  $('tvCountBadge').textContent = liveTvItems.length.toLocaleString('pt-BR') + ' Canais';
  $('moviesCountBadge').textContent = movieItems.length.toLocaleString('pt-BR') + ' Títulos';
  $('seriesCountBadge').textContent = seriesItems.length.toLocaleString('pt-BR') + ' Séries';
  $('favsCountBadge').textContent = favoriteUrls.size + ' Marcados';
}

async function syncBackground(srv) {
  try {
    const res = await fetch(srv.url);
    if (res.ok) {
      const text = await res.text();
      const data = parseAndClassifyM3U(text);
      if (data.all.length > 0) saveCachedData(srv.id, data);
    }
  } catch (e) {}
}

// 9. EVENTOS DOS 4 CARDS DO DASHBOARD
$('cardLiveTv').onclick = () => switchView('channels');
$('cardMovies').onclick = () => {
  vodMode = 'movies';
  switchView('vod');
};
$('cardSeries').onclick = () => {
  vodMode = 'series';
  switchView('vod');
};
$('cardFavs').onclick = () => {
  switchView('channels');
  selectLiveCategory('FAVORITES');
};

$('btnBackFromVod').onclick = () => switchView('home');

// 10. BLOQUEIO ADULTO (+18)
function updateAdultUi() {
  if (isAdultUnlocked) {
    $('lockStatusIcon').textContent = '🔓';
    $('lockStatusText').textContent = 'Adulto (Liberado)';
    $('btnHeaderLock').classList.add('unlocked');
  } else {
    $('lockStatusIcon').textContent = '🔒';
    $('lockStatusText').textContent = 'Adulto (Bloqueado)';
    $('btnHeaderLock').classList.remove('unlocked');
  }
}

$('btnHeaderLock').onclick = () => {
  if (isAdultUnlocked) {
    isAdultUnlocked = false;
    updateAdultUi();
    if (currentView === 'channels') {
      buildLiveCategories();
      renderLiveCategories();
      selectLiveCategory('ALL');
    }
    alert('Canais adultos foram bloqueados com sucesso.');
  } else {
    enteredPin = '';
    $('pinScreen').textContent = '----';
    $('pinStatusMsg').textContent = '';
    $('adultPinModal').classList.remove('hidden');
    const firstKey = $('adultPinModal').querySelector('.key-btn');
    if (firstKey) firstKey.focus();
  }
};

document.querySelectorAll('.key-btn').forEach(btn => {
  btn.onclick = () => {
    const k = btn.getAttribute('data-key');
    if (k === 'C') {
      enteredPin = '';
      $('pinScreen').textContent = '----';
      $('pinStatusMsg').textContent = '';
    } else if (k === 'OK') {
      checkPin();
    } else {
      if (enteredPin.length < 4) {
        enteredPin += k;
        let masked = '';
        for (let i = 0; i < 4; i++) masked += i < enteredPin.length ? '●' : '-';
        $('pinScreen').textContent = masked;
        if (enteredPin.length === 4) checkPin();
      }
    }
  };
});

function checkPin() {
  if (enteredPin === DEFAULT_PIN || enteredPin === '8208') {
    isAdultUnlocked = true;
    updateAdultUi();
    $('adultPinModal').classList.add('hidden');
    if (currentView === 'channels') {
      buildLiveCategories();
      renderLiveCategories();
      const adultCat = liveCategoryKeys.find(c => isAdult(c));
      selectLiveCategory(adultCat || 'ALL');
    }
    alert('Conteúdo adulto liberado com sucesso!');
  } else {
    $('pinStatusMsg').textContent = 'Senha incorreta! (Padrão: 0000)';
    enteredPin = '';
    $('pinScreen').textContent = '----';
  }
}

$('btnClosePinModal').onclick = () => $('adultPinModal').classList.add('hidden');

// 11. MODAL DE SERVIDORES (7 SERVIDORES)
$('btnHeaderServer').onclick = () => {
  const list = $('serverPickList');
  list.innerHTML = '';
  SERVERS.forEach((s, idx) => {
    const b = document.createElement('button');
    b.className = 'server-pick-btn' + (idx === currentServerIndex ? ' selected' : '');
    b.setAttribute('tabindex', '0');
    b.textContent = s.name;
    b.onclick = () => {
      currentServerIndex = idx;
      try {
        localStorage.setItem('mk21_last_server', idx);
      } catch (e) {}
      $('serverPickerModal').classList.add('hidden');
      loadServerData();
    };
    list.appendChild(b);
  });
  $('serverPickerModal').classList.remove('hidden');
  const first = list.querySelector('.server-pick-btn');
  if (first) first.focus();
};

$('btnCloseServerPicker').onclick = () => $('serverPickerModal').classList.add('hidden');

// 12. NAVEGAÇÃO ESPACIAL E CONTROLE REMOTO LG WEBOS
document.addEventListener('keydown', e => {
  const k = e.keyCode;

  // TECLA VOLTAR (461 da LG, 27 Escape, 8 Backspace)
  if (k === 461 || k === 27 || k === 8 || e.key === 'GoBack') {
    e.preventDefault();

    if (document.fullscreenElement || document.webkitFullscreenElement) {
      toggleFullscreen();
      return;
    }
    if (!$('adultPinModal').classList.contains('hidden')) {
      $('adultPinModal').classList.add('hidden');
      return;
    }
    if (!$('serverPickerModal').classList.contains('hidden')) {
      $('serverPickerModal').classList.add('hidden');
      return;
    }
    if (currentView !== 'home') {
      switchView('home');
      return;
    }
  }

  // TECLAS COLORIDAS DO CONTROLE LG
  if (k === 403) { // Botão Vermelho -> Servidores
    e.preventDefault();
    $('btnHeaderServer').click();
    return;
  }
  if (k === 404) { // Botão Verde -> Atualizar Lista
    e.preventDefault();
    loadServerData(true);
    return;
  }
  if (k === 405) { // Botão Amarelo -> Favoritar
    e.preventDefault();
    if (activeChannel) toggleFavorite(activeChannel.url);
    return;
  }
  if (k === 406) { // Botão Azul -> Bloqueio Adulto
    e.preventDefault();
    $('btnHeaderLock').click();
    return;
  }

  // TECLAS NUMÉRICAS (0 a 9) PARA PIN
  if (k >= 48 && k <= 57 && !$('adultPinModal').classList.contains('hidden')) {
    const d = (k - 48).toString();
    const b = $('adultPinModal').querySelector(`[data-key="${d}"]`);
    if (b) b.click();
    e.preventDefault();
    return;
  }
});

// INICIALIZAÇÃO
window.addEventListener('load', () => {
  try {
    const saved = localStorage.getItem('mk21_last_server');
    if (saved !== null && SERVERS[saved]) currentServerIndex = parseInt(saved, 10);
  } catch (e) {}

  updateAdultUi();
  loadServerData();
  switchView('home');
});
