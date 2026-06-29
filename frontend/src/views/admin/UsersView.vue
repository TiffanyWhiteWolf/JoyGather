<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Ban, CheckCircle2, Download, Search, ShieldCheck, UserCheck, UserRoundX, X } from 'lucide-vue-next'
import { useAppStore } from '@/stores/app'
import { apiGet, apiPost } from '@/lib/api'
import type { User } from '@/types'

type ManagedUser = User & { status: '正常' | '已封禁' }
const app = useAppStore()
const route = useRoute()
const users = ref<ManagedUser[]>([])
const query = ref('')
const roleFilter = ref('全部角色')
const statusFilter = ref('全部状态')
const selected = ref<ManagedUser | null>(null)
const detail = ref<ManagedUser | null>(null)
const banForm = reactive({ reason: '', until: '' })
const modalError = ref('')
const visibleUsers = computed(() => users.value.filter(user => (`${user.nickname}${user.id}`).toLowerCase().includes(query.value.toLowerCase()) && (roleFilter.value === '全部角色' || user.role === roleFilter.value) && (statusFilter.value === '全部状态' || user.status === statusFilter.value)))
const normalCount = computed(() => users.value.filter(user => user.status === '正常').length)
const merchantCount = computed(() => users.value.filter(user => user.role === '商家用户').length)
const bannedCount = computed(() => users.value.filter(user => user.status === '已封禁').length)

async function loadUsers() {
  const rows = await apiGet<User[]>('/admin/users')
  users.value = rows.map(user => ({ ...user, status: user.status ?? '正常' }))
}

