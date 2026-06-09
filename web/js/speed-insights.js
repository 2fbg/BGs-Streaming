/**
 * Vercel Speed Insights Integration
 * Tracks web vitals and performance metrics for the application
 */

import { injectSpeedInsights } from '../node_modules/@vercel/speed-insights/dist/index.mjs';

// Initialize Speed Insights when the DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initSpeedInsights);
} else {
    initSpeedInsights();
}

function initSpeedInsights() {
    try {
        injectSpeedInsights({
            // Enable debug mode in development for easier troubleshooting
            debug: false,
            // Optional: Sample rate (1 = 100% of events tracked)
            sampleRate: 1
        });
        console.log('[Speed Insights] Successfully initialized');
    } catch (error) {
        console.error('[Speed Insights] Failed to initialize:', error);
    }
}
