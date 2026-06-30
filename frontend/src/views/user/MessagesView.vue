<script setup lang="ts">
import { onClickOutside } from '@vueuse/core'
import { File, Image, MapPin, MoreHorizontal, Search, Send, Settings, Smile } from 'lucide-vue-next'
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiGet, apiPost, apiUpload } from '@/lib/api'
import { useAppStore } from '@/stores/app'
import type { Conversation, Message } from '@/types'

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

function goTeamManage(teamId?: string) {
  if (teamId) router.push(`/teams/${teamId}/manage`)
}

const filteredConversations = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  if (!keyword) return conversations.value
  return conversations.value.filter(item => `${item.name}${item.lastMessage}`.toLowerCase().includes(keyword))
})
const active = computed(() => conversations.value.find(c => c.id === activeId.value) ?? conversations.value[0] ?? fallbackConversation)

onClickOutside(emojiWrapper, () => { emojiOpen.value = false })

async function scrollToBottom() {
  await nextTick()
  if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
}

async function loadConversations() {
  loading.value = true
  error.value = ''
  try {
    conversations.value = await apiGet<Conversation[]>('/conversations')
    if (!activeId.value && conversations.value.length) activeId.value = conversations.value[0].id
    await scrollToBottom()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '请先登录后查看消息'
  } finally {
    loading.value = false
  }
}

function selectConversation(id: string) {
  activeId.value = id
  emojiOpen.value = false
  void scrollToBottom()
}

function appendMessage(created: Message, conversationId: string) {
  const target = conversations.value.find(item => item.id === conversationId)
  if (!target) return
  target.messages.push(created)
  target.lastMessage = created.type === 'IMAGE' ? '[图片]' : created.type === 'FILE' ? '[文件]' : created.type === 'LOCATION' ? '[位置]' : created.content
  target.lastTime = created.time
  void scrollToBottom()
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
  app.showToast(active.value.type === '小队' ? '小队公告、文件和相册入口已在小队页提供' : '好友资料页将在好友体系完善后开放')
}

function openProfile() {
  void router.push(active.value.type === '小队' ? '/teams' : '/profile')
}

onMounted(loadConversations)
</script>

