<script setup lang="ts">
import { onClickOutside } from '@vueuse/core'
import { ChevronDown, ChevronUp, File, Forward as ForwardIcon, Image, MapPin, MessageCircle, MoreHorizontal, RotateCcw, Search, Send, ShieldBan, ShieldCheck, Smile, UserCheck, UserPlus, UserRoundCheck, UserRoundMinus, UserRoundPlus, UserX, X } from 'lucide-vue-next'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiDelete, apiGet, apiPost, apiPut, apiUpload } from '@/lib/api'
import { useAppStore } from '@/stores/app'
import type { Conversation, Friend, FriendRequest, Message, User } from '@/types'

interface FileResponse {
  id: string
  url: string
  originalName: string
  contentType: string
  size: number
  provider: string
}

const emojis = [
  '😀', '😃', '😄', '😁', '😆', '😅', '😂', '🤣',
  '😊', '🙂', '🙃', '😉', '😍', '🥰', '😘', '😋',
  '😜', '🤪', '🤩', '🥳', '🤗', '🤭', '🤔', '🫡️',
  '😎', '😏', '😒', '😔', '😢', '😭', '😤', '😡',
  '👍', '👏', '🙌', '🤝', '💪', '🤞', '✌️', '🫶',
  '❤️', '🧡', '💛', '💚', '💙', '💜', '💯', '✨',
  '🎉', '🌟', '🔥', '🌈', '☕', '🎂', '🌻', '📷',
]

const router = useRouter()
const app = useAppStore()
const conversations = ref<Conversation[]>([])
const loading = ref(true)
const activeId = ref('')
const text = ref('')
const search = ref('')
const error = ref('')
const sending = ref(false)
const uploading = ref(false)
const locating = ref(false)
const emojiOpen = ref(false)
const imageInput = ref<HTMLInputElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const messageInput = ref<HTMLTextAreaElement | null>(null)
const messageList = ref<HTMLElement | null>(null)
const emojiWrapper = ref<HTMLElement | null>(null)
const fallbackConversation: Conversation = { id: '', name: '消息', avatar: 'https://i.pravatar.cc/160?img=16', type: '好友', unread: 0, lastMessage: '', lastTime: '', online: false, messages: [] }
const recalling = ref<Record<string, boolean>>({})
const forwarding = ref<Message | null>(null)
const forwardingBusy = ref(false)
let refreshTimer: number | undefined

// ---- Friends / Contacts tab ----
const activeTab = ref<'chats' | 'contacts'>('chats')
const friends = ref<Friend[]>([])
const friendRequests = ref<FriendRequest[]>([])
const friendLoading = ref(true)
const friendError = ref('')
const friendFilter = ref('')
const actionLoading = ref<Record<string, boolean>>({})
const actionMenu = ref<string | null>(null)
const menuPosition = ref({ top: 0, left: 0 })

// 添加好友弹窗
const addFriendDialog = reactive<{ show: boolean; query: string; results: User[]; loading: boolean }>({
  show: false, query: '', results: [], loading: false,
})

const filteredFriends = computed(() => {
  const q = friendFilter.value.trim().toLowerCase()
  if (!q) return friends.value
  return friends.value.filter(f => 
    f.nickname.toLowerCase().includes(q) || 
    (f.remark && f.remark.toLowerCase().includes(q)) ||
    (f.city && f.city.toLowerCase().includes(q))
  )
})

function toggleActionMenu(userId: string, event: MouseEvent) {
  if (actionMenu.value === userId) {
    actionMenu.value = null
  } else {
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
    menuPosition.value = { top: rect.bottom + 4, left: rect.right - 140 }
    actionMenu.value = userId
  }
}

// Edit friend meta
const editingFriend = ref<string | null>(null)
const editRemark = ref('')
const editGroup = ref('')

// Friend request dialog
const requestDialog = reactive<{ show: boolean; targetUserId: string; targetNickname: string; message: string; loading: boolean }>({
  show: false, targetUserId: '', targetNickname: '', message: '', loading: false,
})
const commonEmojis = ['😊', '👋', '💪', '🔥', '🎉', '🤝', '👍', '❤️', '😎', '🌟', '🙌', '📸', '🏃', '☕', '🎮']

// Confirm dialog
const confirmDialog = reactive<{ show: boolean; title: string; message: string; confirmText: string; danger?: boolean; onConfirm: (() => void) | null }>({
  show: false, title: '', message: '', confirmText: '确认', danger: false, onConfirm: null,
})

const pendingRequestCount = computed(() => friendRequests.value.filter(r => r.status === '待处理').length)
const pendingRequests = computed(() => friendRequests.value.filter(r => r.status === '待处理'))
const handledRequests = computed(() => friendRequests.value.filter(r => r.status !== '待处理'))
const showHandledRequests = ref(false)

function isBlockedC(uid: string) { return app.blockedIds.includes(uid) }

async function loadFriendsData() {
  friendLoading.value = true
  friendError.value = ''
  try {
    const [f, r] = await Promise.all([
      apiGet<Friend[]>('/friends'),
      apiGet<FriendRequest[]>('/friends/requests'),
    ])
    friends.value = f
    app.friendIds = f.map(x => x.userId)
    friendRequests.value = r
  } catch (e) {
    friendError.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    friendLoading.value = false
  }
}

function openAddFriendDialog() {
  addFriendDialog.show = true
  addFriendDialog.query = ''
  addFriendDialog.results = []
}

function closeAddFriendDialog() {
  addFriendDialog.show = false
  addFriendDialog.query = ''
  addFriendDialog.results = []
}

async function searchAddFriend() {
  const q = addFriendDialog.query.trim()
  if (!q || q.length < 2) { addFriendDialog.results = []; return }
  addFriendDialog.loading = true
  try {
    addFriendDialog.results = await apiGet<User[]>(`/users/search?q=${encodeURIComponent(q)}`)
  } catch { addFriendDialog.results = [] }
  finally { addFriendDialog.loading = false }
}

async function followUser(userId: string) {
  actionLoading.value[userId] = true
  try {
    await apiPost(`/follows/${userId}`, {})
    app.addFollowedId(userId)
    await loadFriendsData()
    await loadConversations()
    app.showToast('已关注')
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '操作失败')
  } finally { actionLoading.value[userId] = false }
}

async function unfollowUser(userId: string) {
  actionLoading.value[userId] = true
  try {
    await apiDelete(`/follows/${userId}`)
    app.removeFollowedId(userId)
    friends.value = friends.value.filter(f => f.userId !== userId)
    app.showToast('已取消关注')
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '操作失败')
  } finally { actionLoading.value[userId] = false; actionMenu.value = null }
}

async function blockUser(userId: string) {
  try {
    await apiPost(`/blocks/${userId}`, { reason: '' })
    app.removeFollowedId(userId)
    app.addBlockedId(userId)
    friends.value = friends.value.filter(f => f.userId !== userId)
    app.showToast('已拉黑')
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '操作失败')
  }
  actionMenu.value = null
}

async function unblockUser(userId: string) {
  actionLoading.value[userId] = true
  try {
    await apiDelete(`/blocks/${userId}`)
    app.removeBlockedId(userId)
    app.showToast('已取消拉黑')
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '操作失败')
  } finally { actionLoading.value[userId] = false }
}

