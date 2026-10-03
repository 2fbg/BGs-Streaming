// MK21 PLAY v3.1.0 — Motor Unificado com Classificação Oficial do APK Android
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
const DB_NAME = 'MK21_PLAY_V31_DB';
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
let currentContentType = 'LIVE'; // 'LIVE', 'MOVIE', 'SERIES', 'FAVORITES', 'SETTINGS'
let allCatalog = { LIVE: [], MOVIE: [], SERIES: [] };
let favoriteUrls = new Set();
let isAdultUnlocked = false;
let currentPin = '0000';
let enteredPin = '';
let activeItem = null;
let hlsInstance = null;

// Categorias e Itens da Aba Atual
let currentCategoriesMap = {};
let currentCategoryKeys = [];
let activeCategoryKey = 'ALL';
let currentFilteredItems = [];

// Gerenciador de Navegação do Controle Remoto LG
let activeZone = 'channels'; // 'header', 'categories', 'channels', 'player', 'settings', 'modal'
let focusedHeaderIdx = 0;
let focusedCatIdx = 0;
let focusedItemIdx = 0;

// Lista de elementos focáveis do cabeçalho
const headerElements = [
  'tabLive', 'tabMovies', 'tabSeries', 'tabFavs', 'tabSettings',
  'btnHeaderServer', 'btnRefreshList', 'btnHeaderAdult'
];

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
  if (currentContentType === 'FAVORITES') renderItemsList();
}

function isAdult(text) {
  if (!text) return false;
  const upper = text.toUpperCase();
  for (let i = 0; i < ADULT_KEYWORDS.length; i++) {
    if (upper.includes(ADULT_KEYWORDS[i])) return true;
  }
  return false;
}

// 4. LÓGICA EXATA DO APK ANDROID (determineType do M3UParser.kt)
function determineType(name, category, url) {
  const uppercaseName = name.toUpperCase();
  const uppercaseCategory = category.toUpperCase();
  const uppercaseUrl = url.toUpperCase();

  // Verificação explícita pela URL (padrão Xtream Codes)
  if (uppercaseUrl.includes("/SERIES/")) return "SERIES";
  if (uppercaseUrl.includes("/MOVIE/")) return "MOVIE";
  if (uppercaseUrl.includes("/LIVE/")) return "LIVE";

  // Palavras-chave explícitas de séries
  const seriesCategories = [
    "SERIES", "SÉRIES", "SERIADOS", "SEASON", "TEMPORADA", "EPISODIOS", "EPISÓDIOS",
    "ANIME", "ANIMES", "NOVELAS", "NOVELA"
  ];

  // Palavras-chave explícitas de filmes
  const movieCategories = [
    "FILMES", "MOVIES", "VOD", "CINEMA", "BLOCKBUSTER", "LANCAMENTOS", "LANÇAMENTOS",
    "PREMIUM FILMES", "CINE", "ACTION", "COMEDY", "DRAMA", "HORROR", "TERROR"
  ];

  // Padrões de séries no nome da categoria ou título do stream
  if (seriesCategories.some(k => uppercaseCategory.includes(k)) ||
      uppercaseName.includes("S0") || uppercaseName.includes("E0") ||
      uppercaseName.includes("TEMPORADA") || uppercaseName.includes("CAPITULO") || uppercaseName.includes("EPISODIO")) {
    return "SERIES";
  }

  // Padrões de filmes na categoria
  if (movieCategories.some(k => uppercaseCategory.includes(k))) {
    return "MOVIE";
  }

  // Fallbacks baseados na extensão da URL
  if (uppercaseUrl.endsWith(".MP4") || uppercaseUrl.endsWith(".MKV") || uppercaseUrl.endsWith(".AVI")) {
    if (uppercaseUrl.includes("/SERIES/") || uppercaseUrl.includes("/EPISODES/") || uppercaseName.includes("S0") || uppercaseName.includes("E0")) {
      return "SERIES";
    } else {
      return "MOVIE";
    }
  }

  // Padrões de canais ao vivo (geralmente .m3u8, .ts ou /live/)
  if (uppercaseUrl.includes("/LIVE/") || uppercaseUrl.endsWith(".M3U8") || uppercaseUrl.endsWith(".TS") || uppercaseUrl.includes(".TS?")) {
    return "LIVE";
  }

  return "LIVE"; // Padrão é Ao Vivo
}

