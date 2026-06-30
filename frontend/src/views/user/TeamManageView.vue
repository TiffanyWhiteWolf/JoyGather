<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft, Check, Crown, Edit3, Flag, Image, Shield, Trash2, UserPlus,
  UserX, Users, X, AlertTriangle,
} from 'lucide-vue-next'
import { apiDelete, apiGet, apiPost, apiPut } from '@/lib/api'
import { useAppStore } from '@/stores/app'
import type { Team, TeamMember } from '@/types'

const app = useAppStore()
const route = useRoute()
const router = useRouter()
const teamId = route.params.id as string

const tab = ref<'info' | 'members' | 'requests' | 'content'>('info')
const team = ref<Team | null>(null)
const members = ref<TeamMember[]>([])
const requests = ref<any[]>([])
const loading = ref(true)

const editMode = ref(false)
const editForm = reactive({ name: '', description: '', tags: '', capacity: 80, joinMode: '公开加入' as string, cover: '' })
const editError = ref('')
const editSaving = ref(false)

const dissolveConfirm = ref(false)

const selectedMember = ref<TeamMember | null>(null)
const actionModal = ref<'kick' | 'transfer' | null>(null)
const actionError = ref('')

const myRole = computed(() => team.value?.myRole)
const isOwner = computed(() => myRole.value === '队长')
const isAdmin = computed(() => myRole.value === '队长' || myRole.value === '管理员')
const pendingCount = computed(() => requests.value.filter((r: any) => r.status === '待审核').length)

async function loadAll() {
  loading.value = true
  try {
    const [t, m] = await Promise.all([
      apiGet<Team>(`/teams/${teamId}`),
      apiGet<TeamMember[]>(`/teams/${teamId}/members`).catch(() => [] as TeamMember[]),
    ])
    team.value = t
    members.value = m
    if (isAdmin.value) {
      try { requests.value = await apiGet<any[]>(`/teams/${teamId}/join-requests`) } catch { requests.value = [] }
    }
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '加载小队信息失败')
  } finally {
    loading.value = false
  }
}

function startEdit() {
  if (!team.value) return
  editForm.name = team.value.name
  editForm.description = team.value.description
  editForm.tags = team.value.tags.join('、')
  editForm.capacity = team.value.capacity
  editForm.joinMode = team.value.joinMode
  editForm.cover = team.value.cover
  editMode.value = true
  editError.value = ''
}

async function saveEdit() {
  if (!editForm.name.trim() || !editForm.description.trim()) {
    editError.value = '名称和简介不能为空'
    return
  }
  editSaving.value = true
  editError.value = ''
  try {
    const updated = await apiPut<Team>(`/teams/${teamId}`, {
      name: editForm.name.trim(),
      description: editForm.description.trim(),
      tags: editForm.tags.split(/[、,，]/).map((t: string) => t.trim()).filter(Boolean),
      capacity: editForm.capacity,
      joinMode: editForm.joinMode,
      cover: editForm.cover.trim(),
    })
    team.value = updated
    editMode.value = false
    app.showToast('小队信息已更新')
  } catch (e) {
    editError.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    editSaving.value = false
  }
}

async function changeRole(member: TeamMember) {
  const newRole = member.role === '管理员' ? '成员' : '管理员'
  try {
    await apiPut<void>(`/teams/${teamId}/members/${member.userId}/role`, { role: newRole })
    member.role = newRole
    app.showToast(newRole === '管理员' ? '已设为管理员' : '已取消管理员')
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '操作失败')
  }
}

function confirmKick(member: TeamMember) {
  selectedMember.value = member
  actionModal.value = 'kick'
  actionError.value = ''
}

async function kickMember() {
  if (!selectedMember.value) return
  try {
    await apiDelete<void>(`/teams/${teamId}/members/${selectedMember.value.userId}`)
    members.value = members.value.filter(m => m.userId !== selectedMember.value!.userId)
    if (team.value) team.value.members = Math.max(0, team.value.members - 1)
    actionModal.value = null
    selectedMember.value = null
    app.showToast('已移出小队')
  } catch (e) {
    actionError.value = e instanceof Error ? e.message : '操作失败'
  }
}

