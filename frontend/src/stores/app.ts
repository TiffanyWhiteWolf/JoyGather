import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { NotificationItem } from '@/types'
import { apiGet ,apiPut} from '@/lib/api'
import type { ActivityDraft } from '@/types'
import type { SupportedCity } from '@/config/cities'
import { getCityConfig } from '@/config/cities'

export const useAppStore = defineStore('app', () => {
  const savedCity = localStorage.getItem('quju:city')
  const city = ref<SupportedCity>(getCityConfig(savedCity ?? undefined).name)
  const notificationItems = ref<NotificationItem[]>([])
  const joinedActivityIds = ref<string[]>([])
  const waitingActivityIds = ref<string[]>([])
  const teamRoles = ref<Record<string, string>>({})
  const friendIds = ref<string[]>([])
  const followedIds = ref<string[]>([])
  const blockedIds = ref<string[]>([])
  const draft = ref<ActivityDraft | null>(null)
  const submittedActivities = ref<ActivityDraft[]>([])
  const toast = ref('')
  let toastTimer = 0

  const joinedTeamIds = computed(() => Object.keys(teamRoles.value))
  const notifications = computed(() => notificationItems.value.filter(item => !item.read).length)
  const isLoggedIn = computed(() => !!localStorage.getItem('quju:token'))

  function showToast(message: string) {
    toast.value = message
    window.clearTimeout(toastTimer)
    toastTimer = window.setTimeout(() => { toast.value = '' }, 2600)
  }

  function setCity(value: SupportedCity) {
    city.value = value
    localStorage.setItem('quju:city', value)
  }

  function myTeamRole(teamId: string): string | undefined {
    return teamRoles.value[teamId]
  }

  function isTeamOwner(teamId: string): boolean {
    return teamRoles.value[teamId] === '队长'
  }

  function isTeamAdmin(teamId: string): boolean {
    const r = teamRoles.value[teamId]
    return r === '队长' || r === '管理员'
  }

  function joinActivity(id: string, full = false) {
    if (full) {
      if (!waitingActivityIds.value.includes(id)) waitingActivityIds.value.push(id)
      showToast('已加入候补队列，有名额时会第一时间通知你')
      return '候补中' as const
    }
    if (!joinedActivityIds.value.includes(id)) joinedActivityIds.value.push(id)
    waitingActivityIds.value = waitingActivityIds.value.filter(item => item !== id)
    showToast('报名成功，期待现场见！')
    return '已报名' as const
  }

  function cancelRegistration(id: string) {
    const wasWaiting = waitingActivityIds.value.includes(id)
    joinedActivityIds.value = joinedActivityIds.value.filter(item => item !== id)
    waitingActivityIds.value = waitingActivityIds.value.filter(item => item !== id)
    showToast(wasWaiting ? '已退出候补队列' : '已取消报名，名额已释放')
  }

  function joinTeam(id: string) {
    if (!joinedTeamIds.value.includes(id)) teamRoles.value[id] = '成员'
    showToast('已加入小队，群聊已同步开放')
  }

  function addFriend(id: string) {
    if (!friendIds.value.includes(id)) friendIds.value.push(id)
    if (!followedIds.value.includes(id)) followedIds.value.push(id)
  }

  function removeFriend(id: string) {
    friendIds.value = friendIds.value.filter(item => item !== id)
    followedIds.value = followedIds.value.filter(item => item !== id)
  }

  function addFollowedId(id: string) {
    if (!followedIds.value.includes(id)) followedIds.value.push(id)
  }

  function removeFollowedId(id: string) {
    followedIds.value = followedIds.value.filter(item => item !== id)
    friendIds.value = friendIds.value.filter(item => item !== id)
  }

  function addBlockedId(id: string) {
    if (!blockedIds.value.includes(id)) blockedIds.value.push(id)
  }

  function removeBlockedId(id: string) {
    blockedIds.value = blockedIds.value.filter(item => item !== id)
  }

  function saveDraft(value: ActivityDraft) {
    draft.value = { ...value, updatedAt: new Date().toLocaleString('zh-CN', { hour12: false }) }
    showToast('草稿已保存，可稍后继续编辑')
  }

  function clearDraft() {
    draft.value = null
  }

  function submitActivity(value: ActivityDraft) {
    const submitted = { ...value, updatedAt: new Date().toLocaleString('zh-CN', { hour12: false }) }
    submittedActivities.value = [submitted, ...submittedActivities.value.filter(item => item.id !== value.id)]
    clearDraft()
    showToast(value.capacity > 50 ? '已提交人工审核，可在活动管理中查看进度' : 'AI 安全审核通过，活动已发布')
  }

  function clearUserState() {
    notificationItems.value = []
    joinedActivityIds.value = []
    waitingActivityIds.value = []
    teamRoles.value = {}
    friendIds.value = []
    followedIds.value = []
    blockedIds.value = []
    draft.value = null
    submittedActivities.value = []
  }

  async function refreshUserState() {
    if (!localStorage.getItem('quju:token')) {
      clearUserState()
      return
    }
    try {
      const rows = await apiGet<Record<string, unknown>[]>('/notifications')
      notificationItems.value = rows.map(mapNotification)
    } catch {
      notificationItems.value = []
    }
    try {
      const statuses = await apiGet<Record<string, string>>('/activities/registrations/me')
      joinedActivityIds.value = Object.entries(statuses).filter(([, status]) => status === '已报名' || status === '已签到').map(([id]) => id)
      waitingActivityIds.value = Object.entries(statuses).filter(([, status]) => status === '候补中').map(([id]) => id)
    } catch {
      joinedActivityIds.value = []
      waitingActivityIds.value = []
    }
    try {
      const roles = await apiGet<Record<string, string>>('/teams/memberships/me')
      teamRoles.value = roles
    } catch {
      teamRoles.value = {}
    }
    try {
      const friends = await apiGet<{ userId: string }[]>('/friends')
      friendIds.value = friends.map(f => f.userId)
    } catch {
      friendIds.value = []
    }
    try {
      followedIds.value = await apiGet<string[]>('/follows/me')
    } catch {
      followedIds.value = []
    }
    try {
      blockedIds.value = await apiGet<string[]>('/blocks/me')
    } catch {
      blockedIds.value = []
    }
  }

  async function refreshNotifications() {
    if (!localStorage.getItem('quju:token')) {
      notificationItems.value = []
      return
    }
    try {
      const rows = await apiGet<Record<string, unknown>[]>('/notifications')
      notificationItems.value = rows.map(mapNotification)
    } catch {
      notificationItems.value = []
    }
  }

  async function markNotificationRead(id: string) {
    const target = notificationItems.value.find(item => item.id === id)
    if (!target || target.read) return
    await apiPut<void>(`/notifications/${id}/read`, {})
    target.read = true
  }

  function mapNotification(row: Record<string, unknown>): NotificationItem {
    return {
      id: String(row.id ?? ''),
      userId: String(row.user_id ?? row.userId ?? ''),
      type: String(row.type ?? ''),
      title: String(row.title ?? ''),
      content: typeof row.content === 'string' ? row.content : '',
      targetType: typeof row.target_type === 'string' ? row.target_type : (typeof row.targetType === 'string' ? row.targetType : ''),
      targetId: typeof row.target_id === 'string' ? row.target_id : (typeof row.targetId === 'string' ? row.targetId : ''),
      read: row.read_flag === 1 || row.read_flag === true || row.readFlag === 1 || row.readFlag === true,
      createdAt: typeof row.created_at === 'string' ? row.created_at : (typeof row.createdAt === 'string' ? row.createdAt : ''),
    }
  }

  const registrationCount = computed(() => joinedActivityIds.value.length)

  return {
    city, notifications, notificationItems, joinedActivityIds, waitingActivityIds, joinedTeamIds, teamRoles, friendIds, followedIds, blockedIds, draft, submittedActivities, toast,
    registrationCount, isLoggedIn, setCity, showToast, joinActivity, cancelRegistration, joinTeam, addFriend, removeFriend, addFollowedId, removeFollowedId, addBlockedId, removeBlockedId, saveDraft, clearDraft, submitActivity, clearUserState, refreshUserState,refreshNotifications, markNotificationRead,
    myTeamRole, isTeamOwner, isTeamAdmin,
  }
})
