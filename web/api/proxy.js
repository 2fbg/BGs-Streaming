// /api/proxy.js - Proxy Serverless para Vercel
const fetch = require('node-fetch');

module.exports = async (req, res) => {
    try {
        const targetUrl = decodeURIComponent(req.query.url);
        
        // Valida se a URL é permitida (apenas servidores do projeto MK21)
        const allowedDomains = [
            'vlogmk.de', 'triimundial.shop', 'infinixparcerias.site',
            'unituf.online', 'cb6.fun', 'appsmk.org', 'novamk21.win'
        ];
        const urlObj = new URL(targetUrl);
        if (!allowedDomains.includes(urlObj.hostname)) {
            return res.status(403).json({ error: 'Domínio não permitido' });
        }

        // Faz requisição para a URL original (HTTP)
        const response = await fetch(targetUrl, {
            method: 'GET',
            redirect: 'follow',
            headers: {
                'User-Agent': 'MK21-MultiServidor/1.0 (+https://mk21.app)'
            }
        });

        // Copia cabeçalhos da resposta original
        const headers = {};
        response.headers.forEach((value, key) => {
            if (!['content-length', 'transfer-encoding', 'connection'].includes(key.toLowerCase())) {
                headers[key] = value;
            }
        });
        headers['Access-Control-Allow-Origin'] = '*'; // Permite acesso do frontend
        headers['Content-Type'] = response.headers.get('Content-Type') || 'application/octet-stream';

        // Envia resposta para o frontend (HTTPS)
        res.writeHead(response.status, headers);
        response.body.pipe(res);

    } catch (error) {
        console.error("Erro no proxy:", error);
        res.status(500).json({ error: 'Falha ao carregar conteúdo' });
    }
};
