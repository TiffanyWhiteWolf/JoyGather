<script setup lang="ts">
import { ArrowUpRight, Heart, MapPin, Users } from 'lucide-vue-next'
import { ref } from 'vue'
import type { Activity } from '@/types'
import { formatPrice, percent } from '@/lib/utils'

defineProps<{ activity: Activity; showStatus?: boolean }>()
const liked = ref(false)
function statusLabel(activity: Activity) {
  if (activity.reviewDecision === '已通过') return '人工审核通过'
  if (activity.reviewDecision === '已驳回' || activity.status === '已下架') return '审核未通过'
  if (activity.reviewDecision === '要求修改') return '审核要求修改'
  if (activity.aiReviewStatus === 'LOW_RISK') return 'AI 审核通过'
  if (activity.aiReviewStatus === 'ERROR') return 'AI 异常 · 人工审核中'
  if (activity.aiReviewStatus === 'INDETERMINATE') return '待人工审核'
  if (activity.aiReviewStatus === 'REVIEW_REQUIRED') return '人工审核中'
  const map: Record<string, string> = { '审核中': '审核中', '已下架': '审核未通过', '报名中': '报名中', '即将开始': '即将开始', '进行中': '进行中', '已截止': '已截止', '已结束': '已结束' }
  return map[activity.status] || activity.status
}
function statusClass(activity: Activity) {
  if (activity.reviewDecision === '已驳回' || activity.reviewDecision === '要求修改' || activity.status === '已下架') return 'status-rejected'
  if (activity.status === '审核中') return 'status-reviewing'
  return 'status-normal'
}
</script>
<template>
  <article class="activity-card">
    <RouterLink :to="`/activities/${activity.id}`" class="card-cover"><img :src="activity.cover" :alt="activity.title" /><span class="category-badge">{{ activity.category }}</span><span v-if="showStatus" :class="['status-badge', statusClass(activity)]">{{ statusLabel(activity) }}</span><button :class="['heart', { liked }]" @click.prevent="liked = !liked"><Heart :size="18" :fill="liked ? 'currentColor' : 'none'" /></button></RouterLink>
    <div class="card-content"><div class="date-price"><span>{{ activity.date }} · {{ activity.time.split(' - ')[0] }}</span><b>{{ formatPrice(activity.price) }}</b></div><RouterLink :to="`/activities/${activity.id}`"><h3>{{ activity.title }}</h3></RouterLink><p>{{ activity.summary }}</p><div class="tag-row"><span v-for="tag in activity.tags.slice(0, 2)" :key="tag"># {{ tag }}</span></div><div class="card-meta"><span><MapPin :size="15" />{{ activity.district }} · {{ activity.distance }}km</span><span><Users :size="15" />{{ activity.joined }}/{{ activity.capacity }}</span></div><div class="capacity-line"><span :style="{ width: `${percent(activity.joined, activity.capacity)}%` }"></span></div><div class="card-footer"><RouterLink :to="`/activities/${activity.id}`" class="card-arrow"><ArrowUpRight :size="18" /></RouterLink><slot name="actions" /></div></div>
  </article>
</template>

<style scoped>
.card-footer{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:10px}
.card-footer :slotted(button),.card-footer :slotted(.card-action-btn){padding:6px 10px;border:0;border-radius:8px;font-size:11px;font-weight:800;cursor:pointer;display:inline-flex;align-items:center;gap:5px;transition:all .15s}
</style>
