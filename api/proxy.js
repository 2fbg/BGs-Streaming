// Vercel Serverless Function to act as a secure, fast, and dedicated streaming CORS Proxy for MK21 IPTV Web.
// Since Node/Vercel acts server-side, it bypasses browser CORS and Mixed Content blocks.
// Pipes binary streams directly from the source to prevent out-of-memory errors and timeout limits.

const http = require('http');
const https = require('https');
const urlModule = require('url');
const { URL } = require('url');

const ALLOWED_DOMAINS = [
    'myopbx.beer',
    'alfatecloan.sbs',
    'coliseuop.site',
    'gip26.sbs',
    'caterlune.top',
    'cdn.caterlune.top',
    'tigerouro.shop',
    'app.tigerouro.shop',
    'cp2026.sbs',
    'new-link.shop',
    'connstar.xyz',
    'tannix26.shop',
    'painelplyon.top',
    'mk21.uk',
    'hll4.top',
    'antaresfusion.shop',
    'cdnconn.xyz',
    'cdn.jsdelivr.net',
    'fonts.googleapis.com',
    'fonts.gstatic.com'
];

const ALLOWED_ORIGINS = ['https://2fbg.github.io'];

// Basic in-memory rate limiting per IP
const requestCounts = new Map();
const MAX_REQUESTS_PER_MINUTE = 30;

function isRateLimited(clientIp) {
    const now = Date.now();
    const windowStart = now - 60000;
    let record = requestCounts.get(clientIp);

    if (!record) {
        record = [];
        requestCounts.set(clientIp, record);
    }

    // Keep only timestamps within last 60 seconds
    const recent = record.filter(timestamp => timestamp > windowStart);
    recent.push(now);
    requestCounts.set(clientIp, recent);

    // Clean up old entries from the map periodically
    if (requestCounts.size > 2000) {
        for (const [ip, timestamps] of requestCounts.entries()) {
            if (timestamps.every(t => t <= windowStart)) {
                requestCounts.delete(ip);
            }
        }
    }

    return recent.length > MAX_REQUESTS_PER_MINUTE;
}

module.exports = function handler(req, res) {
    // Restrict CORS to authorized frontend origins
    const origin = req.headers.origin;
    if (ALLOWED_ORIGINS.includes(origin)) {
        res.setHeader('Access-Control-Allow-Origin', origin);
    }
    res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', '*');

    // Handle preflight OPTIONS request
    if (req.method === 'OPTIONS') {
        res.statusCode = 200;
        res.end();
        return;
    }

    // Rate limiting check
    const clientIp = req.headers['x-forwarded-for'] || req.socket.remoteAddress || 'unknown';
    if (isRateLimited(clientIp)) {
        res.statusCode = 429;
        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify({ error: 'Muitas requisições. Tente novamente em instantes.' }));
        return;
    }

    // Get the target URL parameter
    let targetUrlStr = null;
    if (req.query && req.query.url) {
        targetUrlStr = req.query.url;
    } else {
        const parsedQueryStr = urlModule.parse(req.url, true).query;
        targetUrlStr = parsedQueryStr.url;
    }

    if (!targetUrlStr) {
        res.statusCode = 400;
        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify({ error: 'Falta o parâmetro url com o link de destino.' }));
        return;
    }

    let decodedUrl;
    try {
        decodedUrl = decodeURIComponent(targetUrlStr);
        const parsedTarget = new URL(decodedUrl);
        if (!ALLOWED_DOMAINS.some(d =>
            parsedTarget.hostname === d || parsedTarget.hostname.endsWith('.' + d)
        )) {
            res.statusCode = 403;
            res.setHeader('Content-Type', 'application/json');
            res.end(JSON.stringify({ error: 'Domínio não permitido' }));
            return;
        }
    } catch (e) {
        res.statusCode = 400;
        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify({ error: 'URL inválida' }));
        return;
    }

    try {
        const maxRedirects = 6;

        function handleRequest(urlStr, redirectCount = 0) {
            if (redirectCount > maxRedirects) {
                res.statusCode = 502;
                res.end('Erro: Excesso de redirecionamentos (Redirect Loop)');
                return;
            }

            const parsedUrl = new URL(urlStr);
            if (!ALLOWED_DOMAINS.some(d =>
                parsedUrl.hostname === d || parsedUrl.hostname.endsWith('.' + d)
            )) {
                res.statusCode = 403;
                res.end(JSON.stringify({ error: 'Domínio de redirecionamento não permitido' }));
                return;
            }

            const client = parsedUrl.protocol === 'https:' ? https : http;

            const forwardHeaders = {
                'User-Agent': 'VLC/3.0.18',
                'Accept': '*/*',
                'Connection': 'keep-alive'
            };

            if (req.headers['range']) {
                forwardHeaders['Range'] = req.headers['range'];
            }
            if (req.headers['if-range']) {
                forwardHeaders['If-Range'] = req.headers['if-range'];
            }

            const options = {
                hostname: parsedUrl.hostname,
                port: parsedUrl.port || (parsedUrl.protocol === 'https:' ? 443 : 80),
                path: parsedUrl.pathname + parsedUrl.search,
                method: 'GET',
                headers: forwardHeaders
            };

            const proxyReq = client.request(options, (proxyRes) => {
                const statusCode = proxyRes.statusCode;

                // Check for HTTP Redirection redirects and follow them internally
                if ((statusCode === 301 || statusCode === 302 || statusCode === 303 || statusCode === 307 || statusCode === 308) && proxyRes.headers.location) {
                    let redirUrl = proxyRes.headers.location;
                    if (!redirUrl.startsWith('http://') && !redirUrl.startsWith('https://')) {
                        redirUrl = new URL(redirUrl, urlStr).toString();
                    }
                    console.log(`Proxy following redirect to (depth ${redirectCount}): ${redirUrl}`);
                    handleRequest(redirUrl, redirectCount + 1);
                    return;
                }

                // Copy stream and media relevant response headers
                const responseHeaders = {
                    'Access-Control-Allow-Methods': 'GET, OPTIONS',
                    'Access-Control-Allow-Headers': '*'
                };

                const currentOrigin = req.headers.origin;
                if (ALLOWED_ORIGINS.includes(currentOrigin)) {
                    responseHeaders['Access-Control-Allow-Origin'] = currentOrigin;
                }

                if (proxyRes.headers['content-type']) {
                    responseHeaders['Content-Type'] = proxyRes.headers['content-type'];
                }
                if (proxyRes.headers['content-length']) {
                    responseHeaders['Content-Length'] = proxyRes.headers['content-length'];
                }
                if (proxyRes.headers['content-range']) {
                    responseHeaders['Content-Range'] = proxyRes.headers['content-range'];
                }
                if (proxyRes.headers['accept-ranges']) {
                    responseHeaders['Accept-Ranges'] = proxyRes.headers['accept-ranges'];
                }

                res.writeHead(statusCode, responseHeaders);

                // Stream binary chunks from source to local client response
                proxyRes.pipe(res);
            });

            proxyReq.on('error', (err) => {
                console.error('IPTV Proxy Request Error:', err);
                res.statusCode = 502;
                res.end(`Erro de conexão do proxy: ${err.message}`);
            });

            proxyReq.end();
        }

        handleRequest(decodedUrl);

    } catch (error) {
        console.error('IPTV Proxy Exception:', error);
        res.statusCode = 500;
        res.end(`Erro ao configurar proxy: ${error.message}`);
    }
};
