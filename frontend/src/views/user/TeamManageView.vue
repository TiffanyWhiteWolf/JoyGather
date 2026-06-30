<script setup lang="ts">
import { ArrowLeft, Bell, Check, Crown, FileText, ImagePlus, Settings, ShieldCheck, Trash2, Users, X } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiDelete, apiGet, apiPost, apiPut } from '@/lib/api'
import { useAppStore } from '@/stores/app'
import type { Team, TeamMember } from '@/types'

interface JoinRequest {
  id: string
  userId?: string
  user_id?: string
  nickname: string
  avatar: string
  status: string
  reason?: string
  createdAt?: string
  created_at?: string
}

const route = useRoute()
const router = useRouter()
const app = useAppStore()
const teamId = computed(() => String(route.params.id || ''))
const team = ref<Team | null>(null)
const members = ref<TeamMember[]>([])
const requests = ref<JoinRequest[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const announcement = reactive({ content: '', mentionAll: true })
const album = reactive({ url: '', caption: '' })
const fileForm = reactive({ fileId: '' })
const form = reactive({
  name: '',
  description: '',
  cover: '',
  tags: '',
  capacity: 80,
  joinMode: '公开加入' as Team['joinMode'],
})

const currentUserId = computed(() => {
  try {
    return (JSON.parse(localStorage.getItem('quju:session') || '{}') as { id?: string }).id || ''
  } catch {
    return ''
  }
})
const isOwner = computed(() => team.value?.myRole === '队长')
const isAdmin = computed(() => team.value?.myRole === '队长' || team.value?.myRole === '管理员')
const pendingRequests = computed(() => requests.value.filter(item => item.status === '待审核'))

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const detail = await apiGet<Team>(`/teams/${teamId.value}`)
    team.value = detail
    Object.assign(form, {
      name: detail.name,
      description: detail.description,
      cover: detail.cover,
      tags: detail.tags.join('、'),
      capacity: detail.capacity,
      joinMode: detail.joinMode,
    })
    members.value = await apiGet<TeamMember[]>(`/teams/${teamId.value}/members`)
    if (detail.myRole === '队长' || detail.myRole === '管理员') {
      requests.value = await apiGet<JoinRequest[]>(`/teams/${teamId.value}/join-requests`).catch(() => [])
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '小队管理信息加载失败'
  } finally {
    loading.value = false
  }
}

function parseTags() {
  return form.tags.split(/[、,，]/).map(item => item.trim()).filter(Boolean)
}

function requestUserId(request: JoinRequest) {
  return request.userId || request.user_id || ''
}

function requestCreatedAt(request: JoinRequest) {
  return (request.createdAt || request.created_at || '').replace('T', ' ').slice(0, 16)
}

async function saveTeamInfo() {
  if (!team.value) return
  error.value = ''
  saving.value = true
  try {
    team.value = await apiPut<Team>(`/teams/${team.value.id}`, {
      name: form.name,
      description: form.description,
      cover: form.cover,
      tags: parseTags(),
      capacity: form.capacity,
      joinMode: form.joinMode,
    })
    app.showToast('小队资料已保存')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存失败'
  } finally {
    saving.value = false
  }
}

async function approveRequest(request: JoinRequest) {
  if (!team.value) return
  team.value = await apiPost<Team>(`/teams/${team.value.id}/join-requests/${request.id}/approve`, {})
  app.showToast('已通过加入申请')
  await loadAll()
}

async function rejectRequest(request: JoinRequest) {
  if (!team.value) return
  const reason = window.prompt('请输入驳回原因（可选）') || ''
  await apiPost<void>(`/teams/${team.value.id}/join-requests/${request.id}/reject`, { reason })
  app.showToast('已驳回加入申请')
  await loadAll()
}

async function setRole(member: TeamMember, role: '管理员' | '成员') {
  if (!team.value) return
  await apiPut<void>(`/teams/${team.value.id}/members/${member.userId}/role`, { role })
  app.showToast('成员角色已更新')
  await loadAll()
}

