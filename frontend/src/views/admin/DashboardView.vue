<script setup lang="ts">
import { Activity, ArrowUpRight, ClipboardCheck, Users, UserRoundCheck } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiGet } from '@/lib/api'
import type { ReviewTask } from '@/types'

interface DashboardDto {
  metrics: Record<string, number>
  categoryDistribution: Record<string, number>
  trend: number[]
  adminName: string
}

const CATEGORY_COLORS: Record<string, string> = {
  '城市探索': '#ff6b45',
  '户外运动': '#e8923f',
  '桌游聚会': '#f7b05e',
  '学习交流': '#ff8b6e',
  '运动健身': '#e5734a',
  '公益活动': '#ff9f7c',
}

const STAT_STYLES = [
  { bg: '#fff0ea', color: '#ff6b45', icon: Users },
  { bg: '#fff4ec', color: '#e8923f', icon: Activity },
  { bg: '#fff6ef', color: '#f7b05e', icon: UserRoundCheck },
  { bg: '#fff0ea', color: '#ff6b45', icon: ClipboardCheck },
]

const dashboard = ref<DashboardDto>({ metrics: {}, categoryDistribution: {}, trend: [], adminName: '' })
const reviewTasks = ref<ReviewTask[]>([])
const router = useRouter()
const now = new Date().toLocaleString('zh-CN', { hour12: false, month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })

const stats = computed(() => {
  const values = [
    dashboard.value.metrics.users ?? 0,
    dashboard.value.metrics.monthlyActivities ?? 0,
    dashboard.value.metrics.activeTeams ?? 0,
    dashboard.value.metrics.pendingReviews ?? 0,
  ]
  const labels = ['平台用户', '本月活动', '活跃小队', '待办审核']
  const subtitles = ['注册用户总数', '非草稿状态活动', '正常运营中小队', '等待处理的任务']
  return STAT_STYLES.map((s, i) => ({
    ...s, value: values[i], label: labels[i], subtitle: subtitles[i],
  }))
})

const maxTrend = computed(() => Math.max(1, ...dashboard.value.trend))
const chartHeights = computed(() => {
  const availablePx = 160
  const max = maxTrend.value
  return dashboard.value.trend.map(v => Math.max(6, Math.round((v / max) * availablePx)))
})

const categoryList = computed(() => {
  const dist = dashboard.value.categoryDistribution
  const total = Object.values(dist).reduce((a, b) => a + b, 0)
  return Object.entries(dist).map(([name, count]) => ({
    name,
    count,
    pct: total ? Math.round((count / total) * 100) : 0,
    color: CATEGORY_COLORS[name] ?? '#aaa',
  }))
})

const donutGradient = computed(() => {
  const items = categoryList.value
  if (!items.length) return '#eee'
  let acc = 0
  return items.map(item => {
    const start = acc
    acc += item.pct
    return `${item.color} ${start}% ${acc}%`
  }).join(', ')
})

function goContent() { router.push('/admin/content') }

const DAY_NAMES = ['日', '一', '二', '三', '四', '五', '六']
const dayLabels = computed(() => {
  const today = new Date()
  const labels: string[] = []
  for (let i = 6; i >= 0; i--) {
    const d = new Date(today)
    d.setDate(d.getDate() - i)
    labels.push(DAY_NAMES[d.getDay()])
  }
  return labels
})

onMounted(async () => {
  const [dashboardData, reviews] = await Promise.all([
    apiGet<DashboardDto>('/admin/dashboard'),
    apiGet<ReviewTask[]>('/admin/reviews'),
  ])
  dashboard.value = dashboardData
  reviewTasks.value = reviews
})
</script>

