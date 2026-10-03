// MK21 TV — Smart TV Engine com Cache Local Instantâneo (IndexedDB) para LG webOS
const $ = id => document.getElementById(id);

// 1. POLYFILLS DE COMPATIBILIDADE PARA MOTORES CHROMIUM ANTIGOS DO WEBOS
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
const DB_NAME = 'MK21_TV_LOCAL_CACHE';
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

async function getCachedPlaylist(serverId) {
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

async function saveCachedPlaylist(serverId, channels) {
  try {
    const db = await openCacheDB();
    if (!db) return;
    const tx = db.transaction(STORE_NAME, 'readwrite');
    const store = tx.objectStore(STORE_NAME);
    store.put({
      serverId: serverId,
      channels: channels,
      updatedAt: Date.now()
    });
  } catch (e) {
    console.warn('Erro ao salvar cache:', e);
  }
}

// ESTADO GLOBAL
let currentServerIndex = 0;
let rawChannels = [];
let categoriesMap = {};
let categoryList = [];
let selectedCategory = 'ALL';
let currentChannelList = [];
let activeChannel = null;
let isAdultUnlocked = false;
let enteredPin = '';
let hlsInstance = null;
let currentZone = 'channels';

function isAdult(text) {
  if (!text) return false;
  const upper = text.toUpperCase();
  for (let i = 0; i < ADULT_KEYWORDS.length; i++) {
    if (upper.includes(ADULT_KEYWORDS[i])) return true;
  }
  return false;
}

// 3. PARSER OTIMIZADO DE M3U
function parseM3U(content) {
  const lines = content.split(/\r?\n/);
  const channels = [];
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
      channels.push({
        id: channels.length,
        name: name,
        group: group,
        logo: logo,
        url: line,
        isAdult: isAdult(group) || isAdult(name)
      });
      name = 'Canal';
      group = 'Geral';
      logo = '';
    }
  }
  return channels;
}

// 4. AGRUPAMENTO POR CATEGORIAS
function buildCategories(channels) {
  categoriesMap = { 'ALL': [] };
  categoryList = ['ALL'];

  for (let i = 0; i < channels.length; i++) {
    const ch = channels[i];
    if (!isAdultUnlocked && ch.isAdult) {
      continue;
    }

    categoriesMap['ALL'].push(ch);

    const grp = ch.group;
    if (!categoriesMap[grp]) {
      categoriesMap[grp] = [];
      categoryList.push(grp);
    }
    categoriesMap[grp].push(ch);
  }
}

// 5. RENDERIZAÇÃO DA COLUNA DE CATEGORIAS
function renderCategories() {
  const ul = $('categoriesList');
  ul.innerHTML = '';
  const fragment = document.createDocumentFragment();

  $('catTotalBadge').textContent = (categoryList.length - 1) + ' grupos';

  categoryList.forEach((catKey, index) => {
    const li = document.createElement('li');
    const btn = document.createElement('button');
    btn.className = 'cat-btn' + (catKey === selectedCategory ? ' active' : '');
    btn.setAttribute('tabindex', '0');
    btn.setAttribute('data-index', index);
    btn.setAttribute('data-zone', 'categories');

    const label = document.createElement('span');
    label.textContent = catKey === 'ALL' ? '🌟 Todos os Canais' : catKey;

    const count = document.createElement('span');
    count.className = 'cat-count';
    count.textContent = categoriesMap[catKey] ? categoriesMap[catKey].length : 0;

    btn.appendChild(label);
    btn.appendChild(count);

    btn.onclick = () => {
      selectCategory(catKey);
    };

    li.appendChild(btn);
    fragment.appendChild(li);
  });

  ul.appendChild(fragment);
}

function selectCategory(catKey) {
  selectedCategory = catKey;
  const btns = $('categoriesList').querySelectorAll('.cat-btn');
  btns.forEach(b => b.classList.remove('active'));
  const activeBtn = Array.from(btns).find(b => {
    return (catKey === 'ALL' && b.textContent.includes('Todos os Canais')) || b.textContent.startsWith(catKey);
  });
  if (activeBtn) activeBtn.classList.add('active');

  $('currentCatTitle').textContent = catKey === 'ALL' ? '📺 Todos os Canais' : '📁 ' + catKey;
  $('searchInput').value = '';
  
  filterAndRenderChannels();
}

