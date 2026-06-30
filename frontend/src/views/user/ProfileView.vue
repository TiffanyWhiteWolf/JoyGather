<script setup lang="ts">
import { CalendarDays, Camera, CheckCircle2, ChevronRight, Heart, MapPin, QrCode, Settings, Star, Users } from 'lucide-vue-next'
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import ActivityCard from '@/components/activity/ActivityCard.vue'
import { apiGet, apiPut, apiUpload } from '@/lib/api'
import type { Activity, User } from '@/types'

interface FileResponse {
  id: string
  url: string
  originalName: string
  contentType: string
  size: number
  provider: string
}

const router = useRouter()
const currentUser = ref<User | null>(null)
const activities = ref<Activity[]>([])
const editing = ref(false)
const activeTab = ref<'我的活动' | '动态' | '收藏' | '活动总结'>('我的活动')
const saving = ref(false)
const uploadingAvatar = ref(false)
const error = ref('')
const avatarInput = ref<HTMLInputElement | null>(null)
const suggestedInterests = ['徒步', '骑行', '桌游', '摄影', '城市探索', '运动健身', '学习交流', '公益活动', '露营', '音乐', '读书', '咖啡']
const profile = reactive({ nickname: '', avatar: '', gender: '', birthday: '', city: '杭州', bio: '', interests: '' })
onMounted(async () => {
  try {
    const [user, rows] = await Promise.all([apiGet<User>('/auth/me'), apiGet<Activity[]>('/activities')])
    currentUser.value = user
    Object.assign(profile, { nickname: user.nickname, avatar: user.avatar, gender: user.gender || '', birthday: user.birthday || '', city: user.city, bio: user.bio, interests: user.interests.join('、') })
    activities.value = rows.filter(item => item.organizer.id === user.id).slice(0, 4)
  } catch {
    router.push('/auth')
  }
})
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
</script>
<template><div v-if="currentUser" class="container profile-page"><input ref="avatarInput" class="hidden-file" type="file" accept="image/*" @change="uploadAvatar" /><section class="profile-cover"><div class="profile-pattern"></div><button @click="changeCover"><Camera :size="16" />更换封面</button></section><section class="profile-main"><img :src="currentUser.avatar" /><div class="profile-copy"><div><h1>{{ currentUser.nickname }} <CheckCircle2 v-if="currentUser.verified" :size="18" /></h1><span class="location"><MapPin :size="14" />{{ currentUser.city }}</span></div><p>{{ currentUser.bio || '还没有填写个性签名' }}</p><div class="tag-row"><span v-for="tag in currentUser.interests" :key="tag"># {{ tag }}</span></div></div><div class="profile-stats"><div><b>{{ currentUser.following }}</b><span>关注</span></div><div><b>{{ currentUser.followers }}</b><span>粉丝</span></div><div><b>{{ currentUser.credit }}</b><span>信用分</span></div></div><button class="btn btn-outline" @click="editing=!editing"><Settings :size="17" />编辑资料</button></section><section v-if="editing" class="edit-panel"><div class="avatar-editor"><img :src="profile.avatar || currentUser.avatar" /><div><b>头像</b><button class="btn btn-outline" :disabled="uploadingAvatar" @click="avatarInput?.click()"><Camera :size="16" />{{ uploadingAvatar ? '上传中' : '上传图片' }}</button><input v-model.trim="profile.avatar" class="input" placeholder="或粘贴图片 URL" /></div></div><div class="form-grid"><label>昵称<input v-model.trim="profile.nickname" class="input" /></label><label>性别<input v-model.trim="profile.gender" class="input" /></label><label>生日<input v-model="profile.birthday" type="date" class="input" /></label><label>城市<input v-model.trim="profile.city" class="input" /></label></div><label class="interest-editor">兴趣标签<div class="interest-presets"><button v-for="tag in suggestedInterests" :key="tag" type="button" :class="{active:hasInterest(tag)}" @click="toggleInterest(tag)"># {{ tag }}</button></div><input v-model.trim="profile.interests" class="input" placeholder="可选择上方标签，也可用顿号手动输入" /></label><label>个性签名<textarea v-model.trim="profile.bio" class="textarea"></textarea></label><p v-if="error" class="form-error">{{ error }}</p><div><button class="btn btn-outline" @click="editing=false">取消</button><button class="btn btn-primary" :disabled="saving" @click="saveProfile">{{ saving ? '保存中' : '保存资料' }}</button></div></section><div class="profile-grid"><main><div class="profile-tabs"><button v-for="tab in ['我的活动','动态','收藏','活动总结'] as const" :key="tab" :class="{active:activeTab===tab}" @click="activeTab=tab">{{ tab }}</button></div><template v-if="activeTab==='我的活动'"><div v-if="activities.length" class="activity-grid"><ActivityCard v-for="activity in activities" :key="activity.id" :activity="activity" /></div><div v-else class="empty-state">你还没有发布活动。</div></template><div v-else class="empty-state">{{ activeTab }}功能已接入个人页入口，当前账号暂无数据。</div></main><aside><div class="side-card"><h3>我的快捷入口</h3><RouterLink to="/check-in"><QrCode />扫码签到<ChevronRight /></RouterLink><RouterLink to="/teams"><Users />我的小队<ChevronRight /></RouterLink><RouterLink to="/create"><CalendarDays />活动管理<ChevronRight /></RouterLink><RouterLink to="/discover"><Heart />我的收藏<ChevronRight /></RouterLink></div><div class="side-card credit"><div><Star :size="20" /><b>信用表现优秀</b></div><strong>{{ currentUser.credit }}<small>/100</small></strong><p>按时参与、友善交流，让每次相遇都更安心。</p></div></aside></div></div></template>
<style scoped>
.profile-page{padding:30px 0 70px}.profile-cover{position:relative;height:220px;overflow:hidden;border-radius:var(--radius-xl);background:linear-gradient(120deg,#ffad87,#ff6b45 40%,#7256c7)}.profile-pattern{position:absolute;inset:0;background:radial-gradient(circle at 18% 120%,transparent 0 110px,rgba(255,255,255,.1) 111px 150px,transparent 151px),radial-gradient(circle at 85% -20%,transparent 0 100px,rgba(255,255,255,.1) 101px 155px,transparent 156px)}.profile-cover button{position:absolute;right:18px;top:18px;padding:8px 11px;border:0;border-radius:var(--radius-pill);background:rgba(255,255,255,.85);display:flex;gap:5px;font-size:10px}.profile-main{position:relative;margin:-55px 26px 0;min-height:150px;padding:22px 25px 22px 155px;border-radius:var(--radius-lg);background:#fff;box-shadow:var(--shadow-card);display:flex;align-items:center;gap:20px}.profile-main>img{position:absolute;left:25px;top:-35px;width:112px;height:112px;border:5px solid #fff;border-radius:30px;object-fit:cover}.profile-copy{flex:1}.profile-copy>div:first-child{display:flex;align-items:center;gap:12px}.profile-copy h1{display:flex;align-items:center;gap:6px;margin:0;font-size:25px}.profile-copy h1 svg{color:var(--mint)}.location{display:flex;align-items:center;color:var(--color-ink-soft);font-size:10px}.profile-copy p{margin:9px 0;color:var(--color-ink-soft);font-size:12px}.profile-stats{display:flex;gap:24px}.profile-stats div{display:flex;align-items:center;flex-direction:column}.profile-stats b{font-size:18px}.profile-stats span{color:var(--color-ink-soft);font-size:9px}.profile-main>.btn{font-size:11px}.profile-grid{display:grid;grid-template-columns:1fr 280px;gap:20px;margin-top:30px}.profile-tabs{display:flex;gap:22px;margin-bottom:18px;border-bottom:1px solid var(--color-line)}.profile-tabs button{padding:12px 2px;border:0;border-bottom:2px solid transparent;background:none;color:var(--color-ink-soft);font-weight:700}.profile-tabs button.active{border-color:var(--color-primary);color:var(--color-ink)}.profile-grid .activity-grid{grid-template-columns:1fr 1fr}.side-card{margin-bottom:15px;padding:20px;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-md)}.side-card h3{font-size:14px}.side-card>a{padding:11px 0;border-bottom:1px solid var(--color-line);display:flex;align-items:center;gap:9px;font-size:11px}.side-card>a svg:first-child{width:16px;color:var(--color-primary)}.side-card>a svg:last-child{width:14px;margin-left:auto;color:var(--color-ink-soft)}.credit{background:var(--color-ink);color:#fff}.credit>div{display:flex;align-items:center;gap:7px;color:var(--color-sun);font-size:12px}.credit strong{display:block;margin:15px 0 7px;font-size:35px}.credit strong small{font-size:12px;color:#8f98a8}.credit p{margin:0;color:#aab1bf;font-size:9px;line-height:1.7}
.hidden-file{display:none}.edit-panel{margin:20px 26px 0;padding:22px;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-md)}.avatar-editor{display:flex;align-items:center;gap:14px;margin-bottom:16px;padding:12px;border:1px solid var(--color-line);border-radius:var(--radius-md);background:var(--color-bg)}.avatar-editor>img{width:76px;height:76px;border-radius:22px;object-fit:cover;background:#fff}.avatar-editor>div{min-width:0;flex:1;display:grid;grid-template-columns:auto auto 1fr;align-items:center;gap:10px}.avatar-editor b{font-size:13px}.avatar-editor .btn{height:36px;font-size:11px}.edit-panel .form-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}.edit-panel label{display:flex;flex-direction:column;gap:6px;font-size:11px;font-weight:800}.edit-panel>label{margin-top:12px}.interest-editor{margin-top:12px}.interest-presets{display:flex;flex-wrap:wrap;gap:8px}.interest-presets button{padding:7px 10px;border:1px solid var(--color-line);border-radius:var(--radius-pill);background:#fff;color:var(--color-ink-soft);font-size:11px;font-weight:800}.interest-presets button.active{border-color:var(--color-primary);background:var(--color-primary-soft);color:var(--color-primary)}.edit-panel>div:last-child{display:flex;justify-content:flex-end;gap:8px;margin-top:14px}.form-error{padding:9px;border-radius:8px;background:#ffeaed;color:var(--color-danger);font-size:10px}.empty-state{padding:36px;background:#fff;border:1px dashed var(--color-line);border-radius:var(--radius-md);color:var(--color-ink-soft);text-align:center}
@media(max-width:900px){.profile-main{padding-top:85px;padding-left:25px;align-items:flex-start;flex-wrap:wrap}.profile-main>img{left:25px}.profile-stats{margin-left:auto}.profile-grid{grid-template-columns:1fr}.profile-grid aside{display:grid;grid-template-columns:1fr 1fr;gap:14px}}@media(max-width:600px){.profile-cover{height:160px}.profile-main{margin:-35px 10px 0}.profile-main>img{width:90px;height:90px}.profile-copy{min-width:100%}.profile-copy>div:first-child{align-items:flex-start;flex-direction:column}.profile-stats{margin-left:0}.profile-main>.btn{margin-left:auto}.avatar-editor{align-items:flex-start}.avatar-editor>div{grid-template-columns:1fr}.profile-grid .activity-grid{grid-template-columns:1fr}.profile-grid aside{grid-template-columns:1fr}}
</style>