<template>
  <div>
    <div class="admin-page-title">
      <div>
        <h1>{{ dashboard.adminName ? `早上好，${dashboard.adminName}` : '数据概览' }}</h1>
        <p>趣聚运营概况，数据实时更新。</p>
      </div>
      <span class="muted">更新于 {{ now }}</span>
    </div>

    <!-- 统计卡片 -->
    <div class="dash-stat-grid">
      <div v-for="item in stats" :key="item.label" class="dash-stat-card">
        <span class="dash-stat-icon" :style="{ background: item.bg, color: item.color }">
          <component :is="item.icon" :size="22" />
        </span>
        <div class="dash-stat-body">
          <strong>{{ item.value.toLocaleString() }}</strong>
          <p>{{ item.label }}</p>
        </div>
        <small class="dash-stat-sub">{{ item.subtitle }}</small>
      </div>
    </div>

    <!-- 图表区 -->
    <div class="dash-chart-grid">
      <!-- 报名趋势 -->
      <section class="admin-card dash-card">
        <div class="admin-card-head">
          <div>
            <h2>近 7 日报名趋势</h2>
            <small class="muted">每日新增报名人数</small>
          </div>
        </div>
        <div class="dash-bar-chart">
          <div v-for="(h, i) in chartHeights" :key="i" class="dash-bar-col">
            <span class="dash-bar-val">{{ dashboard.trend[i] || '' }}</span>
            <div class="dash-bar" :style="{ height: `${h}px` }"></div>
            <span class="dash-bar-label">{{ dayLabels[i] }}</span>
          </div>
        </div>
      </section>

      <!-- 类型分布 -->
      <section class="admin-card dash-card">
        <div class="admin-card-head">
          <h2>活动类型分布</h2>
          <button class="tiny-btn" @click="goContent">详情</button>
        </div>
        <div class="dash-donut">
          <div class="dash-donut-ring" :style="{ background: `conic-gradient(${donutGradient})` }">
            <span>
              <b>{{ dashboard.metrics.monthlyActivities ?? 0 }}</b>
              <small>活动总数</small>
            </span>
          </div>
          <div class="dash-legend">
            <div v-for="item in categoryList" :key="item.name" class="dash-legend-item">
              <i :style="{ background: item.color }"></i>
              <span>{{ item.name }}</span>
              <b>{{ item.pct }}%</b>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- 待审核 -->
    <section class="admin-card dash-review">
      <div class="admin-card-head">
        <div>
          <h2>最近待审核</h2>
          <small class="muted">优先处理高风险与临近活动</small>
        </div>
        <RouterLink to="/admin/reviews" class="text-link">查看全部 <ArrowUpRight :size="15" /></RouterLink>
      </div>
      <div class="review-list">
        <div v-for="task in reviewTasks.slice(0, 3)" :key="task.id" class="review-row">
          <div class="review-main"><b>{{ task.title }}</b><span>{{ task.type }} · {{ task.submitter }}</span></div>
          <span>{{ task.reason }}</span>
          <span :class="`risk risk-${task.risk}`">{{ task.risk }}风险</span>
          <span class="muted">{{ task.submittedAt }}</span>
          <div class="review-actions">
            <button class="tiny-btn approve" @click="router.push(`/admin/reviews`)">处理</button>
          </div>
        </div>
        <div v-if="!reviewTasks.length" class="empty-review">暂无待审核任务</div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* 统计卡片 */
.dash-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.dash-stat-card {
  padding: 20px;
  background: #fff;
  border: 1px solid var(--color-line);
  border-radius: 14px;
  display: grid;
  grid-template-columns: 48px 1fr;
  grid-template-rows: auto auto;
  gap: 4px 14px;
}
.dash-stat-icon {
  grid-row: 1 / 3;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: grid;
  place-items: center;
}
.dash-stat-body strong {
  display: block;
  font-size: 24px;
  font-weight: 800;
  letter-spacing: -.02em;
  line-height: 1.1;
}
.dash-stat-body p {
  margin: 0;
  color: var(--color-ink-soft);
  font-size: 12px;
}
.dash-stat-sub {
  grid-column: 2;
  color: #b0b8c4;
  font-size: 11px;
}

/* 图表网格 */
.dash-chart-grid {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 18px;
  margin-bottom: 18px;
}
.dash-card {
  padding: 22px 24px;
}

/* 柱状图 */
.dash-bar-chart {
  height: 210px;
  display: flex;
  align-items: flex-end;
  gap: 14px;
  padding-top: 18px;
  background: repeating-linear-gradient(
    to bottom,
    transparent 0,
    transparent 37px,
    #f5f6f8 38px
  );
}
.dash-bar-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.dash-bar-val {
  font-size: 11px;
  font-weight: 700;
  color: var(--color-ink-soft);
  margin-bottom: 4px;
}
.dash-bar {
  width: 60%;
  max-width: 36px;
  border-radius: 7px 7px 3px 3px;
  background: linear-gradient(180deg, #ff6b45, #ffb599);
  transition: height 0.4s ease;
}
.dash-bar-label {
  margin-top: 8px;
  color: #b0b8c4;
  font-size: 11px;
  font-weight: 600;
}

/* 环形图 */
.dash-donut {
  display: flex;
  align-items: center;
  gap: 28px;
  padding-top: 10px;
}
.dash-donut-ring {
  width: 140px;
  height: 140px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}
.dash-donut-ring::before {
  content: '';
  grid-area: 1 / 1;
  width: 84px;
  height: 84px;
  background: #fff;
  border-radius: 50%;
}
.dash-donut-ring span {
  grid-area: 1 / 1;
  z-index: 1;
  display: flex;
  flex-direction: column;
  text-align: center;
}
.dash-donut-ring b {
  font-size: 22px;
  font-weight: 800;
}
.dash-donut-ring small {
  margin-top: 1px;
  color: var(--color-ink-soft);
  font-size: 11px;
}
.dash-legend {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.dash-legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-ink-soft);
}
.dash-legend-item i {
  width: 9px;
  height: 9px;
  border-radius: 3px;
  flex-shrink: 0;
}
.dash-legend-item span {
  flex: 1;
}
.dash-legend-item b {
  color: var(--color-ink);
  font-weight: 700;
}

/* 待审核 */
.dash-review {
  margin-top: 18px;
}
</style>