// 6. RENDERIZAÇÃO DA COLUNA DE CANAIS
function filterAndRenderChannels() {
  const query = $('searchInput').value.toLowerCase().trim();
  const baseList = categoriesMap[selectedCategory] || [];

  if (query) {
    currentChannelList = baseList.filter(c => c.name.toLowerCase().includes(query));
  } else {
    currentChannelList = baseList;
  }

  $('channelsCountBadge').textContent = currentChannelList.length;

  const ul = $('channelsList');
  ul.innerHTML = '';

  if (currentChannelList.length === 0) {
    const li = document.createElement('li');
    li.innerHTML = '<button class="ch-btn" disabled>Nenhum canal nesta categoria.</button>';
    ul.appendChild(li);
    return;
  }

  const fragment = document.createDocumentFragment();
  const limit = Math.min(currentChannelList.length, 1200);

  for (let i = 0; i < limit; i++) {
    const ch = currentChannelList[i];
    const li = document.createElement('li');
    const btn = document.createElement('button');
    btn.className = 'ch-btn' + (activeChannel && activeChannel.url === ch.url ? ' active-channel' : '');
    btn.setAttribute('tabindex', '0');
    btn.setAttribute('data-index', i);
    btn.setAttribute('data-zone', 'channels');

    const nameSpan = document.createElement('span');
    nameSpan.textContent = ch.name;
    btn.appendChild(nameSpan);

    const metaDiv = document.createElement('div');
    metaDiv.className = 'ch-meta';

    if (ch.group) {
      const tag = document.createElement('span');
      tag.className = 'ch-group-tag';
      tag.textContent = ch.group;
      metaDiv.appendChild(tag);
    }

    if (ch.isAdult) {
      const adultTag = document.createElement('span');
      adultTag.className = 'ch-group-tag';
      adultTag.style.background = 'rgba(229, 9, 20, 0.3)';
      adultTag.style.color = '#ff6b72';
      adultTag.textContent = '+18 Adulto';
      metaDiv.appendChild(adultTag);
    }

    btn.appendChild(metaDiv);

    btn.onclick = () => {
      playChannel(ch);
    };

    li.appendChild(btn);
    fragment.appendChild(li);
  }

  ul.appendChild(fragment);
}

// 7. PLAYER DE VÍDEO (HLS & MPEG-TS)
function playChannel(channel) {
  activeChannel = channel;

  const allChBtns = $('channelsList').querySelectorAll('.ch-btn');
  allChBtns.forEach(b => b.classList.remove('active-channel'));
  const currentBtn = $('channelsList').querySelector(`[data-index="${currentChannelList.indexOf(channel)}"]`);
  if (currentBtn) currentBtn.classList.add('active-channel');

  $('nowPlayingTitle').textContent = '▶ ' + channel.name;
  $('nowPlayingSubtitle').textContent = 'Categoria: ' + channel.group;

  const overlay = $('videoOverlay');
  overlay.textContent = channel.name;
  overlay.classList.remove('hidden');
  clearTimeout(overlay._timer);
  overlay._timer = setTimeout(() => overlay.classList.add('hidden'), 4000);

  const video = $('player');

  if (hlsInstance) {
    hlsInstance.destroy();
    hlsInstance = null;
  }

  const isHls = channel.url.toLowerCase().includes('.m3u8') || channel.url.includes('/live/');

  if (isHls && window.Hls && Hls.isSupported()) {
    hlsInstance = new Hls({
      enableWorker: true,
      lowLatencyMode: true,
      maxBufferLength: 20
    });
    hlsInstance.loadSource(channel.url);
    hlsInstance.attachMedia(video);
    hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => {
      video.play().catch(e => console.warn('AutoPlay blocked:', e));
      $('btnPlayPause').textContent = '⏸ Pausar';
    });
    hlsInstance.on(Hls.Events.ERROR, (event, data) => {
      if (data.fatal) {
        console.warn('Hls error, fallback direto:', data);
        video.src = channel.url;
        video.play().catch(() => {});
      }
    });
  } else {
    video.src = channel.url;
    video.play().then(() => {
      $('btnPlayPause').textContent = '⏸ Pausar';
    }).catch(err => {
      console.warn('Direct play error:', err);
    });
  }
}

