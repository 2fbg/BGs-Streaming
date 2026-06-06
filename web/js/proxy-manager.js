/**
 * Proxy Manager Module
 * Handles CORS, Mixed Content (HTTP in HTTPS), and provides multiple proxy strategies
 */

class ProxyManager {
    constructor() {
        this.proxyMethods = [
            {
                name: 'vercel',
                label: 'Proxy Vercel Dedicado',
                priority: 1,
                getUrl: (url) => `${window.location.protocol}//${window.location.host}/api/proxy?url=${encodeURIComponent(url)}`
            },
            {
                name: 'corsproxy',
                label: 'Proxy CorsProxy.io',
                priority: 2,
                getUrl: (url) => `https://corsproxy.io/?${encodeURIComponent(url)}`
            },
            {
                name: 'allorigins',
                label: 'Proxy AllOrigins',
                priority: 3,
                getUrl: (url) => `https://api.allorigins.win/raw?url=${encodeURIComponent(url)}`
            },
            {
                name: 'direct',
                label: 'Conexão Direta',
                priority: 0,
                getUrl: (url) => url
            }
        ];
        this.selectedProxy = localStorage.getItem('mk21_cors') || 'auto';
        this.proxyLatencies = {};
    }

    getProxyUrl(url, strategy = null) {
        const proxyStrategy = strategy || this.selectedProxy;
        if (window.location.protocol === 'https:' && url.startsWith('http://')) {
            return this.proxyMethods[0].getUrl(url); // Force proxy for mixed content
        }
        if (proxyStrategy === 'auto') return this.getAutoProxyUrl(url);
        if (proxyStrategy === 'direct') return url;
        const method = this.proxyMethods.find(m => m.name === proxyStrategy);
        return method ? method.getUrl(url) : url;
    }

    getAutoProxyUrl(url) {
        return this.proxyMethods[0].getUrl(url); // Default to Vercel for auto
    }

    async testProxyLatencies() {
        // Simplified latency test
        for (const method of this.proxyMethods) {
            if (method.name === 'direct') continue;
            try {
                const start = performance.now();
                await fetch(method.getUrl('https://www.google.com/favicon.ico'), { mode: 'no-cors' });
                this.proxyLatencies[method.name] = Math.round(performance.now() - start);
            } catch (e) {
                this.proxyLatencies[method.name] = 9999;
            }
        }
        return this.proxyLatencies;
    }

    setProxyMethod(method) {
        this.selectedProxy = method;
        localStorage.setItem('mk21_cors', method);
    }

    getAvailableProxies() {
        return this.proxyMethods.map(m => ({ name: m.name, label: m.label }));
    }
}

const proxyManager = new ProxyManager();