async function removeMember(member: TeamMember) {
  if (!team.value) return
  if (!window.confirm(`确认移除 ${member.nickname}？`)) return
  await apiDelete<void>(`/teams/${team.value.id}/members/${member.userId}`)
  app.showToast('成员已移除')
  await loadAll()
}

async function transferOwnership(member: TeamMember) {
  if (!team.value) return
  if (!window.confirm(`确认将队长转让给 ${member.nickname}？转让后你将成为普通成员。`)) return
  try {
    team.value = await apiPost<Team>(`/teams/${team.value.id}/transfer`, { role: member.userId })
    app.showToast('队长已转让')
    await loadAll()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '队长转让失败'
  }
}

async function publishAnnouncement() {
  if (!team.value || !announcement.content.trim()) {
    error.value = '请填写公告内容'
    return
  }
  error.value = ''
  await apiPost<void>(`/teams/${team.value.id}/announcements`, {
    content: announcement.content.trim(),
    mentionAll: announcement.mentionAll,
  })
  announcement.content = ''
  app.showToast('小队公告已发布')
}

async function publishAlbum() {
  if (!team.value || !album.url.trim()) {
    error.value = '请填写照片 URL'
    return
  }
  error.value = ''
  await apiPost<void>(`/teams/${team.value.id}/albums`, {
    url: album.url.trim(),
    caption: album.caption.trim(),
  })
  album.url = ''
  album.caption = ''
  app.showToast('相册照片已添加')
}

async function publishFile() {
  if (!team.value || !fileForm.fileId.trim()) {
    error.value = '请填写文件 ID'
    return
  }
  error.value = ''
  await apiPost<void>(`/teams/${team.value.id}/files`, { fileId: fileForm.fileId.trim() })
  fileForm.fileId = ''
  app.showToast('文件已添加到小队')
}

async function dissolveTeam() {
  if (!team.value || !window.confirm('确认解散小队？解散后群聊会关闭。')) return
  await apiPost<void>(`/teams/${team.value.id}/dissolve`, {})
  app.showToast('小队已解散')
  await router.push('/teams')
}
</script>

