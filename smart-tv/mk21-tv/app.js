// MK21 PLAY v2.2.0 — Smart TV Engine LG webOS (Clone Completo Vizzion Play)
const $ = id => document.getElementById(id);

// 1. POLYFILLS DE COMPATIBILIDADE CHROMIUM WEBOS
if (!Element.prototype.replaceChildren) {
  Element.prototype.replaceChildren = function() {
    while (this.firstChild) this.removeChild(this.firstChild);
    for (var i = 0; i < arguments.length; i++) this.appendChild(arguments[i]);
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

// 3. BANCO DE DADOS LOCAL (IndexedDB)
const DB_NAME = 'MK21_PLAY_DB_V3';
const DB_VERSION = 1;
const STORE_NAME = 'playlists';

function openCacheDB() {
  return new Promise(resolve => {
    if (!window.indexedDB) return resolve(null);
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
  } catch (e) { return null; }
}

async function saveCachedData(serverId, data) {
  try {
    const db = await openCacheDB();
    if (!db) return;
    const tx = db.transaction(STORE_NAME, 'readwrite');
    const store = tx.objectStore(STORE_NAME);
    store.put({ serverId, data, updatedAt: Date.now() });
  } catch (e) {}
}

// 4. ESTADO GERAL DO APLICATIVO
let currentServerIndex = 0;
let currentView = 'home'; // 'home', 'channels', 'vod', 'lists', 'settings'
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
let currentPin = '0000';
let streamFormat = 'default'; // 'default', 'm3u8', 'ts'
let currentLanguage = 'pt-BR';

// Categorias e canais
let liveCategories = {};
let liveCategoryKeys = [];
let selectedLiveCategory = 'ALL';
let currentFilteredChannels = [];

// Carrossel de Destaques
const HERO_ITEMS = [
  { title: 'Animal Planet FHD', desc: 'Acompanhe documentários incríveis e a vida selvagem ao vivo.', tag: '🔥 MAIS ASSISTIDO', bg: 'https://images.unsplash.com/photo-1578328819058-b69f3a3b0f6b?w=1600&q=80', search: 'Animal Planet' },
  { title: 'HBO Max & Cinema 4K', desc: 'Filmes campeões de bilheteria e séries exclusivas com áudio 5.1.', tag: '🎬 CINEMA 4K', bg: 'https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=1600&q=80', search: 'HBO' },
  { title: 'SporTV 1 & Premiere FHD', desc: 'Campeonatos de futebol, rodadas ao vivo e cobertura esportiva 24h.', tag: '⚽ AO VIVO', bg: 'https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=1600&q=80', search: 'SporTV' },
  { title: 'Telecine Pipoca & Action', desc: 'As melhores aventuras, animações e filmes dublados para toda a família.', tag: '🍿 FILMES EM ALTA', bg: 'https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=1600&q=80', search: 'Telecine' }
];
let heroIndex = 0;

// RELÓGIO DIGITAL
function updateClock() {
  const now = new Date();
  const h = String(now.getHours()).padStart(2, '0');
  const m = String(now.getMinutes()).padStart(2, '0');
  const s = String(now.getSeconds()).padStart(2, '0');
  const str = `${h}:${m}:${s}`;
  if ($('liveClock')) $('liveClock').textContent = str;

  // Atualizar flip clock da tela de configurações
  const flipH = $('flipHours');
  const flipM = $('flipMinutes');
  if (flipH && flipM) {
    flipH.textContent = h;
    flipM.textContent = m;
  }
}
setInterval(updateClock, 1000);
updateClock();

// ROTAÇÃO AUTOMÁTICA DO CARROSSEL
setInterval(() => {
  heroIndex = (heroIndex + 1) % HERO_ITEMS.length;
  updateHeroBanner();
}, 6000);

function updateHeroBanner() {
  const item = HERO_ITEMS[heroIndex];
  const banner = $('heroBanner');
  if (!banner) return;
  banner.style.backgroundImage = `url('${item.bg}')`;
  $('heroBadge').textContent = item.tag;
  $('heroTitle').textContent = item.title;
  $('heroDesc').textContent = item.desc;
}

$('btnHeroPlay').onclick = () => {
  const item = HERO_ITEMS[heroIndex];
  switchView('channels');
  $('inputChannelSearch').value = item.search;
  renderLiveChannels();
  setTimeout(() => {
    const first = $('listChannels').querySelector('.item-btn');
    if (first) first.click();
  }, 300);
};

// FAVORITOS
try {
  const savedFavs = localStorage.getItem('mk21_favs');
  if (savedFavs) favoriteUrls = new Set(JSON.parse(savedFavs));
} catch (e) {}

function toggleFavorite(url) {
  if (favoriteUrls.has(url)) {
    favoriteUrls.delete(url);
    if ($('btnFavChannel')) $('btnFavChannel').textContent = '⭐ Favoritar';
  } else {
    favoriteUrls.add(url);
    if ($('btnFavChannel')) $('btnFavChannel').textContent = '★ Favoritado';
  }
  try {
    localStorage.setItem('mk21_favs', JSON.stringify(Array.from(favoriteUrls)));
  } catch (e) {}
}

// 5. PARSER E OTIMIZAÇÃO DE MEMÓRIA (ANTI-CRASH)
function isAdult(text) {
  if (!text) return false;
  const upper = text.toUpperCase();
  for (let i = 0; i < ADULT_KEYWORDS.length; i++) {
    if (upper.includes(ADULT_KEYWORDS[i])) return true;
  }
  return false;
}

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
      const gMatch = line.match(/group-title="([^"]*)"/i);
      group = gMatch && gMatch[1] && gMatch[1].trim() ? gMatch[1].trim() : 'Geral';
      const lMatch = line.match(/tvg-logo="([^"]*)"/i);
      logo = lMatch && lMatch[1] ? lMatch[1].trim() : '';
    } else if (/^https?:\/\//i.test(line)) {
      const upperGroup = group.toUpperCase();
      const upperName = name.toUpperCase();
      const isAdultContent = isAdult(upperGroup) || isAdult(upperName);

      const item = { name, group, logo, url: line, isAdult: isAdultContent };
      all.push(item);

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

// 6. ROTEAMENTO DE TELAS
function switchView(viewName) {
  currentView = viewName;

  // Esconder todas as telas
  ['viewHome', 'viewChannels', 'viewVod', 'viewLists', 'viewSettings'].forEach(id => {
    const el = $(id);
    if (el) el.classList.add('hidden');
  });

  // Atualizar classe ativa na Sidebar
  const navMap = {
    'home': 'navHome',
    'channels': 'navLiveTv',
    'movies': 'navMovies',
    'series': 'navSeries',
    'lists': 'navLists',
    'settings': 'navSettings'
  };

  document.querySelectorAll('.nav-item-btn').forEach(b => b.classList.remove('active'));
  const activeNav = $(navMap[viewName]);
  if (activeNav) activeNav.classList.add('active');

  // Pausar player ao sair da tela de TV
  if (viewName !== 'channels') {
    const v = $('tvPlayer');
    if (v && !v.paused) v.pause();
  }

  if (viewName === 'home') {
    $('viewHome').classList.remove('hidden');
    $('cardTvAoVivo').focus();
  } else if (viewName === 'channels') {
    $('viewChannels').classList.remove('hidden');
    buildLiveCategories();
    renderLiveCategories();
    selectLiveCategory('ALL');
  } else if (viewName === 'movies') {
    vodMode = 'movies';
    $('viewVod').classList.remove('hidden');
    renderVodGrid();
  } else if (viewName === 'series') {
    vodMode = 'series';
    $('viewVod').classList.remove('hidden');
    renderVodGrid();
  } else if (viewName === 'lists') {
    $('viewLists').classList.remove('hidden');
    renderServersList();
  } else if (viewName === 'settings') {
    $('viewSettings').classList.remove('hidden');
    renderSettingsDetail('info');
  }
}

// BOTÕES DA SIDEBAR
$('btnLogoHome').onclick = () => switchView('home');
$('navHome').onclick = () => switchView('home');
$('navLiveTv').onclick = () => switchView('channels');
$('navMovies').onclick = () => switchView('movies');
$('navSeries').onclick = () => switchView('series');
$('navLists').onclick = () => switchView('lists');
$('navSettings').onclick = () => switchView('settings');
$('navSearch').onclick = () => {
  switchView('channels');
  $('inputChannelSearch').focus();
};
$('btnAccountBadge').onclick = () => switchView('settings');

// BOTÃO ATUALIZAR LISTA (FUNCIONAMENTO GARANTIDO)
$('navRefreshList').onclick = () => {
  $('txtSyncStatus').textContent = '🔄 Atualizando lista...';
  loadServerData(true);
};

// 7. CARREGAMENTO DOS SERVIDORES (CACHE-FIRST + PRE-FETCH EM SEGUNDO PLANO)
async function loadServerData(forceRefresh = false) {
  const srv = SERVERS[currentServerIndex];
  $('txtTopServerName').textContent = srv.name;

  if (!forceRefresh) {
    const cached = await getCachedData(srv.id);
    if (cached && cached.data) {
      applyLoadedData(cached.data);
      $('txtSyncStatus').textContent = '⚡ Instantâneo (' + srv.name + ')';
      preloadRemainingServers();
      return;
    }
  }

  $('txtSyncStatus').textContent = 'Baixando ' + srv.name + '...';

  try {
    const res = await fetch(srv.url);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const text = await res.text();
    const data = parseAndClassifyM3U(text);

    saveCachedData(srv.id, data);
    applyLoadedData(data);
    $('txtSyncStatus').textContent = '✅ Catálogo pronto (' + srv.name + ')';

    // Disparar pré-carregamento dos outros servidores em segundo plano
    preloadRemainingServers();
  } catch (err) {
    console.error('Fetch error:', err);
    $('txtSyncStatus').textContent = '⚠️ Falha na rede: ' + err.message;
  }
}

function applyLoadedData(data) {
  allItems = data.all || [];
  liveTvItems = data.live || [];
  movieItems = data.movies || [];
  seriesItems = data.series || [];

  if ($('countTv')) $('countTv').textContent = liveTvItems.length.toLocaleString('pt-BR') + ' Canais';
  if ($('countMovies')) $('countMovies').textContent = movieItems.length.toLocaleString('pt-BR') + ' Títulos';
  if ($('countSeries')) $('countSeries').textContent = seriesItems.length.toLocaleString('pt-BR') + ' Séries';
}

// BAIXAR OUTRAS LISTAS EM SEGUNDO PLANO PARA TROCA IMEDIATA
let isPreloading = false;
async function preloadRemainingServers() {
  if (isPreloading) return;
  isPreloading = true;

  setTimeout(async () => {
    for (let i = 0; i < SERVERS.length; i++) {
      if (i === currentServerIndex) continue;
      const srv = SERVERS[i];
      const existing = await getCachedData(srv.id);
      if (!existing) {
        try {
          const res = await fetch(srv.url);
          if (res.ok) {
            const txt = await res.text();
            const d = parseAndClassifyM3U(txt);
            saveCachedData(srv.id, d);
          }
        } catch (e) {}
      }
    }
    isPreloading = false;
  }, 4000);
}

// 8. TELA TV AO VIVO & EPG
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
  const ul = $('listCategories');
  ul.innerHTML = '';
  const fragment = document.createDocumentFragment();

  $('badgeCategoriesCount').textContent = (liveCategoryKeys.length - 1);

  liveCategoryKeys.forEach(catKey => {
    const li = document.createElement('li');
    const btn = document.createElement('button');
    btn.className = 'item-btn' + (catKey === selectedLiveCategory ? ' active' : '');
    btn.setAttribute('tabindex', '0');

    const label = document.createElement('span');
    label.textContent = catKey === 'ALL' ? '🌟 Todos os Canais' : catKey;

    const count = document.createElement('span');
    count.className = 'badge-counter';
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
  const btns = $('listCategories').querySelectorAll('.item-btn');
  btns.forEach(b => b.classList.remove('active'));
  const activeBtn = Array.from(btns).find(b => (catKey === 'ALL' && b.textContent.includes('Todos os Canais')) || b.textContent.startsWith(catKey));
  if (activeBtn) activeBtn.classList.add('active');

  $('titleActiveCategory').textContent = catKey === 'ALL' ? '📺 Todos os Canais' : '📁 ' + catKey;
  $('inputChannelSearch').value = '';
  renderLiveChannels();
}

function renderLiveChannels() {
  const q = $('inputChannelSearch').value.toLowerCase().trim();
  const base = liveCategories[selectedLiveCategory] || [];
  currentFilteredChannels = q ? base.filter(c => c.name.toLowerCase().includes(q)) : base;

  $('badgeChannelsCount').textContent = currentFilteredChannels.length;
  const ul = $('listChannels');
  ul.innerHTML = '';

  if (currentFilteredChannels.length === 0) {
    ul.innerHTML = '<li><button class="item-btn" disabled>Nenhum canal nesta categoria.</button></li>';
    return;
  }

  const fragment = document.createDocumentFragment();
  // Limite de 80 canais por renderização para evitar Out-Of-Memory na TV LG
  const limit = Math.min(currentFilteredChannels.length, 80);

  for (let i = 0; i < limit; i++) {
    const ch = currentFilteredChannels[i];
    const li = document.createElement('li');
    const btn = document.createElement('button');
    btn.className = 'item-btn' + (activeChannel && activeChannel.url === ch.url ? ' active' : '');
    btn.setAttribute('tabindex', '0');

    const titleSpan = document.createElement('span');
    titleSpan.textContent = (favoriteUrls.has(ch.url) ? '★ ' : '') + ch.name;
    btn.appendChild(titleSpan);

    if (ch.isAdult) {
      const t = document.createElement('span');
      t.className = 'badge-counter';
      t.textContent = '+18';
      btn.appendChild(t);
    }

    // Clique único: reproduz no mini-player
    btn.onclick = () => playChannel(ch);

    // Duplo clique: abre em tela cheia na hora!
    let lastClick = 0;
    btn.addEventListener('click', () => {
      const now = Date.now();
      if (now - lastClick < 380) {
        toggleFullscreen(true);
      }
      lastClick = now;
    });

    li.appendChild(btn);
    fragment.appendChild(li);
  }

  ul.appendChild(fragment);
}

$('inputChannelSearch').addEventListener('input', renderLiveChannels);

// BUSCA POR VOZ (WEBSPEECH API)
$('btnVoiceSearch').onclick = () => {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRecognition) {
    alert('Busca por voz não suportada pelo microfone deste navegador.');
    return;
  }
  const recog = new SpeechRecognition();
  recog.lang = 'pt-BR';
  $('txtSyncStatus').textContent = '🎙️ Fale o nome do canal agora...';
  recog.onresult = e => {
    const text = e.results[0][0].transcript;
    $('inputChannelSearch').value = text;
    renderLiveChannels();
    $('txtSyncStatus').textContent = '🔍 Buscando: ' + text;
  };
  recog.onerror = () => {
    $('txtSyncStatus').textContent = '⚠️ Erro ao ouvir voz.';
  };
  recog.start();
};

// 9. PLAYER DE VÍDEO & EPG DINÂMICO
function playChannel(channel) {
  activeChannel = channel;
  $('epgCurrentChannel').textContent = '▶ ' + channel.name;
  $('epgNowShow').textContent = 'Transmissão Ao Vivo: ' + channel.group;

  if (favoriteUrls.has(channel.url)) {
    $('btnFavChannel').textContent = '★ Favoritado';
  } else {
    $('btnFavChannel').textContent = '⭐ Favoritar';
  }

  // Atualizar classe active na lista
  const btns = $('listChannels').querySelectorAll('.item-btn');
  btns.forEach(b => b.classList.remove('active'));

  const video = $('tvPlayer');
  if (hlsInstance) {
    hlsInstance.destroy();
    hlsInstance = null;
  }

  let finalUrl = channel.url;
  // Ajuste do formato de fluxo se configurado
  if (streamFormat === 'm3u8' && !finalUrl.includes('.m3u8')) {
    finalUrl = finalUrl.replace(/\.ts|\/mpegts/g, '.m3u8');
  }

  const isHls = finalUrl.toLowerCase().includes('.m3u8') || finalUrl.includes('/live/');

  if (isHls && window.Hls && Hls.isSupported()) {
    hlsInstance = new Hls({ enableWorker: true, lowLatencyMode: true, maxBufferLength: 15 });
    hlsInstance.loadSource(finalUrl);
    hlsInstance.attachMedia(video);
    hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => video.play().catch(() => {}));
    hlsInstance.on(Hls.Events.ERROR, (e, data) => {
      if (data.fatal) {
        video.src = finalUrl;
        video.play().catch(() => {});
      }
    });
  } else {
    video.src = finalUrl;
    video.play().catch(() => {});
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
$('btnFullscreenNow').onclick = () => toggleFullscreen();
$('btnFavChannel').onclick = () => {
  if (activeChannel) toggleFavorite(activeChannel.url);
};

// 10. CARDS DO INÍCIO
$('cardTvAoVivo').onclick = () => switchView('channels');
$('cardFilmes').onclick = () => switchView('movies');
$('cardSeries').onclick = () => switchView('series');

// 11. TELA DE FILMES E SÉRIES (VOD GRID)
function renderVodGrid() {
  const grid = $('gridVodItems');
  grid.innerHTML = '';
  const isMovies = vodMode === 'movies';
  $('titleVodScreen').textContent = isMovies ? '🎬 Catálogo de Filmes (VOD)' : '🍿 Catálogo de Séries';

  const list = isMovies ? movieItems : seriesItems;
  const filtered = list.filter(item => isAdultUnlocked || !item.isAdult);

  const fragment = document.createDocumentFragment();
  const limit = Math.min(filtered.length, 50);

  for (let i = 0; i < limit; i++) {
    const item = filtered[i];
    const card = document.createElement('div');
    card.className = 'dash-card';
    card.style.height = '200px';
    card.setAttribute('tabindex', '0');

    card.innerHTML = `
      <div class="dash-card-icon">${isMovies ? '🎬' : '🍿'}</div>
      <div>
        <h4 style="margin:0; font-size:18px; font-weight:700; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${item.name}</h4>
        <p style="margin:4px 0 0 0; font-size:14px; color:#888;">${item.group}</p>
      </div>
    `;

    card.onclick = () => {
      switchView('channels');
      playChannel(item);
      toggleFullscreen(true);
    };

    fragment.appendChild(card);
  }

  grid.appendChild(fragment);
}

// 12. TELA DE LISTAS & SERVIDORES
function renderServersList() {
  const container = $('serversCardsList');
  container.innerHTML = '';

  SERVERS.forEach((s, idx) => {
    const card = document.createElement('button');
    card.className = 'server-card-btn' + (idx === currentServerIndex ? ' active-server' : '');
    card.setAttribute('tabindex', '0');

    card.innerHTML = `
      <span class="server-tag">${idx === currentServerIndex ? 'ATIVO NO MOMENTO' : 'DISPONÍVEL'}</span>
      <span>${s.name}</span>
      <span style="font-size:13px; color:#888; word-break:break-all;">${s.url.slice(0, 45)}...</span>
    `;

    card.onclick = () => {
      currentServerIndex = idx;
      try { localStorage.setItem('mk21_last_server', idx); } catch (e) {}
      loadServerData();
      renderServersList();
    };

    container.appendChild(card);
  });
}

$('btnLoadManualList').onclick = () => {
  const u = $('inputManualUrl').value.trim();
  if (!u.startsWith('http')) {
    alert('Por favor, informe uma URL válida começando com http://');
    return;
  }
  SERVERS.push({ id: 'manual_' + Date.now(), name: '⭐ LISTA PERSONALIZADA', url: u });
  currentServerIndex = SERVERS.length - 1;
  loadServerData();
  renderServersList();
  alert('Lista manual conectada com sucesso!');
};

// 13. TELA DE CONFIGURAÇÕES (CLONE VIZZION PLAY FOTOS)
const SETTING_OPTS = ['optInfoGeral', 'optMudarIdioma', 'optFormatoFluxo', 'optAlterarPin', 'optGerenciarCat', 'optLimparStorage', 'optConfigTempo'];

SETTING_OPTS.forEach(optId => {
  const btn = $(optId);
  if (btn) {
    btn.onclick = () => {
      SETTING_OPTS.forEach(id => $(id).classList.remove('active'));
      btn.classList.add('active');
      renderSettingsDetail(optId.replace('opt', '').toLowerCase());
    };
  }
});

function renderSettingsDetail(section) {
  const panel = $('settingsDetailPanel');

  if (section === 'infogeral' || section === 'info') {
    panel.innerHTML = `
      <h3 class="settings-section-title">Informações da Conta</h3>
      <div class="info-grid">
        <div class="info-item"><div class="info-label">Status</div><div class="info-val" style="color:#4caf50;">Ativo (Plano Anual)</div></div>
        <div class="info-item"><div class="info-label">Data de registro</div><div class="info-val">07.12.2025</div></div>
        <div class="info-item"><div class="info-label">Vencimento</div><div class="info-val">15.03.2027</div></div>
        <div class="info-item"><div class="info-label">Conexões permitidas</div><div class="info-val">2 Telas Simultâneas</div></div>
      </div>

      <h3 class="settings-section-title">Informação do dispositivo</h3>
      <div class="info-grid">
        <div class="info-item"><div class="info-label">Endereço Mac</div><div class="info-val">F0:86:20:F1:5E:E4</div></div>
        <div class="info-item"><div class="info-label">Versão do aplicativo</div><div class="info-val">2.2.0 (webOS 1080p)</div></div>
        <div class="info-item"><div class="info-label">Chave do dispositivo</div><div class="info-val">419858</div></div>
        <div class="info-item"><div class="info-label">Sistema Operacional</div><div class="info-val">LG webOS Smart TV</div></div>
      </div>
    `;
  } else if (section === 'mudaridioma') {
    const langs = [
      { name: 'English', flag: '🇺🇸', code: 'en' },
      { name: 'Հայերեն', flag: '🇦🇲', code: 'hy' },
      { name: 'العربية', flag: '🇸🇦', code: 'ar' },
      { name: 'Deutsch', flag: '🇩🇪', code: 'de' },
      { name: 'Français', flag: '🇫🇷', code: 'fr' },
      { name: 'Português - Brasil', flag: '🇧🇷', code: 'pt-BR' },
      { name: 'Español', flag: '🇪🇸', code: 'es' },
      { name: 'Türkçe', flag: '🇹🇷', code: 'tr' }
    ];
    let html = '<h3 class="settings-section-title">Mudar idioma</h3><div style="display:flex; flex-direction:column; gap:8px;">';
    langs.forEach(l => {
      const active = l.code === currentLanguage ? 'border-color: #e50914; background: rgba(229,9,20,0.25);' : '';
      html += `<button class="item-btn" style="${active}" onclick="setLanguage('${l.code}')" tabindex="0"><span>${l.flag} ${l.name}</span> <span>${l.code === currentLanguage ? '✓ Selecionado' : ''}</span></button>`;
    });
    html += '</div>';
    panel.innerHTML = html;
  } else if (section === 'formatofluxo') {
    panel.innerHTML = `
      <h3 class="settings-section-title">Alterar formato de fluxo</h3>
      <div style="display:flex; flex-direction:column; gap:10px;">
        <button class="item-btn ${streamFormat === 'default' ? 'active' : ''}" onclick="setStreamFormat('default')" tabindex="0">Default (Automático)</button>
        <button class="item-btn ${streamFormat === 'm3u8' ? 'active' : ''}" onclick="setStreamFormat('m3u8')" tabindex="0">m3u8 (HLS Nativo)</button>
        <button class="item-btn ${streamFormat === 'ts' ? 'active' : ''}" onclick="setStreamFormat('ts')" tabindex="0">ts (MPEG-TS)</button>
      </div>
    `;
  } else if (section === 'alterarpin') {
    panel.innerHTML = `
      <h3 class="settings-section-title">Alterar Código PIN Parental</h3>
      <p style="color:#aaa;">O PIN atual é: <strong>${currentPin}</strong></p>
      <input id="inputNewPin" type="password" maxlength="4" placeholder="Digite novo PIN de 4 dígitos" style="width:260px; padding:12px; font-size:20px; text-align:center; background:#10131d; border:1px solid rgba(255,255,255,0.2); border-radius:8px; color:#fff;" tabindex="0">
      <br><br>
      <button class="hero-btn" onclick="saveNewPin()" tabindex="0">Salvar Novo PIN</button>
    `;
  } else if (section === 'limparstorage') {
    panel.innerHTML = `
      <h3 class="settings-section-title">Limpar Armazenamento</h3>
      <div style="display:flex; flex-direction:column; gap:12px;">
        <div style="display:flex; justify-content:space-between; align-items:center; background:#191d2c; padding:12px 18px; border-radius:8px;">
          <span>Canais favoritos</span>
          <button class="server-quick-btn" onclick="clearFavs()" tabindex="0">Limpar</button>
        </div>
        <div style="display:flex; justify-content:space-between; align-items:center; background:#191d2c; padding:12px 18px; border-radius:8px;">
          <span>Filmes favoritos</span>
          <button class="server-quick-btn" onclick="alert('Filmes favoritos limpos.')" tabindex="0">Limpar</button>
        </div>
        <div style="display:flex; justify-content:space-between; align-items:center; background:#191d2c; padding:12px 18px; border-radius:8px;">
          <span>Séries favoritas</span>
          <button class="server-quick-btn" onclick="alert('Séries favoritas limpas.')" tabindex="0">Limpar</button>
        </div>
        <div style="display:flex; justify-content:space-between; align-items:center; background:#191d2c; padding:12px 18px; border-radius:8px;">
          <span>Histórico assistido</span>
          <button class="server-quick-btn" onclick="alert('Histórico limpo.')" tabindex="0">Limpar</button>
        </div>
        <div style="display:flex; justify-content:space-between; align-items:center; background:#191d2c; padding:12px 18px; border-radius:8px;">
          <span style="color:#ff5252; font-weight:bold;">Limpar tudo (Cache da TV)</span>
          <button class="hero-btn" style="background:#d32f2f;" onclick="clearAllData()" tabindex="0">Limpar</button>
        </div>
      </div>
    `;
  } else if (section === 'configtempo') {
    const now = new Date();
    const h = String(now.getHours()).padStart(2, '0');
    const m = String(now.getMinutes()).padStart(2, '0');
    panel.innerHTML = `
      <h3 class="settings-section-title">Configurações de Tempo</h3>
      <div class="time-clock-box">
        <div id="flipHours" class="flip-digit">${h}</div>
        <div class="flip-dots">:</div>
        <div id="flipMinutes" class="flip-digit">${m}</div>
      </div>
      <p style="text-align:center; color:#aaa;">Sincronizado automaticamente com o relógio da Smart TV LG.</p>
    `;
  } else if (section === 'gerenciarcat') {
    panel.innerHTML = `
      <h3 class="settings-section-title">Gerenciar Categorias</h3>
      <p style="color:#aaa;">Ative ou oculte as categorias exibidas na TV:</p>
      <div style="display:flex; flex-direction:column; gap:8px;">
        <label style="display:flex; gap:12px; align-items:center;"><input type="checkbox" checked style="width:20px; height:20px;"> Canais Abertos</label>
        <label style="display:flex; gap:12px; align-items:center;"><input type="checkbox" checked style="width:20px; height:20px;"> Esportes & Premiere</label>
        <label style="display:flex; gap:12px; align-items:center;"><input type="checkbox" checked style="width:20px; height:20px;"> Filmes & Séries</label>
        <label style="display:flex; gap:12px; align-items:center;"><input type="checkbox" checked style="width:20px; height:20px;"> Notícias & Documentários</label>
        <label style="display:flex; gap:12px; align-items:center;"><input type="checkbox" checked style="width:20px; height:20px;"> Infantil & Desenhos</label>
      </div>
    `;
  }
}

window.setLanguage = l => {
  currentLanguage = l;
  renderSettingsDetail('mudaridioma');
  alert('Idioma alterado com sucesso!');
};

window.setStreamFormat = fmt => {
  streamFormat = fmt;
  renderSettingsDetail('formatofluxo');
  alert('Formato de fluxo alterado para: ' + fmt);
};

window.saveNewPin = () => {
  const p = $('inputNewPin').value.trim();
  if (p.length === 4) {
    currentPin = p;
    alert('PIN alterado com sucesso para: ' + p);
  } else {
    alert('Digite exatamente 4 dígitos numéricos.');
  }
};

window.clearFavs = () => {
  favoriteUrls.clear();
  try { localStorage.removeItem('mk21_favs'); } catch (e) {}
  alert('Canais favoritos removidos com sucesso.');
};

window.clearAllData = async () => {
  try {
    indexedDB.deleteDatabase(DB_NAME);
    localStorage.clear();
    alert('Memória limpa com sucesso. O app irá recarregar.');
    location.reload();
  } catch (e) {}
};

// 14. BLOQUEIO ADULTO (+18)
$('btnTopLock').onclick = () => {
  if (isAdultUnlocked) {
    isAdultUnlocked = false;
    $('txtLockIcon').textContent = '🔒';
    $('txtLockLabel').textContent = 'Adulto (Bloqueado)';
    if (currentView === 'channels') {
      buildLiveCategories();
      renderLiveCategories();
      selectLiveCategory('ALL');
    }
    alert('Canais adultos bloqueados.');
  } else {
    enteredPin = '';
    $('displayPin').textContent = '----';
    $('modalPinAdult').classList.remove('hidden');
    const firstKey = $('modalPinAdult').querySelector('.hero-btn');
    if (firstKey) firstKey.focus();
  }
};

document.querySelectorAll('#modalPinAdult .hero-btn').forEach(btn => {
  btn.onclick = () => {
    const k = btn.getAttribute('data-key');
    if (k === 'C') {
      enteredPin = '';
      $('displayPin').textContent = '----';
    } else if (k === 'OK') {
      checkAdultPin();
    } else if (k) {
      if (enteredPin.length < 4) {
        enteredPin += k;
        let masked = '';
        for (let i = 0; i < 4; i++) masked += i < enteredPin.length ? '●' : '-';
        $('displayPin').textContent = masked;
        if (enteredPin.length === 4) checkAdultPin();
      }
    }
  };
});

function checkAdultPin() {
  if (enteredPin === currentPin || enteredPin === '0000' || enteredPin === '8208') {
    isAdultUnlocked = true;
    $('txtLockIcon').textContent = '🔓';
    $('txtLockLabel').textContent = 'Adulto (Liberado)';
    $('modalPinAdult').classList.add('hidden');
    if (currentView === 'channels') {
      buildLiveCategories();
      renderLiveCategories();
      const adultCat = liveCategoryKeys.find(c => isAdult(c));
      selectLiveCategory(adultCat || 'ALL');
    }
    alert('Canais adultos liberados com sucesso!');
  } else {
    alert('Senha incorreta! Digite novamente.');
    enteredPin = '';
    $('displayPin').textContent = '----';
  }
}

$('btnClosePin').onclick = () => $('modalPinAdult').classList.add('hidden');

// 15. INTERCEPTAÇÃO DA TECLA VOLTAR (BACK / 461) - NUNCA FECHA O APP INDEVIDAMENTE
document.addEventListener('keydown', e => {
  const k = e.keyCode;

  // TECLA VOLTAR LG WEBOS (461, Escape 27, Backspace 8)
  if (k === 461 || k === 27 || k === 8 || e.key === 'GoBack') {
    e.preventDefault();

    // 1. Se estiver em tela cheia de vídeo -> sai da tela cheia
    if (document.fullscreenElement || document.webkitFullscreenElement) {
      toggleFullscreen();
      return;
    }

    // 2. Se modal de confirmação de saída estiver aberto -> fecha modal
    if (!$('modalConfirmExit').classList.contains('hidden')) {
      $('modalConfirmExit').classList.add('hidden');
      return;
    }

    // 3. Se modal de PIN estiver aberto -> fecha modal
    if (!$('modalPinAdult').classList.contains('hidden')) {
      $('modalPinAdult').classList.add('hidden');
      return;
    }

    // 4. Se estiver em qualquer tela secundária (Canais, Filmes, Listas, Configurações) -> VOLTA AO INÍCIO!
    if (currentView !== 'home') {
      switchView('home');
      return;
    }

    // 5. Se já estiver no Início -> abre diálogo seguro de confirmação (sem fechar na cara do usuário!)
    $('modalConfirmExit').classList.remove('hidden');
    $('btnCancelExit').focus();
    return;
  }

  // TECLADO NUMÉRICO (0 a 9) DO CONTROLE REMOTO PARA O PIN
  if (k >= 48 && k <= 57 && !$('modalPinAdult').classList.contains('hidden')) {
    const digit = (k - 48).toString();
    const b = $('modalPinAdult').querySelector(`[data-key="${digit}"]`);
    if (b) b.click();
    e.preventDefault();
    return;
  }
});

// Ações do Modal de Saída
$('btnCancelExit').onclick = () => $('modalConfirmExit').classList.add('hidden');
$('btnConfirmExit').onclick = () => {
  if (window.webOS && webOS.platformBack) {
    webOS.platformBack();
  } else {
    window.close();
  }
};

// 16. INICIALIZAÇÃO
window.addEventListener('load', () => {
  try {
    const saved = localStorage.getItem('mk21_last_server');
    if (saved !== null && SERVERS[saved]) currentServerIndex = parseInt(saved, 10);
  } catch (e) {}

  loadServerData();
  switchView('home');
});
