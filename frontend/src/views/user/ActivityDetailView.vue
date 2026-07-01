<script setup lang="ts">
import { CalendarDays, Check, ChevronLeft, Clock, Copy, Heart, MapPin, MessageCircle, Share2, ShieldBan, ShieldCheck, UserCheck, UserRoundPlus, Users, X } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatPrice, percent } from '@/lib/utils'
import { useAppStore } from '@/stores/app'
import { apiDelete, apiGet, apiPost } from '@/lib/api'
import type { Activity, User } from '@/types'
import CityMap from '@/components/map/CityMap.vue'

const route = useRoute(); const router = useRouter(); const app = useAppStore(); const confirmOpen = ref(false); const cancelOpen = ref(false); const liked = ref(false); const safetyAccepted = ref(false)
const formError = ref('')
const registrationForm = reactive<Record<string, string>>({})
const currentUser = ref<User | null>(null)
const fallbackActivity: Activity = {
  id: '', title: '加载中', summary: '', description: '', category: '城市探索',
  cover: 'https://images.unsplash.com/photo-1519501025264-65ba15a82390?auto=format&fit=crop&w=1400&q=85',
  date: '', time: '', location: '', city: '杭州', district: '', distance: 0, longitude: 120.15507, latitude: 30.274085,
  price: 0, capacity: 1, joined: 0, tags: [], status: '报名中',
  organizer: { id: '', nickname: '', avatar: 'https://i.pravatar.cc/160?img=47', role: '个人用户', city: '', bio: '', interests: [], following: 0, followers: 0, credit: 100 },
}
const activityRow = ref<Activity | null>(null)
const participants = ref<Participant[]>([])
const activity = computed(() => activityRow.value ?? fallbackActivity)
const joined = computed(() => app.joinedActivityIds.includes(activity.value.id))
const waiting = computed(() => app.waitingActivityIds.includes(activity.value.id))
const full = computed(() => activity.value.joined >= activity.value.capacity)
const canRegister = computed(() => activity.value.status === '报名中')
const canCancel = computed(() => activity.value.status === '报名中')
const statusButtonLabel = computed(() => {
  if (activity.value.status === '已截止') return '报名已结束'
  if (activity.value.status === '进行中') return '活动进行中'
  if (activity.value.status === '已结束') return '活动已结束'
  if (activity.value.status === '已下架') return '活动已下架'
  if (activity.value.status === '审核中') return '审核中'
  return '暂不可报名'
})

function parseSafeDate(iso?: string): Date | null {
  if (!iso) return null
  const d = new Date(iso)
  if (!isNaN(d.getTime())) return d
  // Safari fallback for YYYY-MM-DD
  const d2 = new Date(iso.replace(/-/g, '/'))
  return isNaN(d2.getTime()) ? null : d2
}

function formatDeadline(iso?: string) {
  if (!iso) return ''
  const d = parseSafeDate(iso)
  if (!d) return iso
  return `${d.getMonth() + 1}月${d.getDate()}日 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const formattedActivityTime = computed(() => {
  // 优先用 startAt（ISO 时间戳），兼容所有浏览器
  const d = parseSafeDate(activity.value.startAt) ?? parseSafeDate(activity.value.date)
  if (d) {
    return `${d.getMonth() + 1}月${d.getDate()}日 ${activity.value.time}`
  }
  return `${activity.value.date} ${activity.value.time}`
})

const formattedRegistrationTime = computed(() => {
  const d = parseSafeDate(activity.value.deadline)
  if (d) {
    return `${d.getMonth() + 1}月${d.getDate()}日 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }
  return activity.value.deadline || ''
})
const isOrganizer = computed(() => currentUser.value?.id === activity.value.organizer.id)
const showRejectionBanner = computed(() => isOrganizer.value && (activity.value.status === '已下架' || activity.value.status === '审核中'))
interface RegistrationResult { status: '已报名' | '候补中' | '已取消'; promotedUserId?: string }
interface Participant { id: string; nickname: string; avatar: string; city: string; status: string }

