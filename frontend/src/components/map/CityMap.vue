<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { LocateFixed, MapPin } from 'lucide-vue-next'
import L, { type CircleMarker, type LatLngBounds, type Map as LeafletMap, type Marker } from 'leaflet'
import 'leaflet/dist/leaflet.css'
import type { Activity } from '@/types'

const props = defineProps<{ activities: Activity[]; compact?: boolean }>()
const emit = defineEmits<{
  'bounds-change': [bounds: { minLng: number; maxLng: number; minLat: number; maxLat: number }]
  'location-change': [location: { latitude: number; longitude: number }]
}>()

const mapEl = ref<HTMLDivElement | null>(null)
const selected = ref<Activity | null>(null)
const mapError = ref('')
let map: LeafletMap | null = null
let markers: Marker[] = []
let userMarker: CircleMarker | null = null
let shouldAutoFit = true

function toBounds(bounds: LatLngBounds) {
  const west = bounds.getWest()
  const east = bounds.getEast()
  const south = bounds.getSouth()
  const north = bounds.getNorth()
  emit('bounds-change', { minLng: west, maxLng: east, minLat: south, maxLat: north })
}

function clearMarkers() {
  markers.forEach(marker => marker.remove())
  markers = []
}

function markerIcon(index: number) {
  return L.divIcon({
    className: 'quju-marker',
    html: `<span>${index + 1}</span>`,
    iconSize: [34, 34],
    iconAnchor: [17, 34],
  })
}

function refreshMarkers() {
  if (!map) return
  clearMarkers()
  props.activities.forEach((activity, index) => {
    if (!Number.isFinite(Number(activity.latitude)) || !Number.isFinite(Number(activity.longitude))) return
    const marker = L.marker([Number(activity.latitude), Number(activity.longitude)], { icon: markerIcon(index) })
      .addTo(map!)
      .on('click', () => { selected.value = activity })
    markers.push(marker)
  })
  if (props.activities.length && markers.length && !props.compact) {
    if (!shouldAutoFit) return
    const group = L.featureGroup(markers)
    map.fitBounds(group.getBounds().pad(0.2), { maxZoom: 14 })
  }
}

function locateMe() {
  mapError.value = ''
  if (!navigator.geolocation || !map) {
    mapError.value = '无法读取定位，可继续通过列表或手动筛选浏览活动。'
    return
  }
  navigator.geolocation.getCurrentPosition(
    position => {
      if (!map) return
      const latitude = position.coords.latitude
      const longitude = position.coords.longitude
      shouldAutoFit = false
      userMarker?.remove()
      userMarker = L.circleMarker([latitude, longitude], {
        radius: 8,
        color: '#fff',
        weight: 3,
        fillColor: '#22b8a7',
        fillOpacity: 1,
      }).addTo(map).bindTooltip('我的位置', { direction: 'top' })
      map.setView([latitude, longitude], 14, { animate: true })
      emit('location-change', { latitude, longitude })
    },
    () => { mapError.value = '定位授权未开启，可继续通过列表或手动筛选浏览活动。' },
    { timeout: 5000 },
  )
}

onMounted(async () => {
  await nextTick()
  if (!mapEl.value) return
  map = L.map(mapEl.value, { zoomControl: !props.compact, attributionControl: !props.compact })
    .setView([30.274085, 120.15507], props.compact ? 11 : 12)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap',
  })
    .on('tileerror', () => { mapError.value = '地图服务暂不可用，已保留活动列表和手动筛选入口。' })
    .addTo(map)
  map.on('moveend zoomend', () => { if (map) toBounds(map.getBounds()) })
  refreshMarkers()
  setTimeout(() => map?.invalidateSize(), 50)
})

watch(() => props.activities, refreshMarkers, { deep: true })

onBeforeUnmount(() => {
  clearMarkers()
  userMarker?.remove()
  userMarker = null
  map?.remove()
  map = null
})
</script>

<template>
  <div :class="['real-map', { compact }]">
    <div ref="mapEl" class="leaflet-host"></div>
    <button class="locate-btn" type="button" @click="locateMe"><LocateFixed :size="17" />定位到我</button>
    <div class="map-legend"><span></span>{{ activities.length }} 场活动在当前范围</div>
    <div v-if="mapError" class="map-error">{{ mapError }}</div>
    <RouterLink v-if="selected" class="map-summary" :to="`/activities/${selected.id}`">
      <img :src="selected.cover" :alt="selected.title" />
      <span>
        <b>{{ selected.title }}</b>
        <small><MapPin :size="13" />{{ selected.district }} · {{ selected.location }}</small>
      </span>
    </RouterLink>
  </div>
</template>

<style scoped>
.real-map{position:relative;z-index:0;isolation:isolate;min-height:600px;overflow:hidden;border:1px solid #dfe3db;border-radius:var(--radius-lg);background:#e9ece7}
.real-map.compact{min-height:390px}
.leaflet-host{position:absolute;z-index:0;inset:0}
.locate-btn,.map-legend,.map-error,.map-summary{position:absolute;z-index:20;background:#fff;border:0;border-radius:var(--radius-pill);box-shadow:var(--shadow-soft);display:flex;align-items:center;gap:7px;font-size:12px;font-weight:700}
.locate-btn{right:18px;top:18px;padding:10px 14px}
.map-legend{left:18px;bottom:18px;padding:10px 14px}
.map-legend span{width:8px;height:8px;background:var(--color-primary);border-radius:50%}
.map-error{left:18px;right:18px;top:18px;padding:10px 14px;border-radius:10px;color:var(--color-danger);font-weight:700}
.map-summary{left:18px;right:18px;bottom:64px;padding:10px;border-radius:12px;color:var(--color-ink)}
.map-summary img{width:62px;height:48px;border-radius:8px;object-fit:cover}
.map-summary span{min-width:0;display:flex;flex-direction:column;gap:5px}
.map-summary b{overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.map-summary small{display:flex;align-items:center;gap:4px;color:var(--color-ink-soft);font-size:10px}
:global(.quju-marker){width:34px!important;height:34px!important;border:3px solid #fff;background:var(--color-primary);color:#fff;border-radius:50% 50% 50% 8px;box-shadow:0 8px 18px rgba(23,34,56,.24);display:grid;place-items:center;transform:rotate(-45deg)}
:global(.quju-marker span){display:grid;place-items:center;width:100%;height:100%;font-size:10px;font-weight:900;transform:rotate(45deg)}
:global(.leaflet-control-attribution){font-size:9px}
@media(max-width:600px){.real-map{min-height:470px}.map-summary{bottom:60px}}
</style>
