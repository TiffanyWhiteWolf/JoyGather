<script setup lang="ts">
import { CalendarDays, Check, ChevronLeft, Clock, Copy, Heart, MapPin, MessageCircle, Share2, ShieldCheck, UserCheck, Users, X } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatPrice, percent } from '@/lib/utils'
import { useAppStore } from '@/stores/app'
import { apiDelete, apiGet, apiPost } from '@/lib/api'
import type { Activity } from '@/types'

const route = useRoute(); const router = useRouter(); const app = useAppStore(); const confirmOpen = ref(false); const cancelOpen = ref(false); const liked = ref(false); const safetyAccepted = ref(false)
const formError = ref('')
const registrationForm = reactive<Record<string, string>>({})
const fallbackActivity: Activity = {
  id: '', title: '加载中', summary: '', description: '', category: '城市探索',
  cover: 'https://images.unsplash.com/photo-1519501025264-65ba15a82390?auto=format&fit=crop&w=1400&q=85',
  date: '', time: '', location: '', district: '', distance: 0, longitude: 50, latitude: 50,
  price: 0, capacity: 1, joined: 0, tags: [], status: '报名中',
  organizer: { id: '', nickname: '', avatar: 'https://i.pravatar.cc/160?img=47', role: '个人用户', city: '', bio: '', interests: [], following: 0, followers: 0, credit: 100 },
}
const activityRow = ref<Activity | null>(null)
const activity = computed(() => activityRow.value ?? fallbackActivity)
const joined = computed(() => app.joinedActivityIds.includes(activity.value.id))
const waiting = computed(() => app.waitingActivityIds.includes(activity.value.id))
const full = computed(() => activity.value.joined >= activity.value.capacity)
const canRegister = computed(() => ['报名中','即将开始'].includes(activity.value.status))
interface RegistrationResult { status: '已报名' | '候补中' | '已取消'; promotedUserId?: string }

async function loadActivity() {
  activityRow.value = await apiGet<Activity>(`/activities/${route.params.id}`)
  await app.refreshUserState()
}

async function confirmJoin(){
  formError.value = ''
  for (const field of activity.value.joinFields ?? []) {
    if (!registrationForm[field]?.trim()) {
      formError.value = `请填写${field}`
      return
    }
  }
  try {
    const result = await apiPost<RegistrationResult>(`/activities/${activity.value.id}/registrations`, { fields: registrationForm })
    app.joinActivity(activity.value.id, result.status === '候补中')
    await loadActivity()
    confirmOpen.value=false; safetyAccepted.value=false
  } catch (err) {
    formError.value = err instanceof Error ? err.message : '报名失败，请稍后重试'
  }
}
async function cancelJoin(){
  try {
    await apiDelete<RegistrationResult>(`/activities/${activity.value.id}/registrations/me`)
    app.cancelRegistration(activity.value.id)
    await loadActivity()
    cancelOpen.value=false
  } catch (err) {
    formError.value = err instanceof Error ? err.message : '取消失败，请稍后重试'
  }
}

async function shareActivity() {
  const url = window.location.href
  try {
    if (navigator.share) await navigator.share({ title: activity.value.title, text: activity.value.summary, url })
    else await navigator.clipboard.writeText(url)
    app.showToast('活动链接已复制或分享')
  } catch {
    app.showToast('分享已取消')
  }
}

async function contactOrganizer() {
  await router.push('/messages')
  app.showToast('只有好友或同队成员可以发送消息')
}

