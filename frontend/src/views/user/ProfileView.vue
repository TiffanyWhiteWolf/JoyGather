<script setup lang="ts">
import { Building2, CalendarDays, Camera, CheckCircle2, ChevronRight, Copy, FileCheck, FileText, Heart, MapPin, QrCode, Send, Settings, ShieldAlert, Star, Trash2, Users, X } from 'lucide-vue-next'
import QRCode from 'qrcode'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import ActivityCard from '@/components/activity/ActivityCard.vue'
import { apiDelete, apiGet, apiPost, apiPut, apiUpload, clearAuthStorage } from '@/lib/api'
import { useAppStore } from '@/stores/app'
import type { Activity, User } from '@/types'

interface FileResponse {
  id: string
  url: string
  originalName: string
  contentType: string
  size: number
  provider: string
}

interface MerchantApplication {
  id: string
  merchantName: string
  licenseName?: string
  licenseUrl?: string
  status: string
  reason?: string
  submittedAt?: string
  reviewedAt?: string
}

interface CheckinCodeResponse {
  code: string
  url: string
  expiresAt: string
}

interface RegistrationManagementRow {
  id: string
  userId: string
  nickname: string
  avatar: string
  status: string
  queuePosition: number
  createdAt?: string
  checkedInAt?: string
}

const profileTabs = ['我的活动', '商家中心', '动态', '收藏', '活动总结'] as const
type ProfileTab = typeof profileTabs[number]

const router = useRouter()
const app = useAppStore()
const currentUser = ref<User | null>(null)
const activities = ref<Activity[]>([])
const editing = ref(false)
const activeTab = ref<ProfileTab>('我的活动')
const saving = ref(false)
const uploadingAvatar = ref(false)
const error = ref('')
const avatarInput = ref<HTMLInputElement | null>(null)
const merchantApplications = ref<MerchantApplication[]>([])
const merchantSaving = ref(false)
const merchantSubmitting = ref(false)
const merchantError = ref('')
const merchantForm = reactive({ merchantName: '', merchantNickname: '', merchantFields: '', licenseName: '', licenseUrl: '' })
const checkinOpen = ref(false)
const checkinLoading = ref(false)
const checkinError = ref('')
const checkinActivity = ref<Activity | null>(null)
const checkinCode = ref<CheckinCodeResponse | null>(null)
const checkinUrl = ref('')
const checkinQr = ref('')
const checkinRows = ref<RegistrationManagementRow[]>([])
const registrationLoading = ref(false)
const cancelOpen = ref(false)
const cancelling = ref(false)
const cancelError = ref('')
const cancelForm = reactive({ password: '', confirmText: '', reason: '' })
const suggestedInterests = ['徒步', '骑行', '桌游', '摄影', '城市探索', '运动健身', '学习交流', '公益活动', '露营', '音乐', '读书', '咖啡']
const merchantFieldPresets = ['户外运动', '城市探索', '桌游聚会', '亲子活动', '学习交流', '公益活动', '运动健身', '商业沙龙']
const profile = reactive({ nickname: '', avatar: '', gender: '', birthday: '', city: '杭州', bio: '', interests: '' })
const showCustomInterest = ref(false)
const customInterestInput = ref('')

const birthdayDisplay = computed(() => {
  if (!profile.birthday) return ''
  const [year, month, day] = profile.birthday.split('-')
  return `${year}年${month}月${day}日`
})
const latestMerchantApplication = computed(() => merchantApplications.value[0] ?? null)
const customInterestTags = computed(() => parseInterestTags().filter(tag => !suggestedInterests.includes(tag)))
const checkedInCount = computed(() => checkinRows.value.filter(item => item.status === '已签到').length)
const registeredCount = computed(() => checkinRows.value.filter(item => item.status === '已报名' || item.status === '已签到').length)
const waitingCount = computed(() => checkinRows.value.filter(item => item.status === '候补中').length)
const merchantStatusText = computed(() => {
  if (currentUser.value?.role === '商家用户') return currentUser.value.verified ? '已认证商家' : '商家账号'
  if (latestMerchantApplication.value) return `认证${latestMerchantApplication.value.status}`
  return '未提交认证'
})

onMounted(async () => {
  try {
    const [user, rows, applications] = await Promise.all([
      apiGet<User>('/auth/me'),
      apiGet<Activity[]>('/activities/my'),
      apiGet<MerchantApplication[]>('/users/merchant-applications/me').catch(() => []),
    ])
    currentUser.value = user
    merchantApplications.value = applications
    const latestApplication = applications[0]
    Object.assign(profile, {
      nickname: user.nickname,
      avatar: user.avatar,
      gender: user.gender || '',
      birthday: user.birthday || '',
      city: user.city,
      bio: user.bio,
      interests: user.interests.join('、'),
    })
    Object.assign(merchantForm, {
      merchantName: user.merchantName || latestApplication?.merchantName || '',
      merchantNickname: user.merchantNickname || user.merchantName || '',
      merchantFields: (user.merchantFields || []).join('、'),
      licenseName: '',
      licenseUrl: '',
    })
    activities.value = rows
  } catch {
    router.push('/auth')
  }
})

