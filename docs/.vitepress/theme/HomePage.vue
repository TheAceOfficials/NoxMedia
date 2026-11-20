<template>
  <div class="home-container">
    <!-- Hero Section -->
    <div class="hero-section">
      <div class="hero-logo-box">
        <img src="/logo.png" alt="NoxMedia Logo" />
      </div>
      
      <h1 class="hero-title">NoxMedia</h1>
      <p class="hero-tagline">Designed for Your Media.</p>

      <div class="hero-actions">
        <a href="/guide/getting-started" class="btn-primary">Get Started</a>
        <a href="https://github.com/TheAceOfficials/NoxMedia/releases/latest" class="btn-secondary" target="_blank" rel="noopener">Download App</a>
        <a href="https://www.patreon.com/cw/TheAceOfficials" class="btn-tertiary" target="_blank" rel="noopener">Donation / Support</a>
      </div>
    </div>

    <!-- Access NoxMedia Server Across Devices Showcase -->
    <div class="access-section">
      <div class="access-header">
        <div class="access-tag">Universal DirectPlay</div>
        <h2 class="access-title">Access NoxMedia Server</h2>
        <p class="access-subtitle">Stream and play effortlessly across all your screens and platforms</p>
      </div>

      <div class="device-showcase-grid">
        <a 
          v-for="device in devices" 
          :key="device.name" 
          href="/guide/getting-started#clients-compatibility" 
          class="device-card"
        >
          <div class="device-card-icon">
            <DeviceIcon :name="device.name" />
          </div>
          <span class="device-card-title">{{ device.label }}</span>
        </a>
      </div>
    </div>

    <!-- Minimalist Bottom Bar -->
    <div class="home-bottom-bar">
      <div class="bottom-left">
        <span>Copyright &copy; 2026 TheAceOfficials &bull; <a href="https://github.com/TheAceOfficials/NoxMedia/blob/main/LICENSE" target="_blank" rel="noopener">AGPLv3 License</a></span>
      </div>
      <div class="bottom-right">
        <span class="version-group">
          <span class="version-label">Stable</span>
          <a href="https://github.com/TheAceOfficials/NoxMedia/releases/latest" target="_blank" rel="noopener" class="version-link">v1.4.320</a>
        </span>
        <span class="version-divider">&bull;</span>
        <span class="version-group">
          <span class="version-label">Beta</span>
          <a href="https://github.com/TheAceOfficials/NoxMedia/releases" target="_blank" rel="noopener" class="version-link">v1.4.325</a>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import DeviceIcon from './DeviceIcon.vue'

const devices = [
  { name: 'iphone', label: 'iPhone' },
  { name: 'android-phone', label: 'Android' },
  { name: 'ipad', label: 'iPad' },
  { name: 'android-tablet', label: 'Tablet' },
  { name: 'appletv', label: 'Apple TV' },
  { name: 'android-tv', label: 'Android TV' },
  { name: 'mac', label: 'MacBook' },
  { name: 'windows', label: 'Windows' },
  { name: 'linux', label: 'Linux' },
  { name: 'smart-tv', label: 'Smart TV' },
  { name: 'web', label: 'Web Browser' }
]
</script>

<style scoped>
.home-container {
  min-height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 3rem 1.5rem 2rem 1.5rem;
  max-width: 1100px;
  margin: 0 auto;
}

/* Hero Section */
.hero-section {
  text-align: center;
  padding: 2.5rem 0 3.5rem 0;
}

.hero-logo-box {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 96px;
  height: 96px;
  margin-bottom: 2rem;
  border-radius: 24px;
  background: radial-gradient(circle at 50% 50%, rgba(168, 85, 247, 0.2), transparent 70%), #0c0a14;
  border: 1px solid rgba(168, 85, 247, 0.3);
  box-shadow: 0 0 45px -5px rgba(168, 85, 247, 0.35);
}

:root:not(.dark) .hero-logo-box {
  background: radial-gradient(circle at 50% 50%, rgba(124, 58, 237, 0.12), transparent 70%), #f8fafc;
  border: 1px solid rgba(124, 58, 237, 0.25);
  box-shadow: 0 0 35px -5px rgba(124, 58, 237, 0.2);
}

.hero-logo-box img {
  width: 64px;
  height: 64px;
  border-radius: 14px;
}

.hero-title {
  font-size: clamp(2.75rem, 6.5vw, 4.5rem);
  font-weight: 700;
  letter-spacing: -0.04em;
  color: #ffffff;
  margin: 0 0 0.75rem 0;
  line-height: 1.05;
}

:root:not(.dark) .hero-title {
  color: #0f172a;
}

.hero-tagline {
  font-size: clamp(1.15rem, 2.5vw, 1.5rem);
  color: #94a3b8;
  font-weight: 400;
  letter-spacing: -0.015em;
  margin: 0 auto 2.5rem auto;
  max-width: 600px;
  line-height: 1.4;
}

:root:not(.dark) .hero-tagline {
  color: #64748b;
}