// 5. PARSER OTIMIZADO COM LÓGICA DO APK E PROTEÇÃO DE MEMÓRIA (ANTI-CRASH)
function parseM3UWithApkLogic(content) {
  const lines = content.split(/\r?\n/);
  const live = [];
  const movies = [];
  const series = [];

  let name = 'Canal';
  let group = 'Geral';
  let logo = '';

  // Limites seguros de títulos VOD em memória para evitar que o webOS feche o app
  const MAX_MOVIES = 1500;
  const MAX_SERIES = 600;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (line.startsWith('#EXTINF:')) {
      const commaIndex = line.lastIndexOf(',');
      name = commaIndex >= 0 ? line.slice(commaIndex + 1).trim() : 'Canal';

      const gMatch = line.match(/group-title="([^"]*)"/i);
      group = gMatch && gMatch[1] && gMatch[1].trim() ? gMatch[1].trim() : 'Geral';

      const lMatch = line.match(/tvg-logo="([^"]*)"/i);
      logo = lMatch && lMatch[1] ? lMatch[1].trim() : '';
    } else if (/^https?:\/\//i.test(line)) {
      const isAdultContent = isAdult(name) || isAdult(group);
      const contentType = determineType(name, group, line);

      const item = {
        name,
        group,
        logo,
        url: line,
        contentType,
        isAdult: isAdultContent
      };

      if (contentType === 'SERIES') {
        if (series.length < MAX_SERIES) series.push(item);
      } else if (contentType === 'MOVIE') {
        if (movies.length < MAX_MOVIES) movies.push(item);
      } else {
        live.push(item);
      }

      name = 'Canal';
      group = 'Geral';
      logo = '';
    }
  }

  return { LIVE: live, MOVIE: movies, SERIES: series };
}

// 6. CARREGAMENTO DOS SERVIDORES
async function loadServer(forceRefresh = false) {
  const srv = SERVERS[currentServerIndex];
  $('txtActiveServer').textContent = srv.name;

  if (!forceRefresh) {
    const cached = await getStoredData(srv.id);
    if (cached && cached.LIVE && cached.LIVE.length > 0) {
      allCatalog = cached;
      buildCurrentCategories();
      renderCategoriesList();
      selectCategory('ALL');
      return;
    }
  }

  $('txtCurrentCategoryTitle').textContent = 'Conectando ao ' + srv.name + '...';

  try {
    const res = await fetch(srv.url);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const text = await res.text();
    allCatalog = parseM3UWithApkLogic(text);

    saveStoredData(srv.id, allCatalog);
    buildCurrentCategories();
    renderCategoriesList();
    selectCategory('ALL');
  } catch (err) {
    console.error('Server error:', err);
    alert('Erro ao carregar ' + srv.name + ': ' + err.message);
  }
}

// 7. AGRUPAMENTO DE CATEGORIAS DA ABA ATIVA (LIVE, MOVIE OU SERIES)
function buildCurrentCategories() {
  currentCategoriesMap = { 'ALL': [] };
  currentCategoryKeys = ['ALL'];

  let sourceItems = [];
  if (currentContentType === 'FAVORITES') {
    sourceItems = [...allCatalog.LIVE, ...allCatalog.MOVIE, ...allCatalog.SERIES].filter(i => favoriteUrls.has(i.url));
  } else {
    sourceItems = allCatalog[currentContentType] || [];
  }

  for (let i = 0; i < sourceItems.length; i++) {
    const item = sourceItems[i];
    if (!isAdultUnlocked && item.isAdult) continue;

    currentCategoriesMap['ALL'].push(item);
    const grp = item.group;
    if (!currentCategoriesMap[grp]) {
      currentCategoriesMap[grp] = [];
      currentCategoryKeys.push(grp);
    }
    currentCategoriesMap[grp].push(item);
  }
}