function parseInterestTags() {
  return Array.from(new Set(profile.interests.split(/[、,，]/).map(item => item.trim()).filter(Boolean)))
}

function hasInterest(tag: string) {
  return parseInterestTags().includes(tag)
}

function toggleInterest(tag: string) {
  const tags = parseInterestTags()
  const next = tags.includes(tag) ? tags.filter(item => item !== tag) : [...tags, tag]
  profile.interests = next.join('、')
}

function addCustomInterest() {
  const tag = customInterestInput.value.trim()
  if (!tag) return
  if (!hasInterest(tag)) profile.interests = [...parseInterestTags(), tag].join('、')
  customInterestInput.value = ''
}

function removeCustomInterest(tag: string) {
  profile.interests = parseInterestTags().filter(item => item !== tag).join('、')
}

function parseMerchantFields() {
  return Array.from(new Set(merchantForm.merchantFields.split(/[、,，]/).map(item => item.trim()).filter(Boolean)))
}

function hasMerchantField(field: string) {
  return parseMerchantFields().includes(field)
}

function toggleMerchantField(field: string) {
  const fields = parseMerchantFields()
  const next = fields.includes(field) ? fields.filter(item => item !== field) : [...fields, field]
  merchantForm.merchantFields = next.join('、')
}

async function saveProfile() {
  error.value = ''
  saving.value = true
  try {
    const updated = await apiPut<User>('/users/me', { ...profile, interests: parseInterestTags() })
    currentUser.value = updated
    editing.value = false
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存失败'
  } finally {
    saving.value = false
  }
}

async function refreshMerchantApplications() {
  merchantApplications.value = await apiGet<MerchantApplication[]>('/users/merchant-applications/me')
}

async function saveMerchantProfile() {
  merchantError.value = ''
  if (!merchantForm.merchantName.trim()) {
    merchantError.value = '请填写商家名称'
    return
  }
  merchantSaving.value = true
  try {
    const updated = await apiPut<User>('/users/me', {
      ...profile,
      interests: parseInterestTags(),
      merchantName: merchantForm.merchantName,
      merchantNickname: merchantForm.merchantNickname,
      merchantFields: parseMerchantFields(),
    })
    currentUser.value = updated
    app.showToast('商家资料已保存')
  } catch (err) {
    merchantError.value = err instanceof Error ? err.message : '商家资料保存失败'
  } finally {
    merchantSaving.value = false
  }
}

async function submitMerchantApplication() {
  merchantError.value = ''
  if (!merchantForm.merchantName.trim()) {
    merchantError.value = '请填写商家名称'
    return
  }
  if (!merchantForm.licenseName.trim() && !merchantForm.licenseUrl.trim()) {
    merchantError.value = '请上传或填写营业凭证'
    return
  }
  merchantSubmitting.value = true
  try {
    await apiPost<void>('/users/merchant-applications', {
      merchantName: merchantForm.merchantName,
      merchantNickname: merchantForm.merchantNickname,
      merchantFields: parseMerchantFields(),
      licenseName: merchantForm.licenseName,
      licenseUrl: merchantForm.licenseUrl,
    })
    await refreshMerchantApplications()
    app.showToast('商家认证已提交，等待后台审核')
  } catch (err) {
    merchantError.value = err instanceof Error ? err.message : '认证提交失败'
  } finally {
    merchantSubmitting.value = false
  }
}

async function openCheckinManagement(activity: Activity) {
  checkinError.value = ''
  checkinActivity.value = activity
  checkinCode.value = null
  checkinUrl.value = ''
  checkinQr.value = ''
  checkinOpen.value = true
  await loadCheckinRows(activity.id)
}

async function loadCheckinRows(activityId: string) {
  registrationLoading.value = true
  try {
    checkinRows.value = await apiGet<RegistrationManagementRow[]>(`/activities/${activityId}/registrations`)
  } catch (err) {
    checkinRows.value = []
    checkinError.value = err instanceof Error ? err.message : '签到名单加载失败'
  } finally {
    registrationLoading.value = false
  }
}

