<script setup lang="ts">
import { CalendarDays, Check, ChevronLeft, Clock, MapPin, ShieldAlert, Users, X } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { apiGet, apiPost } from '@/lib/api'
import type { Activity } from '@/types'

const route = useRoute()
const app = useAppStore()
const rejecting = ref(false)
const reason = ref('')
const reasonError = ref('')

const fallbackActivity: Activity = {
  id: '', title: '加载中', summary: '', description: '', category: '城市探索',
  cover: 'https://images.unsplash.com/photo-1519501025264-65ba15a82390?auto=format&fit=crop&w=1400&q=85',
  date: '', time: '', location: '', city: '杭州', district: '', distance: 0, longitude: 120.15507, latitude: 30.274085,
  price: 0, capacity: 1, joined: 0, tags: [], status: '报名中',
  organizer: { id: '', nickname: '', avatar: 'https://i.pravatar.cc/160?img=47', role: '个人用户', city: '', bio: '', interests: [], following: 0, followers: 0, credit: 100 },
}

const activityRow = ref<Activity | null>(null)
const activity = computed(() => activityRow.value ?? fallbackActivity)

const pendingReview = computed(() => activity.value.status === '审核中')
const priceLabel = computed(() => activity.value.price > 0 ? `&yen;${activity.value.price}` : '免费')

async function loadActivity() {
  activityRow.value = await apiGet<Activity>(`/admin/activities/${route.params.id}`)
}

async function approve() {
  await apiPost<void>(`/admin/reviews/${route.query.reviewId}/approve`, { handlerId: 'admin' })
  app.showToast('审核已通过，活动已发布')
  await loadActivity()
}

function openReject() {
  rejecting.value = true
  reason.value = ''
  reasonError.value = ''
}

async function confirmReject() {
  if (!reason.value.trim()) {
    reasonError.value = '驳回时必须填写原因。'
    return
  }
  await apiPost<void>(`/admin/reviews/${route.query.reviewId}/reject`, { reason: reason.value, handlerId: 'admin' })
  rejecting.value = false
  app.showToast('已驳回并记录原因')
  await loadActivity()
}

onMounted(loadActivity)
</script>

<template>
  <div class="detail-page">
    <div class="detail-hero">
      <div class="container">
        <RouterLink to="/admin/reviews" class="back"><ChevronLeft :size="17" />返回审核中心</RouterLink>
        <div class="cover-wrap">
          <img :src="activity.cover" :alt="activity.title" />
          <div class="cover-gradient"></div>
          <div class="cover-title">
            <div class="tag-row">
              <span>{{ activity.category }}</span>
              <span v-for="tag in activity.tags" :key="tag"># {{ tag }}</span>
            </div>
            <h1>{{ activity.title }}</h1>
            <p>{{ activity.summary }}</p>
          </div>
        </div>
      </div>
    </div>

    <div class="container detail-layout">
      <div class="detail-main">
        <section class="detail-section">
          <h2>活动详情</h2>
          <p>{{ activity.description }}</p>
          <div class="facts">
            <div><CalendarDays /><span><b>{{ activity.date }}</b><small>活动日期</small></span></div>
            <div><Clock /><span><b>{{ activity.time }}</b><small>活动时间</small></span></div>
            <div><MapPin /><span><b>{{ activity.location }}</b><small>{{ activity.district }}</small></span></div>
            <div><Users /><span><b>{{ activity.joined }} 人已报名</b><small>上限 {{ activity.capacity }} 人</small></span></div>
          </div>
        </section>

        <section class="detail-section organizer">
          <img :src="activity.organizer.avatar" />
          <div>
            <small>活动发起人</small>
            <h3>{{ activity.organizer.nickname }} <Check :size="14" /></h3>
            <p>{{ activity.organizer.bio }} · 信用分 {{ activity.organizer.credit }}</p>
          </div>
        </section>

        <section class="detail-section">
          <h2>安全须知</h2>
          <p>{{ activity.safetyNote || '请按时到达集合点，并遵守现场组织者安排。户外活动请根据天气准备装备，注意个人安全。' }}</p>
        </section>
      </div>

      <aside>
        <div class="booking-card">
          <div class="status-line">
            <span :class="activity.status === '审核中' ? 'risk risk-中' : ''">{{ activity.status }}</span>
            <b v-html="priceLabel"></b>
          </div>
          <h3>{{ activity.date }} · {{ activity.time }}</h3>
          <p><MapPin :size="16" />{{ activity.location }}</p>
          <div class="capacity">
            <div><span>报名进度</span><b>{{ activity.joined }} / {{ activity.capacity }}</b></div>
          </div>

          <div v-if="pendingReview && route.query.reviewId">
            <button v-if="!rejecting" class="btn btn-primary join-btn" @click="approve">
              <Check :size="17" />通过审核
            </button>
            <button v-if="!rejecting" class="btn action-reject-btn" @click="openReject">
              <X :size="17" />驳回
            </button>
            <div v-if="rejecting" class="reject-form">
              <label>
                <span>驳回原因 *</span>
                <textarea v-model="reason" rows="3" placeholder="请填写驳回原因，供发起人修改参考"></textarea>
              </label>
              <p v-if="reasonError" class="form-error">{{ reasonError }}</p>
              <div class="reject-actions">
                <button class="btn btn-outline btn-sm" @click="rejecting = false">取消</button>
                <button class="btn btn-dark btn-sm" @click="confirmReject">确认驳回</button>
              </div>
            </div>
          </div>
          <div v-else-if="activity.status === '审核中'">
            <div class="safe-note"><ShieldAlert :size="18" /><span><b>待审核</b><small>该活动正在等待人工审核</small></span></div>
          </div>

          <div class="safe-note"><ShieldAlert :size="18" /><span><b>管理视图</b><small>你正在以管理员身份查看此活动</small></span></div>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.detail-page{padding-bottom:50px}