<template>
  <div class="container team-manage-page">
    <button class="back-btn" @click="router.push('/teams')"><ArrowLeft :size="16" />返回小队</button>

    <div v-if="loading" class="empty-state">正在加载小队管理信息...</div>
    <div v-else-if="error && !team" class="empty-state">{{ error }}</div>

    <template v-else-if="team">
      <section class="manage-hero">
        <img :src="team.cover" :alt="team.name" />
        <div>
          <span class="eyebrow">TEAM MANAGEMENT</span>
          <h1>{{ team.name }}</h1>
          <p>{{ team.description }}</p>
          <div class="hero-meta">
            <span><Users :size="15" />{{ team.members }} / {{ team.capacity }} 人</span>
            <span><ShieldCheck :size="15" />{{ team.myRole || '未加入' }}</span>
            <span>{{ team.joinMode }}</span>
          </div>
        </div>
      </section>

      <p v-if="error" class="form-error">{{ error }}</p>

      <div class="manage-grid">
        <main>
          <section class="manage-panel">
            <div class="panel-head">
              <div><Settings :size="18" /><h2>小队资料</h2></div>
              <span>{{ isOwner ? '队长可编辑' : '仅队长可编辑' }}</span>
            </div>
            <div class="form-grid">
              <label>小队名称<input v-model.trim="form.name" class="input" :disabled="!isOwner" /></label>
              <label>加入方式<select v-model="form.joinMode" class="select" :disabled="!isOwner"><option>公开加入</option><option>审核加入</option></select></label>
              <label>人数上限<input v-model.number="form.capacity" class="input" type="number" min="1" :disabled="!isOwner" /></label>
              <label>标签<input v-model.trim="form.tags" class="input" :disabled="!isOwner" /></label>
            </div>
            <label>封面 URL<input v-model.trim="form.cover" class="input" :disabled="!isOwner" /></label>
            <label>简介<textarea v-model.trim="form.description" class="textarea" :disabled="!isOwner"></textarea></label>
            <footer v-if="isOwner">
              <button class="btn btn-primary" :disabled="saving" @click="saveTeamInfo">{{ saving ? '保存中' : '保存资料' }}</button>
            </footer>
          </section>

          <section class="manage-panel">
            <div class="panel-head">
              <div><Users :size="18" /><h2>成员管理</h2></div>
              <span>{{ members.length }} 人</span>
            </div>
            <div class="member-list">
              <div v-for="member in members" :key="member.userId" class="member-row">
                <img :src="member.avatar" :alt="member.nickname" />
                <div>
                  <b>{{ member.nickname }}</b>
                  <small>{{ member.userId }}</small>
                </div>
                <i :class="{ owner: member.role === '队长' }"><Crown v-if="member.role === '队长'" :size="13" />{{ member.role }}</i>
                <div v-if="isOwner && member.role !== '队长' && member.userId !== currentUserId" class="member-actions">
                  <button v-if="member.role === '成员'" @click="setRole(member, '管理员')">设为管理员</button>
                  <button v-else @click="setRole(member, '成员')">设为成员</button>
                  <button @click="transferOwnership(member)"><Crown :size="13" />转让队长</button>
                  <button class="danger" @click="removeMember(member)"><Trash2 :size="13" /></button>
                </div>
                <div v-else-if="isAdmin && member.role === '成员' && member.userId !== currentUserId" class="member-actions">
                  <button class="danger" @click="removeMember(member)"><Trash2 :size="13" />移除</button>
                </div>
              </div>
            </div>
          </section>
        </main>

        <aside>
          <section class="manage-panel">
            <div class="panel-head">
              <div><Check :size="18" /><h2>加入申请</h2></div>
              <span>{{ pendingRequests.length }} 待处理</span>
            </div>
            <div v-if="!isAdmin" class="muted-box">需要小队管理员权限。</div>
            <div v-else-if="!pendingRequests.length" class="muted-box">暂无待处理申请。</div>
            <div v-else class="request-list">
              <div v-for="request in pendingRequests" :key="request.id" class="request-row">
                <img :src="request.avatar" :alt="request.nickname" />
                <div>
                  <b>{{ request.nickname }}</b>
                  <small>{{ requestUserId(request) }} · {{ requestCreatedAt(request) || '刚刚' }}</small>
                </div>
                <div>
                  <button @click="approveRequest(request)"><Check :size="14" /></button>
                  <button @click="rejectRequest(request)"><X :size="14" /></button>
                </div>
              </div>
            </div>
          </section>

          <section class="manage-panel">
            <div class="panel-head">
              <div><Bell :size="18" /><h2>发布公告</h2></div>
            </div>
            <textarea v-model.trim="announcement.content" class="textarea" placeholder="公告内容"></textarea>
            <label class="check-row"><input v-model="announcement.mentionAll" type="checkbox" />通知全体成员</label>
            <button class="btn btn-dark" :disabled="!isAdmin" @click="publishAnnouncement">发布公告</button>
          </section>

          <section class="manage-panel">
            <div class="panel-head">
              <div><ImagePlus :size="18" /><h2>相册</h2></div>
            </div>
            <input v-model.trim="album.url" class="input" placeholder="照片 URL" />
            <input v-model.trim="album.caption" class="input" placeholder="照片说明" />
            <button class="btn btn-outline" @click="publishAlbum">添加照片</button>
          </section>

          <section class="manage-panel">
            <div class="panel-head">
              <div><FileText :size="18" /><h2>文件</h2></div>
            </div>
            <input v-model.trim="fileForm.fileId" class="input" placeholder="文件 ID" />
            <button class="btn btn-outline" @click="publishFile">添加文件</button>
          </section>

          <section v-if="isOwner" class="manage-panel danger-panel">
            <h2>危险操作</h2>
            <p>解散后小队会停用，群聊会关闭。</p>
            <button class="danger-btn" @click="dissolveTeam"><Trash2 :size="15" />解散小队</button>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.team-manage-page{padding:36px 0 80px}.back-btn{margin-bottom:16px;border:0;background:none;color:var(--color-primary);display:flex;align-items:center;gap:6px;font-size:12px;font-weight:800}.manage-hero{position:relative;overflow:hidden;min-height:240px;border-radius:var(--radius-xl);display:grid;grid-template-columns:280px 1fr;background:#fff;border:1px solid var(--color-line);box-shadow:var(--shadow-card)}.manage-hero>img{width:100%;height:100%;object-fit:cover}.manage-hero>div{padding:34px;display:flex;flex-direction:column;justify-content:center}.manage-hero h1{margin:6px 0 10px;font-size:34px}.manage-hero p{margin:0;max-width:620px;color:var(--color-ink-soft);line-height:1.7}.hero-meta{display:flex;flex-wrap:wrap;gap:8px;margin-top:18px}.hero-meta span{padding:7px 10px;border-radius:8px;background:var(--color-bg);display:flex;align-items:center;gap:5px;font-size:11px;font-weight:800}.manage-grid{display:grid;grid-template-columns:1fr 330px;gap:18px;margin-top:20px}.manage-panel{margin-bottom:16px;padding:18px;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-md)}.panel-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:14px}.panel-head>div{display:flex;align-items:center;gap:8px}.panel-head h2,.danger-panel h2{margin:0;font-size:16px}.panel-head span{color:var(--color-ink-soft);font-size:11px;font-weight:800}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.manage-panel label{display:flex;flex-direction:column;gap:6px;margin-top:10px;font-size:11px;font-weight:800}.manage-panel footer{display:flex;justify-content:flex-end;margin-top:14px}.member-list,.request-list{display:grid;gap:8px}.member-row,.request-row{display:grid;grid-template-columns:42px 1fr auto auto;align-items:center;gap:10px;padding:10px;border:1px solid var(--color-line);border-radius:10px;background:var(--color-bg)}.request-row{grid-template-columns:38px 1fr auto}.member-row img,.request-row img{width:42px;height:42px;border-radius:50%;object-fit:cover;background:#fff}.request-row img{width:38px;height:38px}.member-row b,.request-row b{display:block;font-size:12px}.member-row small,.request-row small{display:block;color:var(--color-ink-soft);font-size:10px}.member-row i{padding:5px 8px;border-radius:8px;background:#fff;color:var(--color-ink-soft);display:flex;align-items:center;gap:4px;font-size:10px;font-style:normal;font-weight:800}.member-row i.owner{background:var(--color-primary-soft);color:var(--color-primary)}.member-actions,.request-row>div:last-child{display:flex;gap:6px}.member-actions button,.request-row button{padding:7px 8px;border:1px solid var(--color-line);border-radius:8px;background:#fff;color:var(--color-primary);display:flex;align-items:center;gap:4px;font-size:10px;font-weight:800}.member-actions .danger,.danger-btn{color:var(--color-danger);border-color:#ffd2d8}.muted-box{padding:18px;border:1px dashed var(--color-line);border-radius:10px;background:var(--color-bg);color:var(--color-ink-soft);font-size:12px;text-align:center}.check-row{flex-direction:row!important;align-items:center!important}.manage-panel .btn{width:100%;justify-content:center;margin-top:10px}.manage-panel input+.input{margin-top:8px}.danger-panel{border-color:#ffd2d8;background:#fffafa}.danger-panel p{color:var(--color-ink-soft);font-size:11px;line-height:1.7}.danger-btn{width:100%;padding:10px;border-radius:8px;background:#fff;display:flex;align-items:center;justify-content:center;gap:6px;font-size:11px;font-weight:800}.form-error{margin:14px 0 0;padding:10px;border-radius:8px;background:#ffeaed;color:var(--color-danger);font-size:12px}.empty-state{padding:40px;background:#fff;border:1px dashed var(--color-line);border-radius:var(--radius-md);color:var(--color-ink-soft);text-align:center}
@media(max-width:900px){.manage-hero{grid-template-columns:1fr}.manage-hero>img{height:220px}.manage-grid{grid-template-columns:1fr}}@media(max-width:600px){.manage-hero>div{padding:24px}.manage-hero h1{font-size:27px}.form-grid{grid-template-columns:1fr}.member-row{grid-template-columns:42px 1fr}.member-row i,.member-actions{grid-column:2}.manage-grid{gap:10px}}
</style>