function confirmTransfer(member: TeamMember) {
  selectedMember.value = member
  actionModal.value = 'transfer'
  actionError.value = ''
}

async function transferOwnership() {
  if (!selectedMember.value) return
  try {
    const updated = await apiPost<Team>(`/teams/${teamId}/transfer`, { role: selectedMember.value.userId })
    team.value = updated
    actionModal.value = null
    selectedMember.value = null
    await loadAll()
    app.showToast('队长已转让')
  } catch (e) {
    actionError.value = e instanceof Error ? e.message : '操作失败'
  }
}

async function approveRequest(requestId: string) {
  try {
    const updated = await apiPost<Team>(`/teams/${teamId}/join-requests/${requestId}/approve`, {})
    team.value = updated
    requests.value = requests.value.map((r: any) => r.id === requestId ? { ...r, status: '已通过' } : r)
    await loadAll()
    app.showToast('已通过申请')
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '操作失败')
  }
}

async function rejectRequest(requestId: string) {
  try {
    await apiPost<void>(`/teams/${teamId}/join-requests/${requestId}/reject`, { reason: '' })
    requests.value = requests.value.map((r: any) => r.id === requestId ? { ...r, status: '已驳回' } : r)
    app.showToast('已驳回申请')
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '操作失败')
  }
}

async function dissolveTeam() {
  try {
    await apiPost<void>(`/teams/${teamId}/dissolve`, {})
    app.showToast('小队已解散')
    app.refreshUserState()
    router.push('/teams')
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '解散失败')
    dissolveConfirm.value = false
  }
}

function goBack() { router.push('/teams') }

onMounted(loadAll)
</script>