onMounted(loadActivity)
</script>
<template>
  <div class="detail-page"><div class="detail-hero"><div class="container"><RouterLink to="/discover" class="back"><ChevronLeft :size="17" />返回活动列表</RouterLink><div class="cover-wrap"><img :src="activity.cover" :alt="activity.title" /><div class="cover-gradient"></div><div class="cover-title"><div class="tag-row"><span>{{ activity.category }}</span><span v-for="tag in activity.tags" :key="tag"># {{ tag }}</span></div><h1>{{ activity.title }}</h1><p>{{ activity.summary }}</p></div><div class="cover-actions"><button @click="liked=!liked"><Heart :fill="liked?'currentColor':'none'" :size="18" /></button><button @click="shareActivity"><Share2 :size="18" /></button></div></div></div></div>
    <div class="container detail-layout"><div class="detail-main"><section class="detail-section"><h2>关于这场活动</h2><p>{{ activity.description }}</p><div class="facts"><div><CalendarDays /><span><b>{{ activity.date }}</b><small>活动日期</small></span></div><div><Clock /><span><b>{{ activity.time }}</b><small>活动时间</small></span></div><div><MapPin /><span><b>{{ activity.location }}</b><small>{{ activity.district }} · 距你 {{ activity.distance }}km</small></span></div><div><Users /><span><b>{{ activity.joined }} 人已报名</b><small>上限 {{ activity.capacity }} 人</small></span></div></div></section><section class="detail-section"><h2>活动安排</h2><div class="timeline"><div><span>18:20</span><i></i><p><b>集合与签到</b><small>桥西游客中心门口，出示报名二维码</small></p></div><div><span>18:40</span><i></i><p><b>沿运河出发</b><small>破冰分组，认识同行伙伴</small></p></div><div><span>20:10</span><i></i><p><b>小河直街自由探索</b><small>自由拍摄、咖啡小憩</small></p></div><div><span>21:00</span><i></i><p><b>合影与结束</b><small>现场可自由组队续摊</small></p></div></div></section><section class="detail-section organizer"><img :src="activity.organizer.avatar" /><div><small>活动发起人</small><h3>{{ activity.organizer.nickname }} <Check :size="14" /></h3><p>{{ activity.organizer.bio }} · 信用分 {{ activity.organizer.credit }}</p></div><button class="btn btn-outline" @click="contactOrganizer"><MessageCircle :size="16" />联系 Ta</button></section><RouterLink :to="`/create?clone=${activity.id}`" class="clone-link"><Copy :size="16" />克隆这场活动并修改后再次发布</RouterLink></div>
      <aside><div class="booking-card"><div class="status-line"><span>{{ full ? '名额已满' : activity.status }}</span><b>{{ formatPrice(activity.price) }}</b></div><h3>{{ activity.date }} · {{ activity.time }}</h3><p><MapPin :size="16" />{{ activity.location }}</p><div class="capacity"><div><span>报名进度</span><b>{{ activity.joined }} / {{ activity.capacity }}</b></div><div><span :style="{width:`${percent(activity.joined,activity.capacity)}%`}"></span></div><small>{{ !canRegister ? '当前状态不可报名' : full ? '报名已满，可加入候补等待名额释放' : `仅剩 ${activity.capacity-activity.joined} 个名额，想去就别等啦` }}</small></div><button v-if="canRegister && !joined && !waiting" class="btn btn-primary join-btn" @click="confirmOpen=true">{{ full ? '加入候补队列' : '立即报名' }}</button><button v-else-if="canRegister && waiting" class="btn waiting-btn" @click="cancelOpen=true"><Clock :size="17" />候补中 · 退出候补</button><button v-else-if="joined" class="btn joined-btn" @click="cancelOpen=true"><Check :size="17" />已报名 · 取消报名</button><button v-else class="btn btn-outline join-btn" disabled>暂不可报名</button><div class="safe-note"><ShieldCheck :size="18" /><span><b>平台安全保障</b><small>实名参与 · 信誉校验 · 紧急联系人</small></span></div></div></aside>
    </div>
    <div v-if="confirmOpen" class="modal-mask" @click.self="confirmOpen=false"><div class="confirm-modal"><button class="modal-close" @click="confirmOpen=false"><X /></button><span class="modal-icon"><ShieldCheck /></span><h2>{{ full ? '确认加入候补队列？' : '确认加入这场活动？' }}</h2><div class="validation-list"><span><Check />名额状态：{{ full ? '已满，将排入候补' : '校验通过' }}</span><span><UserCheck />信用分与年龄条件：提交后校验</span></div><div v-if="activity.joinFields?.length" class="registration-fields"><label v-for="field in activity.joinFields" :key="field"><span>{{ field }} *</span><input v-model.trim="registrationForm[field]" :placeholder="`请输入${field}`" /></label></div><p>{{ activity.safetyNote || '请按时到达集合点，并遵守现场组织者安排。户外活动请根据天气准备装备，注意个人安全。' }}</p><label><input v-model="safetyAccepted" type="checkbox" /> 我已阅读并同意《活动安全须知》</label><p v-if="formError" class="form-error">{{ formError }}</p><div><button class="btn btn-outline" @click="confirmOpen=false">再想想</button><button class="btn btn-primary" :disabled="!safetyAccepted" @click="confirmJoin">{{ full ? '确认候补' : '确认报名' }}</button></div></div></div>
    <div v-if="cancelOpen" class="modal-mask" @click.self="cancelOpen=false"><div class="confirm-modal"><span class="modal-icon cancel"><X /></span><h2>{{ waiting ? '退出候补队列？' : '确认取消报名？' }}</h2><p>{{ waiting ? '退出后将失去当前候补顺位。' : '取消后名额会立即释放；若已有候补用户，系统会按顺序通知递补。' }}</p><div><button class="btn btn-outline" @click="cancelOpen=false">保留</button><button class="btn btn-dark" @click="cancelJoin">确认取消</button></div></div></div>
  </div>
