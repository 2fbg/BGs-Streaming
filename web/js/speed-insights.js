/**
 * Vercel Speed Insights Integration
 * 
 * This script initializes Vercel Speed Insights to track web vitals
 * and performance metrics for the MK21 MultiServidor Web application.
 * 
 * Documentation: https://vercel.com/docs/speed-insights/quickstart
 */

// Import the injectSpeedInsights function from the CDN
import { injectSpeedInsights } from 'https://cdn.jsdelivr.net/npm/@vercel/speed-insights@latest/dist/index.mjs';

// Initialize Speed Insights
// This will automatically track Core Web Vitals (LCP, FID, CLS, FCP, TTFB, INP)
injectSpeedInsights({
    // Framework identifier for vanilla JS
    framework: 'vanilla',
    
    // Enable debug mode in development (can be toggled based on environment)
    debug: false,
    
    // Sample rate: 1.0 means 100% of sessions are tracked
    // Adjust this value if you want to reduce data collection
    sampleRate: 1.0
});

console.log('Vercel Speed Insights initialized successfully');
