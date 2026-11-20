<template>
  <div class="home-container">
    <!-- Hero Section -->
    <div class="hero-section">
      <h1 class="hero-title">NoxMedia</h1>
      <p class="hero-tagline">Designed for Your Media.</p>

      <div class="hero-actions">
        <a :href="withBase('/guide/getting-started')" class="btn-primary">Get Started</a>
        <a href="https://github.com/TheAceOfficials/NoxMedia/releases/latest" class="btn-secondary" target="_blank" rel="noopener">Download App</a>
        <a href="https://www.patreon.com/cw/TheAceOfficials" class="btn-tertiary" target="_blank" rel="noopener">Donation / Support</a>
      </div>
    </div>

    <!-- Access NoxMedia Server Showcase -->
    <div class="access-section">
      <div class="access-header">
        <div class="access-tag">Universal DirectPlay</div>
        <h2 class="access-title">Access NoxMedia Server</h2>
        <p class="access-subtitle">Stream and play effortlessly across all your screens and platforms</p>
      </div>

      <div class="device-strip">
        <!-- Group 1: Mobile & Tablets -->
        <div class="device-cluster">
          <a 
            v-for="d in mobileDevices" 
            :key="d.name" 
            :href="withBase('/guide/getting-started#nuvio')" 
            class="device-item"
            :title="d.label"
          >
            <div class="device-icon">
              <DeviceIcon :name="d.name" />
            </div>
            <span class="device-name">{{ d.label }}</span>
          </a>
        </div>

        <div class="device-divider" />

        <!-- Group 2: TVs -->
        <div class="device-cluster">
          <a 
            v-for="d in tvDevices" 
            :key="d.name" 
            :href="withBase('/guide/getting-started#nuvio')" 
            class="device-item"
            :title="d.label"
          >
            <div class="device-icon">
              <DeviceIcon :name="d.name" />
            </div>
            <span class="device-name">{{ d.label }}</span>
          </a>
        </div>

        <div class="device-divider" />

        <!-- Group 3: Desktop -->
        <div class="device-cluster">
          <a 
            v-for="d in desktopDevices" 
            :key="d.name" 
            :href="withBase('/guide/getting-started#nuvio')" 
            class="device-item"
            :title="d.label"
          >
            <div class="device-icon">
              <DeviceIcon :name="d.name" />
            </div>
            <span class="device-name">{{ d.label }}</span>
          </a>
        </div>
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
import { withBase } from 'vitepress'
import DeviceIcon from './DeviceIcon.vue'

// 1. Android Phone, Tablet, iPhone, iPad
const mobileDevices = [
  { name: 'android-phone', label: 'Android Phone' },
  { name: 'android-tablet', label: 'Tablet' },
  { name: 'iphone', label: 'iPhone' },
  { name: 'ipad', label: 'iPad' }
]

// 2. Smart TV, Android TV, Apple TV, Vision Pro
const tvDevices = [
  { name: 'smart-tv', label: 'Smart TV' },
  { name: 'android-tv', label: 'Android TV' },
  { name: 'appletv', label: 'Apple TV' },
  { name: 'visionpro', label: 'Vision Pro' }
]

// 3. Windows, Linux, MacBook (Web Browser removed)
const desktopDevices = [
  { name: 'windows', label: 'Windows' },
  { name: 'linux', label: 'Linux' },
  { name: 'mac', label: 'MacBook' }
]
</script>

<style scoped>
.home-container {
  min-height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 3.5rem 1.5rem 2rem 1.5rem;
  max-width: 1040px;
  margin: 0 auto;
}

/* Hero Section */
.hero-section {
  text-align: center;
  padding: 2.5rem 0 3.5rem 0;
}

.hero-title {
  font-family: 'NDot', monospace;
  font-size: clamp(2.2rem, 5vw, 3.25rem);
  font-weight: 400;
  letter-spacing: 0.05em;
  color: #ffffff;
  margin: 0 0 0.5rem 0;
  line-height: 1.1;
  text-shadow: 0 0 30px rgba(168, 85, 247, 0.35);
}