</template>
<style scoped>
.detail-page{padding-bottom:50px}.detail-hero{padding:24px 0 0;background:#edeae4}.back{display:inline-flex;align-items:center;gap:5px;margin-bottom:16px;font-size:13px;color:var(--color-ink-soft)}.cover-wrap{position:relative;height:480px;overflow:hidden;border-radius:var(--radius-xl) var(--radius-xl) 0 0}.cover-wrap>img{width:100%;height:100%;object-fit:cover}.cover-gradient{position:absolute;inset:0;background:linear-gradient(180deg,transparent 30%,rgba(9,15,25,.85))}.cover-title{position:absolute;left:45px;right:150px;bottom:40px;color:#fff}.cover-title h1{margin:12px 0;font-size:42px;letter-spacing:-.04em}.cover-title p{margin:0;color:#e1e3e7}.cover-actions{position:absolute;right:32px;top:28px;display:flex;gap:8px}.cover-actions button{width:42px;height:42px;border:0;border-radius:50%;background:rgba(255,255,255,.9);display:grid;place-items:center}.detail-layout{display:grid;grid-template-columns:1fr 350px;gap:38px;padding-top:38px}.detail-section{margin-bottom:18px;padding:28px;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-lg)}.detail-section h2{margin-bottom:16px;font-size:21px}.detail-section>p{color:var(--color-ink-soft);line-height:1.85}.facts{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-top:24px}.facts>div{padding:14px;background:var(--color-bg);border-radius:12px;display:flex;align-items:center;gap:12px}.facts svg{color:var(--color-primary)}.facts span{display:flex;flex-direction:column}.facts b{font-size:13px}.facts small{margin-top:4px;color:var(--color-ink-soft);font-size:10px}.timeline>div{display:grid;grid-template-columns:55px 15px 1fr;gap:11px;min-height:70px}.timeline>div>span{padding-top:2px;font-size:11px;font-weight:800}.timeline i{position:relative;width:9px;height:9px;border:2px solid var(--color-primary);border-radius:50%}.timeline i:after{content:'';position:absolute;top:9px;left:2px;width:1px;height:55px;background:var(--color-line)}.timeline>div:last-child i:after{display:none}.timeline p{display:flex;flex-direction:column}.timeline p b{font-size:13px}.timeline p small{margin-top:5px;color:var(--color-ink-soft)}.organizer{display:flex;align-items:center;gap:14px}.organizer>img{width:58px;height:58px;border-radius:50%}.organizer>div{flex:1}.organizer small{color:var(--color-ink-soft)}.organizer h3{display:flex;align-items:center;gap:5px;margin:4px 0}.organizer h3 svg{padding:2px;background:var(--color-mint);color:#fff;border-radius:50%}.organizer p{margin:0;font-size:12px}.booking-card{position:sticky;top:96px;padding:25px;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-lg);box-shadow:var(--shadow-card)}.status-line{display:flex;justify-content:space-between;align-items:center}.status-line span{padding:6px 9px;border-radius:var(--radius-pill);background:var(--color-mint-soft);color:var(--color-mint);font-size:11px;font-weight:800}.status-line b{font-size:23px}.booking-card h3{margin:20px 0 10px}.booking-card>p{display:flex;gap:7px;color:var(--color-ink-soft);font-size:12px}.capacity{margin:22px 0}.capacity>div:first-child{display:flex;justify-content:space-between;font-size:11px}.capacity>div:nth-child(2){height:5px;margin:8px 0;background:#eee;border-radius:5px}.capacity>div:nth-child(2) span{display:block;height:100%;background:var(--color-mint);border-radius:5px}.capacity small{color:var(--color-ink-soft);font-size:10px}.join-btn,.joined-btn{width:100%}.joined-btn{background:var(--color-mint-soft);color:var(--color-mint)}.safe-note{display:flex;gap:10px;margin-top:18px;padding-top:17px;border-top:1px solid var(--color-line);color:var(--color-mint)}.safe-note span{display:flex;flex-direction:column;color:var(--color-ink)}.safe-note b{font-size:11px}.safe-note small{margin-top:3px;color:var(--color-ink-soft);font-size:9px}.modal-mask{position:fixed;z-index:100;inset:0;padding:16px;background:rgba(13,21,34,.55);backdrop-filter:blur(5px);display:grid;place-items:center}.confirm-modal{width:min(100%,460px);padding:34px;background:#fff;border-radius:var(--radius-lg);box-shadow:var(--shadow-float);text-align:center}.modal-icon{width:58px;height:58px;margin:0 auto 18px;border-radius:50%;background:var(--color-mint-soft);color:var(--color-mint);display:grid;place-items:center}.confirm-modal p{color:var(--color-ink-soft);line-height:1.7;font-size:13px}.confirm-modal label{display:block;margin:20px 0;font-size:12px}.confirm-modal>div{display:flex;gap:10px;justify-content:center}
.waiting-btn{width:100%;background:#fff4dd;color:#a36a11}.clone-link{padding:13px 18px;border:1px dashed var(--color-line);border-radius:12px;background:#fff;display:flex;align-items:center;justify-content:center;gap:7px;color:var(--color-primary);font-size:12px;font-weight:800}.confirm-modal{position:relative}.modal-close{position:absolute;right:16px;top:16px;border:0;background:none;color:var(--color-ink-soft)}.modal-close svg{width:18px}.modal-icon.cancel{background:#ffeaed;color:var(--color-danger)}.validation-list{display:flex!important;align-items:stretch;flex-direction:column;gap:7px!important;margin:18px 0;text-align:left}.validation-list span{padding:9px;border-radius:8px;background:var(--color-mint-soft);display:flex;align-items:center;gap:6px;color:#168b7d;font-size:10px}.validation-list svg{width:14px}.confirm-modal .btn:disabled{opacity:.45;cursor:not-allowed}
.registration-fields{display:grid!important;grid-template-columns:1fr;gap:9px!important;margin:14px 0;text-align:left}.registration-fields label{margin:0!important;font-size:10px;font-weight:800}.registration-fields span{display:block;margin-bottom:5px}.registration-fields input{width:100%;padding:10px;border:1px solid var(--color-line);border-radius:8px}.form-error{padding:9px;border-radius:8px;background:#ffeaed;color:var(--color-danger)!important;font-size:10px}
@media(max-width:850px){.detail-layout{grid-template-columns:1fr}.booking-card{position:static}.cover-wrap{height:390px}.cover-title h1{font-size:34px}}@media(max-width:600px){.cover-wrap{height:350px;border-radius:18px}.cover-title{left:22px;right:22px;bottom:25px}.cover-title h1{font-size:28px}.facts{grid-template-columns:1fr}.organizer{align-items:flex-start;flex-wrap:wrap}.detail-layout{padding-top:20px}.detail-section{padding:21px}}
</style>