function openBan(user: ManagedUser) { selected.value = user; banForm.reason = ''; banForm.until = ''; modalError.value = '' }
async function confirmBan() {
  if (!selected.value) return
  if (!banForm.reason.trim() || !banForm.until) { modalError.value = '封禁原因和封禁期限均为必填项。'; return }
  await apiPost<void>(`/admin/users/${selected.value.id}/ban`, { reason: banForm.reason, until: banForm.until, handlerId: 'admin' })
  selected.value = null
  app.showToast('用户已封禁，封禁期间将无法登录')
  await loadUsers()
}
async function unblock(user: ManagedUser) { await apiPost<void>(`/admin/users/${user.id}/unblock`, { handlerId: 'admin' }); app.showToast('用户已解封，原有账号数据保持不变'); await loadUsers() }
function exportUsers() {
  const header = ['ID', '昵称', '角色', '城市', '信用分', '状态']
  const rows = visibleUsers.value.map(user => [user.id, user.nickname, user.role, user.city, String(user.credit), user.status])
  const csv = [header, ...rows].map(row => row.map(value => `"${String(value).replace(/"/g, '""')}"`).join(',')).join('\n')
  const url = URL.createObjectURL(new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = 'quju-users.csv'
  link.click()
  URL.revokeObjectURL(url)
}
onMounted(async () => {
  query.value = String(route.query.q ?? '')
  await loadUsers()
})
</script>

<template><div><div class="admin-page-title"><div><h1>用户管理</h1><p>查询账户状态、查看活动记录并处理封禁。</p></div><button class="btn btn-outline btn-sm" @click="exportUsers"><Download :size="16" />导出数据</button></div><div class="user-summary"><div><UserCheck /><span><b>{{ normalCount }}</b><small>正常用户</small></span></div><div><ShieldCheck /><span><b>{{ merchantCount }}</b><small>认证商家</small></span></div><div><UserRoundX /><span><b>{{ bannedCount }}</b><small>封禁账户</small></span></div></div><div class="user-tools"><label><Search :size="16" /><input v-model="query" placeholder="搜索昵称或用户 ID" /></label><select v-model="roleFilter"><option>全部角色</option><option>个人用户</option><option>商家用户</option></select><select v-model="statusFilter"><option>全部状态</option><option>正常</option><option>已封禁</option></select></div><section class="admin-card user-table"><div class="user-row user-head"><span>用户</span><span>角色</span><span>城市</span><span>信用分</span><span>账号状态</span><span>操作</span></div><div v-for="user in visibleUsers" :key="user.id" class="user-row"><div class="user-cell"><img :src="user.avatar" /><span><b>{{ user.nickname }}</b><small>ID: {{ user.id }}</small></span></div><span><i :class="['role',user.role==='商家用户'?'merchant':'']">{{ user.role }}</i></span><span>{{ user.city }}</span><span><b>{{ user.credit }}</b> / 100</span><span><i :class="user.status==='正常'?'status-normal':'status-banned'">{{ user.status }}</i><small v-if="user.banUntil" class="until">至 {{ user.banUntil }}</small></span><div><button class="tiny-btn" @click="detail=user">详情</button><button v-if="user.status==='正常'" class="tiny-btn ban" @click="openBan(user)"><Ban :size="13" />封禁</button><button v-else class="tiny-btn unblock" @click="unblock(user)"><CheckCircle2 :size="13" />解封</button></div></div><div v-if="!visibleUsers.length" class="empty">没有符合筛选条件的用户</div></section>
  <div v-if="detail" class="admin-modal" @click.self="detail=null"><div><button class="close" @click="detail=null"><X /></button><h2>用户详情</h2><div class="detail-lines"><span><b>ID</b>{{ detail.id }}</span><span><b>昵称</b>{{ detail.nickname }}</span><span><b>角色</b>{{ detail.role }}</span><span><b>城市</b>{{ detail.city }}</span><span><b>状态</b>{{ detail.status }}</span><span><b>信用分</b>{{ detail.credit }}/100</span><span v-if="detail.banReason"><b>封禁原因</b>{{ detail.banReason }}</span></div></div></div>
  <div v-if="selected" class="admin-modal" @click.self="selected=null"><div><button class="close" @click="selected=null"><X /></button><h2>封禁用户</h2><p>封禁后 {{ selected.nickname }} 将无法登录，原有数据会被保留。</p><label>封禁原因 *<textarea v-model.trim="banForm.reason" placeholder="请填写明确、可追溯的原因"></textarea></label><label>封禁至 *<input v-model="banForm.until" type="date" /></label><p v-if="modalError" class="modal-error">{{ modalError }}</p><footer><button class="btn btn-outline" @click="selected=null">取消</button><button class="btn btn-dark" @click="confirmBan">确认封禁</button></footer></div></div>
</div></template>
<style scoped>.user-summary{display:grid;grid-template-columns:repeat(3,1fr);gap:15px;margin-bottom:18px}.user-summary>div{padding:18px;background:#fff;border:1px solid var(--color-line);border-radius:13px;display:flex;align-items:center;gap:12px}.user-summary svg{width:38px;height:38px;padding:9px;border-radius:10px;background:var(--color-primary-soft);color:var(--color-primary)}.user-summary span{display:flex;flex-direction:column}.user-summary b{font-size:20px}.user-summary small{color:var(--color-ink-soft);font-size:9px}.user-tools{display:flex;gap:8px;margin-bottom:14px}.user-tools label{width:330px;padding:9px 12px;background:#fff;border:1px solid var(--color-line);border-radius:8px;display:flex;align-items:center;gap:7px}.user-tools input{width:100%;border:0;outline:0}.user-tools select{padding:9px;border:1px solid var(--color-line);border-radius:8px;background:#fff}.user-table{padding:0;overflow:hidden}.user-row{display:grid;grid-template-columns:1.4fr .7fr .6fr .6fr .7fr .9fr;gap:12px;align-items:center;padding:13px 17px;border-top:1px solid var(--color-line);font-size:10px}.user-head{border:0;background:#fafafa;color:var(--color-ink-soft);font-size:9px;font-weight:800}.user-cell{display:flex;align-items:center;gap:9px}.user-cell img{width:36px;height:36px;border-radius:10px}.user-cell span{display:flex;flex-direction:column}.user-cell small{margin-top:3px;color:var(--color-ink-soft);font-size:8px}.role,.status-normal,.status-banned{padding:5px 7px;border-radius:5px;background:#edf1f5;font-style:normal}.role.merchant{background:#fff3de;color:#a36a11}.status-normal{background:var(--color-mint-soft);color:var(--color-mint)}.status-banned{background:#ffeaed;color:var(--color-danger)}.until{display:block;margin-top:5px;color:var(--color-ink-soft);font-size:8px}.user-row>div:last-child{display:flex;gap:5px}.ban{color:var(--color-danger);display:flex;align-items:center;gap:3px}.unblock{color:var(--color-mint);display:flex;align-items:center;gap:3px}.empty{padding:50px;text-align:center;color:var(--color-ink-soft);font-size:12px}.admin-modal{position:fixed;z-index:100;inset:0;padding:18px;background:rgba(13,21,34,.55);display:grid;place-items:center}.admin-modal>div{position:relative;width:min(100%,460px);padding:28px;background:#fff;border-radius:16px}.admin-modal .close{position:absolute;right:18px;top:18px;border:0;background:none}.admin-modal .close svg{width:18px}.admin-modal>div>p{color:var(--color-ink-soft);font-size:11px}.admin-modal label{display:flex;flex-direction:column;gap:6px;margin-top:14px;font-size:10px;font-weight:800}.admin-modal textarea,.admin-modal input{padding:11px;border:1px solid var(--color-line);border-radius:8px;resize:vertical}.admin-modal textarea{min-height:80px}.admin-modal footer{display:flex;justify-content:flex-end;gap:8px;margin-top:20px}.modal-error{padding:8px;background:#ffeaed;color:var(--color-danger)!important;border-radius:7px}.admin-modal .btn{padding:10px 15px;font-size:11px}@media(max-width:750px){.user-summary{grid-template-columns:1fr}.user-tools{flex-wrap:wrap}.user-tools label{width:100%}.user-head{display:none}.user-row{grid-template-columns:1fr auto}.user-row>span{display:none}}</style>
<style scoped>
.detail-lines{display:grid;gap:8px;margin-top:16px}
.detail-lines span{padding:9px;border-radius:8px;background:var(--color-bg);display:flex;justify-content:space-between;gap:16px;font-size:11px}
.detail-lines b{color:var(--color-ink-soft);font-size:10px}
</style>