async function loadActivity() {
  const [act, user] = await Promise.all([
    apiGet<Activity>(`/activities/${route.params.id}`),
    apiGet<User>('/auth/me').catch(() => null),
  ])
  activityRow.value = act
  currentUser.value = user
  await app.refreshUserState()
  try { participants.value = await apiGet<Participant[]>(`/activities/${route.params.id}/participants`) } catch { participants.value = [] }
}

async function confirmJoin(){
  if (!app.isLoggedIn) { router.push('/auth?redirect=' + encodeURIComponent(window.location.pathname + window.location.search)); return }
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
  if (!app.isLoggedIn) { router.push('/auth?redirect=' + encodeURIComponent(window.location.pathname + window.location.search)); return }
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
  const organizerId = activity.value.organizer.id
  if (app.friendIds.includes(organizerId)) {
    await router.push('/messages')
    app.showToast('已打开消息页，可向好友发送消息')
  } else {
    app.showToast('关注对方后，互相关注即可成为好友并发消息')
  }
}

async function followOrganizer() {
  try {
    await apiPost(`/follows/${activity.value.organizer.id}`, {})
    app.addFollowedId(activity.value.organizer.id)
    app.showToast('已关注发起人')
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '操作失败')
  }
}

const participantActionLoading = ref<Record<string, boolean>>({})

// Friend request dialog
const requestDialog = reactive<{ show: boolean; targetUserId: string; targetNickname: string; message: string; loading: boolean }>({
  show: false, targetUserId: '', targetNickname: '', message: '', loading: false,
})
const commonEmojis = ['😊', '👋', '💪', '🔥', '🎉', '🤝', '👍', '❤️', '😎', '🌟', '🙌', '📸', '🏃', '☕', '🎮']

function insertEmoji(emoji: string) { requestDialog.message += emoji }

function openRequestDialog(userId: string, nickname: string) {
  requestDialog.show = true
  requestDialog.targetUserId = userId
  requestDialog.targetNickname = nickname
  requestDialog.message = ''
  requestDialog.loading = false
}

function closeRequestDialog() {
  requestDialog.show = false
  requestDialog.message = ''
}

async function sendFriendRequest() {
  if (!requestDialog.message.trim()) {
    app.showToast('请说点什么吧')
    return
  }
  if (requestDialog.message.length > 100) {
    app.showToast('留言不能超过100字')
    return
  }
  requestDialog.loading = true
  try {
    await apiPost('/friends/requests', {
      userId: requestDialog.targetUserId,
      source: 'ACTIVITY',
      message: requestDialog.message.trim(),
    })
    app.addFollowedId(requestDialog.targetUserId)
    app.showToast('已发送好友申请并关注对方')
    closeRequestDialog()
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '发送失败')
  } finally {
    requestDialog.loading = false
  }
}

// Participant actions
function isBlockedP(uid: string) { return app.blockedIds.includes(uid) }

async function followParticipant(userId: string) {
  participantActionLoading.value[userId] = true
  try {
    await apiPost(`/follows/${userId}`, {})
    app.addFollowedId(userId)
    app.showToast('已关注')
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '操作失败')
  } finally {
    participantActionLoading.value[userId] = false
  }
}

async function unfollowParticipant(userId: string) {
  participantActionLoading.value[userId] = true
  try {
    await apiDelete(`/follows/${userId}`)
    app.removeFollowedId(userId)
    app.showToast('已取消关注')
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '操作失败')
  } finally {
    participantActionLoading.value[userId] = false
  }
}

async function blockParticipant(userId: string) {
  try {
    await apiPost(`/blocks/${userId}`, { reason: '' })
    app.removeFollowedId(userId)
    app.addBlockedId(userId)
    app.showToast('已拉黑')
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '操作失败')
  }
}

async function unblockParticipant(userId: string) {
  participantActionLoading.value[userId] = true
  try {
    await apiDelete(`/blocks/${userId}`)
    app.removeBlockedId(userId)
    app.showToast('已取消拉黑')
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '操作失败')
  } finally {
    participantActionLoading.value[userId] = false
  }
}

function participantGoChat() {
  router.push('/messages')
  app.showToast('已打开消息页')
}

