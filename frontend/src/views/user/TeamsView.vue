<script setup lang="ts">
import { Camera, Crown, Search, Settings, Trophy, UserPlus, Users } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiGet, apiPost } from '@/lib/api'
import { useAppStore } from '@/stores/app'
import type { Team } from '@/types'

const app = useAppStore()
const router = useRouter()
const active = ref('推荐小队')
const query = ref('')
const teams = ref<Team[]>([])
const showCreate = ref(false)
const saving = ref(false)
const error = ref('')
const coverInput = ref<HTMLInputElement | null>(null)
const createForm = reactive({
  name: '',
  description: '',
  tags: '',
  capacity: 80,
  joinMode: '公开加入' as Team['joinMode'],
  cover: '',
})
const interestPresets = ['徒步', '骑行', '桌游', '摄影', '城市探索', '运动健身', '学习交流', '公益活动', '露营', '音乐', '读书', '咖啡']
const joined = computed(() => app.joinedTeamIds)
const filteredTeams = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  let rows = teams.value
  if (active.value === '我的小队') rows = rows.filter(team => joined.value.includes(team.id))
  if (active.value === '附近活跃') rows = [...rows].sort((a, b) => b.activeNow - a.activeNow)
  if (!keyword) return rows
  return rows.filter(team => `${team.name}${team.description}${team.tags.join('')}`.toLowerCase().includes(keyword))
})
const fallbackTeam: Team = { id: '', name: '暂无小队', description: '', cover: 'https://images.unsplash.com/photo-1518005020951-eccb494ad742?auto=format&fit=crop&w=900&q=80', tags: [], members: 0, capacity: 1, joinMode: '公开加入', activeNow: 0 }
const myTeam = computed(() => teams.value.find(team => joined.value.includes(team.id)) ?? teams.value[0] ?? fallbackTeam)
async function loadTeams(){ teams.value = await apiGet<Team[]>('/teams'); await app.refreshUserState() }
async function joinTeam(id: string){
  const before = teams.value.find(team => team.id === id)
  const updated = await apiPost<Team>(`/teams/${id}/members`, {})
  teams.value = teams.value.map(team => team.id === id ? updated : team)
  await app.refreshUserState()
  if (before?.joinMode === '审核加入' && !app.joinedTeamIds.includes(id)) app.showToast('已提交加入申请，等待队长或管理员审核')
  else app.joinTeam(id)
}
function parseCreateTags() {
  return createForm.tags.split(/[、,，]/).map(item => item.trim()).filter(Boolean)
}
function hasTag(tag: string) {
  return parseCreateTags().includes(tag)
}
function toggleTag(tag: string) {
  const tags = parseCreateTags()
  const next = tags.includes(tag) ? tags.filter(item => item !== tag) : [...tags, tag]
  createForm.tags = next.join('、')
}
function selectCoverFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    app.showToast('请选择图片文件作为封面')
    input.value = ''
    return
  }
  createForm.cover = URL.createObjectURL(file)
}
function resetCreateForm() {
  showCreate.value = false
  error.value = ''
  Object.assign(createForm, { name: '', description: '', tags: '', capacity: 80, joinMode: '公开加入', cover: '' })
}
async function createTeam() {
  error.value = ''
  if (!createForm.name.trim() || !createForm.description.trim()) {
    error.value = '请填写小队名称和简介'
    return
  }
  saving.value = true
  try {
    const created = await apiPost<Team>('/teams', {
      name: createForm.name.trim(),
      description: createForm.description.trim(),
      tags: parseCreateTags(),
      capacity: createForm.capacity,
      joinMode: createForm.joinMode,
      cover: createForm.cover.trim(),
    })
    teams.value = [created, ...teams.value]
    await app.refreshUserState()
    resetCreateForm()
    router.push(`/teams/${created.id}/manage`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '创建小队失败'
  } finally {
    saving.value = false
  }
}
async function goTeamChat(team?: Team) {
  if (!team?.id) {
    app.showToast('请先加入或创建一个小队')
    return
  }
  await router.push('/messages')
  app.showToast('已打开消息页，可在小队会话中交流')
}
function manageTeam(team: Team) {
  router.push(`/teams/${team.id}/manage`)
}
function findActivities(team?: Team) {
  router.push({ path: '/discover', query: { q: team?.tags?.[0] || team?.name || '' } })
}
onMounted(loadTeams)
</script>
<template><div class="container teams-page"><div class="teams-hero"><div><span class="eyebrow">INTEREST CREWS</span><h1>一次活动之后，<br />继续和同频的人同行</h1><p>加入兴趣小队，共享活动、相册、文件和每周新计划。</p><div class="team-search"><Search :size="18" /><input v-model="query" placeholder="搜索小队名称或兴趣标签" /></div></div><div class="my-team-card"><div class="my-top"><img :src="myTeam.cover" /><div><span>我加入的小队</span><h3>{{ myTeam.name }}</h3></div><span class="online">{{ myTeam.activeNow }} 人在线</span></div><p>{{ myTeam.id ? '小队群聊、公告和活动会在消息页同步。' : '加入或创建小队后，这里会展示你的常用小队。' }}</p><div class="my-actions"><button @click="goTeamChat(myTeam)">进入群聊</button><button @click="findActivities(myTeam)">查看本周活动</button><button v-if="myTeam.myRole" class="btn-manage" @click="manageTeam(myTeam)"><Settings :size="14" />管理</button></div></div></div><div class="tabs"><button v-for="tab in ['推荐小队','我的小队','附近活跃']" :key="tab" :class="{active:active===tab}" @click="active=tab">{{ tab }}</button><button class="create-team" @click="showCreate=!showCreate"><UserPlus :size="16" />{{ showCreate ? '收起创建' : '创建小队' }}</button></div><input ref="coverInput" class="hidden-file" type="file" accept="image/*" @change="selectCoverFile" /><section v-if="showCreate" class="create-panel"><div class="create-title"><span class="eyebrow">NEW CREW</span><h2>创建你的兴趣小队</h2><p>填写小队信息，创建后自动成为队长，群聊将同步开放。</p></div><div class="cover-editor"><img :src="createForm.cover || 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2276%22 height=%2276%22%3E%3Crect fill=%22%23f7f5f1%22 width=%2276%22 height=%2276%22/%3E%3Ctext x=%2238%22 y=%2242%22 text-anchor=%22middle%22 fill=%22%23bbb%22 font-size=%2224%22%3E🖼%3C/text%3E%3C/svg%3E'" /><div><b>封面</b><button class="btn btn-outline btn-xs" @click="coverInput?.click()"><Camera :size="16" />上传图片</button><input v-model.trim="createForm.cover" class="input" placeholder="或粘贴封面图片链接" /></div></div><div class="form-grid"><div class="input-group"><label>小队名称 *</label><input v-model.trim="createForm.name" class="input" maxlength="20" placeholder="给你的小队起个名字" /><small>{{ createForm.name.length }} / 20</small></div><div class="input-group"><label>加入方式</label><select v-model="createForm.joinMode" class="select"><option>公开加入</option><option>审核加入</option></select></div></div><div class="form-grid"><div class="input-group"><label>人数上限</label><input v-model.number="createForm.capacity" class="input" type="number" min="2" max="500" /></div></div><div class="input-group"><label>小队简介 *</label><textarea v-model.trim="createForm.description" class="textarea" maxlength="200" placeholder="描述一下这个小队的兴趣主题和适合的人群"></textarea><small>{{ createForm.description.length }} / 200</small></div><div class="input-group"><label>兴趣标签</label><div class="interest-presets"><button v-for="tag in interestPresets" :key="tag" type="button" :class="{active:hasTag(tag)}" @click="toggleTag(tag)"># {{ tag }}</button></div><input v-model.trim="createForm.tags" class="input" placeholder="可选择上方标签，也可用顿号手动输入" /></div><p v-if="error" class="form-error">{{ error }}</p><div class="create-actions"><button class="btn btn-outline" @click="resetCreateForm">取消</button><button class="btn btn-primary" :disabled="saving" @click="createTeam">{{ saving ? '创建中...' : '创建小队' }}</button></div></section><div class="teams-grid"><article v-for="team in filteredTeams" :key="team.id" class="team-card"><div class="team-cover"><img :src="team.cover" /><span>{{ team.joinMode }}</span></div><div class="team-body"><div class="team-name"><h2>{{ team.name }}</h2><span v-if="joined.includes(team.id)"><Crown :size="13" />我的小队</span></div><p>{{ team.description }}</p><div class="tag-row"><span v-for="tag in team.tags" :key="tag"># {{ tag }}</span></div><div class="team-data"><span><Users :size="15" />{{ team.members }} / {{ team.capacity }} 人</span><span><i></i>{{ team.activeNow }} 人在线</span></div><div class="team-card-actions"><button v-if="!joined.includes(team.id)" class="btn btn-primary" @click="joinTeam(team.id)">加入小队</button><template v-else><button class="btn joined" @click="goTeamChat(team)">已加入 · 进入看看</button><button v-if="team.myRole" class="btn btn-outline btn-sm" @click="manageTeam(team)"><Settings :size="14" /></button></template></div></div></article></div><div v-if="!filteredTeams.length" class="empty-state">没有符合条件的小队。</div><section class="leaderboard"><div><span class="eyebrow">WEEKLY RANKING</span><h2>本周小队活跃榜</h2><p>参与活动、分享动态，让热爱被更多人看见。</p></div><ol><li v-for="(team,index) in teams.slice(0,3)" :key="team.id"><b>{{ index + 1 }}</b><img :src="team.cover" /><span>{{ team.name }}</span><small>{{ team.activeNow }} 活跃</small></li></ol></section></div></template>
<style scoped>
.teams-page{padding:48px 0 80px}.teams-hero{display:grid;grid-template-columns:1fr 1fr;gap:50px;align-items:center;padding:42px;background:linear-gradient(135deg,#fff3ed,#eaf8f5);border-radius:var(--radius-xl)}.teams-hero h1{margin:5px 0 14px;font-size:40px;line-height:1.25;letter-spacing:-.05em}.teams-hero p{color:var(--color-ink-soft)}.team-search{max-width:430px;height:48px;padding:0 14px;background:#fff;border-radius:var(--radius-pill);display:flex;align-items:center;gap:8px;box-shadow:var(--shadow-soft)}.team-search input{flex:1;border:0;outline:0}.my-team-card{padding:24px;background:rgba(255,255,255,.85);border:1px solid #fff;border-radius:20px;box-shadow:var(--shadow-card)}.my-top{display:flex;align-items:center;gap:12px}.my-top img{width:55px;height:55px;border-radius:14px;object-fit:cover}.my-top div{display:flex;flex-direction:column}.my-top div>span{color:var(--color-ink-soft);font-size:10px}.my-top h3{margin:4px 0}.online{margin-left:auto;padding:5px 8px;border-radius:var(--radius-pill);background:var(--color-mint-soft);color:var(--color-mint);font-size:9px;font-weight:800}.my-team-card>p{margin:17px 0;padding:12px;background:var(--color-bg);border-radius:9px;font-size:11px}.my-actions{display:flex;gap:8px}.my-actions button{flex:1;padding:10px;border:1px solid var(--color-line);border-radius:9px;background:#fff;font-size:11px;font-weight:700}.my-actions .btn-manage{flex:0 0 auto;padding:10px 14px;background:var(--color-primary-soft);color:var(--color-primary);border-color:transparent;display:flex;align-items:center;gap:5px}.tabs{display:flex;gap:4px;margin:32px 0 20px;border-bottom:1px solid var(--color-line)}.tabs button{padding:13px 17px;border:0;border-bottom:2px solid transparent;background:none;color:var(--color-ink-soft);font-weight:700}.tabs button.active{border-color:var(--color-primary);color:var(--color-ink)}.tabs .create-team{margin-left:auto;color:var(--color-primary);display:flex;align-items:center;gap:6px}.teams-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:18px}.team-card{overflow:hidden;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-lg)}.team-cover{position:relative;height:170px;overflow:hidden}.team-cover img{width:100%;height:100%;object-fit:cover}.team-cover span{position:absolute;right:12px;top:12px;padding:5px 10px;background:rgba(255,255,255,.9);border-radius:var(--radius-pill);font-size:10px;font-weight:800}.team-body{padding:18px}.team-name{display:flex;align-items:center;gap:10px;margin-bottom:8px}.team-name h2{margin:0;font-size:20px;letter-spacing:-.02em}.team-name span{display:flex;align-items:center;gap:4px;padding:3px 8px;background:var(--color-primary-soft);color:var(--color-primary);border-radius:var(--radius-pill);font-size:10px;font-weight:800}.team-body>p{height:40px;color:var(--color-ink-soft);font-size:13px;line-height:1.5;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.team-data{display:flex;justify-content:space-between;margin-bottom:14px;color:var(--color-ink-soft);font-size:12px}.team-data span{display:flex;align-items:center;gap:4px}.team-data i{width:7px;height:7px;border-radius:50%;background:var(--color-mint)}.team-card-actions{display:flex;gap:8px}.team-card-actions .btn{flex:1}.team-card-actions .btn-outline{flex:0}.form-error{color:var(--color-danger);font-size:12px;margin:8px 0 0}.leaderboard{display:grid;grid-template-columns:1fr 320px;align-items:center;gap:40px;margin-top:60px;padding:36px;background:#fff;border-radius:var(--radius-xl);border:1px solid var(--color-line)}.leaderboard ol{list-style:none;padding:0;margin:0;display:flex;flex-direction:column;gap:14px}.leaderboard ol li{display:flex;align-items:center;gap:12px;padding:12px;background:var(--color-bg);border-radius:12px}.leaderboard ol b{width:28px;height:28px;display:grid;place-items:center;border-radius:50%;background:var(--color-primary);color:#fff;font-size:13px}.leaderboard ol img{width:36px;height:36px;border-radius:10px;object-fit:cover}.leaderboard ol small{margin-left:auto;color:var(--color-ink-soft);font-size:11px}.create-panel{margin-bottom:20px;padding:28px 32px;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-lg)}.create-title{margin-bottom:22px}.create-title h2{margin:3px 0 8px;font-size:24px;letter-spacing:-.03em}.create-title p{color:var(--color-ink-soft);font-size:14px}.cover-editor{display:flex;align-items:center;gap:14px;margin-bottom:18px;padding:12px;border:1px solid var(--color-line);border-radius:var(--radius-md);background:var(--color-bg)}.cover-editor>img{width:76px;height:76px;border-radius:18px;object-fit:cover;background:#fff;flex-shrink:0}.cover-editor>div{min-width:0;flex:1;display:grid;grid-template-columns:auto auto 1fr;align-items:center;gap:10px}.cover-editor b{font-size:13px}.cover-editor .btn{height:36px;font-size:11px}.hidden-file{display:none}.input-group{display:flex;flex-direction:column;gap:6px}.input-group label{font-size:11px;font-weight:800}.input-group small{margin-top:2px;text-align:right;color:var(--color-ink-soft);font-size:10px}.interest-presets{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:10px}.interest-presets button{padding:7px 10px;border:1px solid var(--color-line);border-radius:var(--radius-pill);background:#fff;color:var(--color-ink-soft);font-size:11px;font-weight:800;cursor:pointer}.interest-presets button.active{border-color:var(--color-primary);background:var(--color-primary-soft);color:var(--color-primary)}.create-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:18px}.btn-xs{padding:6px 12px;font-size:11px}
@media(max-width:900px){.teams-hero{grid-template-columns:1fr}.teams-grid{grid-template-columns:1fr 1fr}.leaderboard{grid-template-columns:1fr}}@media(max-width:600px){.teams-hero{padding:28px}.teams-hero h1{font-size:31px}.teams-grid{grid-template-columns:1fr}.tabs button{padding:11px 8px;font-size:11px}.tabs .create-team{font-size:0}.leaderboard{padding:27px 20px}.create-panel{padding:20px}.cover-editor>div{grid-template-columns:1fr}.create-title h2{font-size:20px}}
</style>