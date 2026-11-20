<template>
  <span class="device-icon-wrapper" :title="label">
    <svg v-if="name === 'iphone'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
      <rect x="6" y="2" width="12" height="20" rx="3.5" ry="3.5" />
      <line x1="10" y1="5.2" x2="14" y2="5.2" stroke-width="2" />
      <line x1="9.5" y1="18.8" x2="14.5" y2="18.8" />
    </svg>

    <svg v-else-if="name === 'ipad'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
      <rect x="4" y="2.5" width="16" height="19" rx="3" ry="3" />
      <line x1="9.5" y1="18.5" x2="14.5" y2="18.5" />
    </svg>

    <svg v-else-if="name === 'mac'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
      <rect x="3.5" y="4" width="17" height="11.5" rx="2" ry="2" />
      <path d="M10.5 4v1.2h3V4" stroke-width="1.5" />
      <path d="M1.5 18h21a1 1 0 0 0 1-1v-.5H.5v.5a1 1 0 0 0 1 1z" />
      <line x1="10" y1="16.2" x2="14" y2="16.2" />
    </svg>

    <svg v-else-if="name === 'appletv'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
      <rect x="3" y="6" width="18" height="12" rx="3.5" ry="3.5" />
      <line x1="3" y1="12" x2="21" y2="12" stroke-width="1.2" opacity="0.6" />
      <circle cx="12" cy="15" r="0.75" fill="currentColor" />
    </svg>

    <svg v-else-if="name === 'visionpro'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
      <path d="M3.5 10.5 C3.5 6.5, 7 5, 12 5 C17 5, 20.5 6.5, 20.5 10.5 C20.5 14.5, 18 17, 15 17 C13.5 17, 12.8 15, 12 15 C11.2 15, 10.5 17, 9 17 C6 17, 3.5 14.5, 3.5 10.5 Z" />
    </svg>

    <svg v-else-if="name === 'tv'" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
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
  mac: 'Mac',
  appletv: 'Apple TV 4K',
  visionpro: 'Apple Vision Pro',
  tv: 'Android / Google TV'
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