:root:not(.dark) .hero-title {
  color: #0f172a;
  text-shadow: none;
}

.hero-tagline {
  font-size: clamp(0.98rem, 2vw, 1.18rem);
  color: #94a3b8;
  font-weight: 400;
  letter-spacing: -0.015em;
  margin: 0 auto 2.2rem auto;
  max-width: 520px;
  line-height: 1.4;
}

:root:not(.dark) .hero-tagline {
  color: #64748b;
}

.hero-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 9px 22px;
  font-size: 0.88rem;
  font-weight: 550;
  border-radius: 9999px;
  color: #ffffff !important;
  background: linear-gradient(135deg, #a855f7, #7c3aed);
  box-shadow: 0 4px 18px -2px rgba(168, 85, 247, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.15);
  text-decoration: none !important;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.btn-primary:hover {
  background: linear-gradient(135deg, #c084fc, #9333ea);
  box-shadow: 0 6px 24px 0 rgba(168, 85, 247, 0.6);
  transform: translateY(-1px);
}

.btn-secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 9px 20px;
  font-size: 0.88rem;
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
  padding: 9px 18px;
  font-size: 0.88rem;
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
  padding: 2.5rem 0 2rem 0;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

:root:not(.dark) .access-section {
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}

.access-header {
  text-align: center;
  margin-bottom: 2rem;
}

.access-tag {
  display: inline-block;
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #a855f7;
  margin-bottom: 0.4rem;
}

:root:not(.dark) .access-tag {
  color: #7c3aed;
}

.access-title {
  font-size: clamp(1.4rem, 3vw, 1.9rem);
  font-weight: 650;
  letter-spacing: -0.03em;
  color: #ffffff;
  margin: 0 0 0.35rem 0;
}

:root:not(.dark) .access-title {
  color: #0f172a;
}

.access-subtitle {
  font-size: 0.88rem;
  color: #94a3b8;
  margin: 0 auto;
  max-width: 480px;
}

:root:not(.dark) .access-subtitle {
  color: #64748b;
}

/* Sleek Minimal Device Strip - NO CARDS, Clean Canvas Flow */
.device-strip {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 16px 20px;
  max-width: 920px;
  margin: 0 auto;
}

.device-cluster {
  display: flex;
  align-items: center;
  gap: 8px 16px;
  flex-wrap: wrap;
  justify-content: center;
}

.device-divider {
  width: 1px;
  height: 24px;
  background: rgba(255, 255, 255, 0.1);
  margin: 0 4px;
}

:root:not(.dark) .device-divider {
  background: rgba(0, 0, 0, 0.1);
}

.device-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 6px 10px;
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
  text-decoration: none !important;
  color: #94a3b8 !important;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
  cursor: pointer;
}

:root:not(.dark) .device-item {
  color: #64748b !important;
}

.device-item:hover {
  color: #ffffff !important;
  transform: translateY(-2px);
}

:root:not(.dark) .device-item:hover {
  color: #7c3aed !important;
}

.device-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  color: inherit;
  transition: color 0.2s ease, filter 0.2s ease;
}

.device-item:hover .device-icon {
  color: #c084fc;
  filter: drop-shadow(0 0 8px rgba(168, 85, 247, 0.5));
}

.device-name {
  font-size: 0.74rem;
  font-weight: 450;
  letter-spacing: -0.01em;
  color: inherit;
  white-space: nowrap;
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
  font-family: var(--vp-font-family-base);
  color: #64748b;
}

:root:not(.dark) .home-bottom-bar {
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}

.bottom-left {
  font-family: inherit;
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
  gap: 8px;
  font-family: inherit;
  font-size: 0.82rem;
}

.version-group {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: inherit;
}

.version-label {
  color: #64748b;
  font-weight: 400;
  font-family: inherit;
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
  font-family: inherit;
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

@media (max-width: 768px) {
  .device-divider {
    display: none;
  }
}

@media (max-width: 640px) {
  .home-bottom-bar {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
}
</style>