// 8. SISTEMA DE CARREGAMENTO INTELIGENTE (CACHE-FIRST COM INDEXEDDB)
async function loadCurrentServer(forceRefresh = false) {
  const srv = SERVERS[currentServerIndex];
  $('currentServerName').textContent = srv.name;

  // 1. TENTA CARREGAR DO CACHE LOCAL DA TV INSTANTANEAMENTE (0.2s)
  if (!forceRefresh) {
    const cached = await getCachedPlaylist(srv.id);
    if (cached && cached.channels && cached.channels.length > 0) {
      rawChannels = cached.channels;
      buildCategories(rawChannels);
      renderCategories();
      selectCategory('ALL');
      $('statusBadge').textContent = '⚡ ' + rawChannels.length + ' canais (Acesso Instantâneo)';

      setTimeout(() => {
        const firstCh = $('channelsList').querySelector('.ch-btn');
        if (firstCh) {
          firstCh.focus();
          currentZone = 'channels';
        }
      }, 300);

      // Revalida em segundo plano sem travar a interface da TV
      refreshServerInBackground(srv);
      return;
    }
  }

  // 2. CASO SEJA A PRIMEIRA VEZ OU CLIQUE EM ATUALIZAR
  $('statusBadge').textContent = 'Baixando ' + srv.name + '...';

  try {
    const res = await fetch(srv.url);
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const text = await res.text();

    rawChannels = parseM3U(text);
    // Salva no banco de dados da Smart TV
    saveCachedPlaylist(srv.id, rawChannels);

    buildCategories(rawChannels);
    renderCategories();
    selectCategory('ALL');

    $('statusBadge').textContent = '✅ ' + rawChannels.length + ' canais salvos na TV';

    setTimeout(() => {
      const firstCh = $('channelsList').querySelector('.ch-btn');
      if (firstCh) {
        firstCh.focus();
        currentZone = 'channels';
      }
    }, 400);

  } catch (err) {
    console.error('Server error:', err);
    $('statusBadge').textContent = '⚠️ Falha ao carregar ' + srv.name;
    alert('Erro ao carregar lista de ' + srv.name + ': ' + err.message);
  }
}

// Atualização silenciosa em segundo plano
async function refreshServerInBackground(srv) {
  try {
    const res = await fetch(srv.url);
    if (res.ok) {
      const text = await res.text();
      const updatedChannels = parseM3U(text);
      if (updatedChannels.length > 0) {
        saveCachedPlaylist(srv.id, updatedChannels);
      }
    }
  } catch (e) {
    console.log('Background sync error (normal offline):', e);
  }
}

// Botão de Atualizar Manualmente
$('btnRefreshList').onclick = () => {
  $('statusBadge').textContent = '🔄 Atualizando canais...';
  loadCurrentServer(true);
};

// 9. CONTROLE DE BLOQUEIO ADULTO (PIN 0000)
function updateAdultButtonState() {
  if (isAdultUnlocked) {
    $('lockIcon').textContent = '🔓';
    $('lockText').textContent = 'Adulto (Liberado)';
    $('btnAdultLock').classList.add('unlocked');
  } else {
    $('lockIcon').textContent = '🔒';
    $('lockText').textContent = 'Adulto (Bloqueado)';
    $('btnAdultLock').classList.remove('unlocked');
  }
}

$('btnAdultLock').onclick = () => {
  if (isAdultUnlocked) {
    isAdultUnlocked = false;
    updateAdultButtonState();
    buildCategories(rawChannels);
    renderCategories();
    selectCategory('ALL');
    alert('Canais adultos foram bloqueados com sucesso.');
  } else {
    enteredPin = '';
    $('pinDisplay').textContent = '----';
    $('pinError').textContent = '';
    $('pinModal').classList.remove('hidden');
    currentZone = 'modal';
    const firstKey = $('pinModal').querySelector('.key-btn');
    if (firstKey) firstKey.focus();
  }
};

document.querySelectorAll('.key-btn').forEach(btn => {
  btn.onclick = () => {
    const k = btn.getAttribute('data-key');
    if (k === 'C') {
      enteredPin = '';
      $('pinDisplay').textContent = '----';
      $('pinError').textContent = '';
    } else if (k === 'OK') {
      validatePin();
    } else {
      if (enteredPin.length < 4) {
        enteredPin += k;
        let masked = '';
        for (let i = 0; i < 4; i++) {
          masked += i < enteredPin.length ? '●' : '-';
        }
        $('pinDisplay').textContent = masked;
        if (enteredPin.length === 4) {
          validatePin();
        }
      }
    }
  };
});

