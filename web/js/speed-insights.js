/**
 * Vercel Speed Insights Integration
 * 
 * This module initializes Vercel Speed Insights for performance monitoring.
 * Speed Insights tracks Core Web Vitals and provides real-world performance data.
 * 
 * Note: Speed Insights only collects data in production (not in development mode).
 * Data will be visible in the Vercel Dashboard after deploying to production.
 */

import { injectSpeedInsights } from '../node_modules/@vercel/speed-insights/dist/index.js';

/**
 * Initialize Speed Insights with optional configuration
 */
function initSpeedInsights() {
  try {
    // Inject Speed Insights with default configuration
    // This will automatically track Core Web Vitals (LCP, FID, CLS, etc.)
    const speedInsights = injectSpeedInsights({
      // Enable debug mode in development (automatically detected)
      debug: window.location.hostname === 'localhost' || 
             window.location.hostname === '127.0.0.1',
      
      // Optional: Sample rate (1.0 = 100% of events tracked)
      // Reduce this value if you want to sample a percentage of users
      sampleRate: 1.0,
      
      // Optional: beforeSend middleware to modify or filter events
      beforeSend: (event) => {
        // You can modify the event here or return null to cancel it
        // For example, to filter out specific URLs:
        // if (event.url.includes('/admin')) return null;
        return event;
      }
    });

    if (speedInsights) {
      console.log('[Speed Insights] Performance tracking initialized');
    }
  } catch (error) {
    // Silently fail if Speed Insights cannot be loaded
    // This prevents breaking the site if the package is not available
    console.warn('[Speed Insights] Failed to initialize:', error);
  }
}

// Initialize Speed Insights when the script loads
initSpeedInsights();

// Export for potential use in other modules
export { initSpeedInsights };