<template>
  <div v-if="loading" class="container" style="padding:80px 0;text-align:center;color:var(--color-ink-soft)">加载中...</div>
  <div v-else-if="!team" class="container" style="padding:80px 0;text-align:center">
    <p>小队不存在或无法访问。</p>
    <button class="btn btn-outline" @click="goBack">返回小队广场</button>
  </div>
  <div v-else class="container team-manage">
    <!-- Header -->
    <div class="manage-header">
      <button class="back-btn" @click="goBack"><ArrowLeft :size="18" />返回</button>
      <div class="manage-title">
        <img :src="team.cover" />
        <div>
          <h1>{{ team.name }}</h1>
          <span class="role-badge" :class="myRole === '队长' ? 'owner' : 'admin'">
            <Crown v-if="myRole==='队长'" :size="12" />
            <Shield v-else :size="12" />
            {{ myRole || '成员' }}
          </span>
        </div>
      </div>
      <div class="manage-stats">
        <span><Users :size="15" />{{ team.members }} / {{ team.capacity }} 人</span>
        <span>{{ team.joinMode }}</span>
        <span>{{ team.activeNow }} 在线</span>
        <span v-if="pendingCount && isAdmin" class="pending-dot">{{ pendingCount }} 条待审</span>
      </div>
    </div>

    <!-- Tabs -->
    <nav class="manage-tabs">
      <button :class="{ active: tab === 'info' }" @click="tab = 'info'">小队信息</button>
      <button :class="{ active: tab === 'members' }" @click="tab = 'members'">成员管理</button>
      <button v-if="isAdmin" :class="{ active: tab === 'requests' }" @click="tab = 'requests'">
        加入申请
        <span v-if="pendingCount" class="tab-badge">{{ pendingCount }}</span>
      </button>
      <button :class="{ active: tab === 'content' }" @click="tab = 'content'">内容管理</button>
    </nav>

    <!-- Info Tab -->
    <section v-if="tab === 'info'" class="manage-panel">
      <template v-if="!editMode">
        <div class="info-hero">
          <img :src="team.cover" />
        </div>
        <div class="info-grid">
          <div class="info-item"><b>小队名称</b><span>{{ team.name }}</span></div>
          <div class="info-item"><b>加入方式</b><span>{{ team.joinMode }}</span></div>
          <div class="info-item"><b>人数</b><span>{{ team.members }} / {{ team.capacity }}</span></div>
          <div class="info-item"><b>状态</b><span>{{ team.status === '已停用' ? '已停用' : '正常' }}</span></div>
          <div class="info-item full"><b>简介</b><span>{{ team.description || '暂无简介' }}</span></div>
          <div class="info-item full"><b>标签</b><span class="tag-row"><span v-for="t in team.tags" :key="t"># {{ t }}</span></span></div>
        </div>
        <div v-if="isOwner" class="info-actions">
          <button class="btn btn-outline btn-sm" @click="startEdit"><Edit3 :size="14" />编辑信息</button>
          <button class="btn btn-sm" style="background:#ffeaed;color:var(--color-danger)" @click="dissolveConfirm = true"><Trash2 :size="14" />解散小队</button>
        </div>
      </template>
      <template v-else>
        <h3>编辑小队信息</h3>
        <div class="edit-form">
          <div class="input-group"><label>名称</label><input class="input" v-model="editForm.name" /></div>
          <div class="input-group"><label>简介</label><textarea class="textarea" v-model="editForm.description" /></div>
          <div class="input-group"><label>标签（顿号分隔）</label><input class="input" v-model="editForm.tags" /></div>
          <div class="form-grid">
            <div class="input-group"><label>人数上限</label><input class="input" type="number" v-model.number="editForm.capacity" /></div>
            <div class="input-group"><label>加入方式</label><select class="select" v-model="editForm.joinMode"><option>公开加入</option><option>审核加入</option></select></div>
          </div>
          <div class="input-group"><label>封面 URL</label><input class="input" v-model="editForm.cover" /></div>
          <p v-if="editError" class="form-error">{{ editError }}</p>
          <div class="edit-actions">
            <button class="btn btn-outline btn-sm" @click="editMode = false">取消</button>
            <button class="btn btn-primary btn-sm" :disabled="editSaving" @click="saveEdit">{{ editSaving ? '保存中...' : '保存' }}</button>
          </div>
        </div>
      </template>
    </section>

    <!-- Members Tab -->
    <section v-if="tab === 'members'" class="manage-panel">
      <div class="section-head">
        <h3>成员列表 <small>（{{ members.length }} 人）</small></h3>
      </div>
      <div class="member-list">
        <div v-for="m in members" :key="m.userId" class="member-row">
          <img :src="m.avatar" />
          <div class="member-info">
            <b>{{ m.nickname }}</b>
            <span class="member-role" :class="{ owner: m.role === '队长', admin: m.role === '管理员' }">{{ m.role }}</span>
          </div>
          <span class="member-date">{{ m.joinedAt?.slice(0, 10) }}</span>
          <div v-if="isAdmin && m.role !== '队长'" class="member-actions">
            <button v-if="isOwner" class="btn-icon" title="设为/取消管理员" @click="changeRole(m)">
              {{ m.role === '管理员' ? '取消管理员' : '设为管理员' }}
            </button>
            <button v-if="isOwner" class="btn-icon warn" title="转让队长" @click="confirmTransfer(m)">转让</button>
            <button class="btn-icon danger" title="移出小队" @click="confirmKick(m)"><X :size="14" /></button>
          </div>
        </div>
        <div v-if="!members.length" class="empty-state">暂无成员数据。</div>
      </div>
    </section>

    <!-- Requests Tab -->
    <section v-if="tab === 'requests' && isAdmin" class="manage-panel">
      <h3>加入申请</h3>
      <div class="request-list">
        <div v-for="r in requests" :key="r.id" class="request-row">
          <img :src="r.avatar || 'https://i.pravatar.cc/80?u=' + r.user_id" />
          <div class="request-info">
            <b>{{ r.nickname || r.user_id }}</b>
            <span>{{ r.created_at?.slice(0, 16) }}</span>
          </div>
          <span class="request-status" :class="{ approved: r.status === '已通过', rejected: r.status === '已驳回' }">{{ r.status }}</span>
          <div v-if="r.status === '待审核'" class="request-actions">
            <button class="btn btn-sm" style="background:var(--color-mint-soft);color:var(--color-mint)" @click="approveRequest(r.id)"><Check :size="14" /></button>
            <button class="btn btn-sm" style="background:#ffeaed;color:var(--color-danger)" @click="rejectRequest(r.id)"><X :size="14" /></button>
          </div>
        </div>
        <div v-if="!requests.length" class="empty-state">暂无加入申请。</div>
      </div>
    </section>

    <!-- Content Tab -->
    <section v-if="tab === 'content'" class="manage-panel">
      <h3>内容管理</h3>
      <div class="content-placeholder">
        <div class="content-card">
          <Flag :size="22" />
          <b>公告管理</b>
          <p>发布和管理小队公告</p>
          <span class="coming-soon">即将上线</span>
        </div>
        <div class="content-card">
          <Image :size="22" />
          <b>相册管理</b>
          <p>管理小队活动相册</p>
          <span class="coming-soon">即将上线</span>
        </div>
        <div class="content-card">
          <AlertTriangle :size="22" />
          <b>文件管理</b>
          <p>管理小队共享文件</p>
          <span class="coming-soon">即将上线</span>
        </div>
      </div>
    </section>

    <!-- Dissolve Confirm Modal -->
    <div v-if="dissolveConfirm" class="reject-modal" @click.self="dissolveConfirm = false">
      <div>
        <h3>确认解散小队？</h3>
        <p>解散后所有成员将被移除，群聊将关闭。此操作不可恢复。</p>
        <footer>
          <button class="btn btn-outline btn-sm" @click="dissolveConfirm = false">取消</button>
          <button class="btn btn-sm" style="background:var(--color-danger);color:#fff" @click="dissolveTeam">确认解散</button>
        </footer>
      </div>
    </div>

    <!-- Kick / Transfer Modal -->
    <div v-if="actionModal" class="reject-modal" @click.self="actionModal = null">
      <div>
        <h3>{{ actionModal === 'kick' ? '移出成员' : '转让队长' }}</h3>
        <p v-if="actionModal === 'kick'">确认将 <b>{{ selectedMember?.nickname }}</b> 移出小队？</p>
        <p v-else>确认将队长身份转让给 <b>{{ selectedMember?.nickname }}</b>？转让后你将成为普通成员。</p>
        <p v-if="actionError" class="form-error">{{ actionError }}</p>
        <footer>
          <button class="btn btn-outline btn-sm" @click="actionModal = null">取消</button>
          <button class="btn btn-primary btn-sm" @click="actionModal === 'kick' ? kickMember() : transferOwnership()">确认</button>
        </footer>
      </div>
    </div>
  </div>
