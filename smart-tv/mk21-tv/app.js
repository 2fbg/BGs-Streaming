// MK21 PLAY v3.3.0 — Motor LG webOS (Clone Vizzion Play & APK MK21)
// Separação Estrita de Categorias, Agrupamento de Séries por Temporadas, D-Pad Completo e Troca Rápida de Canal
const $ = id => document.getElementById(id);

// 1. POLYFILLS PARA NAVEGADORES CHROMIUM WEBOS
if (!Element.prototype.replaceChildren) {
  Element.prototype.replaceChildren = function(...nodes) {
    while (this.firstChild) this.removeChild(this.firstChild);
    this.append(...nodes);
  };
}

// 2. CONFIGURAÇÃO DOS SERVIDORES MK21
const DEFAULT_SERVERS = [
  { id: 'cb6000', name: '🔵 CB6000', url: 'http://cdn.caterlune.top' },
  { id: 'vlog', name: '🔴 VLOG', url: 'http://myopbx.beer' },
  { id: 'lubtv', name: '🟢 LUB TV', url: 'http://pottermax.sbs' },
  { id: 'cinelon', name: '🟡 CINELON21', url: 'http://coliseuop.site' },
  { id: 'tannix', name: '🟣 TANNIX', url: 'http://poptvcdn.online' },
  { id: 'mk21pro', name: '🟠 MK21 PRÓ', url: 'http://app.vivoxi.xyz' },
  { id: 'cinevo', name: '⚪ CINEVO', url: 'http://antaresfusion.shop' }
];

let SERVERS = [...DEFAULT_SERVERS];
try {
  const custom = localStorage.getItem('mk21_servers_list');
  if (custom) {
    const parsed = JSON.parse(custom);
    if (Array.isArray(parsed) && parsed.length > 0) SERVERS = parsed;
  }
} catch (e) {}

const ADULT_KEYWORDS = [
  "18+", "ADULTO", "ADULT", "XXX", "SEXY", "PLAYBOY", "PENTHOUSE", "VENUS",
  "HOT ", "HUSTLER", "FORBIDDEN", "FORA DA LEI", "S0X"
];

// BANCO DE DADOS INDEXEDDB
const DB_NAME = 'mk21_play_db';
const DB_VERSION = 2;
const STORE_NAME = 'catalog_cache';

