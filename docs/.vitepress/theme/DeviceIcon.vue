<template>
  <span class="device-icon-wrapper" :title="label">
    <!-- iPhone -->
    <svg v-if="name === 'iphone'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
      <rect x="6" y="2" width="12" height="20" rx="3.5" ry="3.5" />
      <line x1="10" y1="5.2" x2="14" y2="5.2" stroke-width="2" />
      <line x1="9.5" y1="18.8" x2="14.5" y2="18.8" />
    </svg>

    <!-- iPad -->
    <svg v-else-if="name === 'ipad'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
      <rect x="4" y="2.5" width="16" height="19" rx="3" ry="3" />
      <line x1="9.5" y1="18.5" x2="14.5" y2="18.5" />
    </svg>

    <!-- Mac -->
    <svg v-else-if="name === 'mac'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
      <rect x="3.5" y="4" width="17" height="11.5" rx="2" ry="2" />
      <path d="M10.5 4v1.2h3V4" stroke-width="1.5" />
      <path d="M1.5 18h21a1 1 0 0 0 1-1v-.5H.5v.5a1 1 0 0 0 1 1z" />
      <line x1="10" y1="16.2" x2="14" y2="16.2" />
    </svg>

    <!-- Apple TV -->
    <svg v-else-if="name === 'appletv'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
      <rect x="3" y="6" width="18" height="12" rx="3.5" ry="3.5" />
      <line x1="3" y1="12" x2="21" y2="12" stroke-width="1.2" opacity="0.6" />
      <circle cx="12" cy="15" r="0.75" fill="currentColor" />
    </svg>

    <!-- Apple Vision Pro -->
    <svg v-else-if="name === 'visionpro'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
      <path d="M3.5 10.5 C3.5 6.5, 7 5, 12 5 C17 5, 20.5 6.5, 20.5 10.5 C20.5 14.5, 18 17, 15 17 C13.5 17, 12.8 15, 12 15 C11.2 15, 10.5 17, 9 17 C6 17, 3.5 14.5, 3.5 10.5 Z" />
    </svg>

    <!-- Windows Desktop (Monitor + Tower) -->
    <svg v-else-if="name === 'windows'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
      <rect x="2" y="4" width="13.5" height="11.5" rx="2" ry="2" />
      <line x1="4.5" y1="19" x2="13" y2="19" stroke-width="1.8" />
      <rect x="13.5" y="2" width="8.5" height="17" rx="2.5" ry="2.5" />
      <line x1="16" y1="5.5" x2="19.5" y2="5.5" stroke-width="1.5" />
      <line x1="16" y1="7.5" x2="19.5" y2="7.5" stroke-width="1.5" />
      <circle cx="17.75" cy="15" r="0.75" fill="currentColor" />
    </svg>

    <!-- Linux / CLI Terminal -->
    <svg v-else-if="name === 'linux'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
      <rect x="2.5" y="2.5" width="19" height="19" rx="4.5" ry="4.5" />
      <circle cx="6.5" cy="6.5" r="0.8" fill="currentColor" stroke="none" />
      <circle cx="9.5" cy="6.5" r="0.8" fill="currentColor" stroke="none" />
      <circle cx="12.5" cy="6.5" r="0.8" fill="currentColor" stroke="none" />
      <polyline points="6.5 11.5 10 14 6.5 16.5" stroke-width="1.8" />
      <line x1="12" y1="16.5" x2="16.5" y2="16.5" stroke-width="2" />
    </svg>

    <!-- Android Smartphone -->
    <svg v-else-if="name === 'android-phone'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
      <rect x="5.5" y="2" width="13" height="20" rx="3.5" ry="3.5" />
      <line x1="5.5" y1="5.2" x2="18.5" y2="5.2" stroke-width="1.2" />
      <line x1="5.5" y1="18.8" x2="18.5" y2="18.8" stroke-width="1.2" />
      <line x1="10" y1="3.6" x2="13" y2="3.6" stroke-width="1.2" />
      <circle cx="14.8" cy="3.6" r="0.5" fill="currentColor" stroke="none" />
      <line x1="10.5" y1="20.4" x2="13.5" y2="20.4" stroke-width="1.2" />
      <line x1="4.2" y1="6.8" x2="4.2" y2="8.8" stroke-width="1.5" />
      <line x1="4.2" y1="9.8" x2="4.2" y2="11.8" stroke-width="1.5" />
      <line x1="19.8" y1="8" x2="19.8" y2="10.5" stroke-width="1.5" />
    </svg>

    <!-- Android Tablet -->
    <svg v-else-if="name === 'android-tablet'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
      <rect x="3.5" y="2" width="17" height="20" rx="3" ry="3" />
      <rect x="5.2" y="4.2" width="13.6" height="15.6" rx="1.2" stroke-width="1.2" />
      <circle cx="9" cy="3.1" r="0.45" fill="currentColor" stroke="none" />
      <line x1="10.8" y1="3.1" x2="13.2" y2="3.1" stroke-width="0.9" />
      <circle cx="15" cy="3.1" r="0.45" fill="currentColor" stroke="none" />
      <line x1="21.4" y1="5.5" x2="21.4" y2="7.5" stroke-width="1.5" />
      <line x1="21.4" y1="9" x2="21.4" y2="12" stroke-width="1.5" />
    </svg>

    <!-- Android TV / Google TV -->
    <svg v-else-if="name === 'android-tv'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
      <rect x="2" y="3.5" width="20" height="12.5" rx="1.5" ry="1.5" />
      <path d="M10 16 L9.2 18.2 C6 19, 3.5 19.8, 2.5 20.5" stroke-width="1.8" />
      <path d="M14 16 L14.8 18.2 C18 19, 20.5 19.8, 21.5 20.5" stroke-width="1.8" />
      <path d="M2.5 20.5 C7 19.2, 17 19.2, 21.5 20.5" stroke-width="1.8" />
    </svg>

    <!-- Smart TV Packages (Tizen / webOS) -->
    <svg v-else-if="name === 'smart-tv'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
      <rect x="2.5" y="3" width="19" height="13.5" rx="1.5" ry="1.5" />
      <line x1="5.5" y1="16.5" x2="3.5" y2="20.5" stroke-width="2" />
      <line x1="18.5" y1="16.5" x2="20.5" y2="20.5" stroke-width="2" />
      <circle cx="12" cy="12.5" r="0.75" fill="currentColor" stroke="none" />
      <path d="M9.8 10.3 C 10.5 9.6, 13.5 9.6, 14.2 10.3" stroke-width="1.5" />
      <path d="M7.8 8.3 C 9.5 6.6, 14.5 6.6, 16.2 8.3" stroke-width="1.5" />
    </svg>

    <!-- Web Browser -->
    <svg v-else-if="name === 'web'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
      <rect x="2.5" y="3.5" width="19" height="17" rx="3" ry="3" />
      <line x1="2.5" y1="8" x2="21.5" y2="8" stroke-width="1.4" />
      <circle cx="5.5" cy="5.8" r="0.65" fill="currentColor" stroke="none" />
      <circle cx="8" cy="5.8" r="0.65" fill="currentColor" stroke="none" />
      <circle cx="10.5" cy="5.8" r="0.65" fill="currentColor" stroke="none" />
    </svg>

    <!-- Generic TV / Monitor -->
    <svg v-else viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
      <rect x="2.5" y="4" width="19" height="12.5" rx="2.5" ry="2.5" />
      <line x1="9" y1="20" x2="15" y2="20" stroke-width="1.8" />
      <line x1="12" y1="16.5" x2="12" y2="20" stroke-width="1.8" />
    </svg>

    <span v-if="showLabel" class="device-label">{{ label }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  name: { type: String, required: true },
  showLabel: { type: Boolean, default: false }
})

const labelMap = {
  iphone: 'iPhone',
  ipad: 'iPad',
  mac: 'MacBook / macOS',
  appletv: 'Apple TV 4K',
  visionpro: 'Apple Vision Pro',
  windows: 'Windows PC',
  linux: 'Linux / Headless',
  'android-phone': 'Android Smartphone',
  'android-tablet': 'Android Tablet',
  'android-tv': 'Android TV / Google TV',
  'smart-tv': 'Smart TV (Tizen / webOS)',
  web: 'Web Browser',
  tv: 'TV / Monitor'
}

const label = computed(() => labelMap[props.name] || props.name)
</script>

<style scoped>
.device-icon-wrapper {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #94a3b8;
  transition: color 0.2s ease, transform 0.2s ease;
  vertical-align: middle;
}

.device-icon-wrapper:hover {
  color: #a855f7;
  transform: translateY(-1px);
}

.device-label {
  font-size: 0.75rem;
  letter-spacing: -0.01em;
}
</style>
