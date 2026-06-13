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
        res.status(200).end();
        return;
    }

    const targetUrlStr = req.query.url;

    if (!targetUrlStr) {
        res.status(400).send('Falta o parâmetro url com o link de destino.');
        return;
    }

    function doProxy(currentUrlStr, redirectCount = 0) {
        if (redirectCount > 5) {
            res.status(502).send('Error: Too many redirects on remote stream');
            return;
        }

        try {
            const parsedUrl = urlModule.parse(currentUrlStr);
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
                path: parsedUrl.path || parsedUrl.pathname + (parsedUrl.search || ''),
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
                    'Access-Control-Allow-Origin': '*',
                    'Access-Control-Allow-Methods': 'GET, OPTIONS',
                    'Access-Control-Allow-Headers': '*'
                };

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
                
                // Pipe the data directly - this handles both text (M3U8) and binary (TS) perfectly with minimal memory footprint
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
        const decodedUrl = decodeURIComponent(targetUrlStr);
        doProxy(decodedUrl);
    } catch (error) {
        console.error('IPTV Proxy Decode Error:', error);
        res.status(500).send(`Erro ao decodificar URL: ${error.message}`);
    }
};
