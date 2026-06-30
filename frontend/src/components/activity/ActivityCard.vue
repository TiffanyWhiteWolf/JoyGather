<script setup lang="ts">
import { ArrowUpRight, Heart, MapPin, Users } from 'lucide-vue-next'
import { ref } from 'vue'
import type { Activity } from '@/types'
import { formatPrice, percent } from '@/lib/utils'

defineProps<{ activity: Activity; showStatus?: boolean }>()
const liked = ref(false)
function statusLabel(status: string) {
  const map: Record<string, string> = { '审核中': '审核中', '已下架': '已驳回', '报名中': '报名中', '即将开始': '即将开始', '进行中': '进行中', '已截止': '已截止', '已结束': '已结束' }
  return map[status] || status
}
function statusClass(status: string) {
  if (status === '已下架') return 'status-rejected'
  if (status === '审核中') return 'status-reviewing'
  return 'status-normal'
}
</script>
<template>
  <article class="activity-card">
    <RouterLink :to="`/activities/${activity.id}`" class="card-cover"><img :src="activity.cover" :alt="activity.title" /><span class="category-badge">{{ activity.category }}</span><span v-if="showStatus" :class="['status-badge', statusClass(activity.status)]">{{ statusLabel(activity.status) }}</span><button :class="['heart', { liked }]" @click.prevent="liked = !liked"><Heart :size="18" :fill="liked ? 'currentColor' : 'none'" /></button></RouterLink>
    <div class="card-content"><div class="date-price"><span>{{ activity.date }} · {{ activity.time.split(' - ')[0] }}</span><b>{{ formatPrice(activity.price) }}</b></div><RouterLink :to="`/activities/${activity.id}`"><h3>{{ activity.title }}</h3></RouterLink><p>{{ activity.summary }}</p><div class="tag-row"><span v-for="tag in activity.tags.slice(0, 2)" :key="tag"># {{ tag }}</span></div><div class="card-meta"><span><MapPin :size="15" />{{ activity.district }} · {{ activity.distance }}km</span><span><Users :size="15" />{{ activity.joined }}/{{ activity.capacity }}</span></div><div class="capacity-line"><span :style="{ width: `${percent(activity.joined, activity.capacity)}%` }"></span></div><RouterLink :to="`/activities/${activity.id}`" class="card-arrow"><ArrowUpRight :size="18" /></RouterLink></div>
  </article>
</template>
