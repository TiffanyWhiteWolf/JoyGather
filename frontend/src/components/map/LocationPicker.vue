<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { CheckCircle2, LocateFixed, Search } from 'lucide-vue-next'
import L, { type Map as LeafletMap, type Marker } from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { apiGet } from '@/lib/api'
import { getCityConfig, type SupportedCity } from '@/config/cities'

const props = defineProps<{
  city: SupportedCity
  location?: string
  district?: string
  longitude?: number
  latitude?: number
}>()

const emit = defineEmits<{
  select: [value: { location: string; city: SupportedCity; district: string; longitude: number; latitude: number }]
}>()

interface GeoPoint {
  name: string
  address?: string
  city?: string
  district: string
  longitude: number
  latitude: number
}

const mapEl = ref<HTMLDivElement | null>(null)
const query = ref('')
const notice = ref('')
const searching = ref(false)
const selectedText = ref('')
const selectedAddress = ref('')
const searchResults = ref<GeoPoint[]>([])
const cityConfig = computed(() => getCityConfig(props.city))
let map: LeafletMap | null = null
let marker: Marker | null = null

function markerIcon() {
  return L.divIcon({ className: 'pick-marker', html: '<span></span>', iconSize: [30, 30], iconAnchor: [15, 30] })
}

function setPoint(location: string, district: string, longitude: number, latitude: number, address = location) {
  marker?.remove()
  if (map) {
    marker = L.marker([latitude, longitude], { icon: markerIcon() }).addTo(map)
      .bindTooltip(location, { permanent: true, direction: 'top', offset: [0, -30], className: 'pick-label' })
      .openTooltip()
    map.setView([latitude, longitude], 15)
  }
  selectedText.value = `${district} · ${location}`
  selectedAddress.value = address
  emit('select', { location, city: props.city, district, longitude, latitude })
}

async function reversePoint(longitude: number, latitude: number): Promise<GeoPoint | null> {
  try {
    const point = await apiGet<GeoPoint>(`/map/reverse?longitude=${longitude}&latitude=${latitude}`)
    if (point.name?.trim()) return point
    notice.value = '暂时无法识别该坐标附近的具体地点，请搜索地点名称或手动填写完整地址。'
    return null
  } catch {
    notice.value = '地址识别服务暂不可用，请搜索地点名称或手动填写完整地址。'
    return null
  }
}

function belongsToSelectedCity(point: GeoPoint) {
  if (!point.city || point.city === props.city) return true
  notice.value = `该点位位于${point.city}，请先把活动城市切换到${point.city}后再选择。`
  return false
}

async function searchPlace() {
  const text = query.value.trim()
  if (!text) {
    notice.value = '请输入地点名称，例如“奥林匹克森林公园”。'
    return
  }
  searching.value = true
  notice.value = ''
  searchResults.value = []
  try {
    const params = new URLSearchParams({ keyword: text, city: props.city })
    const rows = await apiGet<GeoPoint[]>(`/map/places?${params}`)
    searchResults.value = rows
    if (rows.length === 1) selectSearchResult(rows[0])
    else if (!rows.length) notice.value = `没有找到“${text}”的真实地图结果，请补充城区、道路或门牌号后重试。`
  } catch {
    notice.value = '在线地点搜索暂不可用，没有使用虚假占位坐标；请稍后重试或手动填写完整地址。'
  } finally {
    searching.value = false
  }
}

function selectSearchResult(point: GeoPoint) {
  if (!belongsToSelectedCity(point)) return
  query.value = point.name
  searchResults.value = []
  notice.value = ''
  setPoint(point.name, point.district, Number(point.longitude), Number(point.latitude), point.address || point.name)
}

function locateMe() {
  if (!navigator.geolocation || !map) {
    notice.value = '定位不可用，可点击地图选点或直接手动输入地址。'
    return
  }
  navigator.geolocation.getCurrentPosition(
    async position => {
      notice.value = ''
      const latitude = position.coords.latitude
      const longitude = position.coords.longitude
      const point = await reversePoint(longitude, latitude)
      if (!point) return
      if (!belongsToSelectedCity(point)) return
      setPoint(point.name, point.district || cityConfig.value.districts[0], longitude, latitude, point.address || point.name)
    },
    () => { notice.value = '定位授权未开启，可点击地图选点或直接手动输入地址。' },
    { timeout: 5000 },
  )
}

function resetForCity() {
  const config = cityConfig.value
  marker?.remove()
  marker = null
  selectedText.value = ''
  selectedAddress.value = ''
  searchResults.value = []
  query.value = ''
  notice.value = `已切换到${props.city}，请重新搜索或点击地图选择地点。`
  map?.setView(config.center, config.zoom)
}

onMounted(async () => {
  await nextTick()
  if (!mapEl.value) return
  const config = cityConfig.value
  map = L.map(mapEl.value, { zoomControl: true, attributionControl: false }).setView(config.center, config.zoom)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 })
    .on('tileerror', () => { notice.value = '地图图层暂不可用，不影响手动填写活动地址。' })
    .addTo(map)
  map.on('click', async event => {
    const latitude = event.latlng.lat
    const longitude = event.latlng.lng
    notice.value = ''
    const point = await reversePoint(longitude, latitude)
    if (!point) return
    if (!belongsToSelectedCity(point)) return
    setPoint(point.name, point.district || config.districts[0], longitude, latitude, point.address || point.name)
  })
  if (Number.isFinite(props.latitude) && Number.isFinite(props.longitude) && props.location) {
    setPoint(props.location, props.district || config.districts[0], Number(props.longitude), Number(props.latitude), props.location)
  }
  setTimeout(() => map?.invalidateSize(), 50)
})

watch(() => props.city, resetForCity)

