const $ = id => document.getElementById(id);

let hlsInstance = null;

// Carregar última URL salva no LocalStorage da Smart TV
try {
  const savedUrl = localStorage.getItem('mk21_m3u_url');
  if (savedUrl) {
    $('url').value = savedUrl;
  }
} catch (e) {
  console.warn('LocalStorage error:', e);
}

function parseM3U(s) {
  let name = 'Canal sem nome', group = 'Geral', out = [];
  for (const raw of s.split(/\r?\n/)) {
    const l = raw.trim();
    if (l.startsWith('#EXTINF:')) {
      let i = l.lastIndexOf(',');
      name = i >= 0 ? l.slice(i + 1).trim() : 'Canal sem nome';
      group = (l.match(/group-title="([^"]*)"/i) || [])[1] || 'Geral';
    } else if (/^https?:\/\//i.test(l)) {
      out.push({ name, group, url: l });
      name = 'Canal sem nome';
      group = 'Geral';
    }
  }
  return out;
}

function playStream(url, name) {
  const video = $('player');
  $('playing').textContent = 'Conectando: ' + name + '...';

  // Destruir instância anterior de HLS se existir
  if (hlsInstance) {
    hlsInstance.destroy();
    hlsInstance = null;
  }

  const isHls = url.toLowerCase().includes('.m3u8') || url.includes('/live/');

  if (isHls && window.Hls && Hls.isSupported()) {
    hlsInstance = new Hls({
      enableWorker: true,
      lowLatencyMode: true,
      maxBufferLength: 10
    });
    hlsInstance.loadSource(url);
    hlsInstance.attachMedia(video);
    hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => {
      video.play().catch(err => {
        console.warn('AutoPlay blocked:', err);
        $('status').textContent = 'Pressione play no reprodutor.';
      });
      $('playing').textContent = '▶ Reproduzindo: ' + name;
    });
    hlsInstance.on(Hls.Events.ERROR, (event, data) => {
      if (data.fatal) {
        console.error('HLS fatal error:', data);
        $('status').textContent = 'Falha no stream HLS (' + data.type + '). Tentando direto...';
        video.src = url;
        video.play().catch(() => {});
      }
    });
  } else {
    // Fallback nativo
    video.src = url;
    video.play().then(() => {
      $('playing').textContent = '▶ Reproduzindo: ' + name;
    }).catch(err => {
      console.warn('Playback error:', err);
      $('status').textContent = 'Falha ao iniciar o fluxo na TV (Verifique codec/CORS).';
    });
  }
}

$('load').onclick = async () => {
  let u = $('url').value.trim();
  if (!/^https?:\/\//i.test(u)) {
    $('status').textContent = 'Informe uma URL HTTP/HTTPS válida.';
    return;
  }

  try {
    localStorage.setItem('mk21_m3u_url', u);
  } catch (e) {}

  $('status').textContent = 'Carregando playlist…';

  try {
    let r = await fetch(u);
    if (!r.ok) throw Error('HTTP ' + r.status);
    let a = parseM3U(await r.text());

    if (a.length === 0) {
      $('status').textContent = 'Nenhum canal encontrado na playlist informada.';
      return;
    }

    // Renderização otimizada com Fragment para não travar a TV com listas grandes
    const fragment = document.createDocumentFragment();
    const displayLimit = Math.min(a.length, 1000); // Exibe até 1000 primeiros canais na TV

    for (let i = 0; i < displayLimit; i++) {
      const c = a[i];
      const li = document.createElement('li');
      const b = document.createElement('button');
      b.textContent = c.name + ' · ' + c.group;
      b.onclick = () => playStream(c.url, c.name);
      li.appendChild(b);
      fragment.appendChild(li);
    }

    $('channels').replaceChildren(fragment);
    $('status').textContent = a.length + ' canais carregados com sucesso' + (a.length > displayLimit ? ' (exibindo primeiros 1000).' : '.');
  } catch (e) {
    $('status').textContent = 'Falha: ' + e.message + ' (CORS/rede/servidor podem bloquear o acesso).';
  }
};

// Suporte a teclas do controle remoto LG Smart TV (webOS)
document.addEventListener('keydown', e => {
  // 461 = Tecla Back/Voltar do Magic Remote LG webOS
  // 27 = Escape, 8 = Backspace
  if (e.keyCode === 461 || e.key === 'Escape' || e.key === 'Backspace' || e.key === 'GoBack') {
    const video = $('player');
    if (!video.paused) {
      video.pause();
      $('playing').textContent = 'Pausado.';
      e.preventDefault();
    }
  }
});
