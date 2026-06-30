<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { LocateFixed, Search } from 'lucide-vue-next'
import L, { type Map as LeafletMap, type Marker } from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { apiGet } from '@/lib/api'

const emit = defineEmits<{
  select: [value: { location: string; district: string; longitude: number; latitude: number }]
}>()

const mapEl = ref<HTMLDivElement | null>(null)
const query = ref('')
const error = ref('')
let map: LeafletMap | null = null
let marker: Marker | null = null

const places = [
  { name: '桥西历史文化街区', district: '拱墅区', longitude: 120.139863, latitude: 30.318332 },
  { name: '九溪公交站', district: '西湖区', longitude: 120.119235, latitude: 30.20984 },
  { name: '湖滨银泰 IN77', district: '上城区', longitude: 120.168929, latitude: 30.255672 },
  { name: '太子湾公园大草坪', district: '西湖区', longitude: 120.146515, latitude: 30.231297 },
  { name: '天目里社区中心', district: '西湖区', longitude: 120.1219, latitude: 30.2833 },
]

function markerIcon() {
  return L.divIcon({ className: 'pick-marker', html: '<span></span>', iconSize: [30, 30], iconAnchor: [15, 30] })
}

function setPoint(location: string, district: string, longitude: number, latitude: number) {
  if (!map) return
  marker?.remove()
  marker = L.marker([latitude, longitude], { icon: markerIcon() }).addTo(map)
  map.setView([latitude, longitude], 15)
  emit('select', { location, district, longitude, latitude })
}

async function searchPlace() {
  const text = query.value.trim().toLowerCase()
  let place = places.find(item => item.name.toLowerCase().includes(text) || item.district.toLowerCase().includes(text)) ?? places[0]
  try {
    const rows = await apiGet<Array<{ name: string; district: string; longitude: number; latitude: number }>>(`/map/places?keyword=${encodeURIComponent(query.value || '杭州')}&city=杭州`)
    if (rows.length) place = rows[0]
  } catch {
    error.value = '地点搜索服务暂不可用，可点击地图或手动输入地点。'
  }
  setPoint(place.name, place.district, place.longitude, place.latitude)
}

function locateMe() {
  if (!navigator.geolocation || !map) {
    error.value = '定位不可用，可点击地图或手动输入地点。'
    return
  }
  navigator.geolocation.getCurrentPosition(
    position => setPoint(`地图选点 ${position.coords.latitude.toFixed(5)}, ${position.coords.longitude.toFixed(5)}`, '杭州', position.coords.longitude, position.coords.latitude),
    () => { error.value = '定位授权未开启，可点击地图或手动输入地点。' },
    { timeout: 5000 },
  )
}

onMounted(async () => {
  await nextTick()
  if (!mapEl.value) return
  map = L.map(mapEl.value, { zoomControl: true, attributionControl: false }).setView([30.274085, 120.15507], 12)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 })
    .on('tileerror', () => { error.value = '地图服务暂不可用，可继续手动输入地点。' })
    .addTo(map)
  map.on('click', event => {
    const lat = event.latlng.lat
    const lng = event.latlng.lng
    setPoint(`地图选点 ${lat.toFixed(5)}, ${lng.toFixed(5)}`, '杭州', lng, lat)
  })
  setTimeout(() => map?.invalidateSize(), 50)
})

onBeforeUnmount(() => {
  marker?.remove()
  map?.remove()
})
</script>

<template>
  <div class="location-picker">
    <div class="picker-tools">
      <label><Search :size="15" /><input v-model="query" placeholder="搜索杭州地点或点击地图标记" @keydown.enter.prevent="searchPlace" /></label>
      <button type="button" @click="searchPlace">搜索</button>
      <button type="button" @click="locateMe"><LocateFixed :size="15" />定位</button>
    </div>
    <div ref="mapEl" class="picker-map"></div>
    <p v-if="error">{{ error }}</p>
  </div>
</template>

<style scoped>
.location-picker{position:relative;z-index:0;isolation:isolate;overflow:hidden;border:1px solid var(--color-line);border-radius:13px;background:#fff}
.picker-tools{padding:10px;display:flex;gap:8px;border-bottom:1px solid var(--color-line)}
.picker-tools label{min-width:0;flex:1;padding:0 10px;border:1px solid var(--color-line);border-radius:9px;display:flex;align-items:center;gap:6px}
.picker-tools input{min-width:0;flex:1;padding:9px 0;border:0;outline:0}
.picker-tools button{border:0;border-radius:8px;background:var(--color-bg);padding:0 10px;font-size:10px;font-weight:800;display:flex;align-items:center;gap:5px}
.picker-map{position:relative;z-index:0;height:280px}
.location-picker p{margin:0;padding:9px 12px;color:var(--color-danger);font-size:10px}
:global(.pick-marker){width:30px!important;height:30px!important;border:3px solid #fff;background:var(--color-primary);border-radius:50% 50% 50% 8px;box-shadow:0 8px 18px rgba(23,34,56,.24);transform:rotate(-45deg)}
:global(.pick-marker span){display:block;width:8px;height:8px;margin:8px;background:#fff;border-radius:50%}
</style>