.hero-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  flex-wrap: wrap;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 11px 26px;
  font-size: 0.95rem;
  font-weight: 550;
  border-radius: 9999px;
  color: #ffffff !important;
  background: linear-gradient(135deg, #a855f7, #7c3aed);
  box-shadow: 0 4px 20px -2px rgba(168, 85, 247, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.15);
  text-decoration: none !important;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.btn-primary:hover {
  background: linear-gradient(135deg, #c084fc, #9333ea);
  box-shadow: 0 6px 28px 0 rgba(168, 85, 247, 0.6);
  transform: translateY(-1px);
}

.btn-secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 11px 24px;
  font-size: 0.95rem;
  font-weight: 500;
  border-radius: 9999px;
  color: #f8fafc !important;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(8px);
  text-decoration: none !important;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

:root:not(.dark) .btn-secondary {
  color: #0f172a !important;
  background: rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(0, 0, 0, 0.1);
}

.btn-secondary:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
  transform: translateY(-1px);
}

.btn-tertiary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 11px 22px;
  font-size: 0.95rem;
  font-weight: 500;
  border-radius: 9999px;
  color: #a855f7 !important;
  background: rgba(168, 85, 247, 0.08);
  border: 1px solid rgba(168, 85, 247, 0.25);
  text-decoration: none !important;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.btn-tertiary:hover {
  background: rgba(168, 85, 247, 0.16);
  border-color: rgba(168, 85, 247, 0.4);
  transform: translateY(-1px);
}

/* Access NoxMedia Server Section */
.access-section {
  padding: 3rem 0;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

:root:not(.dark) .access-section {
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}

.access-header {
  text-align: center;
  margin-bottom: 2.5rem;
}

.access-tag {
  display: inline-block;
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #a855f7;
  margin-bottom: 0.5rem;
}

:root:not(.dark) .access-tag {
  color: #7c3aed;
}

.access-title {
  font-size: clamp(1.6rem, 3.5vw, 2.3rem);
  font-weight: 650;
  letter-spacing: -0.03em;
  color: #ffffff;
  margin: 0 0 0.5rem 0;
}

:root:not(.dark) .access-title {
  color: #0f172a;
}

.access-subtitle {
  font-size: 0.95rem;
  color: #94a3b8;
  margin: 0 auto;
  max-width: 520px;
}

:root:not(.dark) .access-subtitle {
  color: #64748b;
}

/* Device Showcase Grid */
.device-showcase-grid {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 12px;
  max-width: 980px;
  margin: 0 auto;
}

.device-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 18px;
  min-width: 94px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.06);
  text-decoration: none !important;
  color: #94a3b8 !important;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

:root:not(.dark) .device-card {
  background: rgba(0, 0, 0, 0.02);
  border: 1px solid rgba(0, 0, 0, 0.06);
  color: #64748b !important;
}

.device-card:hover {
  background: rgba(168, 85, 247, 0.08);
  border-color: rgba(168, 85, 247, 0.3);
  color: #ffffff !important;
  transform: translateY(-2px);
  box-shadow: 0 4px 16px -2px rgba(168, 85, 247, 0.2);
}

:root:not(.dark) .device-card:hover {
  background: rgba(124, 58, 237, 0.08);
  border-color: rgba(124, 58, 237, 0.3);
  color: #7c3aed !important;
  box-shadow: 0 4px 16px -2px rgba(124, 58, 237, 0.15);
}

.device-card-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
}

.device-card-title {
  font-size: 0.76rem;
  font-weight: 500;
  letter-spacing: -0.01em;
}

/* Minimalist Bottom Bar */
.home-bottom-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  padding-top: 2rem;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  font-size: 0.82rem;
  color: #64748b;
}

:root:not(.dark) .home-bottom-bar {
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}

.bottom-left a {
  color: #94a3b8;
  text-decoration: none;
  transition: color 0.15s ease;
}

.bottom-left a:hover {
  color: #a855f7;
}

.bottom-right {
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: var(--vp-font-family-mono);
  font-size: 0.82rem;
}

.version-group {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.version-label {
  color: #64748b;
  font-weight: 450;
}

:root:not(.dark) .version-label {
  color: #94a3b8;
}

.version-divider {
  color: rgba(255, 255, 255, 0.2);
  font-size: 0.7rem;
}

:root:not(.dark) .version-divider {
  color: rgba(0, 0, 0, 0.2);
}

/* Version as interactive, clean text link without pill box */
.version-link {
  color: #c084fc !important;
  font-weight: 500;
  text-decoration: none !important;
  cursor: pointer;
  transition: all 0.15s ease;
}

:root:not(.dark) .version-link {
  color: #7c3aed !important;
}

.version-link:hover {
  color: #e9d5ff !important;
  text-decoration: underline !important;
  text-underline-offset: 3px;
}

:root:not(.dark) .version-link:hover {
  color: #6d28d9 !important;
}

@media (max-width: 640px) {
  .home-bottom-bar {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
}
</style>

