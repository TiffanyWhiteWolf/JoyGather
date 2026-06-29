<script setup lang="ts">
import { Crown, Search, Trophy, UserPlus, Users } from 'lucide-vue-next'
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
const createForm = reactive({
  name: '',
  description: '',
  tags: '',
  capacity: 80,
  joinMode: '公开加入' as Team['joinMode'],
  cover: '',
})
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
      tags: createForm.tags.split(/[、,，]/).map(item => item.trim()).filter(Boolean),
      capacity: createForm.capacity,
      joinMode: createForm.joinMode,
      cover: createForm.cover.trim(),
    })
    teams.value = [created, ...teams.value]
    await app.refreshUserState()
    showCreate.value = false
    Object.assign(createForm, { name: '', description: '', tags: '', capacity: 80, joinMode: '公开加入', cover: '' })
    app.showToast('小队已创建，群聊已同步开放')
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
function findActivities(team?: Team) {
  router.push({ path: '/discover', query: { q: team?.tags?.[0] || team?.name || '' } })
}
onMounted(loadTeams)
</script>
<template><div class="container teams-page"><div class="teams-hero"><div><span class="eyebrow">INTEREST CREWS</span><h1>一次活动之后，<br />继续和同频的人同行</h1><p>加入兴趣小队，共享活动、相册、文件和每周新计划。</p><div class="team-search"><Search :size="18" /><input v-model="query" placeholder="搜索小队名称或兴趣标签" /></div></div><div class="my-team-card"><div class="my-top"><img :src="myTeam.cover" /><div><span>我加入的小队</span><h3>{{ myTeam.name }}</h3></div><span class="online">{{ myTeam.activeNow }} 人在线</span></div><p>{{ myTeam.id ? '小队群聊、公告和活动会在消息页同步。' : '加入或创建小队后，这里会展示你的常用小队。' }}</p><div class="my-actions"><button @click="goTeamChat(myTeam)">进入群聊</button><button @click="findActivities(myTeam)">查看本周活动</button></div></div></div><div class="tabs"><button v-for="tab in ['推荐小队','我的小队','附近活跃']" :key="tab" :class="{active:active===tab}" @click="active=tab">{{ tab }}</button><button class="create-team" @click="showCreate=true"><UserPlus :size="16" />创建小队</button></div><div class="teams-grid"><article v-for="team in filteredTeams" :key="team.id" class="team-card"><div class="team-cover"><img :src="team.cover" /><span>{{ team.joinMode }}</span></div><div class="team-body"><div class="team-name"><h2>{{ team.name }}</h2><span v-if="joined.includes(team.id)"><Crown :size="13" />我的小队</span></div><p>{{ team.description }}</p><div class="tag-row"><span v-for="tag in team.tags" :key="tag"># {{ tag }}</span></div><div class="team-data"><span><Users :size="15" />{{ team.members }} / {{ team.capacity }} 人</span><span><i></i>{{ team.activeNow }} 人在线</span></div><button v-if="!joined.includes(team.id)" class="btn btn-primary" @click="joinTeam(team.id)">加入小队</button><button v-else class="btn joined" @click="goTeamChat(team)">已加入 · 进入看看</button></div></article></div><div v-if="!filteredTeams.length" class="empty-state">没有符合条件的小队。</div><section class="leaderboard"><div><span class="eyebrow">WEEKLY RANKING</span><h2>本周小队活跃榜</h2><p>参与活动、分享动态，让热爱被更多人看见。</p></div><ol><li v-for="(team,index) in teams.slice(0,3)" :key="team.id"><b>{{ index + 1 }}</b><img :src="team.cover" /><span><strong>{{ team.name }}</strong><small>本周 {{ 3 - index }} 场活动 · {{ 86 - index * 15 }} 条动态</small></span><em><Trophy v-if="index===0" :size="15" />{{ 2840 - index * 720 }}</em></li></ol></section><div v-if="showCreate" class="team-modal" @click.self="showCreate=false"><div><h2>创建小队</h2><label>小队名称 *<input v-model.trim="createForm.name" class="input" /></label><label>简介 *<textarea v-model.trim="createForm.description" class="textarea"></textarea></label><label>兴趣标签<input v-model.trim="createForm.tags" class="input" placeholder="用顿号分隔" /></label><div class="form-grid"><label>人数上限<input v-model.number="createForm.capacity" class="input" type="number" min="2" /></label><label>加入方式<select v-model="createForm.joinMode" class="select"><option>公开加入</option><option>审核加入</option></select></label></div><label>封面 URL<input v-model.trim="createForm.cover" class="input" placeholder="可选" /></label><p v-if="error" class="form-error">{{ error }}</p><footer><button class="btn btn-outline" @click="showCreate=false">取消</button><button class="btn btn-primary" :disabled="saving" @click="createTeam">{{ saving ? '创建中' : '创建小队' }}</button></footer></div></div></div></template>
<style scoped>
.teams-page{padding:48px 0 80px}.teams-hero{display:grid;grid-template-columns:1fr 1fr;gap:50px;align-items:center;padding:42px;background:linear-gradient(135deg,#fff3ed,#eaf8f5);border-radius:var(--radius-xl)}.teams-hero h1{margin:5px 0 14px;font-size:40px;line-height:1.25;letter-spacing:-.05em}.teams-hero p{color:var(--color-ink-soft)}.team-search{max-width:430px;height:48px;padding:0 14px;background:#fff;border-radius:var(--radius-pill);display:flex;align-items:center;gap:8px;box-shadow:var(--shadow-soft)}.team-search input{flex:1;border:0;outline:0}.my-team-card{padding:24px;background:rgba(255,255,255,.85);border:1px solid #fff;border-radius:20px;box-shadow:var(--shadow-card)}.my-top{display:flex;align-items:center;gap:12px}.my-top img{width:55px;height:55px;border-radius:14px;object-fit:cover}.my-top div{display:flex;flex-direction:column}.my-top div>span{color:var(--color-ink-soft);font-size:10px}.my-top h3{margin:4px 0}.online{margin-left:auto;padding:5px 8px;border-radius:var(--radius-pill);background:var(--color-mint-soft);color:var(--color-mint);font-size:9px;font-weight:800}.my-team-card>p{margin:17px 0;padding:12px;background:var(--color-bg);border-radius:9px;font-size:11px}.my-actions{display:flex;gap:8px}.my-actions button{flex:1;padding:10px;border:1px solid var(--color-line);border-radius:9px;background:#fff;font-size:11px;font-weight:700}.tabs{display:flex;gap:4px;margin:32px 0 20px;border-bottom:1px solid var(--color-line)}.tabs button{padding:13px 17px;border:0;border-bottom:2px solid transparent;background:none;color:var(--color-ink-soft);font-weight:700}.tabs button.active{border-color:var(--color-primary);color:var(--color-ink)}.tabs .create-team{margin-left:auto;color:var(--color-primary);display:flex;align-items:center;gap:6px}.teams-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:18px}.team-card{overflow:hidden;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-lg)}.team-cover{position:relative;height:170px}.team-cover img{width:100%;height:100%;object-fit:cover}.team-cover>span{position:absolute;right:12px;top:12px;padding:6px 9px;background:rgba(255,255,255,.9);border-radius:var(--radius-pill);font-size:9px;font-weight:800}.team-body{padding:20px}.team-name{display:flex;justify-content:space-between;align-items:center}.team-name h2{margin:0;font-size:20px}.team-name span{padding:4px 6px;background:#fff5d9;color:#9a6b00;border-radius:5px;display:flex;align-items:center;gap:3px;font-size:8px}.team-body>p{height:37px;color:var(--color-ink-soft);font-size:12px;line-height:1.6}.team-data{display:flex;justify-content:space-between;margin:14px 0;color:var(--color-ink-soft);font-size:10px}.team-data span{display:flex;align-items:center;gap:4px}.team-data i{width:6px;height:6px;border-radius:50%;background:var(--color-mint)}.team-body>.btn{width:100%;padding:10px;font-size:11px}.team-body>.joined{background:var(--color-bg)}.empty-state{padding:34px;border:1px dashed var(--color-line);border-radius:var(--radius-md);background:#fff;color:var(--color-ink-soft);text-align:center}.leaderboard{margin-top:45px;padding:35px 42px;border-radius:var(--radius-xl);background:var(--color-ink);color:#fff;display:grid;grid-template-columns:.7fr 1.3fr;gap:50px;align-items:center}.leaderboard h2{font-size:28px}.leaderboard>div>p{color:#aeb5c3}.leaderboard ol{list-style:none;margin:0;padding:0}.leaderboard li{display:flex;align-items:center;gap:12px;padding:12px;border-bottom:1px solid rgba(255,255,255,.1)}.leaderboard li>b{width:20px;color:var(--color-sun);font-size:18px}.leaderboard li img{width:40px;height:40px;border-radius:10px;object-fit:cover}.leaderboard li span{display:flex;flex-direction:column}.leaderboard li small{margin-top:3px;color:#8e98aa;font-size:9px}.leaderboard li em{margin-left:auto;font-style:normal;color:var(--color-sun);font-size:12px;display:flex;gap:5px}.team-modal{position:fixed;z-index:100;inset:0;padding:18px;background:rgba(13,21,34,.55);display:grid;place-items:center}.team-modal>div{width:min(100%,520px);padding:28px;background:#fff;border-radius:16px}.team-modal label{display:flex;flex-direction:column;gap:6px;margin-top:12px;font-size:10px;font-weight:800}.team-modal textarea{min-height:90px}.team-modal .form-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.team-modal footer{display:flex;justify-content:flex-end;gap:8px;margin-top:18px}.form-error{padding:8px;border-radius:7px;background:#ffeaed;color:var(--color-danger);font-size:10px}
@media(max-width:900px){.teams-hero{grid-template-columns:1fr}.teams-grid{grid-template-columns:1fr 1fr}.leaderboard{grid-template-columns:1fr}}@media(max-width:600px){.teams-hero{padding:28px}.teams-hero h1{font-size:31px}.teams-grid{grid-template-columns:1fr}.tabs button{padding:11px 8px;font-size:11px}.tabs .create-team{font-size:0}.leaderboard{padding:27px 20px}}
</style>
