// Vercel Serverless Function to act as a secure, fast, and dedicated CORS Proxy for MK21 IPTV Web.
// Since Node/Vercel acts server-side, it bypasses browser CORS and Mixed Content blocks.

module.exports = async function handler(req, res) {
    // Set permissive CORS headers for the proxy
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    // Handle preflight OPTIONS request
    if (req.method === 'OPTIONS') {
        res.status(200).end();
        return;
    }

    const { url } = req.query;

    if (!url) {
        res.status(400).send('Falta o parâmetro url com o link de destino.');
        return;
    }

    try {
        const decodedUrl = decodeURIComponent(url);
        
        // Node.js 18+ has native fetch support
        const response = await fetch(decodedUrl, {
            method: 'GET',
            headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
                'Accept': '*/*',
                'Connection': 'keep-alive'
            }
        });

        if (!response.ok) {
            res.status(response.status).send(`Erro retornado pelo servidor IPTV: ${response.status}`);
            return;
        }

        const contentType = response.headers.get('content-type') || 'text/plain';
        const data = await response.text();
        
        res.setHeader('Content-Type', contentType.includes('utf') ? contentType : 'text/plain; charset=utf-8');
        res.status(200).send(data);
    } catch (error) {
        console.error('IPTV Proxy Error:', error);
        res.status(500).send(`Erro interno ao processar requisição através do Proxy: ${error.message}`);
    }
};
