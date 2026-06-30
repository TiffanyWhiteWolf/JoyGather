<script setup lang="ts">
import { Grid2X2, ListFilter, Map, Search, SlidersHorizontal } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import ActivityCard from '@/components/activity/ActivityCard.vue'
import CityMap from '@/components/map/CityMap.vue'
import { useActivityFilters } from '@/hooks/useActivityFilters'
import { apiGet } from '@/lib/api'
import type { Activity, ActivityCategory } from '@/types'

const route = useRoute()
const allActivities = ref<Activity[]>([])
const { keyword, categories: selectedCategories, maxDistance, onlyFree, district, timeRange, toggleCategory, filteredActivities } = useActivityFilters(allActivities)
const view = ref<'grid' | 'map'>('grid')
const advancedOpen = ref(false)
const loading = ref(false)
const loadError = ref('')
let requestSequence = 0
let boundsTimer = 0
const sortMode = ref<'综合推荐' | '距离最近' | '价格最低'>('综合推荐')
const categories: ActivityCategory[] = ['城市探索','户外运动','桌游聚会','学习交流','运动健身','公益活动']
const sortedActivities = computed(() => {
  const rows = [...filteredActivities.value]
  if (sortMode.value === '距离最近') return rows.sort((a, b) => a.distance - b.distance)
  if (sortMode.value === '价格最低') return rows.sort((a, b) => a.price - b.price)
  return rows.sort((a, b) => Number(Boolean(b.featured)) - Number(Boolean(a.featured)) || a.distance - b.distance)
})
function toggleSort() {
  sortMode.value = sortMode.value === '综合推荐' ? '距离最近' : sortMode.value === '距离最近' ? '价格最低' : '综合推荐'
}
async function loadActivities(bounds?: { minLng: number; maxLng: number; minLat: number; maxLat: number }) {
  const sequence = ++requestSequence
  const params = new URLSearchParams()
  if (bounds) {
    params.set('minLng', String(bounds.minLng))
    params.set('maxLng', String(bounds.maxLng))
    params.set('minLat', String(bounds.minLat))
    params.set('maxLat', String(bounds.maxLat))
  }
  loading.value = true
  loadError.value = ''
  try {
    const rows = await apiGet<Activity[]>(`/activities${params.size ? `?${params}` : ''}`)
    if (sequence === requestSequence) allActivities.value = rows
  } catch (err) {
    if (sequence === requestSequence) loadError.value = err instanceof Error ? err.message : '区域活动加载失败，已保留当前结果。'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}
function refreshMapBounds(bounds: { minLng: number; maxLng: number; minLat: number; maxLat: number }) {
  window.clearTimeout(boundsTimer)
  boundsTimer = window.setTimeout(() => { void loadActivities(bounds) }, 260)
}
onMounted(async () => {
  keyword.value = String(route.query.q ?? '')
  if (route.query.view === 'map') view.value = 'map'
  await loadActivities()
})
</script>
<template>
  <div class="container discover-page">
    <div class="discover-head"><div><span class="eyebrow">EXPLORE HANGZHOU</span><h1>发现城市里的新鲜事</h1><p>按兴趣、时间和距离，找到刚刚好的那一场。</p></div><div class="view-toggle"><button :class="{active:view==='grid'}" @click="view='grid'"><Grid2X2 :size="17" />列表</button><button :class="{active:view==='map'}" @click="view='map'"><Map :size="17" />地图</button></div></div>
    <div class="search-filter"><div class="big-search"><Search :size="20" /><input v-model="keyword" placeholder="搜索活动、地点、兴趣标签" /><span>⌘ K</span></div><button class="btn btn-outline" @click="advancedOpen=!advancedOpen"><SlidersHorizontal :size="18" />高级筛选</button></div>
    <div class="filter-panel"><div class="filter-group"><b>活动类型（可多选）</b><div class="chips"><button :class="{active:!selectedCategories.length}" @click="selectedCategories=[]">全部</button><button v-for="item in categories" :key="item" :class="{active:selectedCategories.includes(item)}" @click="toggleCategory(item)">{{ item }}</button></div></div><div class="filter-group distance"><b>距离 ≤ {{ maxDistance }}km</b><input v-model="maxDistance" type="range" min="1" max="20" /><label><input v-model="onlyFree" type="checkbox" /> 只看免费</label></div></div>
    <div v-if="advancedOpen" class="advanced-panel"><label>城区<select v-model="district"><option v-for="item in ['全部城区','拱墅区','西湖区','上城区','滨江区']" :key="item">{{ item }}</option></select></label><label>时间范围<select v-model="timeRange"><option>全部时间</option><option>今天</option><option>本周</option><option>本周末</option></select></label><label>费用<input v-model="onlyFree" type="checkbox" /> 仅免费活动</label><button @click="district='全部城区';timeRange='全部时间';onlyFree=false;maxDistance=20;selectedCategories=[]">重置筛选</button></div>
    <div class="result-bar"><span>共找到 <b>{{ sortedActivities.length }}</b> 场活动</span><button @click="toggleSort"><ListFilter :size="15" />{{ sortMode }}</button></div>
    <div v-if="view==='grid'" class="activity-grid"><ActivityCard v-for="activity in sortedActivities" :key="activity.id" :activity="activity" /></div>
    <div v-else class="map-mode"><div class="map-mode-note"><span><Map :size="16" />拖动或缩放地图，活动点位会按当前可视区域自动刷新</span><em v-if="loading">正在更新…</em><em v-else>{{ sortedActivities.length }} 个点位</em></div><p v-if="loadError" class="map-degrade">{{ loadError }} 你仍可使用左侧列表与筛选功能。</p><div class="map-layout"><div class="map-list"><ActivityCard v-for="activity in sortedActivities" :key="activity.id" :activity="activity" /></div><CityMap :activities="sortedActivities" :loading="loading" @bounds-change="refreshMapBounds" /></div></div>
    <div v-if="!filteredActivities.length" class="empty-state"><Search :size="34" /><h3>暂时没找到合适的活动</h3><p>放宽一点距离，或换个关键词试试。</p></div>
  </div>
</template>
<style scoped>
.discover-page{padding:48px 0 70px}.discover-head{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:28px}.discover-head h1{margin:0 0 8px;font-size:38px;letter-spacing:-.05em}.discover-head p{margin:0;color:var(--color-ink-soft)}.view-toggle{padding:4px;border:1px solid var(--color-line);border-radius:var(--radius-pill);background:#fff;display:flex}.view-toggle button{border:0;background:none;border-radius:var(--radius-pill);padding:9px 14px;color:var(--color-ink-soft);display:flex;align-items:center;gap:6px}.view-toggle button.active{background:var(--color-ink);color:#fff}.search-filter{display:flex;gap:12px}.big-search{height:54px;flex:1;padding:0 17px;border:1px solid var(--color-line);border-radius:var(--radius-md);background:#fff;display:flex;align-items:center;gap:10px}.big-search input{flex:1;border:0;outline:0}.big-search>span{padding:4px 7px;border:1px solid var(--color-line);border-radius:5px;color:var(--color-ink-soft);font-size:10px}.filter-panel{margin:14px 0 24px;padding:18px 20px;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-md);display:flex;align-items:center;justify-content:space-between;gap:20px}.filter-group{display:flex;align-items:center;gap:14px;font-size:12px}.filter-group>b{white-space:nowrap}.chips{display:flex;flex-wrap:wrap;gap:6px}.chips button{border:0;border-radius:var(--radius-pill);padding:7px 10px;background:var(--color-bg);color:var(--color-ink-soft);font-size:11px}.chips button.active{background:var(--color-primary-soft);color:var(--color-primary);font-weight:800}.distance input[type=range]{accent-color:var(--color-primary);width:100px}.distance label{white-space:nowrap}.result-bar{margin-bottom:17px;display:flex;justify-content:space-between;color:var(--color-ink-soft);font-size:13px}.result-bar b{color:var(--color-primary)}.result-bar button{border:0;background:none;display:flex;align-items:center;gap:5px}.map-mode-note{margin-bottom:12px;padding:11px 14px;border:1px solid #d7e9e5;border-radius:12px;background:var(--color-mint-soft);display:flex;align-items:center;justify-content:space-between;color:#147d70;font-size:11px}.map-mode-note span{display:flex;align-items:center;gap:7px}.map-mode-note em{font-style:normal;font-weight:800}.map-degrade{margin:0 0 12px;padding:10px 13px;border-radius:10px;background:#fff4dd;color:#94600e;font-size:11px}.map-layout{display:grid;grid-template-columns:430px 1fr;gap:18px}.map-list{max-height:600px;overflow:auto;display:grid;grid-template-columns:1fr;gap:14px;padding-right:6px}.map-list :deep(.activity-card){display:grid;grid-template-columns:150px 1fr}.map-list :deep(.card-cover){height:100%}.empty-state{padding:80px;text-align:center;color:var(--color-ink-soft)}
@media(max-width:900px){.filter-panel{align-items:flex-start;flex-direction:column}.map-layout{grid-template-columns:1fr}.map-list{display:none}}@media(max-width:600px){.discover-head{align-items:flex-start;gap:18px;flex-direction:column}.discover-head h1{font-size:30px}.search-filter .btn{font-size:0;padding:13px}.filter-group{align-items:flex-start;flex-direction:column}.distance{flex-direction:row}.big-search>span{display:none}}
</style>