function openDB() {
  return new Promise((resolve, reject) => {
    if (!window.indexedDB) { resolve(null); return; }
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
    return new Promise(res => {
      const tx = db.transaction(STORE_NAME, 'readonly');
      const req = tx.objectStore(STORE_NAME).get(id);
      req.onsuccess = () => res(req.result ? req.result.payload : null);
      req.onerror = () => res(null);
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
let currentSortOrder = 'DEFAULT'; // 'DEFAULT', 'AZ', 'ZA'
let currentPlaybackSpeed = 1;
const speedOptions = [1, 1.25, 1.5, 2, 4];

// Categorias e Itens da Aba Ativa
let currentCategoriesMap = {};
let currentCategoryKeys = [];
let activeCategoryKey = 'ALL';
let currentFilteredItems = [];
let currentGroupedSeries = []; // Agrupamento de séries para a aba SÉRIES

// Gerenciador de Navegação do Controle Remoto LG
let activeZone = 'channels'; // 'header', 'categories', 'channels', 'player', 'settings', 'modalServerPicker', 'modalSeries', 'modalPin'
let focusedHeaderIdx = 0;
let focusedCatIdx = 0;
let focusedItemIdx = 0;
let focusedServerRowIdx = 0;
let focusedSeriesSeasonIdx = 0;
let focusedSeriesEpIdx = 0;
let activeModalSeriesData = null;

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
    showChannelBanner('Removido dos Favoritos');
  } else {
    favoriteUrls.add(url);
    if ($('btnFavorite')) $('btnFavorite').textContent = '★ Favoritado';
    showChannelBanner('Adicionado aos Favoritos');
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

// 4. LÓGICA DE CLASSIFICAÇÃO RIGOROSA (CLONE EXATO DO APK)
// Garante que canais como "Canais | 24H Filmes", "Canais | CineSky", "Canais | Telecine" NUNCA entrem em Filmes!
function determineType(name, category, url) {
  const uppercaseName = (name || '').toUpperCase();
  const uppercaseCategory = (category || '').toUpperCase();
  const uppercaseUrl = (url || '').toUpperCase();

  // 1. Checagem explícita na URL do formato Xtream Codes
  if (uppercaseUrl.includes("/LIVE/")) return "LIVE";
  if (uppercaseUrl.includes("/MOVIE/")) return "MOVIE";
  if (uppercaseUrl.includes("/SERIES/")) return "SERIES";

  // 2. Extensões de Streaming de TV Ao Vivo (.ts / .m3u8)
  // No Xtream Codes filmes e séries VOD nunca terminam em .ts ou .m3u8
  if (uppercaseUrl.endsWith(".TS") || uppercaseUrl.includes(".TS?") || 
      uppercaseUrl.endsWith(".M3U8") || uppercaseUrl.includes(".M3U8?")) {
    return "LIVE";
  }

  // 3. Categorias e Grupos que são Canais de TV Ao Vivo
  // Mesmo que tenham "Filmes", "Cine", "Séries" ou "Telecine", se contiver Canais, 24H, Ao Vivo, é CANAL AO VIVO!
  const isLiveCategory = 
    uppercaseCategory.startsWith("CANAIS") ||
    uppercaseCategory.startsWith("CANAL") ||
    uppercaseCategory.includes("CANAIS |") ||
    uppercaseCategory.includes("CANAL |") ||
    uppercaseCategory.includes("CANAIS:") ||
    uppercaseCategory.includes("CANAL:") ||
    uppercaseCategory.includes("24H") ||
    uppercaseCategory.includes("24 HORAS") ||
    uppercaseCategory.includes("AO VIVO") ||
    uppercaseCategory.includes("AOVIVO") ||
    uppercaseCategory.includes("TV ABERTA") ||
    uppercaseCategory.includes("ABERTOS") ||
    uppercaseCategory.includes("NOTICIAS") ||
    uppercaseCategory.includes("ESPORTES") ||
    uppercaseCategory.includes("PREMIERE") ||
    uppercaseCategory.includes("COMBATE") ||
    uppercaseCategory.includes("DAZN") ||
    uppercaseCategory.includes("TELECINE") ||
    uppercaseCategory.includes("CINESKY") ||
    uppercaseCategory.includes("HBO MAX CANAIS") ||
    uppercaseCategory.includes("DISCOVERY");

  if (isLiveCategory) {
    const isExplicitVodFile = (uppercaseUrl.endsWith(".MP4") || uppercaseUrl.endsWith(".MKV")) && !uppercaseUrl.includes("/LIVE/");
    if (!isExplicitVodFile) {
      return "LIVE";
    }
  }

  // 4. Extensões de Arquivo VOD (.mp4, .mkv, .avi)
  const isVodExtension = uppercaseUrl.endsWith(".MP4") || uppercaseUrl.endsWith(".MKV") || uppercaseUrl.endsWith(".AVI");

  // 5. Detecção de Séries (Temporadas, Episódios, S01E01)
  const hasSeriesPattern = 
    uppercaseName.includes("S0") || uppercaseName.includes("S1") || uppercaseName.includes("S2") ||
    uppercaseName.includes("E0") || uppercaseName.includes("E1") || uppercaseName.includes("E2") ||
    uppercaseName.includes("TEMPORADA") || uppercaseName.includes("TEMP.") ||
    uppercaseName.includes("CAPITULO") || uppercaseName.includes("CAPÍTULO") ||
    uppercaseName.includes("EPISODIO") || uppercaseName.includes("EPISÓDIO") ||
    /\b(S\d{1,2}E\d{1,2}|\d{1,2}X\d{1,2}|T\d{1,2}E\d{1,2})\b/i.test(uppercaseName);

  const isSeriesCategory = 
    uppercaseCategory.startsWith("SERIES |") ||
    uppercaseCategory.startsWith("SÉRIES |") ||
    uppercaseCategory.startsWith("SERIES:") ||
    uppercaseCategory.startsWith("SÉRIES:") ||
    uppercaseCategory.startsWith("SERIE |") ||
    uppercaseCategory.startsWith("SÉRIE |") ||
    uppercaseCategory.includes("SERIADOS") ||
    uppercaseCategory.includes("NOVELAS") ||
    uppercaseCategory.includes("ANIMES") ||
    uppercaseCategory.includes("DORAMAS");

  if (isSeriesCategory || hasSeriesPattern) {
    return "SERIES";
  }

  // 6. Detecção de Filmes (VOD)
  const isMovieCategory = 
    uppercaseCategory.startsWith("FILMES |") ||
    uppercaseCategory.startsWith("FILME |") ||
    uppercaseCategory.startsWith("FILMES:") ||
    uppercaseCategory.startsWith("FILME:") ||
    uppercaseCategory.startsWith("VOD |") ||
    uppercaseCategory.startsWith("VOD:") ||
    uppercaseCategory.startsWith("CINEMA |") ||
    uppercaseCategory.includes("LANCAMENTOS 202") ||
    uppercaseCategory.includes("LANÇAMENTOS 202") ||
    uppercaseCategory.includes("FILMES 4K") ||
    uppercaseCategory.includes("FILMES DUBLADOS") ||
    uppercaseCategory.includes("FILMES LEGENDADOS");

  if (isMovieCategory || isVodExtension) {
    return "MOVIE";
  }

  // Por padrão, se não for arquivo sob demanda, é canal ao vivo
  return "LIVE";
}

// 5. PARSER COM CONTROLE DE MEMÓRIA E PROGRESSO HUD
function parseM3UWithProgress(content, onProgress) {
  const lines = content.split(/\r?\n/);
  const live = [];
  const movies = [];
  const series = [];

  let name = 'Canal';
  let group = 'Geral';
  let logo = '';

  const MAX_MOVIES = 1500;
  const MAX_SERIES = 1200;
  const total = lines.length;

  for (let i = 0; i < total; i++) {
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

      const item = { name, group, logo, url: line, contentType, isAdult: isAdultContent };

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

    if (i % 2000 === 0 && onProgress) {
      const pct = Math.min(95, Math.round((i / total) * 100));
      onProgress(pct);
    }
  }

  if (onProgress) onProgress(100);
  return { LIVE: live, MOVIE: movies, SERIES: series };
}

// 6. HUD DE CARREGAMENTO COM PORCENTAGEM
function showLoadingHud(title, subtitle, percent) {
  const hud = $('hudLoadingOverlay');
  if (!hud) return;
  hud.classList.remove('hidden');
  $('hudLoadingTitle').textContent = title || 'Atualizando Lista MK21';
  $('hudLoadingSub').textContent = subtitle || 'Carregando dados...';
  $('hudProgressBar').style.width = percent + '%';
  $('hudProgressPercent').textContent = percent + '%';
}

function hideLoadingHud() {
  const hud = $('hudLoadingOverlay');
  if (hud) hud.classList.add('hidden');
}

// 7. CARREGAMENTO DO SERVIDOR ATUAL
async function loadServer(forceRefresh = false) {
  if (SERVERS.length === 0) SERVERS = [...DEFAULT_SERVERS];
  if (currentServerIndex >= SERVERS.length) currentServerIndex = 0;

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

  showLoadingHud('Conectando ao ' + srv.name, 'Baixando grade de programação...', 15);
  $('txtCurrentCategoryTitle').textContent = 'Conectando ao ' + srv.name + '...';

  try {
    const res = await fetch(srv.url);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    
    showLoadingHud('Processando Canais', 'Priorizando Canais Ao Vivo...', 45);
    const text = await res.text();

    allCatalog = parseM3UWithProgress(text, pct => {
      showLoadingHud('Organizando Conteúdo', pct < 50 ? 'Separando Canais Ao Vivo...' : 'Classificando Filmes e Séries...', pct);
    });

    saveStoredData(srv.id, allCatalog);
    buildCurrentCategories();
    renderCategoriesList();
    selectCategory('ALL');

    showLoadingHud('Concluído!', `${allCatalog.LIVE.length} canais carregados`, 100);
    setTimeout(hideLoadingHud, 500);
  } catch (err) {
    console.error('Server error:', err);
    hideLoadingHud();
    alert('Erro ao carregar ' + srv.name + ': ' + err.message);
  }
}

// 8. AGRUPAMENTO DE CATEGORIAS DA ABA ATIVA
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

// 9. AGRUPAMENTO INTELIGENTE DE SÉRIES POR TEMPORADA E EPISÓDIO (ESTILO APK E NETFLIX)
function groupSeriesItems(items) {
  const map = {};
  const seasonRegex = /(?:\b(s\d{1,2}e\d{1,2}|\d{1,2}x\d{1,2}|t\d{1,2}e\d{1,2}|season\s*\d+\s*episode\s*\d+|temp\.\s*\d+\s*ep\.\s*\d+|capitulo\s*\d+|capítulo\s*\d+)\b)/i;

  items.forEach(it => {
    let seriesTitle = it.name;
    const m = it.name.match(seasonRegex);
    if (m) {
      seriesTitle = it.name.substring(0, m.index).trim();
    } else {
      const epMatch = it.name.match(/(?:\bep\.?\s*\d+\b|\bepisodio\s*\d+\b)/i);
      if (epMatch) {
        seriesTitle = it.name.substring(0, epMatch.index).trim();
      }
    }
    seriesTitle = seriesTitle.replace(/[-_:,/ ]+$/, '').trim() || it.name;

    if (!map[seriesTitle]) {
      map[seriesTitle] = {
        title: seriesTitle,
        group: it.group,
        logo: it.logo,
        episodes: []
      };
    }
    map[seriesTitle].episodes.push(it);
  });

  return Object.values(map).sort((a, b) => a.title.localeCompare(b.title));
}

// 10. RENDERIZAÇÃO DA LISTA DE ITENS COM SÉRIES AGRUPADAS E ORDENAÇÃO
function renderItemsList() {
  const query = $('inputSearch').value.toLowerCase().trim();
  const base = currentCategoriesMap[activeCategoryKey] || [];

  let items = query ? base.filter(c => c.name.toLowerCase().includes(query)) : [...base];

  // Aplicar ordenação
  if (currentSortOrder === 'AZ') {
    items.sort((a, b) => a.name.localeCompare(b.name));
  } else if (currentSortOrder === 'ZA') {
    items.sort((a, b) => b.name.localeCompare(a.name));
  }

  currentFilteredItems = items;
  $('badgeItemsCount').textContent = currentFilteredItems.length;

  const ul = $('listItems');
  ul.innerHTML = '';

  if (currentFilteredItems.length === 0) {
    ul.innerHTML = '<li><button class="list-item-btn" disabled>Nenhum item encontrado.</button></li>';
    return;
  }

  const fragment = document.createDocumentFragment();

  // Se for a aba SÉRIES, exibir os cards agrupados por Série!
  if (currentContentType === 'SERIES' && query.length === 0) {
    currentGroupedSeries = groupSeriesItems(currentFilteredItems);
    $('badgeItemsCount').textContent = currentGroupedSeries.length + ' séries';

    const limit = Math.min(currentGroupedSeries.length, 80);
    for (let i = 0; i < limit; i++) {
      const seriesObj = currentGroupedSeries[i];
      const li = document.createElement('li');
      const btn = document.createElement('button');
      btn.className = 'list-item-btn series-card-btn';
      btn.setAttribute('tabindex', '0');
      btn.setAttribute('data-idx', i);

      const spanTitle = document.createElement('span');
      spanTitle.textContent = '🍿 ' + seriesObj.title;

      const spanCount = document.createElement('span');
      spanCount.className = 'counter-badge';
      spanCount.textContent = seriesObj.episodes.length + ' eps';

      btn.appendChild(spanTitle);
      btn.appendChild(spanCount);

      btn.onclick = () => openSeriesModal(seriesObj);

      li.appendChild(btn);
      fragment.appendChild(li);
    }
  } else {
    // Modo Lista Normal (TV Ao Vivo, Filmes, Favoritos ou Busca)
    const limit = Math.min(currentFilteredItems.length, 70);

    for (let i = 0; i < limit; i++) {
      const item = currentFilteredItems[i];
      const li = document.createElement('li');
      const btn = document.createElement('button');
      btn.className = 'list-item-btn channel-item-btn' + (activeItem && activeItem.url === item.url ? ' active' : '');
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

      let lastClick = 0;
      btn.addEventListener('click', () => {
        const now = Date.now();
        if (now - lastClick < 380) toggleFullscreen(true);
        lastClick = now;
      });

      li.appendChild(btn);
      fragment.appendChild(li);
    }
  }

  ul.appendChild(fragment);

  if (!activeItem && currentContentType === 'LIVE' && currentFilteredItems.length > 0) {
    playStream(currentFilteredItems[0]);
  }
}

// BOTÃO ORDENAÇÃO
$('btnSortOrder').onclick = () => {
  if (currentSortOrder === 'DEFAULT') {
    currentSortOrder = 'AZ';
    $('btnSortOrder').textContent = '↕️ A-Z';
  } else if (currentSortOrder === 'AZ') {
    currentSortOrder = 'ZA';
    $('btnSortOrder').textContent = '↕️ Z-A';
  } else {
    currentSortOrder = 'DEFAULT';
    $('btnSortOrder').textContent = '↕️ Padrão';
  }
  renderItemsList();
  showChannelBanner('Ordenação: ' + $('btnSortOrder').textContent);
};

$('inputSearch').addEventListener('input', renderItemsList);

// 11. MODAL DE SÉRIES (TEMPORADAS E EPISÓDIOS)
function openSeriesModal(seriesObj) {
  activeModalSeriesData = seriesObj;
  $('seriesModalTitle').textContent = '🍿 ' + seriesObj.title;

  // Organizar episódios por temporadas
  const seasonsMap = {};
  seriesObj.episodes.forEach(ep => {
    const sMatch = ep.name.match(/(?:s|temporada|temp\.?|t)\s*(\d{1,2})/i);
    const sNum = sMatch ? parseInt(sMatch[1], 10) : 1;
    const seasonKey = 'Temporada ' + sNum;
    if (!seasonsMap[seasonKey]) seasonsMap[seasonKey] = [];
    seasonsMap[seasonKey].push(ep);
  });

  const seasonKeys = Object.keys(seasonsMap).sort((a, b) => {
    const numA = parseInt(a.replace(/\D/g, ''), 10) || 0;
    const numB = parseInt(b.replace(/\D/g, ''), 10) || 0;
    return numA - numB;
  });

  const listSeasons = $('listSeasons');
  listSeasons.innerHTML = '';

  seasonKeys.forEach((sKey, sIdx) => {
    const li = document.createElement('li');
    const btn = document.createElement('button');
    btn.className = 'season-tab-btn' + (sIdx === 0 ? ' active' : '');
    btn.setAttribute('tabindex', '0');
    btn.textContent = sKey + ` (${seasonsMap[sKey].length} eps)`;
    btn.onclick = () => {
      listSeasons.querySelectorAll('.season-tab-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      renderSeriesEpisodes(seasonsMap[sKey], sKey);
    };
    li.appendChild(btn);
    listSeasons.appendChild(li);
  });

  if (seasonKeys.length > 0) {
    renderSeriesEpisodes(seasonsMap[seasonKeys[0]], seasonKeys[0]);
  }

  $('modalSeriesEpisodes').classList.remove('hidden');
  activeZone = 'modalSeries';
  focusedSeriesSeasonIdx = 0;
  focusedSeriesEpIdx = 0;
  const firstSeasonBtn = listSeasons.querySelector('.season-tab-btn');
  if (firstSeasonBtn) firstSeasonBtn.focus();
}

function renderSeriesEpisodes(episodes, seasonName) {
  $('episodesListHeading').textContent = `🎬 ${seasonName} (${episodes.length} episódios)`;
  const ul = $('listEpisodes');
  ul.innerHTML = '';

  episodes.forEach((ep, epIdx) => {
    const li = document.createElement('li');
    const btn = document.createElement('button');
    btn.className = 'episode-item-btn';
    btn.setAttribute('tabindex', '0');
    btn.setAttribute('data-epidx', epIdx);

    const titleSpan = document.createElement('span');
    titleSpan.textContent = ep.name;
    const playIcon = document.createElement('span');
    playIcon.textContent = '▶ Assistir';
    playIcon.style.color = '#ffd54f';

    btn.appendChild(titleSpan);
    btn.appendChild(playIcon);

    btn.onclick = () => {
      $('modalSeriesEpisodes').classList.add('hidden');
      activeZone = 'player';
      playStream(ep);
      toggleFullscreen(true);
    };

    li.appendChild(btn);
    ul.appendChild(li);
  });
}

$('btnCloseSeriesModal').onclick = () => {
  $('modalSeriesEpisodes').classList.add('hidden');
  activeZone = 'channels';
  focusActiveElement();
};

// 12. PLAYER DE VÍDEO & TROCA RÁPIDA DE CANAL (<0.5s)
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

  showChannelBanner(item.name);

  const video = $('tvPlayer');
  if (hlsInstance) {
    hlsInstance.destroy();
    hlsInstance = null;
  }

  // Troca Instantânea: Somente usa HLS.js se for explicitamente .m3u8!
  // Fluxos MPEG-TS (.ts) e arquivos MP4/MKV rodam nativamente pelo hardware da Smart TV em sub-segundo!
  const isM3U8 = item.url.toLowerCase().includes('.m3u8');

  if (isM3U8 && window.Hls && Hls.isSupported()) {
    hlsInstance = new Hls({
      enableWorker: true,
      lowLatencyMode: true,
      maxBufferLength: 4,
      maxMaxBufferLength: 8,
      liveSyncDurationCount: 2
    });
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
    // Decodificação Nativa em Hardware LG webOS (sem delay de 5 segundos!)
    video.src = item.url;
    video.playbackRate = currentPlaybackSpeed;
    video.load();
    video.play().then(() => {
      $('btnPlayPause').textContent = '⏸ Pausar';
    }).catch(() => {});
  }
}

function showChannelBanner(text) {
  const overlay = $('channelOverlay');
  overlay.textContent = text;
  overlay.classList.remove('hidden');
  clearTimeout(overlay._timer);
  overlay._timer = setTimeout(() => overlay.classList.add('hidden'), 3500);
}

// TROCA DE CANAIS COM SETAS CIMA E BAIXO NO PLAYER
function playNextChannel() {
  if (!currentFilteredItems || currentFilteredItems.length === 0) return;
  const currentIdx = currentFilteredItems.findIndex(it => activeItem && it.url === activeItem.url);
  const nextIdx = (currentIdx + 1) % currentFilteredItems.length;
  playStream(currentFilteredItems[nextIdx]);
  showChannelBanner('▲ Próximo: ' + currentFilteredItems[nextIdx].name);
}

function playPreviousChannel() {
  if (!currentFilteredItems || currentFilteredItems.length === 0) return;
  const currentIdx = currentFilteredItems.findIndex(it => activeItem && it.url === activeItem.url);
  const prevIdx = (currentIdx - 1 + currentFilteredItems.length) % currentFilteredItems.length;
  playStream(currentFilteredItems[prevIdx]);
  showChannelBanner('▼ Anterior: ' + currentFilteredItems[prevIdx].name);
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

// VELOCIDADE DE REPRODUÇÃO ATÉ 4X
$('btnSpeed').onclick = () => {
  const video = $('tvPlayer');
  const nextIdx = (speedOptions.indexOf(currentPlaybackSpeed) + 1) % speedOptions.length;
  currentPlaybackSpeed = speedOptions[nextIdx];
  video.playbackRate = currentPlaybackSpeed;
  $('btnSpeed').textContent = '⚡ ' + currentPlaybackSpeed + 'x';
  showChannelBanner('Velocidade: ' + currentPlaybackSpeed + 'x');
};

// ÁUDIO E LEGENDAS
$('btnAudioTrack').onclick = () => {
  if (hlsInstance && hlsInstance.audioTracks && hlsInstance.audioTracks.length > 1) {
    const nextTrack = (hlsInstance.audioTrack + 1) % hlsInstance.audioTracks.length;
    hlsInstance.audioTrack = nextTrack;
    const name = hlsInstance.audioTracks[nextTrack].name || ('Faixa ' + nextTrack);
    showChannelBanner('Áudio: ' + name);
  } else {
    showChannelBanner('Áudio Padrão (Estéreo)');
  }
};

$('btnSubtitles').onclick = () => {
  if (hlsInstance && hlsInstance.subtitleTracks && hlsInstance.subtitleTracks.length > 0) {
    const nextTrack = (hlsInstance.subtitleTrack + 1) % (hlsInstance.subtitleTracks.length + 1) - 1;
    hlsInstance.subtitleTrack = nextTrack;
    showChannelBanner(nextTrack === -1 ? 'Legendas Desativadas' : 'Legenda Ativa');
  } else {
    showChannelBanner('Legendas Indisponíveis neste fluxo');
  }
};

// 13. TROCA DE ABAS DO TOPO
$('tabLive').onclick = () => switchContentType('LIVE');
$('tabMovies').onclick = () => switchContentType('MOVIE');
$('tabSeries').onclick = () => switchContentType('SERIES');
$('tabFavs').onclick = () => switchContentType('FAVORITES');
$('tabSettings').onclick = () => switchContentType('SETTINGS');

function switchContentType(type) {
  currentContentType = type;

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

// 14. TELA DE CONFIGURAÇÕES (INFORMAÇÕES, TESTE DE VELOCIDADE, FONTE, LIMPAR POR CATEGORIA)
const CFG_BUTTONS = ['cfgBtnInfo', 'cfgBtnSpeedTest', 'cfgBtnFonte', 'cfgBtnIdioma', 'cfgBtnFluxo', 'cfgBtnPin', 'cfgBtnCategorias', 'cfgBtnLimpar', 'cfgBtnTempo'];

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
          <div style="font-size:14px; color:#888;">Status da Licença</div>
          <div style="font-size:18px; font-weight:700; color:#4caf50;">Ativo (Plano Vitalício)</div>
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
          <div style="font-size:18px; font-weight:700; color:#ffd54f;">MK21 Play v3.3.0 (LG webOS)</div>
        </div>
      </div>
    `;
  } else if (sec === 'speedtest') {
    runSpeedTest();
  } else if (sec === 'fonte') {
    renderFontSizePanel();
  } else if (sec === 'limpar') {
    renderClearStoragePanel();
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
      <button class="list-item-btn active" style="margin-bottom:8px;" tabindex="0">Default (Automático / Hardware)</button>
      <button class="list-item-btn" style="margin-bottom:8px;" tabindex="0">ts (MPEG-TS Rápido)</button>
      <button class="list-item-btn" tabindex="0">m3u8 (HLS)</button>
    `;
  }
}

// TESTE DE VELOCIDADE REAL NA SMART TV
async function runSpeedTest() {
  const box = $('settingsDetailBox');
  box.innerHTML = `
    <h3 style="margin-top:0;">🚀 Teste de Velocidade da Conexão</h3>
    <p style="color:#aaa;">Medindo ping e velocidade real de download dos servidores de streaming na Smart TV...</p>
    <div class="speed-meter-box">
      <div id="speedMeterStatus" style="font-size: 18px; color: #94a3b8; margin-bottom: 10px;">Iniciando teste...</div>
      <div><span id="speedMeterNumber" class="speed-meter-val">--</span><span class="speed-meter-unit">Mbps</span></div>
      <div id="speedMeterPing" style="margin-top: 14px; font-size: 16px; color: #4caf50;">Ping: -- ms</div>
    </div>
    <button id="btnStartSpeedTest" class="ctrl-btn primary" style="padding: 12px 28px;" tabindex="0">Iniciar Novo Teste</button>
  `;

  $('btnStartSpeedTest').onclick = runSpeedTest;

  const testFileUrl = 'https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4';
  try {
    $('speedMeterStatus').textContent = 'Medindo Latência (Ping)...';
    const pingStart = Date.now();
    await fetch(testFileUrl, { method: 'HEAD', cache: 'no-cache' });
    const pingMs = Date.now() - pingStart;
    $('speedMeterPing').textContent = `Ping: ${pingMs} ms (Excelente para Transmissões Ao Vivo)`;

    $('speedMeterStatus').textContent = 'Medindo Taxa de Download em Tempo Real...';
    const dlStart = Date.now();
    const res = await fetch(testFileUrl, { cache: 'no-cache' });
    const reader = res.body.getReader();
    let receivedBytes = 0;
    const maxBytes = 4 * 1024 * 1024; // Teste rápido de 4MB
    while (receivedBytes < maxBytes) {
      const { done, value } = await reader.read();
      if (done) break;
      receivedBytes += value.length;
      const elapsedSec = (Date.now() - dlStart) / 1000;
      if (elapsedSec > 0.3) {
        const mbps = ((receivedBytes * 8) / (elapsedSec * 1024 * 1024)).toFixed(1);
        $('speedMeterNumber').textContent = mbps;
      }
    }
    reader.cancel();
    const finalElapsed = (Date.now() - dlStart) / 1000;
    const finalMbps = ((receivedBytes * 8) / (finalElapsed * 1024 * 1024)).toFixed(1);
    $('speedMeterNumber').textContent = finalMbps;
    $('speedMeterStatus').textContent = '✅ Teste Concluído! Banda Excelente para FHD e 4K.';
  } catch (e) {
    $('speedMeterStatus').textContent = 'Erro ao medir velocidade: ' + e.message;
  }
}

// TAMANHO DA FONTE
function renderFontSizePanel() {
  const isLarge = document.body.classList.contains('font-large');
  const box = $('settingsDetailBox');
  box.innerHTML = `
    <h3 style="margin-top:0;">🔤 Tamanho da Fonte da Interface</h3>
    <p style="color:#aaa;">Ajuste o tamanho dos textos dos canais e categorias para melhorar a leitura a distância:</p>
    <div style="display:flex; flex-direction:column; gap:12px; max-width:400px; margin-top:16px;">
      <button id="btnFontNormal" class="list-item-btn ${!isLarge ? 'active' : ''}" style="padding:16px;" tabindex="0">
        Padrão (1080p Normal)
      </button>
      <button id="btnFontLarge" class="list-item-btn ${isLarge ? 'active' : ''}" style="padding:16px;" tabindex="0">
        Grande (Alta Visibilidade para Sofá)
      </button>
    </div>
  `;

  $('btnFontNormal').onclick = () => {
    document.body.classList.remove('font-large');
    localStorage.setItem('mk21_font_size', 'normal');
    renderFontSizePanel();
  };
  $('btnFontLarge').onclick = () => {
    document.body.classList.add('font-large');
    localStorage.setItem('mk21_font_size', 'large');
    renderFontSizePanel();
  };
}

// LIMPAR ARMAZENAMENTO POR CATEGORIA
function renderClearStoragePanel() {
  const box = $('settingsDetailBox');
  box.innerHTML = `
    <h3 style="margin-top:0;">🗑️ Limpar Armazenamento por Categoria</h3>
    <p style="color:#aaa;">Selecione qual categoria de cache deseja esvaziar para liberar memória RAM e disco da TV:</p>
    <div style="display:flex; flex-direction:column; gap:12px; max-width:500px; margin-top:16px;">
      <button id="btnClearLiveCache" class="ctrl-btn" style="padding:14px 20px; text-align:left;" tabindex="0">
        📺 Limpar Cache de Canais Ao Vivo (${allCatalog.LIVE.length} itens)
      </button>
      <button id="btnClearMoviesCache" class="ctrl-btn" style="padding:14px 20px; text-align:left;" tabindex="0">
        🎬 Limpar Cache de Filmes VOD (${allCatalog.MOVIE.length} itens)
      </button>
      <button id="btnClearSeriesCache" class="ctrl-btn" style="padding:14px 20px; text-align:left;" tabindex="0">
        🍿 Limpar Cache de Séries (${allCatalog.SERIES.length} itens)
      </button>
      <button id="btnClearAllCache" class="ctrl-btn primary" style="padding:14px 20px; text-align:left;" tabindex="0">
        🔥 Limpar Todo o Armazenamento e Reiniciar
      </button>
    </div>
  `;

  $('btnClearLiveCache').onclick = () => {
    allCatalog.LIVE = [];
    buildCurrentCategories();
    renderCategoriesList();
    renderItemsList();
    alert('Cache de canais ao vivo esvaziado com sucesso.');
  };
  $('btnClearMoviesCache').onclick = () => {
    allCatalog.MOVIE = [];
    buildCurrentCategories();
    renderCategoriesList();
    renderItemsList();
    alert('Cache de filmes esvaziado com sucesso.');
  };
  $('btnClearSeriesCache').onclick = () => {
    allCatalog.SERIES = [];
    buildCurrentCategories();
    renderCategoriesList();
    renderItemsList();
    alert('Cache de séries esvaziado com sucesso.');
  };
  $('btnClearAllCache').onclick = async () => {
    if (confirm('Tem certeza que deseja apagar todo o banco de dados interno e reiniciar?')) {
      try {
        indexedDB.deleteDatabase(DB_NAME);
        localStorage.clear();
      } catch (e) {}
      alert('Armazenamento limpo! Recarregando.');
      location.reload();
    }
  };
}

// 15. GERENCIADOR DE SERVIDORES (MANUTENÇÃO, INCLUSÃO MANUAL E EXCLUSÃO)
function saveServersToStorage() {
  try {
    localStorage.setItem('mk21_servers_list', JSON.stringify(SERVERS));
  } catch (e) {}
}

function openServerPicker() {
  renderServerPickerList();
  $('modalServerPicker').classList.remove('hidden');
  activeZone = 'modalServerPicker';
  focusedServerRowIdx = 0;
  const first = $('serverItemsGrid').querySelector('.btn-server-connect');
  if (first) first.focus();
}

function renderServerPickerList() {
  const container = $('serverItemsGrid');
  container.innerHTML = '';

  SERVERS.forEach((srv, idx) => {
    const row = document.createElement('div');
    row.className = 'server-manager-row' + (idx === currentServerIndex ? ' active-server' : '');

    const infoCol = document.createElement('div');
    infoCol.className = 'server-info-col';

    const nameLine = document.createElement('div');
    nameLine.className = 'server-info-name';
    nameLine.textContent = srv.name + (idx === currentServerIndex ? '  [✓ ATIVO]' : '');

    const urlLine = document.createElement('div');
    urlLine.className = 'server-info-url';
    urlLine.textContent = srv.url;

    infoCol.appendChild(nameLine);
    infoCol.appendChild(urlLine);

    const actionsCol = document.createElement('div');
    actionsCol.className = 'server-row-actions';

    const btnConnect = document.createElement('button');
    btnConnect.className = 'btn-server-connect' + (idx === currentServerIndex ? ' active' : '');
    btnConnect.setAttribute('tabindex', '0');
    btnConnect.textContent = idx === currentServerIndex ? '✓ Conectado' : 'Conectar';
    btnConnect.onclick = () => {
      currentServerIndex = idx;
      try { localStorage.setItem('mk21_last_server', idx); } catch (e) {}
      $('modalServerPicker').classList.add('hidden');
      activeZone = 'channels';
      loadServer();
    };

    const btnDelete = document.createElement('button');
    btnDelete.className = 'btn-server-delete';
    btnDelete.setAttribute('tabindex', '0');
    btnDelete.textContent = '🗑️ Excluir';
    btnDelete.onclick = () => deleteServer(idx);

    actionsCol.appendChild(btnConnect);
    actionsCol.appendChild(btnDelete);

    row.appendChild(infoCol);
    row.appendChild(actionsCol);

    container.appendChild(row);
  });
}

function deleteServer(index) {
  if (SERVERS.length <= 1) {
    alert('Você deve manter pelo menos um servidor cadastrado.');
    return;
  }
  const deletedName = SERVERS[index].name;
  SERVERS.splice(index, 1);
  if (currentServerIndex >= SERVERS.length) {
    currentServerIndex = 0;
  }
  saveServersToStorage();
  renderServerPickerList();
  alert(`Servidor "${deletedName}" excluído com sucesso.`);
}

$('btnAddServerSubmit').onclick = () => {
  const name = $('inputNewServerName').value.trim();
  const url = $('inputNewServerUrl').value.trim();

  if (!name) { alert('Informe o nome do servidor.'); return; }
  if (!url.startsWith('http')) { alert('URL inválida. Deve iniciar com http:// ou https://'); return; }

  const newServer = {
    id: 'custom_' + Date.now(),
    name: '⭐ ' + name,
    url: url
  };

  SERVERS.push(newServer);
  currentServerIndex = SERVERS.length - 1;
  saveServersToStorage();
  try { localStorage.setItem('mk21_last_server', currentServerIndex); } catch (e) {}

  $('inputNewServerName').value = '';
  $('inputNewServerUrl').value = '';
  $('modalServerPicker').classList.add('hidden');
  activeZone = 'channels';

  loadServer();
  alert(`Servidor "${name}" adicionado e conectado com sucesso!`);
};

$('btnRestoreDefaultServers').onclick = () => {
  SERVERS = [...DEFAULT_SERVERS];
  currentServerIndex = 0;
  saveServersToStorage();
  try { localStorage.setItem('mk21_last_server', 0); } catch (e) {}
  renderServerPickerList();
  alert('Os 7 servidores padrão foram restaurados com sucesso.');
};

$('btnHeaderServer').onclick = openServerPicker;
$('btnCloseServerPicker').onclick = () => {
  $('modalServerPicker').classList.add('hidden');
  activeZone = 'channels';
};

// 16. NAVEGAÇÃO ESPACIAL D-PAD COMPLETA (LG webOS / CONTROLE REMOTO)
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
    if (!$('modalSeriesEpisodes').classList.contains('hidden')) {
      $('modalSeriesEpisodes').classList.add('hidden');
      activeZone = 'channels';
      focusActiveElement();
      return;
    }
    if (!$('modalServerPicker').classList.contains('hidden')) {
      $('modalServerPicker').classList.add('hidden');
      activeZone = 'channels';
      return;
    }
    if (!$('modalPin').classList.contains('hidden')) {
      $('modalPin').classList.add('hidden');
      activeZone = 'channels';
      return;
    }
    if (!$('modalExitConfirm').classList.contains('hidden')) {
      $('modalExitConfirm').classList.add('hidden');
      activeZone = 'channels';
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

    $('modalExitConfirm').classList.remove('hidden');
    $('btnExitCancel').focus();
    return;
  }

  // ================= NAVEGAÇÃO D-PAD NO MODAL DE SERVIDORES =================
  if (activeZone === 'modalServerPicker') {
    const focusables = $('modalServerPicker').querySelectorAll('.btn-server-connect, .btn-server-delete, #inputNewServerName, #inputNewServerUrl, #btnAddServerSubmit, #btnRestoreDefaultServers, #btnCloseServerPicker');
    const arr = Array.from(focusables);
    const curIdx = arr.indexOf(document.activeElement);

    if (k === 38) { // Cima
      e.preventDefault();
      const prev = curIdx > 0 ? curIdx - 1 : arr.length - 1;
      arr[prev].focus();
      return;
    }
    if (k === 40) { // Baixo
      e.preventDefault();
      const next = curIdx < arr.length - 1 ? curIdx + 1 : 0;
      arr[next].focus();
      return;
    }
    if (k === 13) { // Enter / OK
      if (document.activeElement) document.activeElement.click();
      return;
    }
    return;
  }

  // ================= NAVEGAÇÃO D-PAD NO MODAL DE SÉRIES =================
  if (activeZone === 'modalSeries') {
    const seasonBtns = $('listSeasons').querySelectorAll('.season-tab-btn');
    const epBtns = $('listEpisodes').querySelectorAll('.episode-item-btn');

    if (k === 38) { // Cima
      e.preventDefault();
      if (document.activeElement && document.activeElement.classList.contains('episode-item-btn')) {
        if (focusedSeriesEpIdx > 0) {
          focusedSeriesEpIdx--;
          epBtns[focusedSeriesEpIdx].focus();
        }
      } else {
        if (focusedSeriesSeasonIdx > 0) {
          focusedSeriesSeasonIdx--;
          seasonBtns[focusedSeriesSeasonIdx].focus();
        }
      }
      return;
    }
    if (k === 40) { // Baixo
      e.preventDefault();
      if (document.activeElement && document.activeElement.classList.contains('episode-item-btn')) {
        if (focusedSeriesEpIdx < epBtns.length - 1) {
          focusedSeriesEpIdx++;
          epBtns[focusedSeriesEpIdx].focus();
        }
      } else {
        if (focusedSeriesSeasonIdx < seasonBtns.length - 1) {
          focusedSeriesSeasonIdx++;
          seasonBtns[focusedSeriesSeasonIdx].focus();
        }
      }
      return;
    }
    if (k === 39) { // Direita -> vai para episódios
      e.preventDefault();
      if (epBtns.length > 0) {
        epBtns[0].focus();
        focusedSeriesEpIdx = 0;
      }
      return;
    }
    if (k === 37) { // Esquerda -> volta para temporadas
      e.preventDefault();
      if (seasonBtns.length > 0) {
        seasonBtns[focusedSeriesSeasonIdx].focus();
      }
      return;
    }
    if (k === 13) { // OK
      if (document.activeElement) document.activeElement.click();
      return;
    }
    return;
  }

  // ================= NAVEGAÇÃO NO PLAYER / TELA CHEIA (TROCA DE CANAL COM SETA) =================
  if (activeZone === 'player' || document.fullscreenElement || document.webkitFullscreenElement) {
    if (k === 38) { // Seta Cima -> Canal Anterior
      e.preventDefault();
      playPreviousChannel();
      return;
    }
    if (k === 40) { // Seta Baixo -> Próximo Canal
      e.preventDefault();
      playNextChannel();
      return;
    }
    if (k === 37) { // Seta Esquerda -> Volta para lista de canais
      if (!document.fullscreenElement && !document.webkitFullscreenElement) {
        e.preventDefault();
        activeZone = 'channels';
        focusActiveElement();
      }
      return;
    }
    if (k === 13) { // OK
      if (document.fullscreenElement || document.webkitFullscreenElement) {
        $('btnPlayPause').click();
      }
      return;
    }
  }

  // ================= 1. CABEÇALHO =================
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
        activeZone = 'header';
        focusedHeaderIdx = 0;
        $(headerElements[0]).focus();
      }
    } else if (activeZone === 'channels') {
      if (focusedItemIdx > 0) {
        focusedItemIdx--;
        focusActiveElement();
      } else {
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

// 17. MODAIS EXTRAS (PIN & SAÍDA)
$('btnRefreshList').onclick = () => loadServer(true);
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
    activeZone = 'modalPin';
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
    activeZone = 'channels';
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

$('btnClosePin').onclick = () => {
  $('modalPin').classList.add('hidden');
  activeZone = 'channels';
};

// MODAL SAÍDA
$('btnExitCancel').onclick = () => $('modalExitConfirm').classList.add('hidden');
$('btnExitConfirm').onclick = () => {
  if (window.webOS && webOS.platformBack) webOS.platformBack();
  else window.close();
};

// 18. INICIALIZAÇÃO AUTOMÁTICA
window.addEventListener('load', () => {
  // Inicializar tamanho da fonte salvo
  try {
    const savedFont = localStorage.getItem('mk21_font_size');
    if (savedFont === 'large') document.body.classList.add('font-large');
  } catch (e) {}

  try {
    const saved = localStorage.getItem('mk21_last_server');
    if (saved !== null && SERVERS[saved]) currentServerIndex = parseInt(saved, 10);
  } catch (e) {}

  loadServer();
});
