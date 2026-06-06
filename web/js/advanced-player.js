/**
 * Advanced Player Module
 * Supports: MPEG-TS, HLS, MP4, WebM
 */

class AdvancedPlayer {
    constructor(videoElement) {
        this.videoElement = videoElement;
        this.currentPlayer = null;
    }

    async play(url, options = {}) {
        const { isLive = false } = options;
        this.stopCurrentPlayer();

        const isMpegTs = url.includes('/ts') || url.includes('.ts') || url.includes('mpegts');
        const isHls = url.includes('.m3u8') || url.includes('m3u8');

        try {
            if (isMpegTs && typeof mpegts !== 'undefined' && mpegts.isSupported()) {
                this.currentPlayer = mpegts.createPlayer({ type: 'mpegts', isLive, url, cors: true });
                this.currentPlayer.attachMediaElement(this.videoElement);
                this.currentPlayer.load();
                await this.currentPlayer.play();
                return true;
            } else if (isHls && typeof Hls !== 'undefined' && Hls.isSupported()) {
                this.currentPlayer = new Hls();
                this.currentPlayer.loadSource(url);
                this.currentPlayer.attachMedia(this.videoElement);
                return new Promise((resolve) => {
                    this.currentPlayer.on(Hls.Events.MANIFEST_PARSED, () => {
                        this.videoElement.play();
                        resolve(true);
                    });
                });
            } else {
                this.videoElement.src = url;
                await this.videoElement.play();
                return true;
            }
        } catch (e) {
            console.error('Playback failed:', e);
            return false;
        }
    }

    stopCurrentPlayer() {
        if (this.currentPlayer) {
            if (this.currentPlayer.destroy) this.currentPlayer.destroy();
            this.currentPlayer = null;
        }
        this.videoElement.pause();
        this.videoElement.removeAttribute('src');
        this.videoElement.load();
    }
}
