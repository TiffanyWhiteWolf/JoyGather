<script setup lang="ts">
import { ArrowLeft, Crown, Save, ShieldCheck, Trash2, UserCheck, Users } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiDelete, apiGet, apiPost, apiPut } from '@/lib/api'
import type { Team, TeamMember } from '@/types'

interface JoinRequest {
  id: string
  userId: string
  nickname: string
  avatar: string
  message?: string
  createdAt?: string
}

const route = useRoute()
const router = useRouter()
const team = ref<Team | null>(null)
const members = ref<TeamMember[]>([])
const requests = ref<JoinRequest[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const form = reactive({ name: '', description: '', cover: '', tags: '', capacity: 30, joinMode: '公开加入' })
const teamId = computed(() => String(route.params.id || ''))
const isOwner = computed(() => team.value?.myRole === '队长')
const canManageMembers = computed(() => isOwner.value || team.value?.myRole === '管理员')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [detail, memberRows, requestRows] = await Promise.all([
      apiGet<Team>(`/teams/${teamId.value}`),
      apiGet<TeamMember[]>(`/teams/${teamId.value}/members`),
      apiGet<JoinRequest[]>(`/teams/${teamId.value}/join-requests`).catch(() => []),
    ])
    team.value = detail
    members.value = memberRows
    requests.value = requestRows
    Object.assign(form, {
      name: detail.name,
      description: detail.description,
      cover: detail.cover,
      tags: detail.tags.join('、'),
      capacity: detail.capacity,
      joinMode: detail.joinMode,
    })
  } catch (err) {
    error.value = err instanceof Error ? err.message : '小队管理信息加载失败'
  } finally {
    loading.value = false
  }
}

async function saveTeam() {
  if (!team.value || !isOwner.value) return
  saving.value = true
  error.value = ''
  try {
    team.value = await apiPut<Team>(`/teams/${teamId.value}`, {
      ...form,
      tags: form.tags.split(/[、，,]/).map(item => item.trim()).filter(Boolean),
    })
    notice.value = '小队资料已保存'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '小队资料保存失败'
  } finally {
    saving.value = false
  }
}

async function changeRole(member: TeamMember, event: Event) {
  const role = (event.target as HTMLSelectElement).value
  try {
    await apiPut<void>(`/teams/${teamId.value}/members/${member.userId}/role`, { role })
    member.role = role as TeamMember['role']
    notice.value = `${member.nickname}的角色已更新`
  } catch (err) {
    error.value = err instanceof Error ? err.message : '角色更新失败'
    await load()
  }
}

async function removeMember(member: TeamMember) {
  if (!window.confirm(`确定将“${member.nickname}”移出小队吗？`)) return
  try {
    await apiDelete<void>(`/teams/${teamId.value}/members/${member.userId}`)
    members.value = members.value.filter(item => item.userId !== member.userId)
    notice.value = '成员已移出小队'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '移除成员失败'
  }
}

async function handleRequest(request: JoinRequest, approved: boolean) {
  try {
    await apiPost<void>(`/teams/${teamId.value}/join-requests/${request.id}/${approved ? 'approve' : 'reject'}`, approved ? {} : { reason: '管理员未通过申请' })
    requests.value = requests.value.filter(item => item.id !== request.id)
    if (approved) await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '申请处理失败'
  }
}

async function transferOwner(member: TeamMember) {
  if (!window.confirm(`确定将队长转让给“${member.nickname}”吗？`)) return
  try {
    team.value = await apiPost<Team>(`/teams/${teamId.value}/transfer`, { role: member.userId })
    await load()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '队长转让失败'
  }
}

async function dissolveTeam() {
  if (!window.confirm('解散后小队成员关系将被清除，确定继续吗？')) return
  try {
    await apiPost<void>(`/teams/${teamId.value}/dissolve`, {})
    await router.push('/teams')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '解散小队失败'
  }
}

onMounted(load)
</script>