.detail-hero{padding:24px 0 0;background:#edeae4}
.back{display:inline-flex;align-items:center;gap:5px;margin-bottom:16px;font-size:13px;color:var(--color-ink-soft)}
.cover-wrap{position:relative;height:480px;overflow:hidden;border-radius:var(--radius-xl) var(--radius-xl) 0 0}
.cover-wrap>img{width:100%;height:100%;object-fit:cover}
.cover-gradient{position:absolute;inset:0;background:linear-gradient(180deg,transparent 30%,rgba(9,15,25,.85))}
.cover-title{position:absolute;left:45px;right:150px;bottom:40px;color:#fff}
.cover-title h1{margin:12px 0;font-size:42px;letter-spacing:-.04em}
.cover-title p{margin:0;color:#e1e3e7}
.tag-row{display:flex;gap:8px;font-size:12px}
.tag-row span{padding:4px 10px;border-radius:14px;background:rgba(255,255,255,.2)}
.detail-layout{display:grid;grid-template-columns:1fr 350px;gap:38px;padding-top:38px}
.detail-section{margin-bottom:18px;padding:28px;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-lg)}
.detail-section h2{margin-bottom:16px;font-size:21px}
.detail-section>p{color:var(--color-ink-soft);line-height:1.85}
.facts{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-top:24px}
.facts>div{padding:14px;background:var(--color-bg);border-radius:12px;display:flex;align-items:center;gap:12px}
.facts svg{color:var(--color-primary)}
.facts span{display:flex;flex-direction:column}
.facts b{font-size:13px}
.facts small{margin-top:4px;color:var(--color-ink-soft);font-size:10px}
.organizer{display:flex;align-items:center;gap:14px}
.organizer>img{width:58px;height:58px;border-radius:50%}
.organizer>div{flex:1}
.organizer small{color:var(--color-ink-soft)}
.organizer h3{display:flex;align-items:center;gap:5px;margin:4px 0}
.organizer h3 svg{padding:2px;background:var(--color-mint);color:#fff;border-radius:50%}
.organizer p{margin:0;font-size:12px}
.booking-card{position:sticky;top:96px;padding:25px;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-lg)}
.status-line{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}
.status-line span{font-size:11px;font-weight:800}
.status-line b{font-size:22px}
.booking-card h3{margin:12px 0 4px;font-size:17px}
.booking-card>p{display:flex;align-items:center;gap:6px;margin:0 0 18px;color:var(--color-ink-soft);font-size:13px}
.capacity{margin:18px 0}
.capacity>div:first-child{display:flex;justify-content:space-between;font-size:12px;margin-bottom:6px}
.capacity b{font-size:14px}
.capacity small{display:block;margin-top:8px;color:var(--color-ink-soft);font-size:10px;line-height:1.6}
.join-btn{width:100%;margin-bottom:10px}
.action-reject-btn{width:100%;display:flex;align-items:center;justify-content:center;gap:6px;padding:13px;border:1px solid var(--color-danger);border-radius:10px;background:#ffeaed;color:var(--color-danger);font-size:13px;font-weight:800;cursor:pointer}
.safe-note{padding:14px;border-radius:10px;background:var(--color-mint-soft);display:flex;gap:10px;margin-top:14px;color:#168b7d}
.safe-note small{display:block;font-size:10px;font-weight:400}
.reject-form{margin-top:10px}
.reject-form label span{display:block;margin-bottom:5px;font-size:11px;font-weight:800}
.reject-form textarea{width:100%;padding:10px;border:1px solid var(--color-line);border-radius:8px;resize:vertical;font-size:12px}
.reject-actions{display:flex;gap:8px;margin-top:10px;justify-content:flex-end}
.form-error{padding:9px;border-radius:8px;background:#ffeaed;color:var(--color-danger)!important;font-size:10px;margin:8px 0}
.risk{display:inline-block;padding:3px 9px;border-radius:8px;font-size:10px;font-weight:800}
.risk-中{background:#fff4dd;color:#a36a11}
@media(max-width:850px){.detail-layout{grid-template-columns:1fr}.booking-card{position:static}.cover-wrap{height:390px}.cover-title h1{font-size:34px}}
@media(max-width:600px){.cover-wrap{height:350px;border-radius:18px}.cover-title{left:22px;right:22px;bottom:25px}.cover-title h1{font-size:28px}.facts{grid-template-columns:1fr}.organizer{align-items:flex-start;flex-wrap:wrap}.detail-layout{padding-top:20px}.detail-section{padding:21px}}
</style>
