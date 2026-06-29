<script setup lang="ts">
import { Activity, ArrowUpRight, ClipboardCheck, Users, UserRoundCheck } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiGet } from '@/lib/api'
import type { ReviewTask } from '@/types'

interface DashboardDto {
  metrics: Record<string, number>
  categoryDistribution: Record<string, number>
}

const dashboard = ref<DashboardDto>({ metrics: {}, categoryDistribution: {} })
const reviewTasks = ref<ReviewTask[]>([])
const router = useRouter()
const range = ref<'本周' | '本月'>('本周')
const stats = computed(() => [
  {label:'平台用户',value:String(dashboard.value.metrics.users ?? 0),trend:'+12.4%',icon:Users},
  {label:'本月活动',value:String(dashboard.value.metrics.monthlyActivities ?? 0),trend:'+8.6%',icon:Activity},
  {label:'活跃小队',value:String(dashboard.value.metrics.activeTeams ?? 0),trend:'+6.2%',icon:UserRoundCheck},
  {label:'待办审核',value:String(dashboard.value.metrics.pendingReviews ?? 0),trend:'需关注',icon:ClipboardCheck},
])
const heights=[38,55,46,72,62,88,76]
function toggleRange() { range.value = range.value === '本周' ? '本月' : '本周' }
function goReviews(id?: string) { router.push({ path: '/admin/reviews', query: id ? { id } : {} }) }
function goContent() { router.push('/admin/content') }

onMounted(async () => {
  const [dashboardData, reviews] = await Promise.all([
    apiGet<DashboardDto>('/admin/dashboard'),
    apiGet<ReviewTask[]>('/admin/reviews'),
  ])
  dashboard.value = dashboardData
  reviewTasks.value = reviews
})
</script>
<template><div><div class="admin-page-title"><div><h1>早上好，周晴</h1><p>这是趣聚今天的运营概况，整体运行平稳。</p></div><span class="muted">数据更新于 10:48</span></div><div class="stat-grid"><div v-for="item in stats" :key="item.label" class="stat-card"><div class="stat-card-top"><span class="stat-icon"><component :is="item.icon" :size="20" /></span><span class="stat-trend">{{ item.trend }}</span></div><strong>{{ item.value }}</strong><p>{{ item.label }}</p></div></div><div class="admin-grid"><section class="admin-card"><div class="admin-card-head"><div><h2>{{ range }}活动参与趋势</h2><small class="muted">报名与实际签到人数</small></div><button class="tiny-btn" @click="toggleRange">{{ range }}⌄</button></div><div class="simple-chart"><div v-for="(height,i) in heights" :key="i" class="chart-bar-wrap"><div class="chart-bar" :style="{height:`${range==='本周'?height:Math.min(96,height+8)}%`}"></div><span>{{ ['周一','周二','周三','周四','周五','周六','周日'][i] }}</span></div></div></section><section class="admin-card"><div class="admin-card-head"><h2>活动类型分布</h2><button class="tiny-btn" @click="goContent">详情</button></div><div class="donut-wrap"><div class="donut"><span><b>{{ dashboard.metrics.monthlyActivities ?? 0 }}</b><small>活动总数</small></span></div><ul><li><i style="background:#ff6b45"></i>城市探索 <b>32%</b></li><li><i style="background:#22b8a7"></i>户外运动 <b>26%</b></li><li><i style="background:#6f4ad8"></i>兴趣聚会 <b>24%</b></li><li><i style="background:#ffc857"></i>其他 <b>18%</b></li></ul></div></section></div><section class="admin-card recent"><div class="admin-card-head"><div><h2>最近待审核</h2><small class="muted">优先处理高风险与临近活动</small></div><RouterLink to="/admin/reviews" class="text-link">查看全部 <ArrowUpRight :size="15" /></RouterLink></div><div class="review-list"><div v-for="task in reviewTasks.slice(0,3)" :key="task.id" class="review-row"><div class="review-main"><b>{{ task.title }}</b><span>{{ task.type }} · {{ task.submitter }}</span></div><span>{{ task.reason }}</span><span :class="`risk risk-${task.risk}`">{{ task.risk }}风险</span><span class="muted">{{ task.submittedAt }}</span><div class="review-actions"><button class="tiny-btn" @click="goReviews(task.id)">查看</button><button class="tiny-btn approve" @click="goReviews(task.id)">处理</button></div></div></div></section></div></template>
<style scoped>.donut-wrap{display:flex;align-items:center;gap:28px;padding-top:14px}.donut{width:135px;height:135px;border-radius:50%;background:conic-gradient(#ff6b45 0 32%,#22b8a7 32% 58%,#6f4ad8 58% 82%,#ffc857 82%);display:grid;place-items:center}.donut:before{content:'';grid-area:1/1;width:82px;height:82px;background:#fff;border-radius:50%}.donut span{grid-area:1/1;z-index:1;display:flex;flex-direction:column;text-align:center}.donut b{font-size:19px}.donut small{color:var(--color-ink-soft);font-size:8px}.donut-wrap ul{flex:1;margin:0;padding:0;list-style:none}.donut-wrap li{padding:7px 0;color:var(--color-ink-soft);font-size:10px}.donut-wrap li i{display:inline-block;width:7px;height:7px;margin-right:6px;border-radius:50%}.donut-wrap li b{float:right;color:var(--color-ink)}.recent{margin-top:18px}</style>
