// MK21 TV — Smart TV Engine para LG webOS
const $ = id => document.getElementById(id);

// Polyfill universal para compatibilidade com navegadores Chromium antigos da LG (webOS 3.0 até 24)
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

let hlsInstance = null;
let allChannels = [];
let currentCategory = 'all';

// URL padrão capturada ou recuperada do LocalStorage da TV
const DEFAULT_URL = 'http://myopbx.beer/get.php?username=601334065&password=820866576&tpe=m3u_plus&output=mpegts';

try {
  const saved = localStorage.getItem('mk21_m3u_url');
  $('url').value = saved ? saved : DEFAULT_URL;
} catch (e) {
  $('url').value = DEFAULT_URL;
}

function parseM3U(s) {
  let name = 'Canal sem nome', group = 'Geral', out = [];
  const lines = s.split(/\r?\n/);
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i].trim();
    if (l.startsWith('#EXTINF:')) {
      const idx = l.lastIndexOf(',');
      name = idx >= 0 ? l.slice(idx + 1).trim() : 'Canal sem nome';
      const match = l.match(/group-title="([^"]*)"/i);
      group = match && match[1] ? match[1].trim() : 'Geral';
    } else if (/^https?:\/\//i.test(l)) {
      out.push({ name: name, group: group, url: l });
      name = 'Canal sem nome';
      group = 'Geral';
    }
  }
  return out;
}

function playStream(url, name) {
  const video = $('player');
  $('playing').textContent = 'Conectando: ' + name + '...';

  if (hlsInstance) {
    hlsInstance.destroy();
    hlsInstance = null;
  }

  const isHls = url.toLowerCase().includes('.m3u8') || url.includes('/live/');

  if (isHls && window.Hls && Hls.isSupported()) {
    hlsInstance = new Hls({
      enableWorker: true,
      lowLatencyMode: true,
      maxBufferLength: 15
    });
    hlsInstance.loadSource(url);
    hlsInstance.attachMedia(video);
    hlsInstance.on(Hls.Events.MANIFEST_PARSED, function() {
      video.play().catch(function(err) {
        console.warn('AutoPlay blocked:', err);
        $('status').textContent = 'Pressione Reproduzir no player.';
      });
      $('playing').textContent = '▶ ' + name;
    });
    hlsInstance.on(Hls.Events.ERROR, function(event, data) {
      if (data.fatal) {
        console.error('HLS error:', data);
        $('status').textContent = 'Tentando reprodução direta...';
        video.src = url;
        video.play().catch(function() {});
      }
    });
  } else {
    video.src = url;
    video.play().then(function() {
      $('playing').textContent = '▶ ' + name;
    }).catch(function(err) {
      console.warn('Playback error:', err);
      $('status').textContent = 'Falha ao iniciar canal. Verifique conexão/codec da TV.';
    });
  }
}

function renderChannels(list) {
  const ul = $('channels');
  // Limpeza 100% segura para qualquer versão do webOS
  while (ul.firstChild) {
    ul.removeChild(ul.firstChild);
  }

  if (list.length === 0) {
    const li = document.createElement('li');
    const b = document.createElement('button');
    b.textContent = 'Nenhum canal encontrado com esse filtro.';
    b.disabled = true;
    li.appendChild(b);
    ul.appendChild(li);
    return;
  }

  const fragment = document.createDocumentFragment();
  const displayLimit = Math.min(list.length, 1200);

  for (let i = 0; i < displayLimit; i++) {
    const c = list[i];
    const li = document.createElement('li');
    const b = document.createElement('button');
    b.setAttribute('tabindex', '0');

    const titleSpan = document.createElement('span');
    titleSpan.textContent = c.name;
    b.appendChild(titleSpan);

    if (c.group && c.group !== 'Geral') {
      const tag = document.createElement('span');
      tag.className = 'cat-tag';
      tag.textContent = c.group;
      b.appendChild(tag);
    }

    b.onclick = (function(channel) {
      return function() {
        playStream(channel.url, channel.name);
      };
    })(c);

    li.appendChild(b);
    fragment.appendChild(li);
  }

  ul.appendChild(fragment);
}

// Filtro rápido
$('filter').addEventListener('input', function(e) {
  const q = e.target.value.toLowerCase().trim();
  if (!q) {
    renderChannels(allChannels);
    return;
  }
  const filtered = allChannels.filter(function(c) {
    return c.name.toLowerCase().includes(q) || c.group.toLowerCase().includes(q);
  });
  renderChannels(filtered);
});

// Ação de Carregar
$('load').onclick = async function() {
  const u = $('url').value.trim();
  if (!/^https?:\/\//i.test(u)) {
    $('status').textContent = 'Informe uma URL HTTP/HTTPS válida.';
    return;
  }

  try {
    localStorage.setItem('mk21_m3u_url', u);
  } catch (e) {}

  $('status').textContent = 'Baixando e processando canais da playlist...';

  try {
    const response = await fetch(u);
    if (!response.ok) throw new Error('HTTP ' + response.status);
    const text = await response.text();
    allChannels = parseM3U(text);

    if (allChannels.length === 0) {
      $('status').textContent = 'Nenhum canal válido encontrado na playlist.';
      return;
    }

    renderChannels(allChannels);
    $('status').textContent = '✅ ' + allChannels.length + ' canais carregados com sucesso!';
    
    // Auto-foco no primeiro canal após carregar
    setTimeout(function() {
      const firstBtn = $('channels').querySelector('button');
      if (firstBtn) firstBtn.focus();
    }, 200);

  } catch (err) {
    console.error('Fetch error:', err);
    $('status').textContent = 'Falha ao carregar: ' + err.message + ' (Verifique conexão com a internet).';
  }
};

// Tela Cheia
$('btnFullscreen').onclick = function() {
  const video = $('player');
  if (video.requestFullscreen) {
    video.requestFullscreen();
  } else if (video.webkitRequestFullscreen) {
    video.webkitRequestFullscreen();
  }
};

// Teclas do Controle Remoto LG Smart TV
document.addEventListener('keydown', function(e) {
  // 461 = Tecla Voltar/Back oficial da LG (Magic Remote)
  // 27 = Escape, 8 = Backspace
  if (e.keyCode === 461 || e.key === 'Escape' || e.key === 'Backspace' || e.key === 'GoBack') {
    if (document.fullscreenElement || document.webkitFullscreenElement) {
      if (document.exitFullscreen) document.exitFullscreen();
      else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
      e.preventDefault();
      return;
    }
    const video = $('player');
    if (!video.paused) {
      video.pause();
      $('playing').textContent = 'Pausado.';
      e.preventDefault();
    }
  }
});

// Auto-carregar se já houver URL válida configurada
window.addEventListener('load', function() {
  if ($('url').value.startsWith('http')) {
    setTimeout(function() {
      $('load').click();
    }, 500);
  }
});
