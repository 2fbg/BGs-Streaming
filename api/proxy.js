// Vercel Serverless Function to act as a secure, fast, and dedicated streaming CORS Proxy for MK21 IPTV Web.
// Since Node/Vercel acts server-side, it bypasses browser CORS and Mixed Content blocks.
// Pipes binary streams directly from the source to prevent out-of-memory errors and timeout limits.

const http = require('http');
const https = require('https');
const urlModule = require('url');

module.exports = function handler(req, res) {
    // Set permissive CORS headers for the proxy
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', '*');

    // Handle preflight OPTIONS request
    if (req.method === 'OPTIONS') {
        res.statusCode = 200;
        res.end();
        return;
    }

    // Get the target URL parameter
    // Support both standard Node.js query as well as Vercel helper req.query
    let targetUrlStr = null;
    if (req.query && req.query.url) {
        targetUrlStr = req.query.url;
    } else {
        const parsedQueryStr = urlModule.parse(req.url, true).query;
        targetUrlStr = parsedQueryStr.url;
    }

    if (!targetUrlStr) {
        res.statusCode = 400;
        res.end('Falta o parâmetro url com o link de destino.');
        return;
    }

    try {
        const decodedUrl = decodeURIComponent(targetUrlStr);
        const maxRedirects = 6;

        function handleRequest(urlStr, redirectCount = 0) {
            if (redirectCount > maxRedirects) {
                res.statusCode = 502;
                res.end('Erro: Excesso de redirecionamentos (Redirect Loop)');
                return;
            }

            const parsedUrl = urlModule.parse(urlStr);
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
                path: parsedUrl.path || parsedUrl.pathname + (parsedUrl.search || ''),
                method: 'GET',
                headers: forwardHeaders
            };

            const proxyReq = client.request(options, (proxyRes) => {
                const statusCode = proxyRes.statusCode;

                // Check for HTTP Redirection redirects and follow them internally
                if ((statusCode === 301 || statusCode === 302 || statusCode === 303 || statusCode === 307 || statusCode === 308) && proxyRes.headers.location) {
                    let redirUrl = proxyRes.headers.location;
                    if (!redirUrl.startsWith('http://') && !redirUrl.startsWith('https://')) {
                        redirUrl = urlModule.resolve(urlStr, redirUrl);
                    }
                    console.log(`Proxy following redirect to (depth ${redirectCount}): ${redirUrl}`);
                    handleRequest(redirUrl, redirectCount + 1);
                    return;
                }

                // Copy stream and media relevant response headers
                const responseHeaders = {
                    'Access-Control-Allow-Origin': '*',
                    'Access-Control-Allow-Methods': 'GET, OPTIONS',
                    'Access-Control-Allow-Headers': '*'
                };
                
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