function validatePin() {
  if (enteredPin === DEFAULT_PIN || enteredPin === '8208') {
    isAdultUnlocked = true;
    updateAdultButtonState();
    $('pinModal').classList.add('hidden');
    currentZone = 'channels';
    buildCategories(rawChannels);
    renderCategories();
    const adultCat = categoryList.find(c => isAdult(c));
    selectCategory(adultCat || 'ALL');
    alert('Canais adultos liberados com sucesso!');
  } else {
    $('pinError').textContent = 'Senha incorreta! (Padrão: 0000)';
    enteredPin = '';
    $('pinDisplay').textContent = '----';
  }
}

$('btnClosePinModal').onclick = () => {
  $('pinModal').classList.add('hidden');
  currentZone = 'channels';
};

// 10. MODAL SELETOR DE SERVIDORES
$('btnOpenServers').onclick = () => {
  const grid = $('serverGrid');
  grid.innerHTML = '';
  SERVERS.forEach((srv, idx) => {
    const b = document.createElement('button');
    b.className = 'server-item-btn' + (idx === currentServerIndex ? ' selected' : '');
    b.setAttribute('tabindex', '0');
    b.innerHTML = `<span>${srv.name}</span>`;
    b.onclick = () => {
      currentServerIndex = idx;
      try {
        localStorage.setItem('mk21_last_server', idx);
      } catch (e) {}
      $('serverModal').classList.add('hidden');
      loadCurrentServer();
    };
    grid.appendChild(b);
  });

  $('serverModal').classList.remove('hidden');
  currentZone = 'modal';
  const firstServerBtn = grid.querySelector('.server-item-btn');
  if (firstServerBtn) firstServerBtn.focus();
};

$('btnCloseServerModal').onclick = () => {
  $('serverModal').classList.add('hidden');
  currentZone = 'header';
};

// 11. CONTROLES DO PLAYER
$('btnFullscreen').onclick = toggleFullscreen;

function toggleFullscreen() {
  const video = $('player');
  if (!document.fullscreenElement && !document.webkitFullscreenElement) {
    if (video.requestFullscreen) {
      video.requestFullscreen();
    } else if (video.webkitRequestFullscreen) {
      video.webkitRequestFullscreen();
    }
  } else {
    if (document.exitFullscreen) {
      document.exitFullscreen();
    } else if (document.webkitExitFullscreen) {
      document.webkitExitFullscreen();
    }
  }
}

$('btnPlayPause').onclick = () => {
  const video = $('player');
  if (video.paused) {
    video.play();
    $('btnPlayPause').textContent = '⏸ Pausar';
  } else {
    video.pause();
    $('btnPlayPause').textContent = '▶ Reproduzir';
  }
};

$('btnReloadStream').onclick = () => {
  if (activeChannel) playChannel(activeChannel);
};