onMounted(loadActivity)
</script>
<template>
  <div class="detail-page"><div class="detail-hero"><div class="container"><RouterLink to="/discover" class="back"><ChevronLeft :size="17" />返回活动列表</RouterLink><div class="cover-wrap"><img :src="activity.cover" :alt="activity.title" /><div class="cover-gradient"></div><div class="cover-title"><div class="tag-row"><span>{{ activity.category }}</span><span v-for="tag in activity.tags" :key="tag"># {{ tag }}</span></div><h1>{{ activity.title }}</h1><p>{{ activity.summary }}</p></div><div class="cover-actions"><button @click="liked=!liked"><Heart :fill="liked?'currentColor':'none'" :size="18" /></button><button @click="shareActivity"><Share2 :size="18" /></button></div></div></div></div>
    <div v-if="showRejectionBanner" class="container"><div class="rejection-banner"><div class="rejection-icon"><X :size="22" /></div><div class="rejection-body"><b>{{ activity.status === '已下架' ? '你的活动未通过审核' : '活动正在安全审核中' }}</b><p>{{ activity.reviewReason || activity.offlineReason || '平台正在核查活动内容，请耐心等待。' }}</p></div><div class="rejection-actions"><RouterLink :to="`/create?clone=${activity.id}`" class="btn btn-primary btn-sm">根据反馈修改并重新提交</RouterLink></div></div></div>
    <div class="container detail-layout"><div class="detail-main"><section class="detail-section"><h2>关于这场活动</h2><p>{{ activity.description }}</p><div class="facts"><div><CalendarDays /><span><b>{{ activity.date }}</b><small>活动日期</small></span></div><div><Clock /><span><b>{{ activity.time }}</b><small>活动时间</small></span></div><div><MapPin /><span><b>{{ activity.location }}</b><small>{{ activity.city }} · {{ activity.district }} · 距你 {{ activity.distance }}km</small></span></div><div><Users /><span><b>{{ activity.joined }} 人已报名</b><small>上限 {{ activity.capacity }} 人</small></span></div><div><UserCheck /><span><b>{{ activity.minAge === undefined || activity.minAge === null ? '不限' : `${activity.minAge} 岁以上` }}</b><small>最低年龄限制</small></span></div></div></section><section class="detail-section location-section"><div class="location-title"><div><span class="eyebrow">EXACT LOCATION</span><h2>活动集合点</h2><p>{{ activity.city }} · {{ activity.district }} · {{ activity.location }}</p></div><code>{{ Number(activity.latitude).toFixed(6) }}, {{ Number(activity.longitude).toFixed(6) }}</code></div><CityMap :activities="[activity]" :city="activity.city" compact /></section><section v-if="participants.length" class="detail-section"><h2>已报名参与者 ({{ participants.length }})</h2><div class="participant-list"><div v-for="p in participants" :key="p.id" class="participant-item"><img :src="p.avatar" :alt="p.nickname" style="cursor:pointer" @click="router.push(`/profile/${p.id}`)" /><div class="participant-info"><b>{{ p.nickname }}</b><span>{{ p.city }}</span></div><template v-if="currentUser && currentUser.id !== p.id"><template v-if="isBlockedP(p.id)"><span class="state-tag blocked">已拉黑</span><button class="btn btn-outline btn-sm unblock-btn" :disabled="participantActionLoading[p.id]" @click.stop="unblockParticipant(p.id)">取消拉黑</button></template><template v-else-if="app.friendIds.includes(p.id)"><button class="btn btn-outline btn-sm" @click.stop="participantGoChat()"><MessageCircle :size="14" />发消息</button><button class="btn btn-outline btn-sm danger-btn" :disabled="participantActionLoading[p.id]" @click.stop="blockParticipant(p.id)">拉黑</button></template><template v-else-if="app.followedIds.includes(p.id)"><button class="btn btn-outline btn-sm" :disabled="participantActionLoading[p.id]" @click.stop="unfollowParticipant(p.id)">已关注</button><button class="btn btn-outline btn-sm" :disabled="participantActionLoading[p.id]" @click.stop="openRequestDialog(p.id, p.nickname)">申请好友</button><button class="btn btn-outline btn-sm danger-btn" :disabled="participantActionLoading[p.id]" @click.stop="blockParticipant(p.id)">拉黑</button></template><template v-else><button class="btn btn-primary btn-sm" :disabled="participantActionLoading[p.id]" @click.stop="followParticipant(p.id)">关注</button><button class="btn btn-outline btn-sm" :disabled="participantActionLoading[p.id]" @click.stop="openRequestDialog(p.id, p.nickname)">申请好友</button><button class="btn btn-outline btn-sm danger-btn" :disabled="participantActionLoading[p.id]" @click.stop="blockParticipant(p.id)">拉黑</button></template></template></div></div></section><section class="detail-section organizer"><img :src="activity.organizer.avatar" style="cursor:pointer" @click="router.push(`/profile/${activity.organizer.id}`)" /><div><small>活动发起人</small><h3>{{ activity.organizer.nickname }} <Check :size="14" /></h3><p>{{ activity.organizer.bio }} · 信用分 {{ activity.organizer.credit }}</p></div><div class="organizer-actions"><button v-if="currentUser && currentUser.id !== activity.organizer.id && !app.friendIds.includes(activity.organizer.id)" class="btn btn-outline btn-sm" @click="followOrganizer">{{ app.followedIds.includes(activity.organizer.id) ? '已关注' : '关注' }}</button><button class="btn btn-outline btn-sm" @click="contactOrganizer"><MessageCircle :size="16" />{{ app.friendIds.includes(activity.organizer.id) ? '发消息' : '联系 Ta' }}</button></div></section><RouterLink :to="`/create?clone=${activity.id}`" class="clone-link"><Copy :size="16" />克隆这场活动并修改后再次发布</RouterLink></div>
      <aside><div class="booking-card"><div class="status-line"><span>{{ full ? '名额已满' : activity.status }}</span><b>{{ formatPrice(activity.price) }}</b></div><h3>{{ activity.date }} · {{ activity.time }}</h3><p><MapPin :size="16" />{{ activity.location }}</p><div class="deadline-info"><Clock :size="14" /><span>报名截止：{{ formatDeadline(activity.deadline) }}</span></div><div class="capacity"><div><span>报名进度</span><b>{{ activity.joined }} / {{ activity.capacity }}</b></div><div><span :style="{width:`${percent(activity.joined,activity.capacity)}%`}"></span></div><small v-if="canRegister">{{ full ? '报名已满，可加入候补等待名额释放' : `仅剩 ${activity.capacity-activity.joined} 个名额，想去就别等啦` }}</small><small v-else>{{ statusButtonLabel }}</small></div><button v-if="canRegister && !joined && !waiting" class="btn btn-primary join-btn" @click="confirmOpen=true">{{ full ? '加入候补队列' : '立即报名' }}</button><button v-else-if="canRegister && waiting" class="btn waiting-btn" @click="cancelOpen=true"><Clock :size="17" />候补中 · 退出候补</button><button v-else-if="canRegister && joined" class="btn joined-btn" @click="cancelOpen=true"><Check :size="17" />已报名 · 取消报名</button><button v-else-if="!canRegister && joined" class="btn joined-btn" disabled><Check :size="17" />已报名</button><button v-else class="btn btn-outline join-btn" disabled>{{ statusButtonLabel }}</button><div class="safe-note"><ShieldCheck :size="18" /><span><b>平台安全保障</b><small>实名参与 · 信誉校验 · 紧急联系人</small></span></div></div></aside>
    </div>
    <div v-if="confirmOpen" class="modal-mask" @click.self="confirmOpen=false"><div class="confirm-modal"><button class="modal-close" @click="confirmOpen=false"><X /></button><span class="modal-icon"><ShieldCheck /></span><h2>{{ full ? '确认加入候补队列？' : '确认加入这场活动？' }}</h2><div class="validation-list"><span><Check />名额状态：{{ full ? '已满，将排入候补' : '校验通过' }}</span><span><UserCheck />信用分与年龄条件：提交后校验</span></div><div v-if="activity.joinFields?.length" class="registration-fields"><label v-for="field in activity.joinFields" :key="field"><span>{{ field }} *</span><input v-model.trim="registrationForm[field]" :placeholder="`请输入${field}`" /></label></div><p>{{ activity.safetyNote || '请按时到达集合点，并遵守现场组织者安排。户外活动请根据天气准备装备，注意个人安全。' }}</p><label><input v-model="safetyAccepted" type="checkbox" /> 我已阅读并同意《活动安全须知》</label><p v-if="formError" class="form-error">{{ formError }}</p><div><button class="btn btn-outline" @click="confirmOpen=false">再想想</button><button class="btn btn-primary" :disabled="!safetyAccepted" @click="confirmJoin">{{ full ? '确认候补' : '确认报名' }}</button></div></div></div>
    <div v-if="cancelOpen" class="modal-mask" @click.self="cancelOpen=false"><div class="confirm-modal"><span class="modal-icon cancel"><X /></span><h2>{{ waiting ? '退出候补队列？' : '确认取消报名？' }}</h2><p>{{ waiting ? '退出后将失去当前候补顺位。' : '取消后名额会立即释放；若已有候补用户，系统会按顺序通知递补。' }}</p><div><button class="btn btn-outline" @click="cancelOpen=false">保留</button><button class="btn btn-dark" @click="cancelJoin">确认取消</button></div></div></div>
    <!-- Friend Request Dialog -->
    <div v-if="requestDialog.show" class="modal-mask" @click.self="closeRequestDialog">
      <div class="request-modal">
        <h3>申请添加 {{ requestDialog.targetNickname }} 为好友</h3>
        <p class="request-hint">发送申请后将自动关注对方。对方确认后你们会成为互相关注的好友。</p>
        <div class="message-area"><textarea v-model="requestDialog.message" placeholder="说点什么吧，让对方更了解你..." maxlength="100" rows="3"></textarea><span class="char-count">{{ requestDialog.message.length }}/100</span></div>
        <div class="emoji-row"><button v-for="e in commonEmojis" :key="e" class="emoji-btn" @click="insertEmoji(e)" :title="e">{{ e }}</button></div>
        <div class="edit-actions"><button class="btn btn-outline" @click="closeRequestDialog" :disabled="requestDialog.loading">取消</button><button class="btn btn-primary" @click="sendFriendRequest" :disabled="requestDialog.loading || !requestDialog.message.trim()">{{ requestDialog.loading ? '发送中...' : '发送申请' }}</button></div>
      </div>
    </div>
  </div>
