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

    try {
        const decodedUrl = decodeURIComponent(targetUrlStr);
        const parsedUrl = urlModule.parse(decodedUrl);
        
        const client = parsedUrl.protocol === 'https:' ? https : http;
        
        const options = {
            hostname: parsedUrl.hostname,
            port: parsedUrl.port || (parsedUrl.protocol === 'https:' ? 443 : 80),
            path: parsedUrl.path || parsedUrl.pathname + (parsedUrl.search || ''),
            method: 'GET',
            headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
                'Accept': '*/*'
            }
        };

        const proxyReq = client.request(options, (proxyRes) => {
            // Forward HTTP status code & content type
            res.writeHead(proxyRes.statusCode, {
                'Content-Type': proxyRes.headers['content-type'] || 'application/octet-stream',
                'Access-Control-Allow-Origin': '*',
                'Access-Control-Allow-Methods': 'GET, OPTIONS',
                'Access-Control-Allow-Headers': '*'
            });
            
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
};