function openRequestDialog(user: User) {
  requestDialog.show = true
  requestDialog.targetUserId = user.id
  requestDialog.targetNickname = user.nickname
  requestDialog.message = ''
  requestDialog.loading = false
}

function closeRequestDialog() { requestDialog.show = false; requestDialog.message = '' }

function insertEmojiC(emoji: string) { requestDialog.message += emoji }

async function sendFriendRequest() {
  if (!requestDialog.message.trim()) { app.showToast('请说点什么吧'); return }
  if (requestDialog.message.length > 100) { app.showToast('留言不能超过100字'); return }
  requestDialog.loading = true
  try {
    await apiPost('/friends/requests', { userId: requestDialog.targetUserId, source: 'PROFILE', message: requestDialog.message.trim() })
    app.addFollowedId(requestDialog.targetUserId)
    await loadFriendsData()
    app.showToast('已发送好友申请并关注对方')
    closeRequestDialog()
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '发送失败')
  } finally { requestDialog.loading = false }
}

async function approveRequest(requestId: string) {
  try {
    await apiPost(`/friends/requests/${requestId}/approve`, {})
    await loadFriendsData()
    await loadConversations()
    await app.refreshUserState()
    app.showToast('已通过申请，成为好友')
  } catch (e) { app.showToast(e instanceof Error ? e.message : '操作失败') }
}

async function rejectRequest(requestId: string) {
  try {
    await apiPost(`/friends/requests/${requestId}/reject`, {})
    friendRequests.value = friendRequests.value.filter(r => r.id !== requestId)
    app.showToast('已拒绝申请')
  } catch (e) { app.showToast(e instanceof Error ? e.message : '操作失败') }
}

async function saveEdit(friendId: string) {
  try {
    await apiPut(`/friends/${friendId}`, { remark: editRemark.value, groupName: editGroup.value })
    const f = friends.value.find(ff => ff.userId === friendId)
    if (f) { f.remark = editRemark.value; f.groupName = editGroup.value }
    app.showToast('备注已更新')
    editingFriend.value = null
  } catch (e) { app.showToast(e instanceof Error ? e.message : '更新失败') }
}

function startChatFromFriend(friend: Friend) {
  // Find conversation with this friend and select it
  const conv = conversations.value.find(c => c.type === '好友' && c.friendUserId === friend.userId)
  if (conv) {
    selectConversation(conv.id)
    activeTab.value = 'chats'
  } else {
    app.showToast('暂无会话，互相关注后会自动创建')
  }
}

function showConfirm(title: string, message: string, onConfirm: () => void, confirmText = '确认', danger = true) {
  confirmDialog.show = true
  confirmDialog.title = title
  confirmDialog.message = message
  confirmDialog.confirmText = confirmText
  confirmDialog.danger = danger
  confirmDialog.onConfirm = onConfirm
}
function closeConfirm() { confirmDialog.show = false; confirmDialog.onConfirm = null }
function doConfirm() { if (confirmDialog.onConfirm) confirmDialog.onConfirm(); closeConfirm() }

function closeActionMenu() { actionMenu.value = null }

function isSelfC(uid: string) {
  try { return JSON.parse(localStorage.getItem('quju:session') || '{}').id === uid } catch { return false }
}

// Load friends when switching to contacts tab
async function switchTab(tab: 'chats' | 'contacts') {
  activeTab.value = tab
  if (tab === 'contacts') {
    await loadFriendsData()
    await app.refreshUserState()
  }
}

const filteredConversations = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) return conversations.value
  return conversations.value.filter(item => `${item.name}${item.lastMessage}`.toLowerCase().includes(keyword))
})
const active = computed(() => conversations.value.find(c => c.id === activeId.value) ?? conversations.value[0] ?? fallbackConversation)
const forwardTargets = computed(() => conversations.value.filter(item => item.id && item.id !== active.value.id && (item.type !== '好友' || !item.friendUserId || app.friendIds.includes(item.friendUserId))))

onClickOutside(emojiWrapper, () => { emojiOpen.value = false })

async function scrollToBottom() {
  await nextTick()
  if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
}

async function loadConversations() {
  loading.value = true
  error.value = ''
  try {
    await app.refreshUserState()
    conversations.value = await apiGet<Conversation[]>('/conversations')
    if (!activeId.value && conversations.value.length) activeId.value = conversations.value[0].id
    markIncomingMessagesRead(conversations.value.find(c => c.id === activeId.value))
    await scrollToBottom()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '请先登录后查看消息'
  } finally {
    loading.value = false
  }
}

async function refreshConversationsSilently() {
  if (loading.value || !localStorage.getItem('quju:token')) return
  const beforeCount = conversations.value.find(c => c.id === activeId.value)?.messages.length ?? 0
  try {
    const rows = await apiGet<Conversation[]>('/conversations')
    conversations.value = rows
    if (!activeId.value && rows.length) activeId.value = rows[0].id
    if (activeId.value && rows.length && !rows.some(item => item.id === activeId.value)) activeId.value = rows[0].id
    const current = rows.find(c => c.id === activeId.value)
    markIncomingMessagesRead(current)
    const afterCount = current?.messages.length ?? 0
    if (afterCount > beforeCount) await scrollToBottom()
  } catch {
    // 静默刷新失败不打断用户正在输入的消息。
  }
}

function selectConversation(id: string) {
  activeId.value = id
  emojiOpen.value = false
  const conv = conversations.value.find(c => c.id === id)
  if (conv && conv.unread > 0) {
    conv.unread = 0
    apiPost(`/conversations/${id}/read`, {}).catch(() => {})
  }
  markIncomingMessagesRead(conv)
  void scrollToBottom()
}

function markIncomingMessagesRead(conversation?: Conversation) {
  if (!conversation) return
  conversation.messages
    .filter(message => !message.mine && !message.read && !message.recalled)
    .forEach(message => {
      message.read = true
      apiPost(`/messages/${message.id}/read`, {}).catch(() => { message.read = false })
    })
}

async function togglePin(conversationId: string) {
  try {
    await apiPost(`/conversations/${conversationId}/pin`, {})
    const conv = conversations.value.find(c => c.id === conversationId)
    if (conv) conv.pinned = !conv.pinned
    // Re-sort: pinned first
    conversations.value.sort((a, b) => {
      if (a.pinned !== b.pinned) return a.pinned ? -1 : 1
      return 0
    })
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '操作失败')
  }
}

async function toggleMute(conversationId: string) {
  try {
    await apiPost(`/conversations/${conversationId}/mute`, {})
    const conv = conversations.value.find(c => c.id === conversationId)
    if (conv) conv.muted = !conv.muted
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '操作失败')
  }
}

const canSend = computed(() => {
  if (!active.value.id) return false
  if (active.value.type !== '好友') return true
  // 好友会话需要检查是否仍是好友
  const fid = active.value.friendUserId
  if (!fid) return true
  if (app.friendIds.includes(fid)) return true
  // 非好友：最多 5 条消息
  const count = active.value.nonFriendMessageCount ?? 0
  return count < 5
})