async function generateCheckinCode(activity: Activity) {
  checkinError.value = ''
  checkinActivity.value = activity
  checkinCode.value = null
  checkinUrl.value = ''
  checkinQr.value = ''
  checkinOpen.value = true
  checkinLoading.value = true
  try {
    const result = await apiPost<CheckinCodeResponse>(`/activities/${activity.id}/checkins/qr`, { locationRequired: false })
    const fullUrl = new URL(result.url, window.location.origin).toString()
    checkinCode.value = result
    checkinUrl.value = fullUrl
    checkinQr.value = await QRCode.toDataURL(fullUrl, { width: 220, margin: 1 })
  } catch (err) {
    checkinError.value = err instanceof Error ? err.message : '签到码生成失败'
  } finally {
    checkinLoading.value = false
  }
}

async function copyCheckinUrl() {
  if (!checkinUrl.value) return
  await navigator.clipboard.writeText(checkinUrl.value)
  app.showToast('签到链接已复制')
}

function formatDateTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}

async function uploadAvatar(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  error.value = ''
  if (!file.type.startsWith('image/')) {
    error.value = '请选择图片文件作为头像'
    input.value = ''
    return
  }
  uploadingAvatar.value = true
  try {
    const form = new FormData()
    form.append('file', file)
    const uploaded = await apiUpload<FileResponse>('/files/avatar', form)
    profile.avatar = uploaded.url
    if (currentUser.value) currentUser.value = { ...currentUser.value, avatar: uploaded.url }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '头像上传失败'
  } finally {
    uploadingAvatar.value = false
    input.value = ''
  }
}

function changeCover() {
  error.value = ''
  window.alert('封面图上传暂未开放，请先在头像 URL 中更新个人展示图片。')
}

function openCancelAccount() {
  cancelError.value = ''
  cancelForm.password = ''
  cancelForm.confirmText = ''
  cancelForm.reason = ''
  cancelOpen.value = true
}

async function cancelAccount() {
  cancelError.value = ''
  if (!cancelForm.password) {
    cancelError.value = '请输入当前密码'
    return
  }
  if (cancelForm.confirmText !== '注销账号') {
    cancelError.value = '请完整输入“注销账号”'
    return
  }
  cancelling.value = true
  try {
    await apiDelete<void>('/users/me', {
      password: cancelForm.password,
      confirmText: cancelForm.confirmText,
      reason: cancelForm.reason,
    })
    clearAuthStorage()
    app.clearUserState()
    currentUser.value = null
    cancelOpen.value = false
    await router.push('/auth')
    app.showToast('账号已注销')
  } catch (err) {
    cancelError.value = err instanceof Error ? err.message : '注销失败，请稍后重试'
  } finally {
    cancelling.value = false
  }
}
</script>