onBeforeUnmount(() => {
  marker?.remove()
  map?.remove()
})
</script>

<template>
  <div class="location-picker">
    <div class="picker-head">
      <div><b>地图选点</b><span>{{ city }} · 搜索地点或直接点击地图</span></div>
      <em>地址与经纬度自动回填</em>
    </div>
    <div class="picker-tools">
      <label><Search :size="16" /><input v-model="query" :placeholder="`搜索${city}地点`" @keydown.enter.prevent="searchPlace" /></label>
      <button type="button" :disabled="searching" @click="searchPlace">{{ searching ? '搜索中' : '搜索' }}</button>
      <button type="button" @click="locateMe"><LocateFixed :size="15" />定位</button>
    </div>
    <div v-if="searchResults.length" class="search-results">
      <div class="result-title"><b>请选择准确地点</b><span>结果来自 OpenStreetMap 实际地理数据</span></div>
      <button v-for="point in searchResults" :key="`${point.longitude}-${point.latitude}`" type="button" @click="selectSearchResult(point)">
        <span><b>{{ point.name }}</b><small>{{ point.address || `${point.city} · ${point.district}` }}</small></span>
        <em>{{ point.district }}<br />{{ Number(point.latitude).toFixed(5) }}, {{ Number(point.longitude).toFixed(5) }}</em>
      </button>
    </div>
    <div ref="mapEl" class="picker-map"></div>
    <div v-if="selectedText" class="picked"><CheckCircle2 :size="16" /><span><b>已选择</b>{{ selectedText }}<small v-if="selectedAddress && selectedAddress !== selectedText">{{ selectedAddress }}</small></span></div>
    <p v-if="notice">{{ notice }}</p>
  </div>
</template>

<style scoped>
.location-picker{position:relative;z-index:0;isolation:isolate;overflow:hidden;border:1px solid var(--color-line);border-radius:16px;background:#fff;box-shadow:0 10px 30px rgba(28,38,54,.06)}
.picker-head{padding:14px 16px 11px;display:flex;align-items:center;justify-content:space-between;background:linear-gradient(120deg,#fff7f2,#edf9f6)}.picker-head>div{display:flex;flex-direction:column;gap:3px}.picker-head b{font-size:13px}.picker-head span{color:var(--color-ink-soft);font-size:10px}.picker-head em{padding:5px 8px;border-radius:20px;background:#fff;color:var(--color-mint);font-size:9px;font-style:normal;font-weight:800}
.picker-tools{padding:10px;display:flex;gap:8px;border-top:1px solid rgba(0,0,0,.03);border-bottom:1px solid var(--color-line)}
.picker-tools label{min-width:0;flex:1;padding:0 10px;border:1px solid var(--color-line);border-radius:9px;display:flex;align-items:center;gap:6px}.picker-tools label:focus-within{border-color:var(--color-primary);box-shadow:0 0 0 3px var(--color-primary-soft)}
.picker-tools input{min-width:0;flex:1;padding:10px 0;border:0;outline:0}.picker-tools button{border:0;border-radius:8px;background:var(--color-bg);padding:0 12px;font-size:10px;font-weight:800;display:flex;align-items:center;gap:5px;cursor:pointer}.picker-tools button:first-of-type{background:var(--color-ink);color:#fff}.picker-tools button:disabled{opacity:.55}
.search-results{position:relative;z-index:5;padding:10px;background:#fff;border-bottom:1px solid var(--color-line);display:grid;gap:6px}.result-title{padding:2px 4px 7px;display:flex;align-items:center;justify-content:space-between}.result-title b{font-size:11px}.result-title span{color:var(--color-mint);font-size:9px}.search-results>button{width:100%;padding:10px 12px;border:1px solid var(--color-line);border-radius:10px;background:#fff;display:flex;align-items:center;justify-content:space-between;gap:15px;text-align:left;cursor:pointer}.search-results>button:hover{border-color:var(--color-primary);background:var(--color-primary-soft)}.search-results>button span{min-width:0;display:flex;flex-direction:column;gap:4px}.search-results>button b{font-size:11px}.search-results>button small{overflow:hidden;color:var(--color-ink-soft);font-size:9px;white-space:nowrap;text-overflow:ellipsis}.search-results>button em{flex-shrink:0;color:var(--color-ink-soft);font-size:8px;font-style:normal;text-align:right;line-height:1.5}
.picker-map{position:relative;z-index:0;height:300px}.picked{padding:10px 13px;background:var(--color-mint-soft);color:#167b70;display:flex;align-items:flex-start;gap:8px;font-size:10px}.picked>span{display:flex;flex-direction:column;gap:4px}.picked small{max-width:760px;color:#4e766f;font-size:9px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.location-picker>p{margin:0;padding:9px 12px;background:#fff8e8;color:#8a6116;font-size:10px}
:global(.pick-marker){width:30px!important;height:30px!important;border:3px solid #fff;background:var(--color-primary);border-radius:50% 50% 50% 8px;box-shadow:0 8px 18px rgba(23,34,56,.24);transform:rotate(-45deg)}:global(.pick-marker span){display:block;width:8px;height:8px;margin:8px;background:#fff;border-radius:50%}
:global(.pick-label){padding:6px 9px!important;border:0!important;border-radius:8px!important;background:#172238!important;color:#fff!important;box-shadow:0 5px 15px rgba(23,34,56,.2)!important;font-size:10px!important;font-weight:800!important}:global(.pick-label:before){border-top-color:#172238!important}
@media(max-width:600px){.picker-head{align-items:flex-start;gap:8px;flex-direction:column}.picker-tools{flex-wrap:wrap}.picker-tools label{flex-basis:100%}.picker-tools button{height:36px;flex:1;justify-content:center}}
</style>