const sendLimitHint = computed(() => {
  if (!active.value.id || active.value.type !== '好友') return ''
  const fid = active.value.friendUserId
  if (!fid || app.friendIds.includes(fid)) return ''
  const count = active.value.nonFriendMessageCount ?? 0
  if (count >= 5) return '你们还不是好友，已发送 5/5 条消息，不能发送了'
  return `你们还不是好友，已发送 ${count}/5 条消息`
})

function appendMessage(created: Message, conversationId: string) {
  const target = conversations.value.find(item => item.id === conversationId)
  if (!target) return
  target.messages.push(created)
  target.lastMessage = created.type === 'IMAGE' ? '[图片]' : created.type === 'FILE' ? '[文件]' : created.type === 'LOCATION' ? '[位置]' : created.content
  target.lastTime = created.time
  // 非好友会话：发送后立即递增计数
  if (target.type === '好友' && target.friendUserId && !app.friendIds.includes(target.friendUserId)) {
    target.nonFriendMessageCount = (target.nonFriendMessageCount ?? 0) + 1
  }
  void scrollToBottom()
}

function canRecall(message: Message) {
  if (!message.mine || message.recalled || !message.sentAt) return false
  const sent = new Date(message.sentAt).getTime()
  if (Number.isNaN(sent)) return false
  return Date.now() - sent <= 2 * 60 * 1000
}

async function recallMessage(message: Message) {
  if (!canRecall(message) || recalling.value[message.id]) return
  recalling.value[message.id] = true
  try {
    await apiPost(`/messages/${message.id}/recall`, {})
    message.recalled = true
    message.content = '消息已撤回'
    const lastMessage = active.value.messages[active.value.messages.length - 1]
    if (lastMessage?.id === message.id) active.value.lastMessage = '消息已撤回'
    app.showToast('消息已撤回')
  } catch (err) {
    app.showToast(err instanceof Error ? err.message : '撤回失败')
  } finally {
    recalling.value[message.id] = false
  }
}

function openForward(message: Message) {
  if (message.recalled) return
  forwarding.value = message
}

function closeForward() {
  if (forwardingBusy.value) return
  forwarding.value = null
}

async function forwardMessage(targetConversationId: string) {
  if (!forwarding.value || forwardingBusy.value) return
  forwardingBusy.value = true
  try {
    const created = await apiPost<Message>(`/messages/${forwarding.value.id}/forward`, { conversationId: targetConversationId })
    appendMessage(created, targetConversationId)
    app.showToast('消息已转发')
    closeForward()
  } catch (err) {
    app.showToast(err instanceof Error ? err.message : '转发失败')
  } finally {
    forwardingBusy.value = false
  }
}

async function send() {
  const content = text.value.trim()
  const conversationId = active.value.id
  if (!content || !conversationId || sending.value) return
  sending.value = true
  error.value = ''
  try {
    const created = await apiPost<Message>(`/conversations/${conversationId}/messages`, { type: 'TEXT', content })
    appendMessage(created, conversationId)
    text.value = ''
  } catch (err) {
    const message = err instanceof Error ? err.message : '消息发送失败'
    error.value = message
    app.showToast(message)
  } finally {
    sending.value = false
    await nextTick()
    messageInput.value?.focus()
  }
}

function handleEnter(event: KeyboardEvent) {
  if (event.shiftKey) return
  event.preventDefault()
  void send()
}

function insertEmoji(emoji: string) {
  const input = messageInput.value
  const start = input?.selectionStart ?? text.value.length
  const end = input?.selectionEnd ?? start
  text.value = `${text.value.slice(0, start)}${emoji}${text.value.slice(end)}`
  void nextTick(() => {
    const position = start + emoji.length
    input?.focus()
    input?.setSelectionRange(position, position)
  })
}

async function sendLocation() {
  const conversationId = active.value.id
  if (!conversationId || locating.value) return
  if (!navigator.geolocation) {
    app.showToast('当前浏览器不支持定位')
    return
  }
  locating.value = true
  navigator.geolocation.getCurrentPosition(async position => {
    const latitude = position.coords.latitude
    const longitude = position.coords.longitude
    try {
      const created = await apiPost<Message>(`/conversations/${conversationId}/messages`, {
        type: 'LOCATION',
        content: `我的位置：${latitude.toFixed(5)}, ${longitude.toFixed(5)}`,
        latitude,
        longitude,
      })
      appendMessage(created, conversationId)
      app.showToast('位置已发送')
    } catch (err) {
      app.showToast(err instanceof Error ? err.message : '位置发送失败')
    } finally {
      locating.value = false
    }
  }, err => {
    locating.value = false
    app.showToast(err.code === err.PERMISSION_DENIED ? '未获得定位权限' : '暂时无法获取位置')
  }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 })
}