</template>

<style scoped>
.team-manage{padding:36px 0 80px}
.manage-header{display:flex;flex-direction:column;gap:18px;padding:28px 32px;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-lg);margin-bottom:16px}
.back-btn{display:inline-flex;align-items:center;gap:6px;border:0;background:none;color:var(--color-ink-soft);font-size:13px;font-weight:600;cursor:pointer;width:fit-content}
.manage-title{display:flex;align-items:center;gap:18px}
.manage-title img{width:72px;height:72px;border-radius:18px;object-fit:cover}
.manage-title h1{margin:0;font-size:28px;letter-spacing:-.03em}
.manage-title>div{display:flex;flex-direction:column;gap:6px}
.role-badge{display:inline-flex;align-items:center;gap:4px;padding:4px 10px;border-radius:var(--radius-pill);font-size:11px;font-weight:800;width:fit-content}
.role-badge.owner{background:var(--color-primary-soft);color:var(--color-primary)}
.role-badge.admin{background:var(--color-mint-soft);color:var(--color-mint)}
.manage-stats{display:flex;gap:18px;color:var(--color-ink-soft);font-size:12px}
.manage-stats span{display:flex;align-items:center;gap:4px}
.pending-dot{color:var(--color-primary);font-weight:800}
.manage-tabs{display:flex;gap:0;margin-bottom:16px;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-md);overflow:hidden}
.manage-tabs button{flex:1;padding:14px 10px;border:0;border-right:1px solid var(--color-line);background:none;font-size:13px;font-weight:700;color:var(--color-ink-soft);cursor:pointer;display:flex;align-items:center;justify-content:center;gap:6px}
.manage-tabs button:last-child{border-right:0}
.manage-tabs button.active{background:var(--color-primary-soft);color:var(--color-primary)}
.tab-badge{min-width:18px;height:18px;padding:0 5px;border-radius:9px;background:var(--color-primary);color:#fff;font-size:10px;display:grid;place-items:center}
.manage-panel{padding:28px 32px;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-lg)}
.manage-panel h3{margin:0 0 18px;font-size:18px}
.manage-panel h3 small{font-weight:400;color:var(--color-ink-soft);font-size:13px}
.info-hero{margin:-28px -32px 24px}.info-hero img{width:100%;height:180px;object-fit:cover}
.info-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-bottom:18px}
.info-item.full{grid-column:1/-1}
.info-item b{display:block;font-size:11px;color:var(--color-ink-soft);margin-bottom:4px}
.info-item span{font-size:14px;font-weight:600}
.info-actions{display:flex;gap:10px;margin-top:8px}
.edit-form{display:flex;flex-direction:column;gap:14px;max-width:600px}
.edit-actions{display:flex;gap:10px;justify-content:flex-end}.edit-actions .btn{font-size:13px}
.member-list{display:flex;flex-direction:column;gap:2px}
.member-row{display:flex;align-items:center;gap:14px;padding:14px 12px;border-radius:12px}
.member-row:hover{background:var(--color-bg)}
.member-row img{width:42px;height:42px;border-radius:50%;object-fit:cover}
.member-info{display:flex;flex-direction:column;gap:3px;flex:1}.member-info b{font-size:14px}
.member-role{display:inline-block;padding:2px 7px;border-radius:5px;font-size:10px;font-weight:700;width:fit-content;background:var(--color-bg);color:var(--color-ink-soft)}
.member-role.owner{background:var(--color-primary-soft);color:var(--color-primary)}
.member-role.admin{background:var(--color-mint-soft);color:var(--color-mint)}
.member-date{color:var(--color-ink-soft);font-size:11px}
.member-actions{display:flex;gap:6px}.member-actions .btn-icon{padding:5px 10px;border:1px solid var(--color-line);border-radius:7px;background:#fff;font-size:11px;cursor:pointer;color:var(--color-ink-soft)}.member-actions .btn-icon:hover{background:var(--color-bg)}.member-actions .btn-icon.warn{color:var(--color-sun)}.member-actions .btn-icon.danger{color:var(--color-danger)}
.request-list{display:flex;flex-direction:column;gap:2px}
.request-row{display:flex;align-items:center;gap:14px;padding:14px 12px;border-radius:12px}.request-row:hover{background:var(--color-bg)}
.request-row img{width:42px;height:42px;border-radius:50%;object-fit:cover}
.request-info{display:flex;flex-direction:column;gap:3px;flex:1}.request-info b{font-size:14px}.request-info span{font-size:11px;color:var(--color-ink-soft)}
.request-status{padding:4px 9px;border-radius:var(--radius-pill);font-size:10px;font-weight:700;background:var(--color-bg);color:var(--color-ink-soft)}
.request-status.approved{background:var(--color-mint-soft);color:var(--color-mint)}
.request-status.rejected{background:#ffeaed;color:var(--color-danger)}
.request-actions{display:flex;gap:6px}
.content-placeholder{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}
.content-card{padding:24px;border:1px solid var(--color-line);border-radius:var(--radius-md);text-align:center;display:flex;flex-direction:column;align-items:center;gap:10px;color:var(--color-ink-soft)}.content-card b{color:var(--color-ink);font-size:15px}.content-card p{font-size:12px;margin:0}.coming-soon{padding:3px 9px;background:var(--color-bg);border-radius:var(--radius-pill);font-size:10px;color:var(--color-ink-soft)}
.section-head{margin-bottom:10px}
.form-error{color:var(--color-danger);font-size:12px;margin:0}
@media(max-width:680px){.manage-header{padding:20px}.manage-title img{width:52px;height:52px}.manage-title h1{font-size:22px}.manage-tabs button{font-size:11px;padding:10px 6px}.member-row{flex-wrap:wrap}.member-date{display:none}.content-placeholder{grid-template-columns:1fr}.manage-panel{padding:20px}.info-hero{margin:-20px -20px 18px}}
</style>