<template>
  <div v-if="currentUser" class="container profile-page">
    <input ref="avatarInput" class="hidden-file" type="file" accept="image/*" @change="uploadAvatar" />

    <section class="profile-cover">
      <div class="profile-pattern"></div>
      <button @click="changeCover"><Camera :size="16" />更换封面</button>
    </section>

    <section class="profile-main">
      <img :src="currentUser.avatar" />
      <div class="profile-copy">
        <div>
          <h1>{{ currentUser.nickname }} <CheckCircle2 v-if="currentUser.verified" :size="18" /></h1>
          <span class="location"><MapPin :size="14" />{{ currentUser.city }}</span>
        </div>
        <p>{{ currentUser.bio || '还没有填写个性签名' }}</p>
        <div class="tag-row"><span v-for="tag in currentUser.interests" :key="tag"># {{ tag }}</span></div>
      </div>
      <div class="profile-stats">
        <div><b>{{ currentUser.following }}</b><span>关注</span></div>
        <div><b>{{ currentUser.followers }}</b><span>粉丝</span></div>
        <div><b>{{ currentUser.credit }}</b><span>信用分</span></div>
      </div>
      <button class="btn btn-outline" @click="editing=!editing"><Settings :size="17" />编辑资料</button>
    </section>

    <section v-if="editing" class="edit-panel">
      <div class="avatar-editor">
        <img :src="profile.avatar || currentUser.avatar" />
        <div>
          <b>头像</b>
          <button class="btn btn-outline" :disabled="uploadingAvatar" @click="avatarInput?.click()">
            <Camera :size="16" />{{ uploadingAvatar ? '上传中' : '上传图片' }}
          </button>
          <input v-model.trim="profile.avatar" class="input" placeholder="或粘贴图片 URL" />
        </div>
      </div>
      <div class="form-grid">
        <label>昵称<input v-model.trim="profile.nickname" class="input" /></label>
        <label>性别<input v-model.trim="profile.gender" class="input" /></label>
        <label>
          生日
          <div class="date-picker-wrapper">
            <input v-model="profile.birthday" type="date" class="input date-native" />
            <span class="date-overlay" aria-hidden="true">{{ birthdayDisplay || '请选择生日' }}</span>
          </div>
        </label>
        <label>城市<input v-model.trim="profile.city" class="input" /></label>
      </div>
      <label class="interest-editor">
        兴趣标签
        <div class="interest-presets">
          <button v-for="tag in suggestedInterests" :key="tag" type="button" :class="{ active: hasInterest(tag) }" @click="toggleInterest(tag)"># {{ tag }}</button>
          <button type="button" class="interest-other-btn" :class="{ active: showCustomInterest }" @click="showCustomInterest=!showCustomInterest"># 其他</button>
        </div>
        <div v-if="customInterestTags.length" class="custom-tags">
          <span v-for="tag in customInterestTags" :key="tag" class="custom-tag"># {{ tag }}<button type="button" class="custom-tag-remove" @click="removeCustomInterest(tag)">&times;</button></span>
        </div>
        <div v-if="showCustomInterest" class="custom-interest-row">
          <input v-model="customInterestInput" class="input" placeholder="输入自定义兴趣，回车添加" @keyup.enter="addCustomInterest" @keyup.esc="showCustomInterest=false" />
          <button type="button" class="btn btn-primary" @click="addCustomInterest">添加</button>
        </div>
        <input v-model.trim="profile.interests" class="input" placeholder="可选择上方标签，也可用顿号手动输入" />
      </label>
      <label>个性签名<textarea v-model.trim="profile.bio" class="textarea"></textarea></label>
      <p v-if="error" class="form-error">{{ error }}</p>
      <div class="edit-actions">
        <button class="btn btn-outline" @click="editing=false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="saveProfile">{{ saving ? '保存中' : '保存资料' }}</button>
      </div>
    </section>

    <div class="profile-grid">
      <main>
        <div class="profile-tabs">
          <button v-for="tab in profileTabs" :key="tab" :class="{ active: activeTab === tab }" @click="activeTab=tab">{{ tab }}</button>
        </div>

        <template v-if="activeTab==='我的活动'">
          <div v-if="activities.length" class="activity-grid managed-activities">
            <div v-for="activity in activities" :key="activity.id" class="managed-activity">
              <ActivityCard :activity="activity" />
              <div class="activity-tools">
                <span>{{ activity.status }}</span>
                <button @click="openCheckinManagement(activity)"><QrCode :size="15" />签到管理</button>
              </div>
            </div>
          </div>
          <div v-else class="empty-state">你还没有发布活动。</div>
        </template>

        <template v-else-if="activeTab==='商家中心'">
          <section class="merchant-panel">
            <div class="merchant-head">
              <div>
                <span class="eyebrow">MERCHANT</span>
                <h2>商家认证与资料管理</h2>
                <p>商家资料与个人资料分开维护；提交认证后由管理员在审核中心处理。</p>
              </div>
              <i>{{ merchantStatusText }}</i>
            </div>
            <div class="merchant-grid">
              <div class="merchant-form-card">
                <h3>商家资料</h3>
                <label>商家名称 *<input v-model.trim="merchantForm.merchantName" class="input" placeholder="营业执照或经营主体名称" /></label>
                <label>商家昵称<input v-model.trim="merchantForm.merchantNickname" class="input" placeholder="对用户展示的品牌名" /></label>
                <label>
                  关注领域
                  <div class="interest-presets merchant-presets">
                    <button v-for="field in merchantFieldPresets" :key="field" type="button" :class="{ active: hasMerchantField(field) }" @click="toggleMerchantField(field)"># {{ field }}</button>
                  </div>
                  <input v-model.trim="merchantForm.merchantFields" class="input" placeholder="可选择标签，也可用顿号手动输入" />
                </label>
                <button class="btn btn-primary" :disabled="merchantSaving" @click="saveMerchantProfile">
                  <FileCheck :size="16" />{{ merchantSaving ? '保存中' : '保存商家资料' }}
                </button>
              </div>
              <div class="merchant-form-card">
                <h3>认证申请</h3>
                <label>营业凭证文件<input class="input" type="file" accept="image/*,.pdf" @change="merchantForm.licenseName=($event.target as HTMLInputElement).files?.[0]?.name||''" /></label>
                <label>凭证 URL<input v-model.trim="merchantForm.licenseUrl" class="input" placeholder="也可粘贴营业执照或授权书链接" /></label>
                <p v-if="latestMerchantApplication" class="merchant-status">最近申请：{{ latestMerchantApplication.status }}<span v-if="latestMerchantApplication.reason"> · {{ latestMerchantApplication.reason }}</span></p>
                <p v-else class="merchant-status">尚未提交商家认证申请。</p>
                <button class="btn btn-dark" :disabled="merchantSubmitting" @click="submitMerchantApplication">
                  <Send :size="16" />{{ merchantSubmitting ? '提交中' : '提交认证申请' }}
                </button>
              </div>
            </div>
            <p v-if="merchantError" class="form-error">{{ merchantError }}</p>
          </section>
        </template>

        <div v-else class="empty-state">{{ activeTab }}功能已接入个人页入口，当前账号暂无数据。</div>
      </main>

      <aside>
        <div class="side-card">
          <h3>我的快捷入口</h3>
          <RouterLink to="/drafts"><FileText />我的草稿<ChevronRight /></RouterLink>
          <RouterLink to="/check-in"><QrCode />扫码签到<ChevronRight /></RouterLink>
          <RouterLink to="/teams"><Users />我的小队<ChevronRight /></RouterLink>
          <RouterLink to="/create"><CalendarDays />发起活动<ChevronRight /></RouterLink>
          <RouterLink to="/discover"><Heart />我的收藏<ChevronRight /></RouterLink>
          <button class="side-action" @click="activeTab='商家中心'"><Building2 />商家中心<ChevronRight /></button>
        </div>
        <div class="side-card credit">
          <div><Star :size="20" /><b>信用表现优秀</b></div>
          <strong>{{ currentUser.credit }}<small>/100</small></strong>
          <p>按时参与、友善交流，让每次相遇都更安心。</p>
        </div>
        <div class="side-card danger-card">
          <div><ShieldAlert :size="18" /><h3>账号安全</h3></div>
          <p>注销后将退出登录，个人资料会匿名化，已发布且未结束的活动会下架。</p>
          <button class="delete-account" @click="openCancelAccount"><Trash2 :size="16" />注销账号</button>
        </div>
      </aside>
    </div>

    <div v-if="checkinOpen" class="account-modal" @click.self="checkinOpen=false">
      <div class="checkin-modal">
        <button class="modal-close" @click="checkinOpen=false"><X /></button>
        <span class="modal-icon checkin"><QrCode /></span>
        <h2>{{ checkinActivity?.title || '签到管理' }}</h2>
        <p>活动发起人可在这里查看报名与签到状态，也可以生成现场签到二维码给已报名用户扫描。</p>
        <div class="checkin-summary">
          <div><b>{{ registeredCount }}</b><span>报名/签到</span></div>
          <div><b>{{ checkedInCount }}</b><span>已签到</span></div>
          <div><b>{{ waitingCount }}</b><span>候补中</span></div>
        </div>
        <div class="checkin-actions">
          <button class="btn btn-dark" :disabled="checkinLoading" @click="checkinActivity && generateCheckinCode(checkinActivity)">
            <QrCode :size="16" />{{ checkinLoading ? '生成中' : '生成签到二维码' }}
          </button>
          <button class="btn btn-outline" :disabled="registrationLoading" @click="checkinActivity && loadCheckinRows(checkinActivity.id)">刷新名单</button>
        </div>
        <div v-if="checkinLoading" class="qr-loading">正在生成签到二维码...</div>
        <div v-else-if="checkinCode" class="qr-panel">
          <img class="qr-image" :src="checkinQr" alt="签到二维码" />
          <div class="checkin-lines">
            <span><b>签到 code</b>{{ checkinCode.code }}</span>
            <span><b>有效期至</b>{{ formatDateTime(checkinCode.expiresAt) }}</span>
          </div>
          <button class="btn btn-outline" @click="copyCheckinUrl"><Copy :size="16" />复制签到链接</button>
        </div>
        <section class="registration-panel">
          <h3>报名与签到状态</h3>
          <div v-if="registrationLoading" class="qr-loading">正在加载名单...</div>
          <div v-else-if="!checkinRows.length" class="empty-state small">暂无报名用户</div>
          <div v-else class="registration-list">
            <div v-for="row in checkinRows" :key="row.id" class="registration-row">
              <img :src="row.avatar" alt="" />
              <div>
                <b>{{ row.nickname }}</b>
                <small>ID: {{ row.userId }} · {{ formatDateTime(row.checkedInAt || row.createdAt) }}</small>
              </div>
              <i :class="['status-tag', row.status === '已签到' ? 'done' : row.status === '候补中' ? 'waiting' : row.status === '已取消' ? 'cancelled' : '']">{{ row.status }}</i>
            </div>
          </div>
        </section>
        <p v-if="checkinError" class="form-error">{{ checkinError }}</p>
      </div>
    </div>

    <div v-if="cancelOpen" class="account-modal" @click.self="cancelOpen=false">
      <div>
        <button class="modal-close" @click="cancelOpen=false"><X /></button>
        <span class="modal-icon"><ShieldAlert /></span>
        <h2>注销账号</h2>
        <p>此操作会清除登录会话、匿名化个人资料，并释放当前邮箱和昵称。</p>
        <label>当前密码<input v-model="cancelForm.password" class="input" type="password" autocomplete="current-password" /></label>
        <label>确认文案<input v-model.trim="cancelForm.confirmText" class="input" placeholder="请输入：注销账号" /></label>
        <label>注销原因<textarea v-model.trim="cancelForm.reason" class="textarea" placeholder="可选"></textarea></label>
        <p v-if="cancelError" class="form-error">{{ cancelError }}</p>
        <footer>
          <button class="btn btn-outline" :disabled="cancelling" @click="cancelOpen=false">取消</button>
          <button class="btn btn-dark" :disabled="cancelling" @click="cancelAccount">{{ cancelling ? '注销中' : '确认注销' }}</button>
        </footer>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-page{padding:30px 0 70px}