<template>
  <div class="container messages-page">
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <input ref="imageInput" class="hidden-file" type="file" accept="image/*" @change="uploadAndSend($event, 'IMAGE')" />
    <input ref="fileInput" class="hidden-file" type="file" @change="uploadAndSend($event, 'FILE')" />

    <div class="chat-shell">
      <aside class="conversation-list">
        <div class="chat-title"><h1>消息</h1><button type="button" aria-label="会话设置" @click="openInfo"><MoreHorizontal /></button></div>
        <div class="chat-search"><Search :size="16" /><input v-model="search" placeholder="搜索联系人或小队" /></div>
        <div class="conversations">
          <button v-for="item in filteredConversations" :key="item.id" type="button" :class="{ active: item.id === activeId }" @click="selectConversation(item.id)">
            <span class="avatar"><img :src="item.avatar" :alt="item.name" /><i v-if="item.online"></i></span>
            <span class="conversation-copy"><b>{{ item.name }}<small>{{ item.lastTime }}</small></b><em>{{ item.lastMessage }}</em></span>
            <strong v-if="item.unread">{{ item.unread }}</strong>
          </button>
          <p v-if="!filteredConversations.length" class="empty-conversations">暂无可用会话</p>
        </div>
      </aside>

      <main class="chat-main">
        <header>
          <div><img :src="active.avatar" :alt="active.name" /><span><b>{{ active.name }}</b><small>{{ active.type === '小队' ? '小队群聊' : '好友会话' }}</small></span></div>
          <div class="header-actions">
            <button v-if="active.type==='小队' && active.teamId" type="button" aria-label="小队管理" title="小队管理" @click="goTeamManage(active.teamId)"><Settings :size="18" /></button>
            <button type="button" aria-label="聊天详情" @click="openInfo"><MoreHorizontal /></button>
          </div>
        </header>

        <div ref="messageList" class="messages">
          <div class="date-divider">今天</div>
          <div v-if="active.type === '小队'" class="announcement">群公告会在这里展示。</div>
          <div v-for="message in active.messages" :key="message.id" :class="['message', { mine: message.mine }]">
            <img v-if="!message.mine" src="https://i.pravatar.cc/80?img=45" alt="发送者头像" />
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
          <div class="compose-tools">
            <div ref="emojiWrapper" class="emoji-wrapper">
              <button type="button" aria-label="选择表情" :class="{ active: emojiOpen }" :disabled="!active.id" @click="emojiOpen = !emojiOpen"><Smile :size="19" /></button>
              <div v-if="emojiOpen" class="emoji-picker" role="dialog" aria-label="表情列表">
                <div class="emoji-picker-title"><b>选择表情</b><span>{{ emojis.length }} 个</span></div>
                <div class="emoji-grid"><button v-for="emoji in emojis" :key="emoji" type="button" :aria-label="`插入表情 ${emoji}`" @click="insertEmoji(emoji)">{{ emoji }}</button></div>
              </div>
            </div>
            <button type="button" aria-label="发送图片" :disabled="!active.id || uploading" @click="imageInput?.click()"><Image :size="19" /></button>
            <button type="button" aria-label="发送文件" :disabled="!active.id || uploading" @click="fileInput?.click()"><File :size="19" /></button>
            <button type="button" aria-label="发送位置" :disabled="!active.id || locating" @click="sendLocation"><MapPin :size="19" /></button>
            <span v-if="uploading" class="tool-status">正在上传…</span>
            <span v-else-if="locating" class="tool-status">正在定位…</span>
          </div>
          <div class="compose-input">
            <textarea ref="messageInput" v-model="text" :disabled="!active.id || sending" placeholder="输入消息，Enter 发送，Shift + Enter 换行" @keydown.enter="handleEnter"></textarea>
            <button type="button" aria-label="发送消息" :disabled="!text.trim() || !active.id || sending" @click="send"><Send :size="18" /></button>
          </div>
        </div>
      </main>

      <aside class="chat-info">
        <img :src="active.avatar" :alt="active.name" /><h3>{{ active.name }}</h3>
        <p>{{ active.type === '小队' ? '小队成员可以发送文字、图片、文件和位置。' : '好友之间可以即时沟通。' }}</p>
        <div><span>消息免打扰 <input type="checkbox" /></span><span>置顶会话 <input type="checkbox" checked /></span></div>
        <button type="button" @click="openProfile">查看{{ active.type === '小队' ? '小队主页' : '个人主页' }}</button>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.messages-page{padding:30px 0 60px}.hidden-file{display:none}.chat-shell{height:clamp(520px,calc(100vh - 150px),720px);overflow:hidden;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-lg);box-shadow:var(--shadow-soft);display:grid;grid-template-columns:290px minmax(0,1fr) 230px}.conversation-list{min-height:0;overflow:hidden;border-right:1px solid var(--color-line);display:flex;flex-direction:column}button{cursor:pointer}button:disabled{cursor:not-allowed}.chat-title{height:70px;flex:0 0 70px;padding:0 20px;display:flex;align-items:center;justify-content:space-between}.chat-title h1{margin:0;font-size:24px}.chat-title button,.chat-main header button{border:0;background:none}.header-actions{display:flex;align-items:center;gap:4px}.chat-search{margin:0 14px 12px;padding:9px 11px;background:var(--color-bg);border-radius:9px;display:flex;align-items:center;gap:7px}.chat-search input{min-width:0;flex:1;border:0;background:none;outline:0;font-size:11px}.conversations{min-height:0;overflow-y:auto;overscroll-behavior:contain}.conversations button{position:relative;width:100%;padding:13px 14px;border:0;background:#fff;display:flex;align-items:center;gap:10px;text-align:left}.conversations button.active{background:var(--color-primary-soft)}.avatar{position:relative}.avatar img{width:44px;height:44px;border-radius:13px;object-fit:cover}.avatar i{position:absolute;right:-1px;bottom:-1px;width:10px;height:10px;border:2px solid #fff;border-radius:50%;background:var(--color-mint)}.conversation-copy{min-width:0;flex:1}.conversation-copy b{display:flex;justify-content:space-between;font-size:12px}.conversation-copy small{color:var(--color-ink-soft);font-size:8px;font-weight:400}.conversation-copy em{display:block;margin-top:6px;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;color:var(--color-ink-soft);font-size:9px;font-style:normal}.conversations strong{position:absolute;right:14px;bottom:11px;min-width:16px;height:16px;padding:0 4px;border-radius:8px;background:var(--color-primary);color:#fff;font-size:8px;display:grid;place-items:center}.empty-conversations{padding:30px 14px;text-align:center;color:var(--color-ink-soft);font-size:11px}.chat-main{min-width:0;min-height:0;overflow:hidden;background:#faf9f7;display:grid;grid-template-rows:70px minmax(0,1fr) auto}.chat-main header{height:70px;padding:0 20px;border-bottom:1px solid var(--color-line);background:#fff;display:flex;align-items:center;justify-content:space-between}.chat-main header>div{display:flex;align-items:center;gap:10px}.chat-main header img{width:38px;height:38px;border-radius:10px;object-fit:cover}.chat-main header span{display:flex;flex-direction:column;font-size:12px}.chat-main header small{margin-top:4px;color:var(--color-mint);font-size:8px}.messages{min-height:0;padding:18px 26px;overflow-x:hidden;overflow-y:auto;overscroll-behavior:contain;scrollbar-gutter:stable;scroll-behavior:smooth}.date-divider{text-align:center;color:var(--color-ink-soft);font-size:8px}.announcement{margin:14px auto 25px;padding:9px 12px;width:max-content;max-width:90%;border-radius:8px;background:#ece9e3;color:var(--color-ink-soft);font-size:9px}.message{margin:16px 0;display:flex;gap:8px}.message>img{width:30px;height:30px;border-radius:9px}.message-body{max-width:70%}.message-bubble{margin:0;padding:10px 13px;border-radius:4px 13px 13px 13px;background:#fff;box-shadow:0 3px 12px rgba(23,34,56,.06);font-size:12px;line-height:1.6;white-space:pre-wrap;overflow-wrap:anywhere}.message-meta{display:block;margin-top:4px;color:#a4a8b0;font-size:8px}.message.mine{justify-content:flex-end;text-align:right}.message.mine .message-bubble{background:var(--color-primary);color:#fff;border-radius:13px 4px 13px 13px;text-align:left}.message-bubble.recalled{background:#ece9e3!important;color:var(--color-ink-soft)!important;font-style:italic}.image-message{display:block;overflow:hidden;max-width:280px;border-radius:13px;background:#fff;box-shadow:0 3px 12px rgba(23,34,56,.1)}.image-message img{display:block;width:100%;max-height:280px;object-fit:cover}.file-message,.location-message{min-width:210px;padding:12px 14px;border-radius:13px;background:#fff;color:var(--color-ink);box-shadow:0 3px 12px rgba(23,34,56,.08);display:flex;align-items:center;gap:10px;text-align:left;text-decoration:none}.message.mine .file-message,.message.mine .location-message{background:var(--color-primary-soft)}.file-message>span,.location-message>span{min-width:0;display:flex;flex-direction:column}.file-message b{max-width:210px;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;font-size:11px}.file-message small,.location-message small{margin-top:4px;color:var(--color-ink-soft);font-size:8px}.location-message b{font-size:11px}.no-messages{text-align:center;margin-top:120px;color:var(--color-ink-soft);font-size:12px}.composer{position:relative;z-index:2;min-height:114px;box-sizing:border-box;padding:12px 18px 18px;border-top:1px solid var(--color-line);background:#fff}.conversation-required{min-height:114px;padding:18px;border-top:1px solid var(--color-line);background:#fff;display:flex;align-items:center;justify-content:center;gap:9px;color:var(--color-ink-soft);font-size:11px;text-align:center}.conversation-required b{color:var(--color-ink)}.conversation-required button{padding:7px 12px;border:0;border-radius:8px;background:var(--color-primary);color:#fff}.loading-dot{width:9px;height:9px;border-radius:50%;background:var(--color-primary);box-shadow:0 0 0 5px var(--color-primary-soft);animation:pulse 1s infinite alternate}@keyframes pulse{to{opacity:.35;transform:scale(.8)}}.compose-tools{display:flex;align-items:center;gap:3px;margin-bottom:7px}.compose-tools>button,.emoji-wrapper>button{width:31px;height:29px;padding:0;border:0;border-radius:7px;background:none;color:var(--color-ink-soft);display:grid;place-items:center}.compose-tools button:hover,.compose-tools button.active{background:var(--color-primary-soft);color:var(--color-primary)}.compose-tools button:disabled{opacity:.4}.emoji-wrapper{position:relative}.emoji-picker{position:absolute;left:0;bottom:37px;z-index:10;width:310px;padding:12px;border:1px solid var(--color-line);border-radius:14px;background:#fff;box-shadow:0 14px 40px rgba(23,34,56,.16)}.emoji-picker-title{margin-bottom:9px;display:flex;justify-content:space-between;font-size:11px}.emoji-picker-title span{color:var(--color-ink-soft);font-size:9px}.emoji-grid{display:grid;grid-template-columns:repeat(8,1fr);gap:3px;max-height:205px;overflow:auto}.emoji-grid button{height:32px;padding:0;border:0;border-radius:7px;background:none;font-size:20px;line-height:1}.emoji-grid button:hover{background:var(--color-primary-soft);transform:scale(1.1)}.tool-status{margin-left:7px;color:var(--color-primary);font-size:9px}.compose-input{display:flex;gap:8px}.compose-input textarea{height:55px;flex:1;padding:9px;border:0;background:var(--color-bg);border-radius:9px;resize:none;outline:0;font:inherit;font-size:11px}.compose-input textarea:focus{box-shadow:0 0 0 2px var(--color-primary-soft)}.compose-input>button{width:42px;border:0;border-radius:10px;background:var(--color-primary);color:#fff;display:grid;place-items:center}.compose-input>button:disabled{opacity:.4}.chat-info{min-height:0;overflow-y:auto;padding:28px 20px;border-left:1px solid var(--color-line);text-align:center}.chat-info>img{width:70px;height:70px;margin:auto;border-radius:18px;object-fit:cover}.chat-info h3{margin:13px 0 7px}.chat-info>p{color:var(--color-ink-soft);font-size:10px;line-height:1.6}.chat-info>div{margin:25px 0;border-top:1px solid var(--color-line)}.chat-info>div span{padding:12px 0;border-bottom:1px solid var(--color-line);display:flex;justify-content:space-between;font-size:10px}.chat-info>button{padding:9px 13px;border:1px solid var(--color-line);border-radius:8px;background:#fff;font-size:10px}
@media(max-width:900px){.chat-shell{grid-template-columns:250px 1fr}.chat-info{display:none}}
@media(max-width:650px){.chat-shell{height:calc(100vh - 130px);height:calc(100dvh - 130px);min-height:0;grid-template-columns:76px 1fr}.chat-title h1,.chat-search,.conversation-copy,.conversations strong{display:none}.chat-title{justify-content:center}.conversations button{justify-content:center}.conversation-list{min-width:0}.messages{padding:14px}.message-body{max-width:85%}.emoji-picker{position:fixed;left:90px;right:12px;bottom:108px;width:auto}.emoji-grid{grid-template-columns:repeat(7,1fr)}}
</style>