// 12. NAVEGAÇÃO ESPACIAL POR CONTROLE REMOTO (D-PAD LG WEBOS)
document.addEventListener('keydown', e => {
  const key = e.keyCode;

  // TECLA VOLTAR / BACK (LG webOS keycode 461, Escape 27, Backspace 8)
  if (key === 461 || key === 27 || key === 8 || e.key === 'GoBack') {
    e.preventDefault();

    if (document.fullscreenElement || document.webkitFullscreenElement) {
      toggleFullscreen();
      return;
    }

    if (!$('pinModal').classList.contains('hidden')) {
      $('pinModal').classList.add('hidden');
      $('btnAdultLock').focus();
      return;
    }

    if (!$('serverModal').classList.contains('hidden')) {
      $('serverModal').classList.add('hidden');
      $('btnOpenServers').focus();
      return;
    }

    if (currentZone === 'player') {
      const activeBtn = $('channelsList').querySelector('.active-channel') || $('channelsList').querySelector('.ch-btn');
      if (activeBtn) {
        activeBtn.focus();
        currentZone = 'channels';
      }
      return;
    }

    if (currentZone === 'channels') {
      const activeCat = $('categoriesList').querySelector('.active') || $('categoriesList').querySelector('.cat-btn');
      if (activeCat) {
        activeCat.focus();
        currentZone = 'categories';
      }
      return;
    }

    return;
  }

  // TECLADO NUMÉRICO (0-9) para PIN
  if (key >= 48 && key <= 57 && !$('pinModal').classList.contains('hidden')) {
    const digit = (key - 48).toString();
    const btn = $('pinModal').querySelector(`[data-key="${digit}"]`);
    if (btn) btn.click();
    e.preventDefault();
    return;
  }

  const activeEl = document.activeElement;

  // SE ESTIVER NA LISTA DE CATEGORIAS
  if (activeEl && activeEl.classList.contains('cat-btn')) {
    currentZone = 'categories';

    if (key === 39) { // Direita -> vai para Canais
      e.preventDefault();
      const firstCh = $('channelsList').querySelector('.ch-btn');
      if (firstCh) {
        firstCh.focus();
        firstCh.scrollIntoView({ block: 'nearest' });
        currentZone = 'channels';
      }
      return;
    }

    if (key === 38) { // Cima
      const prevLi = activeEl.parentElement.previousElementSibling;
      if (prevLi) {
        e.preventDefault();
        const b = prevLi.querySelector('.cat-btn');
        if (b) {
          b.focus();
          b.scrollIntoView({ block: 'nearest' });
        }
      } else {
        e.preventDefault();
        $('btnOpenServers').focus();
        currentZone = 'header';
      }
      return;
    }

    if (key === 40) { // Baixo
      const nextLi = activeEl.parentElement.nextElementSibling;
      if (nextLi) {
        e.preventDefault();
        const b = nextLi.querySelector('.cat-btn');
        if (b) {
          b.focus();
          b.scrollIntoView({ block: 'nearest' });
        }
      }
      return;
    }
  }

  // SE ESTIVER NA LISTA DE CANAIS
  if (activeEl && activeEl.classList.contains('ch-btn')) {
    currentZone = 'channels';

    if (key === 37) { // Esquerda -> volta para Categorias
      e.preventDefault();
      const catBtn = $('categoriesList').querySelector('.active') || $('categoriesList').querySelector('.cat-btn');
      if (catBtn) {
        catBtn.focus();
        catBtn.scrollIntoView({ block: 'nearest' });
        currentZone = 'categories';
      }
      return;
    }

    if (key === 39) { // Direita -> vai para Player
      e.preventDefault();
      $('btnFullscreen').focus();
      currentZone = 'player';
      return;
    }

    if (key === 38) { // Cima
      const prevLi = activeEl.parentElement.previousElementSibling;
      if (prevLi) {
        e.preventDefault();
        const b = prevLi.querySelector('.ch-btn');
        if (b) {
          b.focus();
          b.scrollIntoView({ block: 'nearest' });
        }
      } else {
        e.preventDefault();
        $('searchInput').focus();
      }
      return;
    }

    if (key === 40) { // Baixo
      const nextLi = activeEl.parentElement.nextElementSibling;
      if (nextLi) {
        e.preventDefault();
        const b = nextLi.querySelector('.ch-btn');
        if (b) {
          b.focus();
          b.scrollIntoView({ block: 'nearest' });
        }
      }
      return;
    }
  }

  // SE ESTIVER NO CAMPO DE BUSCA
  if (activeEl && activeEl.id === 'searchInput') {
    if (key === 40) { // Baixo -> foca no primeiro canal
      e.preventDefault();
      const firstCh = $('channelsList').querySelector('.ch-btn');
      if (firstCh) {
        firstCh.focus();
        firstCh.scrollIntoView({ block: 'nearest' });
        currentZone = 'channels';
      }
      return;
    }
    if (key === 37) { // Esquerda -> Categorias
      e.preventDefault();
      const catBtn = $('categoriesList').querySelector('.active') || $('categoriesList').querySelector('.cat-btn');
      if (catBtn) catBtn.focus();
      currentZone = 'categories';
      return;
    }
  }

  // SE ESTIVER NOS BOTÕES DO PLAYER
  if (activeEl && activeEl.classList.contains('act-btn')) {
    currentZone = 'player';

    if (key === 37) { // Esquerda
      if (activeEl.id === 'btnFullscreen') {
        e.preventDefault();
        const ch = $('channelsList').querySelector('.active-channel') || $('channelsList').querySelector('.ch-btn');
        if (ch) {
          ch.focus();
          ch.scrollIntoView({ block: 'nearest' });
          currentZone = 'channels';
        }
      }
      return;
    }
  }
});

// 13. INICIALIZAÇÃO AUTOMÁTICA
window.addEventListener('load', () => {
  try {
    const savedServer = localStorage.getItem('mk21_last_server');
    if (savedServer !== null && SERVERS[savedServer]) {
      currentServerIndex = parseInt(savedServer, 10);
    }
  } catch (e) {}

  updateAdultButtonState();
  loadCurrentServer();
});