.profile-cover{position:relative;height:220px;overflow:hidden;border-radius:var(--radius-xl);background:linear-gradient(120deg,#ffad87,#ff6b45 40%,#7256c7)}
.profile-pattern{position:absolute;inset:0;background:radial-gradient(circle at 18% 120%,transparent 0 110px,rgba(255,255,255,.1) 111px 150px,transparent 151px),radial-gradient(circle at 85% -20%,transparent 0 100px,rgba(255,255,255,.1) 101px 155px,transparent 156px)}
.profile-cover button{position:absolute;right:18px;top:18px;padding:8px 11px;border:0;border-radius:var(--radius-pill);background:rgba(255,255,255,.85);display:flex;gap:5px;font-size:10px}
.profile-main{position:relative;margin:-55px 26px 0;min-height:150px;padding:22px 25px 22px 155px;border-radius:var(--radius-lg);background:#fff;box-shadow:var(--shadow-card);display:flex;align-items:center;gap:20px}
.profile-main>img{position:absolute;left:25px;top:-35px;width:112px;height:112px;border:5px solid #fff;border-radius:30px;object-fit:cover}
.profile-copy{flex:1;min-width:0}
.profile-copy>div:first-child{display:flex;align-items:center;gap:12px}
.profile-copy h1{display:flex;align-items:center;gap:6px;margin:0;font-size:25px}
.profile-copy h1 svg{color:var(--color-mint)}
.location{display:flex;align-items:center;color:var(--color-ink-soft);font-size:10px}
.profile-copy p{margin:9px 0;color:var(--color-ink-soft);font-size:12px}
.profile-stats{display:flex;gap:24px}
.profile-stats div{display:flex;align-items:center;flex-direction:column}
.profile-stats b{font-size:18px}
.profile-stats span{color:var(--color-ink-soft);font-size:9px}
.profile-main>.btn{font-size:11px}
.profile-grid{display:grid;grid-template-columns:1fr 280px;gap:20px;margin-top:30px}
.profile-tabs{display:flex;gap:22px;margin-bottom:18px;border-bottom:1px solid var(--color-line)}
.profile-tabs button{padding:12px 2px;border:0;border-bottom:2px solid transparent;background:none;color:var(--color-ink-soft);font-weight:700;white-space:nowrap}
.profile-tabs button.active{border-color:var(--color-primary);color:var(--color-ink)}
.profile-grid .activity-grid{grid-template-columns:1fr 1fr}
.side-card{margin-bottom:15px;padding:20px;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-md)}
.side-card h3{font-size:14px}
.side-card>a,.side-action{padding:11px 0;border:0;border-bottom:1px solid var(--color-line);background:none;display:flex;align-items:center;gap:9px;font-size:11px;color:var(--color-ink);text-align:left}
.side-action{width:100%}
.side-card>a svg:first-child,.side-action svg:first-child{width:16px;color:var(--color-primary)}
.side-card>a svg:last-child,.side-action svg:last-child{width:14px;margin-left:auto;color:var(--color-ink-soft)}
.credit{background:var(--color-ink);color:#fff}
.credit>div{display:flex;align-items:center;gap:7px;color:var(--color-sun);font-size:12px}
.credit strong{display:block;margin:15px 0 7px;font-size:35px}
.credit strong small{font-size:12px;color:#8f98a8}
.credit p{margin:0;color:#aab1bf;font-size:9px;line-height:1.7}
.hidden-file{display:none}
.edit-panel{margin:20px 26px 0;padding:22px;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-md)}
.avatar-editor{display:flex;align-items:center;gap:14px;margin-bottom:16px;padding:12px;border:1px solid var(--color-line);border-radius:var(--radius-md);background:var(--color-bg)}
.avatar-editor>img{width:76px;height:76px;border-radius:22px;object-fit:cover;background:#fff}
.avatar-editor>div{min-width:0;flex:1;display:grid;grid-template-columns:auto auto 1fr;align-items:center;gap:10px}
.avatar-editor b{font-size:13px}
.avatar-editor .btn{height:36px;font-size:11px}
.edit-panel .form-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}
.edit-panel label{display:flex;flex-direction:column;gap:6px;font-size:11px;font-weight:800}
.edit-panel>label{margin-top:12px}
.date-picker-wrapper{position:relative}
.date-picker-wrapper .date-native{color:transparent;-webkit-text-fill-color:transparent}
.date-picker-wrapper .date-overlay{position:absolute;inset:0;display:flex;align-items:center;padding:13px 14px;pointer-events:none;overflow:hidden;white-space:nowrap}
.interest-editor{margin-top:12px}
.interest-presets{display:flex;flex-wrap:wrap;gap:8px}
.interest-presets button{padding:7px 10px;border:1px solid var(--color-line);border-radius:var(--radius-pill);background:#fff;color:var(--color-ink-soft);font-size:11px;font-weight:800}
.interest-presets button.active{border-color:var(--color-primary);background:var(--color-primary-soft);color:var(--color-primary)}
.custom-interest-row{display:flex;gap:8px;margin-top:8px}
.custom-interest-row .input{flex:1}
.custom-tags{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}
.custom-tag{display:inline-flex;align-items:center;gap:4px;padding:5px 8px;border:1px solid var(--color-primary);border-radius:var(--radius-pill);background:var(--color-primary-soft);color:var(--color-primary);font-size:11px;font-weight:800}
.custom-tag-remove{padding:0;border:0;background:none;color:var(--color-primary);font-size:14px;line-height:1;cursor:pointer;opacity:.65}
.custom-tag-remove:hover{opacity:1}
.edit-actions{display:flex;justify-content:flex-end;gap:8px;margin-top:14px}
.form-error{padding:9px;border-radius:8px;background:#ffeaed;color:var(--color-danger);font-size:10px}
.empty-state{padding:36px;background:#fff;border:1px dashed var(--color-line);border-radius:var(--radius-md);color:var(--color-ink-soft);text-align:center}
.empty-state.small{padding:20px;font-size:11px}
.managed-activity{display:grid;gap:8px}
.activity-tools{padding:10px;border:1px solid var(--color-line);border-radius:10px;background:#fff;display:flex;align-items:center;justify-content:space-between;gap:8px}
.activity-tools span{padding:5px 7px;border-radius:6px;background:var(--color-mint-soft);color:var(--color-mint);font-size:10px;font-weight:800}
.activity-tools button{border:0;background:none;color:var(--color-primary);display:flex;align-items:center;gap:6px;font-size:11px;font-weight:800}
.merchant-panel{padding:22px;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-md)}
.merchant-head{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:18px}
.merchant-head h2{margin:4px 0 6px}
.merchant-head p{margin:0;color:var(--color-ink-soft);font-size:12px}
.merchant-head i{padding:7px 10px;border-radius:8px;background:var(--color-primary-soft);color:var(--color-primary);font-size:11px;font-style:normal;font-weight:800;white-space:nowrap}
.merchant-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}
.merchant-form-card{padding:16px;border:1px solid var(--color-line);border-radius:12px;background:var(--color-bg)}
.merchant-form-card h3{margin:0 0 14px}
.merchant-form-card label{display:flex;flex-direction:column;gap:6px;margin-top:11px;font-size:11px;font-weight:800}
.merchant-form-card .btn{width:100%;margin-top:14px;justify-content:center}
.merchant-status{min-height:36px;margin:12px 0 0!important;color:var(--color-ink-soft);font-size:11px!important}
.merchant-presets{margin-bottom:2px}
.danger-card{border-color:#ffd2d8;background:#fffafa}
.danger-card>div{display:flex;align-items:center;gap:8px}
.danger-card h3{margin:0}
.danger-card svg{color:var(--color-danger)}
.danger-card p{margin:10px 0 14px;color:var(--color-ink-soft);font-size:10px;line-height:1.7}
.delete-account{width:100%;padding:10px;border:1px solid #ffd2d8;border-radius:8px;background:#fff;color:var(--color-danger);display:flex;align-items:center;justify-content:center;gap:6px;font-size:11px;font-weight:800}
.account-modal{position:fixed;z-index:100;inset:0;padding:18px;background:rgba(13,21,34,.55);display:grid;place-items:center}
.account-modal>div{position:relative;width:min(100%,460px);padding:30px;background:#fff;border-radius:16px;box-shadow:var(--shadow-float)}
.modal-close{position:absolute;right:16px;top:16px;border:0;background:none;color:var(--color-ink-soft)}
.modal-close svg{width:18px}
.modal-icon{width:52px;height:52px;border-radius:50%;background:#ffeaed;color:var(--color-danger);display:grid;place-items:center}
.modal-icon.checkin{background:var(--color-primary-soft);color:var(--color-primary)}
.modal-icon svg{width:24px}
.account-modal h2{margin:14px 0 8px}
.account-modal p{color:var(--color-ink-soft);font-size:12px;line-height:1.7}
.account-modal label{display:flex;flex-direction:column;gap:6px;margin-top:12px;font-size:11px;font-weight:800}
.account-modal .textarea{min-height:76px}
.account-modal footer{display:flex;justify-content:flex-end;gap:8px;margin-top:18px}
.account-modal .btn{padding:10px 14px;font-size:11px}
.account-modal .btn:disabled{opacity:.55;cursor:not-allowed}
.checkin-modal{width:min(100%,720px)!important;max-height:90vh;overflow:auto;text-align:left}
.checkin-summary{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:16px 0}
.checkin-summary div{padding:12px;border:1px solid var(--color-line);border-radius:10px;background:var(--color-bg)}
.checkin-summary b{display:block;font-size:22px}
.checkin-summary span{font-size:10px;color:var(--color-ink-soft);font-weight:800}
.checkin-actions{display:flex;gap:8px;margin-bottom:12px}
.checkin-actions .btn{justify-content:center}
.qr-loading{padding:32px;border:1px dashed var(--color-line);border-radius:12px;color:var(--color-ink-soft);text-align:center}
.qr-panel{padding:14px;border:1px solid var(--color-line);border-radius:12px;background:#fff}
.qr-image{display:block;width:220px;height:220px;margin:4px auto 16px;border:1px solid var(--color-line);border-radius:12px}
.checkin-lines{display:grid;gap:8px;margin:12px 0}
.checkin-lines span{min-width:0;padding:9px;border-radius:8px;background:var(--color-bg);display:flex;align-items:center;justify-content:space-between;gap:12px;font-size:10px;word-break:break-all}
.checkin-lines b{color:var(--color-ink-soft);white-space:nowrap}
.registration-panel{margin-top:16px}
.registration-panel h3{margin:0 0 10px;font-size:14px}
.registration-list{display:grid;gap:8px}
.registration-row{display:grid;grid-template-columns:40px 1fr auto;align-items:center;gap:10px;padding:10px;border:1px solid var(--color-line);border-radius:10px;background:#fff}
.registration-row img{width:40px;height:40px;border-radius:50%;object-fit:cover;background:var(--color-bg)}
.registration-row div{min-width:0}
.registration-row b{display:block;font-size:12px}
.registration-row small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--color-ink-soft);font-size:10px}
.status-tag{padding:6px 8px;border-radius:8px;background:var(--color-primary-soft);color:var(--color-primary);font-size:10px;font-style:normal;font-weight:800;white-space:nowrap}
.status-tag.done{background:var(--color-mint-soft);color:var(--color-mint)}
.status-tag.waiting{background:#fff5d8;color:#9b6b00}
.status-tag.cancelled{background:#f1f2f5;color:var(--color-ink-soft)}
@media(max-width:900px){
  .profile-main{padding-top:85px;padding-left:25px;align-items:flex-start;flex-wrap:wrap}
  .profile-main>img{left:25px}
  .profile-stats{margin-left:auto}
  .profile-grid{grid-template-columns:1fr}
  .profile-grid aside{display:grid;grid-template-columns:1fr 1fr;gap:14px}
  .merchant-grid{grid-template-columns:1fr}
}
@media(max-width:600px){
  .profile-cover{height:160px}
  .profile-main{margin:-35px 10px 0}
  .profile-main>img{width:90px;height:90px}
  .profile-copy{min-width:100%}
  .profile-copy>div:first-child{align-items:flex-start;flex-direction:column}
  .profile-stats{margin-left:0}
  .profile-main>.btn{margin-left:auto}
  .avatar-editor{align-items:flex-start}
  .avatar-editor>div{grid-template-columns:1fr}
  .edit-panel .form-grid,.profile-grid .activity-grid{grid-template-columns:1fr}
  .profile-grid aside{grid-template-columns:1fr}
  .merchant-head{flex-direction:column}
  .profile-tabs{gap:12px;overflow:auto}
  .activity-tools{align-items:flex-start;flex-direction:column}
  .activity-tools button{width:100%;justify-content:center;padding:8px;border:1px solid var(--color-line);border-radius:8px;background:#fff}
  .checkin-summary{grid-template-columns:1fr}
  .registration-row{grid-template-columns:40px 1fr}
  .registration-row .status-tag{grid-column:2}
}
</style>