function renderCategoriesList() {
  const ul = $('listCategories');
  ul.innerHTML = '';
  const fragment = document.createDocumentFragment();

  $('badgeCatCount').textContent = (currentCategoryKeys.length - 1) + ' grupos';
  $('txtCatHeaderTitle').textContent = currentContentType === 'LIVE' ? '📁 Categorias TV' : (currentContentType === 'MOVIE' ? '📁 Gêneros Filmes' : '📁 Gêneros Séries');

  currentCategoryKeys.forEach((k, idx) => {
    const li = document.createElement('li');
    const btn = document.createElement('button');
    btn.className = 'list-item-btn cat-item-btn' + (k === activeCategoryKey ? ' active' : '');
    btn.setAttribute('tabindex', '0');
    btn.setAttribute('data-idx', idx);

    const spanName = document.createElement('span');
    spanName.textContent = k === 'ALL' ? (currentContentType === 'LIVE' ? '🌟 Todos os Canais' : (currentContentType === 'MOVIE' ? '🌟 Todos os Filmes' : '🌟 Todas as Séries')) : k;

    const spanCount = document.createElement('span');
    spanCount.className = 'counter-badge';
    spanCount.textContent = currentCategoriesMap[k] ? currentCategoriesMap[k].length : 0;

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

  const btns = $('listCategories').querySelectorAll('.cat-item-btn');
  btns.forEach(b => b.classList.remove('active'));
  const activeBtn = Array.from(btns).find(b => (catKey === 'ALL' && b.textContent.includes('Todos')) || b.textContent.startsWith(catKey));
  if (activeBtn) activeBtn.classList.add('active');

  $('txtCurrentCategoryTitle').textContent = catKey === 'ALL' ? (currentContentType === 'LIVE' ? '📺 Todos os Canais' : (currentContentType === 'MOVIE' ? '🎬 Todos os Filmes' : '🍿 Todas as Séries')) : '📁 ' + catKey;
  $('inputSearch').value = '';
  renderItemsList();
}

// 8. RENDERIZAÇÃO DA LISTA DE ITENS
function renderItemsList() {
  const query = $('inputSearch').value.toLowerCase().trim();
  const base = currentCategoriesMap[activeCategoryKey] || [];

  currentFilteredItems = query ? base.filter(c => c.name.toLowerCase().includes(query)) : base;
  $('badgeItemsCount').textContent = currentFilteredItems.length;

  const ul = $('listItems');
  ul.innerHTML = '';

  if (currentFilteredItems.length === 0) {
    ul.innerHTML = '<li><button class="list-item-btn" disabled>Nenhum item nesta categoria.</button></li>';
    return;
  }

  const fragment = document.createDocumentFragment();
  const limit = Math.min(currentFilteredItems.length, 60);

  for (let i = 0; i < limit; i++) {
    const item = currentFilteredItems[i];
    const li = document.createElement('li');
    const btn = document.createElement('button');
    btn.className = 'list-item-btn' + (activeItem && activeItem.url === item.url ? ' active' : '');
    btn.setAttribute('tabindex', '0');
    btn.setAttribute('data-idx', i);

    const spanTitle = document.createElement('span');
    spanTitle.textContent = (favoriteUrls.has(item.url) ? '★ ' : '') + item.name;
    btn.appendChild(spanTitle);

    if (item.isAdult) {
      const tag = document.createElement('span');
      tag.className = 'counter-badge';
      tag.textContent = '+18';
      btn.appendChild(tag);
    }

    btn.onclick = () => playStream(item);

    // Duplo clique rápido: tela cheia
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

  // Auto-play no primeiro canal se for TV e nada estiver tocando
  if (!activeItem && currentContentType === 'LIVE' && currentFilteredItems.length > 0) {
    playStream(currentFilteredItems[0]);
  }
}

$('inputSearch').addEventListener('input', renderItemsList);

// 9. PLAYER DE VÍDEO & EPG
function playStream(item) {
  activeItem = item;

  const btns = $('listItems').querySelectorAll('.list-item-btn');
  btns.forEach(b => b.classList.remove('active'));

  $('epgTitle').textContent = '▶ ' + item.name;
  $('epgGroup').textContent = 'Categoria: ' + item.group;

  if (favoriteUrls.has(item.url)) {
    $('btnFavorite').textContent = '★ Favoritado';
  } else {
    $('btnFavorite').textContent = '⭐ Favoritar';
  }

  const overlay = $('channelOverlay');
  overlay.textContent = item.name;
  overlay.classList.remove('hidden');
  clearTimeout(overlay._timer);
  overlay._timer = setTimeout(() => overlay.classList.add('hidden'), 3500);

  const video = $('tvPlayer');
  if (hlsInstance) {
    hlsInstance.destroy();
    hlsInstance = null;
  }

  const isHls = item.url.toLowerCase().includes('.m3u8') || item.url.includes('/live/');

  if (isHls && window.Hls && Hls.isSupported()) {
    hlsInstance = new Hls({ enableWorker: true, lowLatencyMode: true, maxBufferLength: 15 });
    hlsInstance.loadSource(item.url);
    hlsInstance.attachMedia(video);
    hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => {
      video.play().catch(() => {});
      $('btnPlayPause').textContent = '⏸ Pausar';
    });
    hlsInstance.on(Hls.Events.ERROR, (e, data) => {
      if (data.fatal) {
        video.src = item.url;
        video.play().catch(() => {});
      }
    });
  } else {
    video.src = item.url;
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
  if (activeItem) toggleFav(activeItem.url);
};

// 10. TROCA DE ABAS DO TOPO (LIVE, FILMES, SÉRIES, FAVORITOS, CONFIGURAÇÕES)
$('tabLive').onclick = () => switchContentType('LIVE');
$('tabMovies').onclick = () => switchContentType('MOVIE');
$('tabSeries').onclick = () => switchContentType('SERIES');
$('tabFavs').onclick = () => switchContentType('FAVORITES');
$('tabSettings').onclick = () => switchContentType('SETTINGS');

function switchContentType(type) {
  currentContentType = type;

  // Atualizar botões de abas
  ['tabLive', 'tabMovies', 'tabSeries', 'tabFavs', 'tabSettings'].forEach(t => $(t).classList.remove('active'));
  const activeTabId = type === 'LIVE' ? 'tabLive' : (type === 'MOVIE' ? 'tabMovies' : (type === 'SERIES' ? 'tabSeries' : (type === 'FAVORITES' ? 'tabFavs' : 'tabSettings')));
  $(activeTabId).classList.add('active');

  if (type === 'SETTINGS') {
    $('sectionUnified').classList.add('hidden');
    $('sectionSettings').classList.remove('hidden');
    renderSettings('info');
    activeZone = 'settings';
    return;
  }

  $('sectionSettings').classList.add('hidden');
  $('sectionUnified').classList.remove('hidden');

  buildCurrentCategories();
  renderCategoriesList();
  selectCategory('ALL');
  activeZone = 'channels';
}

// 11. TELA DE CONFIGURAÇÕES (CLONE VIZZION PLAY)
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
          <div style="font-size:18px; font-weight:700; color:#fff;">3.1.0 (webOS 1080p)</div>
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

// 12. NAVEGAÇÃO ESPACIAL D-PAD COMPLETA (INCLUINDO CABEÇALHO)
document.addEventListener('keydown', e => {
  const k = e.keyCode;

  // BOTÕES COLORIDOS DA TV LG
  if (k === 403 || e.key === 'Red') { e.preventDefault(); openServerPicker(); return; }
  if (k === 404 || e.key === 'Green') { e.preventDefault(); loadServer(true); return; }
  if (k === 405 || e.key === 'Yellow') { e.preventDefault(); if (activeItem) toggleFav(activeItem.url); return; }
  if (k === 406 || e.key === 'Blue') { e.preventDefault(); openPinModal(); return; }

  // BOTÃO VOLTAR (461 / 27 / 8)
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

    if (currentContentType !== 'LIVE') {
      switchContentType('LIVE');
      return;
    }

    if (activeZone === 'player') {
      activeZone = 'channels';
      focusActiveElement();
      return;
    }

    if (activeZone === 'channels') {
      activeZone = 'categories';
      focusActiveElement();
      return;
    }

    // Se estiver nas categorias do Live TV -> diálogo de saída
    $('modalExitConfirm').classList.remove('hidden');
    $('btnExitCancel').focus();
    return;
  }

  // ================= 1. SE O FOCO ESTIVER NO CABEÇALHO =================
  if (activeZone === 'header') {
    if (k === 39) { // Seta Direita no cabeçalho
      e.preventDefault();
      if (focusedHeaderIdx < headerElements.length - 1) {
        focusedHeaderIdx++;
        $(headerElements[focusedHeaderIdx]).focus();
      }
      return;
    }
    if (k === 37) { // Seta Esquerda no cabeçalho
      e.preventDefault();
      if (focusedHeaderIdx > 0) {
        focusedHeaderIdx--;
        $(headerElements[focusedHeaderIdx]).focus();
      }
      return;
    }
    if (k === 40) { // Seta Baixo no cabeçalho -> desce para as colunas!
      e.preventDefault();
      if (currentContentType === 'SETTINGS') {
        activeZone = 'settings';
        $('cfgBtnInfo').focus();
      } else {
        // Se estava nas primeiras abas, desce para Categorias; senão desce para Canais
        if (focusedHeaderIdx < 2) {
          activeZone = 'categories';
        } else {
          activeZone = 'channels';
        }
        focusActiveElement();
      }
      return;
    }
    if (k === 13) { // OK no cabeçalho
      e.preventDefault();
      $(headerElements[focusedHeaderIdx]).click();
      return;
    }
    return;
  }

  // ================= 2. SETA PARA CIMA (38) =================
  if (k === 38) {
    e.preventDefault();
    if (activeZone === 'categories') {
      if (focusedCatIdx > 0) {
        focusedCatIdx--;
        focusActiveElement();
      } else {
        // Chegou ao topo das categorias: sobe para o CABEÇALHO!
        activeZone = 'header';
        focusedHeaderIdx = 0;
        $(headerElements[0]).focus();
      }
    } else if (activeZone === 'channels') {
      if (focusedItemIdx > 0) {
        focusedItemIdx--;
        focusActiveElement();
      } else {
        // Chegou ao topo dos canais: sobe para o campo de busca ou cabeçalho!
        activeZone = 'header';
        focusedHeaderIdx = 1;
        $(headerElements[1]).focus();
      }
    }
    return;
  }

  // ================= 3. SETA PARA BAIXO (40) =================
  if (k === 40) {
    e.preventDefault();
    if (activeZone === 'categories') {
      const items = $('listCategories').querySelectorAll('.cat-item-btn');
      if (focusedCatIdx < items.length - 1) {
        focusedCatIdx++;
        focusActiveElement();
      }
    } else if (activeZone === 'channels') {
      const items = $('listItems').querySelectorAll('.list-item-btn');
      if (focusedItemIdx < items.length - 1) {
        focusedItemIdx++;
        focusActiveElement();
      }
    }
    return;
  }

  // ================= 4. SETA PARA DIREITA (39) =================
  if (k === 39) {
    e.preventDefault();
    if (activeZone === 'categories') {
      activeZone = 'channels';
      focusActiveElement();
    } else if (activeZone === 'channels') {
      activeZone = 'player';
      $('btnFullscreen').focus();
    }
    return;
  }

  // ================= 5. SETA PARA ESQUERDA (37) =================
  if (k === 37) {
    e.preventDefault();
    if (activeZone === 'player') {
      activeZone = 'channels';
      focusActiveElement();
    } else if (activeZone === 'channels') {
      activeZone = 'categories';
      focusActiveElement();
    }
    return;
  }

  // ================= 6. TECLA OK / ENTER (13) =================
  if (k === 13) {
    if (activeZone === 'categories') {
      const items = $('listCategories').querySelectorAll('.cat-item-btn');
      if (items[focusedCatIdx]) items[focusedCatIdx].click();
    } else if (activeZone === 'channels') {
      const items = $('listItems').querySelectorAll('.list-item-btn');
      const item = items[focusedItemIdx];
      if (item) {
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

function focusActiveElement() {
  if (activeZone === 'categories') {
    const items = $('listCategories').querySelectorAll('.cat-item-btn');
    if (items[focusedCatIdx]) {
      items[focusedCatIdx].focus();
      items[focusedCatIdx].scrollIntoView({ block: 'nearest' });
    }
  } else if (activeZone === 'channels') {
    const items = $('listItems').querySelectorAll('.list-item-btn');
    if (items[focusedItemIdx]) {
      items[focusedItemIdx].focus();
      items[focusedItemIdx].scrollIntoView({ block: 'nearest' });
    }
  }
}

// 13. MODAIS E BOTÕES DO CABEÇALHO
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
    buildCurrentCategories();
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
    buildCurrentCategories();
    renderCategoriesList();
    const adultCat = currentCategoryKeys.find(c => isAdult(c));
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

// 14. INICIALIZAÇÃO AUTOMÁTICA
window.addEventListener('load', () => {
  try {
    const saved = localStorage.getItem('mk21_last_server');
    if (saved !== null && SERVERS[saved]) currentServerIndex = parseInt(saved, 10);
  } catch (e) {}

  loadServer();
});