<template>
  <div class="container manage-page">
    <RouterLink to="/teams" class="back-link"><ArrowLeft :size="16" />返回小队列表</RouterLink>
    <div v-if="loading" class="state-card">正在加载小队管理信息…</div>
    <div v-else-if="!team" class="state-card error">{{ error || '小队不存在' }}</div>
    <template v-else>
      <header class="manage-hero">
        <img :src="team.cover" :alt="team.name" />
        <div><span>TEAM MANAGEMENT</span><h1>{{ team.name }}</h1><p>{{ team.description }}</p></div>
        <i>{{ team.myRole }}</i>
      </header>

      <p v-if="error" class="alert error">{{ error }}</p>
      <p v-if="notice" class="alert success">{{ notice }}</p>

      <div class="manage-grid">
        <main>
          <section v-if="isOwner" class="panel">
            <div class="panel-title"><div><small>基础资料</small><h2>编辑小队信息</h2></div><button class="btn btn-primary" :disabled="saving" @click="saveTeam"><Save :size="16" />{{ saving ? '保存中…' : '保存修改' }}</button></div>
            <div class="form-grid"><label>小队名称<input v-model.trim="form.name" class="input" /></label><label>人数上限<input v-model.number="form.capacity" class="input" type="number" min="2" /></label><label>加入方式<select v-model="form.joinMode" class="select"><option>公开加入</option><option>审核加入</option></select></label><label>兴趣标签<input v-model.trim="form.tags" class="input" placeholder="使用顿号或逗号分隔" /></label></div>
            <label>小队简介<textarea v-model.trim="form.description" class="textarea"></textarea></label>
            <label>封面地址<input v-model.trim="form.cover" class="input" /></label>
          </section>

          <section class="panel">
            <div class="panel-title"><div><small>成员与权限</small><h2>{{ members.length }} 位小队成员</h2></div><Users /></div>
            <div class="member-list">
              <article v-for="member in members" :key="member.userId" class="member-row">
                <img :src="member.avatar" :alt="member.nickname" />
                <div><b>{{ member.nickname }}</b><small>{{ member.joinedAt ? `加入于 ${member.joinedAt.replace('T',' ').slice(0,16)}` : '小队成员' }}</small></div>
                <span :class="['role-badge', member.role]">{{ member.role }}</span>
                <select v-if="isOwner && member.role!=='队长'" :value="member.role" class="select role-select" @change="changeRole(member,$event)"><option>成员</option><option>管理员</option></select>
                <button v-if="isOwner && member.role!=='队长'" class="icon-btn" title="转让队长" @click="transferOwner(member)"><Crown /></button>
                <button v-if="canManageMembers && member.role!=='队长'" class="icon-btn danger" title="移除成员" @click="removeMember(member)"><Trash2 /></button>
              </article>
            </div>
          </section>
        </main>

        <aside>
          <section class="panel request-panel">
            <div class="panel-title"><div><small>待办事项</small><h2>加入申请</h2></div><UserCheck /></div>
            <article v-for="request in requests" :key="request.id" class="request-row"><img :src="request.avatar" /><div><b>{{ request.nickname }}</b><p>{{ request.message || '申请加入小队' }}</p><span><button @click="handleRequest(request,true)">通过</button><button @click="handleRequest(request,false)">拒绝</button></span></div></article>
            <p v-if="!requests.length" class="empty">当前没有待处理申请</p>
          </section>
          <section v-if="isOwner" class="panel danger-zone"><ShieldCheck /><h3>队长操作</h3><p>解散小队会移除全部成员关系，请谨慎操作。</p><button @click="dissolveTeam">解散小队</button></section>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.manage-page{padding:40px 0 80px}.back-link{display:flex;align-items:center;gap:6px;margin-bottom:18px;color:var(--color-ink-soft);font-size:12px}.manage-hero{min-height:210px;padding:30px;border-radius:var(--radius-xl);background:linear-gradient(110deg,#172238,#293c59);color:#fff;display:flex;align-items:center;gap:24px}.manage-hero img{width:150px;height:150px;border-radius:24px;object-fit:cover}.manage-hero div{flex:1}.manage-hero span,.panel-title small{color:var(--color-primary);font-size:10px;font-weight:900;letter-spacing:.15em}.manage-hero h1{margin:8px 0;font-size:34px}.manage-hero p{max-width:650px;color:#c6cfdb}.manage-hero i{padding:8px 12px;border-radius:999px;background:rgba(255,255,255,.12);font-style:normal;font-weight:800}.manage-grid{display:grid;grid-template-columns:minmax(0,1fr) 330px;gap:20px;margin-top:22px}.manage-grid main{display:grid;gap:20px}.panel{padding:24px;border:1px solid var(--color-line);border-radius:var(--radius-lg);background:#fff}.panel-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.panel-title h2{margin:4px 0 0}.panel-title>svg{color:var(--color-primary)}.panel label{display:flex;flex-direction:column;gap:6px;margin-top:12px;font-size:12px;font-weight:800}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 14px}.member-list{display:grid;gap:9px}.member-row{display:grid;grid-template-columns:46px minmax(140px,1fr) auto auto auto auto;align-items:center;gap:10px;padding:11px;border:1px solid var(--color-line);border-radius:12px}.member-row img,.request-row img{width:46px;height:46px;border-radius:50%;object-fit:cover;background:var(--color-bg)}.member-row b,.member-row small{display:block}.member-row small{margin-top:3px;color:var(--color-ink-soft);font-size:9px}.role-badge{padding:5px 8px;border-radius:999px;background:var(--color-bg);font-size:10px;font-weight:900}.role-badge.队长{background:#fff4d9;color:#a46b00}.role-badge.管理员{background:var(--color-primary-soft);color:var(--color-primary)}.role-select{min-width:88px}.icon-btn{width:34px;height:34px;border:1px solid var(--color-line);border-radius:9px;background:#fff;display:grid;place-items:center}.icon-btn svg{width:15px}.icon-btn.danger{color:var(--color-danger)}.request-row{display:flex;gap:10px;padding:12px 0;border-bottom:1px solid var(--color-line)}.request-row div{flex:1}.request-row p{margin:4px 0;color:var(--color-ink-soft);font-size:11px}.request-row span{display:flex;gap:6px}.request-row button,.danger-zone button{padding:7px 10px;border:0;border-radius:8px;background:var(--color-primary);color:#fff;font-size:10px;font-weight:800}.request-row button+button{background:var(--color-bg);color:var(--color-ink)}.empty,.state-card{padding:36px;text-align:center;color:var(--color-ink-soft)}.danger-zone{margin-top:20px;border-color:#ffd5da;background:#fffafb}.danger-zone>svg{color:var(--color-danger)}.danger-zone p{color:var(--color-ink-soft);font-size:11px;line-height:1.7}.danger-zone button{width:100%;background:var(--color-danger)}.alert{padding:11px 14px;border-radius:10px}.alert.error,.state-card.error{background:#fff0f2;color:var(--color-danger)}.alert.success{background:var(--color-mint-soft);color:#147d70}
@media(max-width:900px){.manage-grid{grid-template-columns:1fr}.manage-hero{align-items:flex-start}.member-row{grid-template-columns:46px 1fr auto}.member-row .role-select,.member-row .icon-btn{grid-row:2}.form-grid{grid-template-columns:1fr}}@media(max-width:600px){.manage-hero{flex-direction:column}.manage-hero img{width:100%;height:180px}.member-row{grid-template-columns:40px 1fr auto}.member-row img{width:40px;height:40px}}
</style>