</template>
<style scoped>
.detail-page{padding-bottom:50px}.detail-hero{padding:24px 0 0;background:#edeae4}
.location-section{padding:18px}.location-title{padding:6px 6px 16px;display:flex;align-items:flex-end;justify-content:space-between;gap:16px}.location-title h2{margin:5px 0}.location-title p{margin:0;color:var(--color-ink-soft);font-size:12px}.location-title code{padding:8px 10px;border-radius:8px;background:var(--color-bg);color:var(--color-mint);font-size:10px;white-space:nowrap}.location-section :deep(.real-map){min-height:330px}
.rejection-banner{margin-top:20px;padding:18px 22px;border:1px solid #f5c2c7;border-radius:var(--radius-lg);background:#fff5f5;display:flex;align-items:center;gap:16px}
.rejection-icon{width:44px;height:44px;border-radius:50%;background:#ffeaed;color:var(--color-danger);display:grid;place-items:center;flex-shrink:0}
.rejection-body{flex:1}.rejection-body b{font-size:15px}.rejection-body p{margin:5px 0 0;color:var(--color-ink-soft);font-size:13px}
.rejection-actions{flex-shrink:0}
.back{display:inline-flex;align-items:center;gap:5px;margin-bottom:16px;font-size:13px;color:var(--color-ink-soft)}.cover-wrap{position:relative;height:480px;overflow:hidden;border-radius:var(--radius-xl) var(--radius-xl) 0 0}.cover-wrap>img{width:100%;height:100%;object-fit:cover}.cover-gradient{position:absolute;inset:0;background:linear-gradient(180deg,transparent 30%,rgba(9,15,25,.85))}.cover-title{position:absolute;left:45px;right:150px;bottom:40px;color:#fff}.cover-title h1{margin:12px 0;font-size:42px;letter-spacing:-.04em}.cover-title p{margin:0;color:#e1e3e7}.cover-actions{position:absolute;right:32px;top:28px;display:flex;gap:8px}.cover-actions button{width:42px;height:42px;border:0;border-radius:50%;background:rgba(255,255,255,.9);display:grid;place-items:center}.detail-layout{display:grid;grid-template-columns:1fr 350px;gap:38px;padding-top:38px}.detail-section{margin-bottom:18px;padding:28px;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-lg)}.detail-section h2{margin-bottom:16px;font-size:21px}.detail-section>p{color:var(--color-ink-soft);line-height:1.85}.facts{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-top:24px}.facts>div{padding:14px;background:var(--color-bg);border-radius:12px;display:flex;align-items:center;gap:12px}.facts svg{color:var(--color-primary)}.facts span{display:flex;flex-direction:column}.facts b{font-size:13px}.facts small{margin-top:4px;color:var(--color-ink-soft);font-size:10px}.organizer{display:flex;align-items:center;gap:14px}.organizer>img{width:58px;height:58px;border-radius:50%}.organizer>div{flex:1}.organizer small{color:var(--color-ink-soft)}.organizer h3{display:flex;align-items:center;gap:5px;margin:4px 0}.organizer h3 svg{padding:2px;background:var(--color-mint);color:#fff;border-radius:50%}.organizer p{margin:0;font-size:12px}.organizer-actions{display:flex;flex-direction:column;gap:6px}.organizer-actions .btn{font-size:11px}.participant-list{display:flex;flex-direction:column;gap:8px}.participant-item{display:flex;align-items:center;gap:12px}.participant-item img{width:40px;height:40px;border-radius:50%;object-fit:cover}.participant-info{flex:1}.participant-info b{display:block;font-size:14px}.participant-info span{font-size:11px;color:var(--color-ink-soft)}.state-tag{font-size:11px;font-weight:700;padding:4px 10px;border-radius:var(--radius-pill);background:var(--color-bg);color:var(--color-ink-soft)}.state-tag.friend{background:var(--color-mint-soft);color:var(--color-mint)}.booking-card{position:sticky;top:96px;padding:25px;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-lg);box-shadow:var(--shadow-card)}.status-line{display:flex;justify-content:space-between;align-items:center}.status-line span{padding:6px 9px;border-radius:var(--radius-pill);background:var(--color-mint-soft);color:var(--color-mint);font-size:11px;font-weight:800}.status-line b{font-size:23px}.booking-card h3{margin:20px 0 10px}.booking-card>p{display:flex;gap:7px;color:var(--color-ink-soft);font-size:12px}.capacity{margin:22px 0}.capacity>div:first-child{display:flex;justify-content:space-between;font-size:11px}.capacity>div:nth-child(2){height:5px;margin:8px 0;background:#eee;border-radius:5px}.capacity>div:nth-child(2) span{display:block;height:100%;background:var(--color-mint);border-radius:5px}.capacity small{color:var(--color-ink-soft);font-size:10px}.join-btn,.joined-btn{width:100%}.joined-btn{background:var(--color-mint-soft);color:var(--color-mint)}.safe-note{display:flex;gap:10px;margin-top:18px;padding-top:17px;border-top:1px solid var(--color-line);color:var(--color-mint)}.safe-note span{display:flex;flex-direction:column;color:var(--color-ink)}.safe-note b{font-size:11px}.safe-note small{margin-top:3px;color:var(--color-ink-soft);font-size:9px}.modal-mask{position:fixed;z-index:100;inset:0;padding:16px;background:rgba(13,21,34,.55);backdrop-filter:blur(5px);display:grid;place-items:center}.confirm-modal{width:min(100%,460px);padding:34px;background:#fff;border-radius:var(--radius-lg);box-shadow:var(--shadow-float);text-align:center}.modal-icon{width:58px;height:58px;margin:0 auto 18px;border-radius:50%;background:var(--color-mint-soft);color:var(--color-mint);display:grid;place-items:center}.confirm-modal p{color:var(--color-ink-soft);line-height:1.7;font-size:13px}.confirm-modal label{display:block;margin:20px 0;font-size:12px}.confirm-modal>div{display:flex;gap:10px;justify-content:center}
.waiting-btn{width:100%;background:#fff4dd;color:#a36a11}.deadline-info{display:flex;align-items:center;gap:6px;margin-top:10px;padding:8px 10px;border-radius:8px;background:var(--color-bg);color:var(--color-ink-soft);font-size:12px}.deadline-info svg{flex-shrink:0;color:var(--color-primary)}.clone-link{padding:13px 18px;border:1px dashed var(--color-line);border-radius:12px;background:#fff;display:flex;align-items:center;justify-content:center;gap:7px;color:var(--color-primary);font-size:12px;font-weight:800}.confirm-modal{position:relative}.modal-close{position:absolute;right:16px;top:16px;border:0;background:none;color:var(--color-ink-soft)}.modal-close svg{width:18px}.modal-icon.cancel{background:#ffeaed;color:var(--color-danger)}.validation-list{display:flex!important;align-items:stretch;flex-direction:column;gap:7px!important;margin:18px 0;text-align:left}.validation-list span{padding:9px;border-radius:8px;background:var(--color-mint-soft);display:flex;align-items:center;gap:6px;color:#168b7d;font-size:10px}.validation-list svg{width:14px}.confirm-modal .btn:disabled{opacity:.45;cursor:not-allowed}
.registration-fields{display:grid!important;grid-template-columns:1fr;gap:9px!important;margin:14px 0;text-align:left}.registration-fields label{margin:0!important;font-size:10px;font-weight:800}.registration-fields span{display:block;margin-bottom:5px}.registration-fields input{width:100%;padding:10px;border:1px solid var(--color-line);border-radius:8px}.form-error{padding:9px;border-radius:8px;background:#ffeaed;color:var(--color-danger)!important;font-size:10px}
@media(max-width:850px){.detail-layout{grid-template-columns:1fr}.booking-card{position:static}.cover-wrap{height:390px}.cover-title h1{font-size:34px}}@media(max-width:600px){.cover-wrap{height:350px;border-radius:18px}.cover-title{left:22px;right:22px;bottom:25px}.cover-title h1{font-size:28px}.facts{grid-template-columns:1fr}.organizer{align-items:flex-start;flex-wrap:wrap}.detail-layout{padding-top:20px}.detail-section{padding:21px}}
/* Friend Request Dialog */
.request-modal{width:440px;max-width:90vw;padding:28px;background:#fff;border-radius:var(--radius-xl)}
.request-modal h3{margin-bottom:8px;font-size:18px}
.request-hint{color:var(--color-ink-soft);font-size:13px;margin-bottom:16px;line-height:1.5}
.message-area{position:relative;margin-bottom:12px}
.message-area textarea{width:100%;padding:12px;border:1px solid var(--color-line);border-radius:10px;font-size:14px;font-family:inherit;outline:0;resize:vertical;line-height:1.6}
.message-area textarea:focus{border-color:var(--color-primary)}
.char-count{position:absolute;right:10px;bottom:8px;font-size:11px;color:var(--color-ink-soft)}
.emoji-row{display:flex;gap:6px;flex-wrap:wrap;margin-bottom:16px}
.emoji-btn{width:34px;height:34px;display:flex;align-items:center;justify-content:center;border:1px solid var(--color-line);border-radius:8px;background:#fff;font-size:18px;cursor:pointer;transition:background .15s}
.emoji-btn:hover{background:var(--color-bg)}
.edit-actions{display:flex;gap:8px;justify-content:flex-end;margin-top:8px}
.edit-actions .btn:disabled{opacity:.45;cursor:not-allowed}
/* Social button styles (match FriendsView) */
.state-tag{font-size:12px;font-weight:700;padding:6px 10px;border-radius:var(--radius-pill)}
.state-tag.blocked{background:#fff0f0;color:#c0392b;border:1px solid #f5c2c7}
.state-tag.friend{background:var(--color-mint-soft);color:var(--color-mint)}
.unblock-btn{color:#c0392b;border-color:#e8c8c8;background:#fefafa}
.unblock-btn:hover{background:#fff0f0;border-color:#c0392b}
.btn-mint{background:var(--color-mint-soft);color:var(--color-mint);border:1px solid var(--color-mint);cursor:default}
.danger-btn{color:var(--color-danger);border-color:var(--color-danger)}
.danger-btn:hover{background:#fff5f5}
.participant-item .btn{font-size:11px;padding:4px 10px}
</style>