async function uploadAndSend(event: Event, type: 'IMAGE' | 'FILE') {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  const conversationId = active.value.id
  if (!file || !conversationId || uploading.value) return
  const form = new FormData()
  form.append('file', file)
  uploading.value = true
  try {
    const uploaded = await apiUpload<FileResponse>('/files/upload', form)
    const created = await apiPost<Message>(`/conversations/${conversationId}/messages`, {
      type,
      content: type === 'IMAGE' ? file.name : uploaded.originalName,
      mediaUrl: uploaded.url,
    })
    appendMessage(created, conversationId)
    app.showToast(type === 'IMAGE' ? '图片已发送' : '文件已发送')
  } catch (err) {
    app.showToast(err instanceof Error ? err.message : '发送失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

function locationHref(message: Message) {
  if (message.latitude == null || message.longitude == null) return '#'
  const position = `${message.longitude},${message.latitude}`
  return `https://uri.amap.com/marker?position=${encodeURIComponent(position)}&name=${encodeURIComponent('我的位置')}`
}

function openInfo() {
  app.showToast(active.value.type === '小队' ? '小队公告、文件和相册入口已在小队页提供' : '好友备注和资料可在好友页管理')
}

function openProfile() {
  if (active.value.type === '好友' && active.value.friendUserId) {
    void router.push(`/profile/${active.value.friendUserId}`)
  } else {
    void router.push('/teams')
  }
}

function goToUser(item: Conversation) {
  if (item.type === '好友' && item.friendUserId) {
    void router.push(`/profile/${item.friendUserId}`)
  }
}

onMounted(() => {
  void loadConversations()
  refreshTimer = window.setInterval(refreshConversationsSilently, 5000)
})

onBeforeUnmount(() => {
  if (refreshTimer !== undefined) window.clearInterval(refreshTimer)
})
</script>

<template>
  <div class="container messages-page">
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <input ref="imageInput" class="hidden-file" type="file" accept="image/*" @change="uploadAndSend($event, 'IMAGE')" />
    <input ref="fileInput" class="hidden-file" type="file" @change="uploadAndSend($event, 'FILE')" />

    <div class="chat-shell">
      <aside class="conversation-list">
        <div class="tab-bar">
          <button :class="{ active: activeTab === 'chats' }" @click="switchTab('chats')">消息</button>
          <button :class="{ active: activeTab === 'contacts' }" @click="switchTab('contacts')">
            通讯录
            <span v-if="pendingRequestCount" class="tab-badge">{{ pendingRequestCount }}</span>
          </button>
        </div>

        <!-- Chats tab -->
        <template v-if="activeTab === 'chats'">
        <div class="chat-search"><Search :size="16" /><input v-model="search" placeholder="搜索联系人或小队" /></div>
        <div class="conversations">
          <button v-for="item in filteredConversations" :key="item.id" type="button" :class="{ active: item.id === activeId, pinned: item.pinned }" @click="selectConversation(item.id)">
            <span class="avatar" @click.stop="goToUser(item)"><img :src="item.avatar" :alt="item.name" /><i v-if="item.online"></i></span>
            <span class="conversation-copy"><b>{{ item.name }}<small>{{ item.lastTime }}</small></b><em>{{ item.lastMessage }}</em></span>
            <strong v-if="item.unread" :class="{ muted: item.muted }">{{ item.muted ? '' : item.unread }}</strong>
          </button>
          <p v-if="!filteredConversations.length" class="empty-conversations">暂无可用会话</p>
        </div>
        </template>

        <!-- Contacts tab -->
        <div v-if="activeTab === 'contacts'" class="contacts-panel" @click="closeActionMenu">
          <div class="contact-search"><Search :size="16" /><input v-model="friendFilter" placeholder="搜索好友" /><X v-if="friendFilter" :size="14" class="clear-btn" @click.stop="friendFilter=''" /><button class="add-friend-btn" title="添加好友" @click.stop="openAddFriendDialog"><UserPlus :size="16" /></button></div>

          <div v-if="friendRequests.length" class="request-section">
            <div class="section-label">好友申请 <span v-if="pendingRequestCount" class="count">{{ pendingRequestCount }}</span></div>
            <div v-for="req in pendingRequests" :key="req.id" class="request-item">
              <img :src="req.requesterAvatar" :alt="req.requesterNickname" />
              <div class="request-info">
                <b>{{ req.requesterNickname }}</b>
                <p v-if="req.message" class="request-msg">"{{ req.message }}"</p>
              </div>
              <div class="request-actions">
                <button class="btn btn-xs btn-primary-text" @click="approveRequest(req.id)">通过</button>
                <button class="btn btn-xs" @click="rejectRequest(req.id)">拒绝</button>
              </div>
            </div>
            <div v-if="handledRequests.length" class="handled-toggle" @click="showHandledRequests = !showHandledRequests">
              <ChevronDown v-if="!showHandledRequests" :size="14" />
              <ChevronUp v-else :size="14" />
              {{ showHandledRequests ? '收起' : `已处理 (${handledRequests.length})` }}
            </div>
            <template v-if="showHandledRequests">
              <div v-for="req in handledRequests" :key="req.id" class="request-item handled">
                <img :src="req.requesterAvatar" :alt="req.requesterNickname" />
                <div class="request-info">
                  <b>{{ req.requesterNickname }}</b>
                </div>
                <span class="state-tag">{{ req.status }}</span>
              </div>
            </template>
          </div>

          <p v-if="friendError" class="form-error">{{ friendError }}</p>
          <div v-if="friendLoading" class="loading-dot">加载中...</div>
          <div v-else-if="!friends.length" class="empty-contacts">
            <UserRoundCheck :size="32" /><p>暂无好友</p><span>点击右上角 + 添加好友</span>
          </div>
          <div v-else class="friend-list-compact">
            <div class="section-label">我的好友</div>
            <div v-for="friend in filteredFriends" :key="friend.userId" class="friend-row" @click="startChatFromFriend(friend)">
              <img :src="friend.avatar" :alt="friend.nickname" />
              <div class="friend-info">
                <b>{{ friend.nickname }}<span v-if="friend.remark" class="remark-tag">{{ friend.remark }}</span></b>
                <span>{{ friend.city }}<template v-if="friend.groupName"> · {{ friend.groupName }}</template></span>
              </div>
              <div class="friend-actions" @click.stop>
                <button class="btn btn-xs" @click.stop="startChatFromFriend(friend)"><MessageCircle :size="12" />发消息</button>
                <button class="icon-btn-sm" @click="toggleActionMenu(friend.userId, $event)"><MoreHorizontal :size="14" /></button>
                <Teleport to="body">
                  <div v-if="actionMenu === friend.userId" class="action-dropdown-sm" :style="{ position: 'fixed', top: menuPosition.top + 'px', left: menuPosition.left + 'px' }">
                    <button @click="editingFriend = friend.userId; editRemark = friend.remark || ''; editGroup = friend.groupName || ''; actionMenu = null">修改备注</button>
                    <button class="danger" @click="unfollowUser(friend.userId)">取消关注</button>
                    <button class="danger" @click="blockUser(friend.userId)">拉黑</button>
                  </div>
                </Teleport>
              </div>
            </div>
          </div>
        </div>
      </aside>

      <main class="chat-main">
        <header>
          <div>
            <img :src="active.avatar" :alt="active.name" style="cursor:pointer" @click="goToUser(active)" />
            <span><b>{{ active.name }}</b><small>{{ active.type === '小队' ? '小队群聊' : '好友会话' }}</small></span>
          </div>
          <button type="button" aria-label="聊天详情" @click="openInfo"><MoreHorizontal /></button>
        </header>

        <div ref="messageList" class="messages">
          <div class="date-divider">今天</div>
          <div v-if="active.type === '小队'" class="announcement">群公告会在这里展示。</div>
          <div v-for="message in active.messages" :key="message.id" :class="['message', { mine: message.mine }]">
            <img v-if="!message.mine" :src="message.senderAvatar || active.avatar" alt="发送者头像" style="cursor:pointer" @click="router.push(`/profile/${message.senderId}`)" />
            <div class="message-body">
              <p v-if="message.recalled" class="message-bubble recalled">消息已撤回</p>
              <a v-else-if="message.type === 'IMAGE' && message.mediaUrl" class="image-message" :href="message.mediaUrl" target="_blank" rel="noopener">
                <img :src="message.mediaUrl" :alt="message.content || '聊天图片'" />
              </a>
              <a v-else-if="message.type === 'FILE' && message.mediaUrl" class="file-message" :href="message.mediaUrl" target="_blank" rel="noopener">
                <File :size="21" /><span><b>{{ message.content || '聊天文件' }}</b><small>点击查看文件</small></span>
              </a>
              <a v-else-if="message.type === 'LOCATION' && message.latitude != null && message.longitude != null" class="location-message" :href="locationHref(message)" target="_blank" rel="noopener">
                <MapPin :size="22" /><span><b>我的位置</b><small>{{ message.latitude.toFixed(5) }}, {{ message.longitude.toFixed(5) }}</small></span>
              </a>
              <p v-else class="message-bubble">{{ message.content }}</p>
              <div v-if="!message.recalled" class="message-actions">
                <button v-if="canRecall(message)" type="button" :disabled="recalling[message.id]" title="撤回" @click="recallMessage(message)"><RotateCcw :size="12" />撤回</button>
                <button type="button" title="转发" @click="openForward(message)"><ForwardIcon :size="12" />转发</button>
              </div>
              <span class="message-meta">{{ message.time }} {{ message.mine ? (message.read ? '已读' : '未读') : '' }}</span>
            </div>
          </div>
          <div v-if="!active.messages.length" class="no-messages">{{ active.id ? '还没有消息，来打个招呼吧' : '暂无可用会话' }}</div>
        </div>

        <div v-if="!active.id" class="conversation-required">
          <template v-if="loading"><span class="loading-dot"></span><b>正在为你准备会话…</b></template>
          <template v-else><b>还没有可用会话</b><span>点击重试，系统会为新用户建立“趣聚小助手”会话。</span><button type="button" @click="loadConversations">重试加载</button></template>
        </div>
        <div v-else class="composer">
          <div v-if="sendLimitHint" class="send-limit-hint">{{ sendLimitHint }}</div>
          <div class="compose-tools">
            <div ref="emojiWrapper" class="emoji-wrapper">
              <button type="button" aria-label="选择表情" :class="{ active: emojiOpen }" :disabled="!active.id || !canSend" @click="emojiOpen = !emojiOpen"><Smile :size="19" /></button>
              <div v-if="emojiOpen" class="emoji-picker" role="dialog" aria-label="表情列表">
                <div class="emoji-picker-title"><b>选择表情</b><span>{{ emojis.length }} 个</span></div>
                <div class="emoji-grid"><button v-for="emoji in emojis" :key="emoji" type="button" :aria-label="`插入表情 ${emoji}`" @click="insertEmoji(emoji)">{{ emoji }}</button></div>
              </div>
            </div>
            <button type="button" aria-label="发送图片" :disabled="!active.id || uploading || !canSend" @click="imageInput?.click()"><Image :size="19" /></button>
            <button type="button" aria-label="发送文件" :disabled="!active.id || uploading || !canSend" @click="fileInput?.click()"><File :size="19" /></button>
            <button type="button" aria-label="发送位置" :disabled="!active.id || locating || !canSend" @click="sendLocation"><MapPin :size="19" /></button>
            <span v-if="uploading" class="tool-status">正在上传…</span>
            <span v-else-if="locating" class="tool-status">正在定位…</span>
          </div>
          <div class="compose-input">
            <textarea ref="messageInput" v-model="text" :disabled="!active.id || sending || !canSend" :placeholder="canSend ? (sendLimitHint || '输入消息，Enter 发送，Shift + Enter 换行') : '你们还不是好友，无法发送消息'" @keydown.enter="handleEnter"></textarea>
            <button type="button" aria-label="发送消息" :disabled="!text.trim() || !active.id || sending || !canSend" @click="send"><Send :size="18" /></button>
          </div>
        </div>
      </main>

      <aside class="chat-info">
        <img :src="active.avatar" :alt="active.name" /><h3>{{ active.name }}</h3>
        <p>{{ active.type === '小队' ? '小队成员可以发送文字、图片、文件和位置。' : '好友之间可以即时沟通。' }}</p>
        <div><span>消息免打扰 <input type="checkbox" :checked="active.muted" @change="toggleMute(active.id)" /></span><span>置顶会话 <input type="checkbox" :checked="active.pinned" @change="togglePin(active.id)" /></span></div>
        <button type="button" @click="openProfile">查看{{ active.type === '小队' ? '小队主页' : '个人主页' }}</button>
      </aside>
    </div>

    <!-- Edit Friend Modal -->
    <div v-if="editingFriend" class="modal-mask" @click.self="editingFriend = null">
      <div class="edit-modal">
        <h3>修改备注</h3>
        <label>备注名<input v-model="editRemark" placeholder="给好友起个备注" maxlength="20" /></label>
        <label>分组<input v-model="editGroup" placeholder="例如：摄影组、徒步搭子" maxlength="20" /></label>
        <div class="modal-actions"><button class="btn btn-outline btn-sm" @click="editingFriend = null">取消</button><button class="btn btn-primary btn-sm" @click="saveEdit(editingFriend!)">保存</button></div>
      </div>
    </div>

    <!-- Friend Request Dialog -->
    <div v-if="requestDialog.show" class="modal-mask" @click.self="closeRequestDialog">
      <div class="request-modal">
        <h3>申请添加 {{ requestDialog.targetNickname }} 为好友</h3>
        <p class="request-hint">发送申请后将自动关注对方。</p>
        <div class="message-area"><textarea v-model="requestDialog.message" placeholder="说点什么吧..." maxlength="100" rows="3"></textarea><span class="char-count">{{ requestDialog.message.length }}/100</span></div>
        <div class="emoji-row"><button v-for="e in commonEmojis" :key="e" class="emoji-btn" @click="insertEmojiC(e)">{{ e }}</button></div>
        <div class="modal-actions"><button class="btn btn-outline btn-sm" @click="closeRequestDialog" :disabled="requestDialog.loading">取消</button><button class="btn btn-primary btn-sm" @click="sendFriendRequest" :disabled="requestDialog.loading || !requestDialog.message.trim()">{{ requestDialog.loading ? '发送中...' : '发送申请' }}</button></div>
      </div>
    </div>

    <!-- Confirm Dialog -->
    <div v-if="confirmDialog.show" class="modal-mask" @click.self="closeConfirm">
      <div class="confirm-modal">
        <h3>{{ confirmDialog.title }}</h3><p>{{ confirmDialog.message }}</p>
        <div class="modal-actions"><button class="btn btn-outline btn-sm" @click="closeConfirm">取消</button><button :class="['btn btn-sm', confirmDialog.danger ? 'btn-dark' : 'btn-primary']" @click="doConfirm">{{ confirmDialog.confirmText }}</button></div>
      </div>
    </div>

    <!-- Forward Message Dialog -->
    <div v-if="forwarding" class="modal-mask" @click.self="closeForward">
      <div class="forward-modal">
        <h3>转发消息</h3>
        <p class="request-hint">选择一个好友或小队会话。</p>
        <div class="forward-list">
          <button v-for="item in forwardTargets" :key="item.id" type="button" :disabled="forwardingBusy" @click="forwardMessage(item.id)">
            <img :src="item.avatar" :alt="item.name" />
            <span><b>{{ item.name }}</b><small>{{ item.type === '小队' ? '小队群聊' : '好友会话' }}</small></span>
          </button>
          <p v-if="!forwardTargets.length" class="empty-forward">暂无可转发的会话。</p>
        </div>
        <div class="modal-actions"><button class="btn btn-outline btn-sm" :disabled="forwardingBusy" @click="closeForward">取消</button></div>
      </div>
    </div>

    <!-- Add Friend Dialog -->
    <div v-if="addFriendDialog.show" class="modal-mask" @click.self="closeAddFriendDialog">
      <div class="add-friend-modal">
        <h3>添加好友</h3>
        <div class="add-friend-search"><Search :size="16" /><input v-model="addFriendDialog.query" placeholder="输入用户名搜索" @input="searchAddFriend" /><X v-if="addFriendDialog.query" :size="14" class="clear-btn" @click.stop="addFriendDialog.query='';addFriendDialog.results=[]" /></div>
        <div v-if="addFriendDialog.loading" class="loading-dot">搜索中...</div>
        <div v-else-if="addFriendDialog.results.length" class="add-friend-results">
          <div v-for="user in addFriendDialog.results" :key="user.id" class="add-friend-item">
            <img :src="user.avatar" :alt="user.nickname" />
            <div class="result-info"><b>{{ user.nickname }}</b><span>{{ user.city }}</span></div>
            <template v-if="isSelfC(user.id)"><span class="state-tag">我自己</span></template>
            <template v-else-if="isBlockedC(user.id)">
              <span class="state-tag blocked">已拉黑</span>
              <button class="btn btn-xs unblock-btn" :disabled="actionLoading[user.id]" @click.stop="unblockUser(user.id)">取消拉黑</button>
            </template>
            <template v-else-if="app.friendIds.includes(user.id)">
              <button class="btn btn-mint btn-xs" disabled>已是好友</button>
            </template>
            <template v-else-if="app.followedIds.includes(user.id)">
              <button class="btn btn-xs" @click.stop="openRequestDialog(user);closeAddFriendDialog()">申请好友</button>
            </template>
            <template v-else>
              <button class="btn btn-xs btn-primary-text" :disabled="actionLoading[user.id]" @click.stop="followUser(user.id);closeAddFriendDialog()">关注</button>
              <button class="btn btn-xs" :disabled="actionLoading[user.id]" @click.stop="openRequestDialog(user);closeAddFriendDialog()">申请好友</button>
            </template>
          </div>
        </div>
        <p v-else-if="addFriendDialog.query.length >= 2" class="add-friend-empty">未找到匹配的用户</p>
        <div class="modal-actions"><button class="btn btn-outline btn-sm" @click="closeAddFriendDialog">关闭</button></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.messages-page{padding:30px 0 60px}.hidden-file{display:none}.chat-shell{height:clamp(520px,calc(100vh - 150px),720px);overflow:hidden;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-lg);box-shadow:var(--shadow-soft);display:grid;grid-template-columns:290px minmax(0,1fr) 230px}.conversation-list{min-height:0;overflow:hidden;border-right:1px solid var(--color-line);display:flex;flex-direction:column}button{cursor:pointer}button:disabled{cursor:not-allowed}.chat-title{height:70px;flex:0 0 70px;padding:0 20px;display:flex;align-items:center;justify-content:space-between}.chat-title h1{margin:0;font-size:24px}.chat-title button,.chat-main header button{border:0;background:none}.chat-search{margin:10px 14px 12px;padding:9px 11px;background:var(--color-bg);border-radius:9px;display:flex;align-items:center;gap:7px}.chat-search input{min-width:0;flex:1;border:0;background:none;outline:0;font-size:11px}.conversations{min-height:0;overflow-y:auto;overscroll-behavior:contain}.conversations button{position:relative;width:100%;padding:13px 14px;border:0;background:#fff;display:flex;align-items:center;gap:10px;text-align:left}.conversations button.active{background:var(--color-primary-soft)}.conversations button.pinned{background:#f5f3f0}.conversations button.active.pinned{background:var(--color-primary-soft)}.avatar{position:relative}.avatar img{width:44px;height:44px;border-radius:13px;object-fit:cover}.avatar i{position:absolute;right:-1px;bottom:-1px;width:10px;height:10px;border:2px solid #fff;border-radius:50%;background:var(--color-mint)}.conversation-copy{min-width:0;flex:1}.conversation-copy b{display:flex;justify-content:space-between;font-size:12px}.conversation-copy small{color:var(--color-ink-soft);font-size:8px;font-weight:400}.conversation-copy em{display:block;margin-top:6px;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;color:var(--color-ink-soft);font-size:9px;font-style:normal}.conversations strong{position:absolute;right:14px;bottom:11px;min-width:16px;height:16px;padding:0 4px;border-radius:8px;background:var(--color-primary);color:#fff;font-size:8px;display:grid;place-items:center}.conversations strong.muted{min-width:8px;width:8px;height:8px;padding:0;border-radius:50%;background:rgba(245,170,110,.5);font-size:0}.empty-conversations{padding:30px 14px;text-align:center;color:var(--color-ink-soft);font-size:11px}.chat-main{min-width:0;min-height:0;overflow:hidden;background:#faf9f7;display:grid;grid-template-rows:70px minmax(0,1fr) auto}.chat-main header{height:70px;padding:0 20px;border-bottom:1px solid var(--color-line);background:#fff;display:flex;align-items:center;justify-content:space-between}.chat-main header>div{display:flex;align-items:center;gap:10px}.chat-main header img{width:38px;height:38px;border-radius:10px;object-fit:cover}.chat-main header span{display:flex;flex-direction:column;font-size:12px}.chat-main header small{margin-top:4px;color:var(--color-mint);font-size:8px}.messages{min-height:0;padding:18px 26px;overflow-x:hidden;overflow-y:auto;overscroll-behavior:contain;scrollbar-gutter:stable;scroll-behavior:smooth}.date-divider{text-align:center;color:var(--color-ink-soft);font-size:8px}.announcement{margin:14px auto 25px;padding:9px 12px;width:max-content;max-width:90%;border-radius:8px;background:#ece9e3;color:var(--color-ink-soft);font-size:9px}.message{margin:16px 0;display:flex;gap:8px}.message>img{width:30px;height:30px;border-radius:9px}.message-body{max-width:70%}.message-bubble{margin:0;padding:10px 13px;border-radius:4px 13px 13px 13px;background:#fff;box-shadow:0 3px 12px rgba(23,34,56,.06);font-size:12px;line-height:1.6;white-space:pre-wrap;overflow-wrap:anywhere}.message-meta{display:block;margin-top:4px;color:#a4a8b0;font-size:8px}.message-actions{display:flex;gap:5px;margin-top:5px;opacity:0;transition:opacity .15s}.message:hover .message-actions,.message:focus-within .message-actions{opacity:1}.message-actions button{height:22px;padding:0 7px;border:1px solid var(--color-line);border-radius:7px;background:#fff;color:var(--color-ink-soft);display:flex;align-items:center;gap:3px;font-size:9px}.message-actions button:hover{color:var(--color-primary);border-color:var(--color-primary-soft);background:var(--color-primary-soft)}.message.mine{justify-content:flex-end;text-align:right}.message.mine .message-actions{justify-content:flex-end}.message.mine .message-bubble{background:var(--color-primary);color:#fff;border-radius:13px 4px 13px 13px;text-align:left}.message-bubble.recalled{background:#ece9e3!important;color:var(--color-ink-soft)!important;font-style:italic}.image-message{display:block;overflow:hidden;max-width:280px;border-radius:13px;background:#fff;box-shadow:0 3px 12px rgba(23,34,56,.1)}.image-message img{display:block;width:100%;max-height:280px;object-fit:cover}.file-message,.location-message{min-width:210px;padding:12px 14px;border-radius:13px;background:#fff;color:var(--color-ink);box-shadow:0 3px 12px rgba(23,34,56,.08);display:flex;align-items:center;gap:10px;text-align:left;text-decoration:none}.message.mine .file-message,.message.mine .location-message{background:var(--color-primary-soft)}.file-message>span,.location-message>span{min-width:0;display:flex;flex-direction:column}.file-message b{max-width:210px;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;font-size:11px}.file-message small,.location-message small{margin-top:4px;color:var(--color-ink-soft);font-size:8px}.location-message b{font-size:11px}.no-messages{text-align:center;margin-top:120px;color:var(--color-ink-soft);font-size:12px}.composer{position:relative;z-index:2;min-height:114px;box-sizing:border-box;padding:12px 18px 18px;border-top:1px solid var(--color-line);background:#fff}.conversation-required{min-height:114px;padding:18px;border-top:1px solid var(--color-line);background:#fff;display:flex;align-items:center;justify-content:center;gap:9px;color:var(--color-ink-soft);font-size:11px;text-align:center}.conversation-required b{color:var(--color-ink)}.conversation-required button{padding:7px 12px;border:0;border-radius:8px;background:var(--color-primary);color:#fff}.loading-dot{width:9px;height:9px;border-radius:50%;background:var(--color-primary);box-shadow:0 0 0 5px var(--color-primary-soft);animation:pulse 1s infinite alternate}@keyframes pulse{to{opacity:.35;transform:scale(.8)}}.compose-tools{display:flex;align-items:center;gap:3px;margin-bottom:7px}.compose-tools>button,.emoji-wrapper>button{width:31px;height:29px;padding:0;border:0;border-radius:7px;background:none;color:var(--color-ink-soft);display:grid;place-items:center}.compose-tools button:hover,.compose-tools button.active{background:var(--color-primary-soft);color:var(--color-primary)}.compose-tools button:disabled{opacity:.4}.emoji-wrapper{position:relative}.emoji-picker{position:absolute;left:0;bottom:37px;z-index:10;width:310px;padding:12px;border:1px solid var(--color-line);border-radius:14px;background:#fff;box-shadow:0 14px 40px rgba(23,34,56,.16)}.emoji-picker-title{margin-bottom:9px;display:flex;justify-content:space-between;font-size:11px}.emoji-picker-title span{color:var(--color-ink-soft);font-size:9px}.emoji-grid{display:grid;grid-template-columns:repeat(8,1fr);gap:3px;max-height:205px;overflow:auto}.emoji-grid button{height:32px;padding:0;border:0;border-radius:7px;background:none;font-size:20px;line-height:1}.emoji-grid button:hover{background:var(--color-primary-soft);transform:scale(1.1)}.tool-status{margin-left:7px;color:var(--color-primary);font-size:9px}.compose-input{display:flex;gap:8px}.compose-input textarea{height:55px;flex:1;padding:9px;border:0;background:var(--color-bg);border-radius:9px;resize:none;outline:0;font:inherit;font-size:11px}.compose-input textarea:focus{box-shadow:0 0 0 2px var(--color-primary-soft)}.compose-input>button{width:42px;border:0;border-radius:10px;background:var(--color-primary);color:#fff;display:grid;place-items:center}.compose-input>button:disabled{opacity:.4}.chat-info{min-height:0;overflow-y:auto;padding:28px 20px;border-left:1px solid var(--color-line);text-align:center}.chat-info>img{width:70px;height:70px;margin:auto;border-radius:18px;object-fit:cover}.chat-info h3{margin:13px 0 7px}.chat-info>p{color:var(--color-ink-soft);font-size:10px;line-height:1.6}.chat-info>div{margin:25px 0;border-top:1px solid var(--color-line)}.chat-info>div span{padding:12px 0;border-bottom:1px solid var(--color-line);display:flex;justify-content:space-between;font-size:10px}.chat-info>button{padding:9px 13px;border:1px solid var(--color-line);border-radius:8px;background:#fff;font-size:10px}
/* ---- Contacts tab ---- */
.tab-bar{height:52px;flex:0 0 52px;display:flex;border-bottom:1px solid var(--color-line);padding:0 8px}.tab-bar button{flex:1;border:0;background:none;font-size:13px;font-weight:600;color:var(--color-ink-soft);position:relative;transition:color .15s}.tab-bar button.active{color:var(--color-primary)}.tab-bar button.active::after{content:'';position:absolute;bottom:0;left:20%;right:20%;height:2px;background:var(--color-primary);border-radius:1px}.tab-badge{position:absolute;top:10px;margin-left:4px;min-width:16px;height:16px;padding:0 5px;border-radius:8px;background:var(--color-danger);color:#fff;font-size:10px;line-height:16px;font-weight:700}.contacts-panel{min-height:0;overflow-y:auto;overscroll-behavior:contain}.contact-search{margin:10px 12px 8px;padding:7px 10px;background:var(--color-bg);border-radius:8px;display:flex;align-items:center;gap:6px}.contact-search input{min-width:0;flex:1;border:0;background:none;outline:0;font-size:12px}.clear-btn{cursor:pointer;color:var(--color-ink-soft);opacity:.6}.clear-btn:hover{opacity:1}.add-friend-btn{width:28px;height:28px;border:0;background:var(--color-primary);color:#fff;border-radius:6px;display:flex;align-items:center;justify-content:center;cursor:pointer;flex-shrink:0}.add-friend-btn:hover{opacity:.85}.search-results{background:#fff;border-bottom:1px solid var(--color-line)}.search-result-item{display:flex;align-items:center;gap:10px;padding:10px 14px}.search-result-item+.search-result-item{border-top:1px solid var(--color-line)}.search-result-item img{width:34px;height:34px;border-radius:50%;object-fit:cover;flex-shrink:0}.result-info{flex:1;min-width:0}.result-info b{display:block;font-size:12px}.result-info span{font-size:11px;color:var(--color-ink-soft)}.state-tag{font-size:10px;font-weight:700;padding:3px 7px;border-radius:var(--radius-pill)}.state-tag.blocked{background:#fff0f0;color:#c0392b;border:1px solid #f5c2c7}.section-label{padding:10px 14px 6px;font-size:11px;font-weight:700;color:var(--color-ink-soft);display:flex;align-items:center;gap:6px}.section-label .count{color:var(--color-danger)}.friend-row{display:flex;align-items:center;gap:10px;padding:10px 14px;cursor:pointer}.friend-row:hover{background:var(--color-bg)}.friend-row img{width:36px;height:36px;border-radius:50%;object-fit:cover;flex-shrink:0}.friend-row .friend-info{flex:1;min-width:0}.friend-row .friend-info b{display:flex;align-items:center;gap:5px;font-size:13px}.friend-row .friend-info span{display:block;font-size:11px;color:var(--color-ink-soft)}.remark-tag{padding:0 5px;background:var(--color-primary-soft);color:var(--color-primary);border-radius:3px;font-size:10px;font-weight:600}.friend-actions{position:relative}.icon-btn-sm{width:28px;height:28px;display:flex;align-items:center;justify-content:center;border:1px solid var(--color-line);border-radius:6px;background:#fff;cursor:pointer}.action-dropdown-sm{position:absolute;right:0;top:100%;z-index:20;min-width:120px;padding:4px;background:#fff;border:1px solid var(--color-line);border-radius:8px;box-shadow:var(--shadow-card)}.action-dropdown-sm button{display:flex;align-items:center;gap:6px;width:100%;padding:6px 8px;border:0;border-radius:4px;background:none;font-size:11px;text-align:left}.action-dropdown-sm button:hover{background:var(--color-bg)}.action-dropdown-sm button.danger{color:var(--color-danger)}.empty-contacts{text-align:center;padding:32px 16px;color:var(--color-ink-soft)}.empty-contacts svg{margin-bottom:8px;opacity:.4}.empty-contacts p{margin:4px 0;font-size:13px;font-weight:600}.empty-contacts span{font-size:11px}.request-section{padding:0}.request-item{display:flex;align-items:flex-start;gap:8px;padding:10px 14px}.request-item+.request-item{border-top:1px solid var(--color-line)}.request-item img{width:30px;height:30px;border-radius:50%;object-fit:cover;flex-shrink:0}.request-info{flex:1;min-width:0}.request-info b{font-size:12px}.request-msg{margin:2px 0 0;font-size:11px;color:var(--color-ink-soft);font-style:italic;word-break:break-word}.request-actions{display:flex;gap:4px;flex-shrink:0}.btn-xs{padding:3px 8px;font-size:11px;border-radius:6px;border:1px solid var(--color-line);background:#fff;cursor:pointer;font-weight:600;white-space:nowrap;line-height:1.4}.btn-xs.btn-primary-text{background:var(--color-primary);color:#fff;border-color:var(--color-primary)}.btn-mint{background:var(--color-mint-soft);color:var(--color-mint);border:1px solid var(--color-mint);cursor:default}.danger-btn{color:var(--color-danger);border-color:var(--color-danger)}.danger-btn:hover{background:#fff5f5}.unblock-btn{color:#c0392b;border-color:#e8c8c8;background:#fefafa}.unblock-btn:hover{background:#fff0f0;border-color:#c0392b}.form-error{padding:6px 10px;margin:4px 10px;border-radius:6px;background:#ffeaed;color:var(--color-danger);font-size:10px}.loading-dot{padding:20px;text-align:center;color:var(--color-ink-soft);font-size:12px}.modal-mask{position:fixed;inset:0;z-index:200;background:rgba(0,0,0,.3);display:flex;align-items:center;justify-content:center}.edit-modal,.request-modal,.confirm-modal,.forward-modal{width:380px;max-width:90vw;padding:24px;background:#fff;border-radius:var(--radius-xl)}.edit-modal h3,.request-modal h3,.confirm-modal h3,.forward-modal h3{margin:0 0 16px;font-size:18px}.edit-modal label{display:block;margin-bottom:12px;font-size:12px;font-weight:600;color:var(--color-ink-soft)}.edit-modal input{display:block;width:100%;margin-top:4px;padding:8px 10px;border:1px solid var(--color-line);border-radius:6px;font-size:13px;outline:0}.modal-actions{display:flex;gap:8px;justify-content:flex-end;margin-top:12px}.request-hint{color:var(--color-ink-soft);font-size:12px;margin-bottom:12px}.forward-list{display:grid;gap:8px;max-height:320px;overflow-y:auto}.forward-list button{width:100%;padding:10px;border:1px solid var(--color-line);border-radius:10px;background:#fff;display:flex;align-items:center;gap:10px;text-align:left}.forward-list button:hover:not(:disabled){background:var(--color-bg)}.forward-list button:disabled{opacity:.55}.forward-list img{width:38px;height:38px;border-radius:10px;object-fit:cover;flex-shrink:0}.forward-list span{min-width:0;display:flex;flex-direction:column}.forward-list b{overflow:hidden;white-space:nowrap;text-overflow:ellipsis;font-size:12px}.forward-list small,.empty-forward{color:var(--color-ink-soft);font-size:11px}.empty-forward{text-align:center;padding:22px 0;margin:0}.message-area{position:relative;margin-bottom:10px}.message-area textarea{width:100%;padding:10px;border:1px solid var(--color-line);border-radius:8px;font-size:13px;font-family:inherit;outline:0;resize:vertical;line-height:1.5}.message-area textarea:focus{border-color:var(--color-primary)}.char-count{position:absolute;right:8px;bottom:6px;font-size:10px;color:var(--color-ink-soft)}.emoji-row{display:flex;gap:4px;flex-wrap:wrap;margin-bottom:12px}.emoji-btn{width:30px;height:30px;display:flex;align-items:center;justify-content:center;border:1px solid var(--color-line);border-radius:6px;background:#fff;font-size:16px;cursor:pointer}.emoji-btn:hover{background:var(--color-bg)}.confirm-modal p{color:var(--color-ink-soft);font-size:13px;line-height:1.5;margin-bottom:16px}.add-friend-modal{width:380px;max-width:90vw;padding:24px;background:#fff;border-radius:var(--radius-xl)}.add-friend-modal h3{margin:0 0 14px;font-size:18px}.add-friend-search{display:flex;align-items:center;gap:6px;padding:7px 10px;background:var(--color-bg);border-radius:8px;margin-bottom:10px}.add-friend-search input{min-width:0;flex:1;border:0;background:none;outline:0;font-size:13px}.add-friend-results{max-height:300px;overflow-y:auto}.add-friend-item{display:flex;align-items:center;gap:10px;padding:10px 8px}.add-friend-item+.add-friend-item{border-top:1px solid var(--color-line)}.add-friend-item img{width:34px;height:34px;border-radius:50%;object-fit:cover;flex-shrink:0}.add-friend-empty{text-align:center;color:var(--color-ink-soft);font-size:13px;padding:20px 0}
.handled-toggle{padding:8px 14px;font-size:12px;color:var(--color-ink-soft);cursor:pointer;user-select:none;display:flex;align-items:center;gap:4px}.handled-toggle:hover{opacity:.8}.request-item.handled{opacity:.6}.request-item.handled:hover{opacity:1}.friend-actions{display:flex;align-items:center;gap:6px;flex-shrink:0}.icon-btn-sm{width:28px;height:28px;border:0;background:none;border-radius:6px;display:flex;align-items:center;justify-content:center;cursor:pointer;color:var(--color-ink-soft)}.icon-btn-sm:hover{background:var(--color-bg);color:var(--color-ink)}.friend-list-compact{margin-bottom:12px}
.send-limit-hint{padding:6px 14px;margin-bottom:8px;border-radius:6px;background:#fff3cd;color:#856404;font-size:11px;text-align:center}
@media(max-width:900px){.chat-shell{grid-template-columns:250px 1fr}.chat-info{display:none}}
@media(max-width:650px){.chat-shell{height:calc(100vh - 130px);height:calc(100dvh - 130px);min-height:0;grid-template-columns:76px 1fr}.chat-title h1,.chat-search,.conversation-copy,.conversations strong{display:none}.chat-title{justify-content:center}.conversations button{justify-content:center}.conversation-list{min-width:0}.messages{padding:14px}.message-body{max-width:85%}.emoji-picker{position:fixed;left:90px;right:12px;bottom:108px;width:auto}.emoji-grid{grid-template-columns:repeat(7,1fr)}}
</style>

<style>
.action-dropdown-sm{position:fixed;z-index:9999;background:#fff;border:1px solid var(--color-line);border-radius:8px;box-shadow:0 4px 16px rgba(0,0,0,.12);padding:4px 0;width:fit-content}.action-dropdown-sm button{display:block;width:100%;padding:5px 10px;border:0;background:none;text-align:left;font-size:11px;white-space:nowrap;cursor:pointer}.action-dropdown-sm button:hover{background:var(--color-bg)}.action-dropdown-sm button.danger{color:var(--color-danger)}
</style>
