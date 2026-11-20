<template>
  <div class="device-group-container">
    <div
      v-for="device in list"
      :key="device"
      class="device-item"
      :title="getLabel(device)"
    >
      <DeviceIcon :name="device" />
      <span v-if="showLabels" class="item-name">{{ getLabel(device) }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import DeviceIcon from './DeviceIcon.vue'

const props = defineProps({
  devices: {
    type: [Array, String],
    required: true
  },
  showLabels: {
    type: Boolean,
    default: false
  }
})

const list = computed(() => {
  if (Array.isArray(props.devices)) return props.devices
  return props.devices.split(',').map(s => s.trim().toLowerCase())
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

const getLabel = (name) => labelMap[name] || name
</script>

<style scoped>
.device-group-container {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 5px 12px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 9999px;
  vertical-align: middle;
}

html:not(.dark) .device-group-container {
  background: rgba(0, 0, 0, 0.03);
  border: 1px solid rgba(0, 0, 0, 0.08);
}

.device-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #94a3b8;
  transition: color 0.2s ease, transform 0.2s ease;
  cursor: pointer;
}

.device-item:hover {
  color: #a855f7;
  transform: translateY(-1px);
}

.item-name {
  font-size: 0.72rem;
  letter-spacing: -0.01em;
}
</style>
