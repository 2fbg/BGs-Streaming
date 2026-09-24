// Vercel Serverless Function to act as a secure, fast, and dedicated streaming CORS Proxy for MK21 IPTV Web.
// Since Node/Vercel acts server-side, it bypasses browser CORS and Mixed Content blocks.
// Pipes binary streams directly from the source to prevent out-of-memory errors and timeout limits.

const http = require('http');
const https = require('https');
const urlModule = require('url');
const { URL } = require('url');

const ALLOWED_DOMAINS = [
    'new-link.shop',
    'alfatecloan.sbs',
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

    const recent = record.filter(timestamp => timestamp > windowStart);
    recent.push(now);
    requestCounts.set(clientIp, recent);

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
        res.status(200).end();
        return;
    }

    // Rate limiting check
    const clientIp = req.headers['x-forwarded-for'] || req.socket.remoteAddress || 'unknown';
    if (isRateLimited(clientIp)) {
        res.status(429).json({ error: 'Muitas requisições. Tente novamente em instantes.' });
        return;
    }

    const targetUrlStr = req.query.url;

    if (!targetUrlStr) {
        res.status(400).json({ error: 'Falta o parâmetro url com o link de destino.' });
        return;
    }

    let decodedUrl;
    try {
        decodedUrl = decodeURIComponent(targetUrlStr);
        const parsedTarget = new URL(decodedUrl);
        if (!ALLOWED_DOMAINS.some(d =>
            parsedTarget.hostname === d || parsedTarget.hostname.endsWith('.' + d)
        )) {
            res.status(403).json({ error: 'Domínio não permitido' });
            return;
        }
    } catch (e) {
        res.status(400).json({ error: 'URL inválida' });
        return;
    }

    function doProxy(currentUrlStr, redirectCount = 0) {
        if (redirectCount > 5) {
            res.status(502).send('Error: Too many redirects on remote stream');
            return;
        }

        try {
            const parsedUrl = new URL(currentUrlStr);
            if (!ALLOWED_DOMAINS.some(d =>
                parsedUrl.hostname === d || parsedUrl.hostname.endsWith('.' + d)
            )) {
                res.status(403).json({ error: 'Domínio de redirecionamento não permitido' });
                return;
            }

            const client = parsedUrl.protocol === 'https:' ? https : http;

            const headers = {
                'User-Agent': 'VLC/3.0.18',
                'Accept': '*/*'
            };

            // Forward range request header for media streaming compatibility
            if (req.headers.range) {
                headers['Range'] = req.headers.range;
            }

            const options = {
                hostname: parsedUrl.hostname,
                port: parsedUrl.port || (parsedUrl.protocol === 'https:' ? 443 : 80),
                path: parsedUrl.pathname + parsedUrl.search,
                method: 'GET',
                headers: headers
            };

            const proxyReq = client.request(options, (proxyRes) => {
                const statusCode = proxyRes.statusCode;

                // Handle Redirects (301, 302, 303, 307, 308)
                if ((statusCode === 301 || statusCode === 302 || statusCode === 303 || statusCode === 307 || statusCode === 308) && proxyRes.headers.location) {
                    let location = proxyRes.headers.location;
                    try {
                        const resolvedUrl = new URL(location, currentUrlStr).toString();
                        console.log(`[Proxy Redirect ${redirectCount + 1}] From ${currentUrlStr} To ${resolvedUrl}`);
                        proxyRes.resume(); // free connection
                        doProxy(resolvedUrl, redirectCount + 1);
                    } catch (e) {
                        res.status(500).send(`Erro ao processar redirecionamento para ${location}: ${e.message}`);
                    }
                    return;
                }

                // Construct response headers
                const responseHeaders = {
                    'Content-Type': proxyRes.headers['content-type'] || 'application/octet-stream',
                    'Access-Control-Allow-Methods': 'GET, OPTIONS',
                    'Access-Control-Allow-Headers': '*'
                };

                const currentOrigin = req.headers.origin;
                if (ALLOWED_ORIGINS.includes(currentOrigin)) {
                    responseHeaders['Access-Control-Allow-Origin'] = currentOrigin;
                }

                // Forward vital headers for media byte ranges, content length, and seeking
                if (proxyRes.headers['content-range']) {
                    responseHeaders['Content-Range'] = proxyRes.headers['content-range'];
                }
                if (proxyRes.headers['accept-ranges']) {
                    responseHeaders['Accept-Ranges'] = proxyRes.headers['accept-ranges'];
                }
                if (proxyRes.headers['content-length']) {
                    responseHeaders['Content-Length'] = proxyRes.headers['content-length'];
                }

                res.writeHead(statusCode, responseHeaders);

                // Pipe the data directly
                proxyRes.pipe(res);
            });

            proxyReq.on('error', (err) => {
                console.error('IPTV Proxy Error:', err);
                res.status(500).send(`Erro de conexão do proxy: ${err.message}`);
            });

            proxyReq.end();
        } catch (error) {
            console.error('IPTV Proxy Setup Error:', error);
            res.status(500).send(`Erro ao configurar proxy: ${error.message}`);
        }
    }

    try {
        doProxy(decodedUrl);
    } catch (error) {
        console.error('IPTV Proxy Decode Error:', error);
        res.status(500).send(`Erro ao decodificar URL: ${error.message}`);
    }
};
