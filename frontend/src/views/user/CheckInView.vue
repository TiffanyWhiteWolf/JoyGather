<script setup lang="ts">
import { Html5Qrcode } from 'html5-qrcode'
import { Camera, CameraOff, CheckCircle2, MapPin, Navigation, QrCode, ShieldCheck } from 'lucide-vue-next'
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { apiGet, apiPost } from '@/lib/api'
import type { User } from '@/types'

const scanned = ref(false)
const route = useRoute()
const code = ref(String(route.query.code || ''))
const user = ref<User | null>(null)
const message = ref('')
const error = ref('')
const scanning = ref(false)
const locating = ref(false)
const locationError = ref('')
const latitude = ref<number | null>(null)
const longitude = ref<number | null>(null)

// 摄像头相关
const cameraActive = ref(false)
const cameraLoading = ref(false)
const cameraError = ref('')
let html5QrCode: Html5Qrcode | null = null

function startCamera() {
  cameraError.value = ''
  cameraLoading.value = true
  cameraActive.value = true
  // 延迟初始化，等 DOM 渲染 #qr-reader
  setTimeout(() => {
    try {
      html5QrCode = new Html5Qrcode('qr-reader')
      html5QrCode.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 200, height: 200 } },
        (decodedText: string) => {
          // 解析签到 URL 中的 code 参数
          const extracted = extractCode(decodedText)
          if (extracted) {
            code.value = extracted
            stopCamera()
            scan()
          }
        },
        () => { /* ignore scan errors */ },
      ).then(() => {
        cameraLoading.value = false
      }).catch((err: unknown) => {
        cameraLoading.value = false
        handleCameraError(err)
      })
    } catch (err: unknown) {
      cameraLoading.value = false
      handleCameraError(err)
    }
  }, 200)
}

function handleCameraError(err: unknown) {
  const msg = err instanceof Error ? err.message : String(err)
  if (msg.includes('NotAllowed') || msg.includes('Permission')) {
    cameraError.value = '请允许摄像头权限以扫描二维码'
  } else if (msg.includes('NotFound')) {
    cameraError.value = '未检测到摄像头设备'
  } else {
    cameraError.value = '摄像头启动失败，可手动输入签到码'
  }
  cameraActive.value = false
}

function extractCode(text: string): string | null {
  // 尝试作为 URL 解析
  try {
    const url = new URL(text)
    const c = url.searchParams.get('code')
    if (c) return c
  } catch { /* not a URL */ }
  // 直接是纯 code（32位hex）
  if (/^[a-f0-9]{32}$/i.test(text.trim())) return text.trim()
  return null
}

function stopCamera() {
  if (html5QrCode) {
    html5QrCode.stop().catch(() => {})
    html5QrCode = null
  }
  cameraActive.value = false
  cameraLoading.value = false
}

function toggleCamera() {
  if (cameraActive.value) {
    stopCamera()
  } else {
    startCamera()
  }
}

function getLocation(): Promise<void> {
  return new Promise((resolve) => {
    if (!navigator.geolocation) {
      locationError.value = '浏览器不支持位置获取'
      resolve()
      return
    }
    locating.value = true
    locationError.value = ''
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        latitude.value = pos.coords.latitude
        longitude.value = pos.coords.longitude
        locating.value = false
        resolve()
      },
      (err) => {
        locationError.value = err.code === 1 ? '位置权限被拒绝，部分签到码需要位置校验' : '获取位置失败，可重试'
        locating.value = false
        resolve()
      },
      { timeout: 8000, maximumAge: 30000 }
    )
  })
}

async function scan() {
  error.value = ''
  if (!code.value.trim()) {
    error.value = '请输入或扫描签到码'
    return
  }
  scanning.value = true
  try {
    await getLocation()
    const result = await apiPost<{ activityId: string; activityTitle: string; status: string }>('/checkins/scan', {
      code: code.value,
      latitude: latitude.value,
      longitude: longitude.value,
    })
    message.value = `${result.activityTitle || result.activityId} · ${result.status}`
    scanned.value = true
  } catch (err) {
    error.value = err instanceof Error ? err.message : '签到失败'
  } finally {
    scanning.value = false
  }
}

onMounted(async () => { try { user.value = await apiGet<User>('/auth/me') } catch { /* show scan error after click */ } })
onUnmounted(() => stopCamera())
</script>

<template>
  <div class="check-page">
    <div class="container">
      <div class="check-head">
        <span class="eyebrow">ON-SITE CHECK IN</span>
        <h1>扫码签到</h1>
        <p>二维码由活动发起人在个人页「我的活动」中生成，现场展示给已报名用户扫描。</p>
      </div>
      <div class="check-card">
        <div :class="['scanner', { cam: cameraActive, success: scanned }]">
          <div v-if="cameraActive" id="qr-reader" class="camera-viewport"></div>
          <div v-else class="scan-frame">
            <QrCode v-if="!scanned" :size="100" />
            <CheckCircle2 v-else :size="90" />
            <i></i><span></span>
          </div>
          <div class="scan-line" v-if="!scanned && !cameraActive"></div>
          <div v-if="cameraLoading" class="camera-loading">正在启动摄像头...</div>
          <div v-if="cameraError" class="camera-error-msg">{{ cameraError }}</div>
        </div>
        <div class="check-info">
          <template v-if="!scanned">
            <span class="status-dot">等待扫描</span>
            <h2>摄像头扫码或手动输入</h2>
            <p><MapPin :size="16" />点击下方按钮打开摄像头对准二维码，也可手动输入 code</p>
            <button :class="['btn', cameraActive ? 'btn-outline' : 'btn-dark']" @click="toggleCamera">
              <CameraOff v-if="cameraActive" :size="16" />{{ cameraActive ? '关闭摄像头' : '打开摄像头扫码' }}
            </button>
            <div class="input-divider"><span>或手动输入</span></div>
            <input v-model.trim="code" class="input" placeholder="签到二维码中的 code" @keyup.enter="scan" />
            <div class="location-status" v-if="latitude !== null && !locationError">
              <Navigation :size="14" /><span>已获取位置，可用于现场签到校验</span>
            </div>
            <div class="location-status warn" v-if="locationError">
              <Navigation :size="14" /><span>{{ locationError }}</span>
            </div>
            <p v-if="error" class="auth-error">{{ error }}</p>
            <button class="btn btn-primary" :disabled="scanning" @click="scan">
              <Camera :size="18" />{{ scanning ? '签到中' : '确认签到' }}
            </button>
            <div class="privacy"><ShieldCheck :size="18" /><span>签到码和活动绑定，未报名用户或过期二维码会被拒绝；重复签到会提示已到场。</span></div>
          </template>
          <template v-else>
            <span class="status-dot done">签到成功</span>
            <h2>嗨，{{ user?.nickname || '伙伴' }}！</h2>
            <p>{{ message }}</p>
            <RouterLink to="/profile" class="btn btn-dark">返回个人页</RouterLink>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.check-page{min-height:calc(100vh - 72px);padding:55px 0;background:linear-gradient(150deg,#edf9f6,#f7f5f1)}.check-head{text-align:center}.check-head h1{margin:0 0 8px;font-size:38px}.check-head p{color:var(--color-ink-soft)}.check-card{width:min(100%,760px);margin:32px auto;overflow:hidden;border-radius:var(--radius-xl);background:#fff;box-shadow:var(--shadow-card);display:grid;grid-template-columns:1fr 1fr}.scanner{position:relative;min-height:400px;background:#172238;display:grid;place-items:center;color:#fff}.scanner.cam{background:#000;padding:0}.scan-frame{position:relative;width:220px;height:220px;border:2px solid rgba(255,255,255,.18);border-radius:22px;display:grid;place-items:center}.scan-frame:before,.scan-frame:after,.scan-frame i:before,.scan-frame i:after{content:'';position:absolute;width:34px;height:34px;border-color:var(--color-primary);border-style:solid}.scan-frame:before{left:-3px;top:-3px;border-width:4px 0 0 4px;border-radius:12px 0 0}.scan-frame:after{right:-3px;top:-3px;border-width:4px 4px 0 0;border-radius:0 12px 0 0}.scan-frame i:before{left:-3px;bottom:-3px;border-width:0 0 4px 4px;border-radius:0 0 0 12px}.scan-frame i:after{right:-3px;bottom:-3px;border-width:0 4px 4px 0;border-radius:0 0 12px}.scan-line{position:absolute;width:190px;height:2px;background:var(--color-primary);box-shadow:0 0 12px var(--color-primary);animation:scan 2s ease-in-out infinite}.scanner.success{background:var(--color-mint)}.camera-viewport{width:100%;height:100%;min-height:400px}.camera-loading,.camera-error-msg{position:absolute;bottom:0;left:0;right:0;padding:12px;text-align:center;font-size:10px;font-weight:800}.camera-loading{background:rgba(23,34,56,.9);color:#fff}.camera-error-msg{background:rgba(255,234,237,.95);color:var(--color-danger)}@keyframes scan{0%,100%{transform:translateY(-85px)}50%{transform:translateY(85px)}}.check-info{padding:35px;display:flex;align-items:flex-start;justify-content:center;flex-direction:column}.status-dot{padding:6px 10px;border-radius:var(--radius-pill);background:var(--color-primary-soft);color:var(--color-primary);font-size:10px;font-weight:800}.status-dot.done{background:var(--color-mint-soft);color:var(--color-mint)}.check-info h2{margin:18px 0 10px}.check-info p{display:flex;align-items:center;gap:5px;color:var(--color-ink-soft);font-size:12px;line-height:1.6}.check-info>.btn{margin-top:12px}.input-divider{display:flex;align-items:center;gap:10px;width:100%;margin:14px 0;color:var(--color-ink-soft);font-size:10px}.input-divider:before,.input-divider:after{content:'';flex:1;height:1px;background:var(--color-line)}.location-status{display:flex;align-items:center;gap:6px;margin-top:8px;padding:8px 10px;border-radius:8px;background:#eefaf5;color:var(--color-mint);font-size:10px;font-weight:800;width:100%;box-sizing:border-box}.location-status.warn{background:#fff5d8;color:#9b6b00}.auth-error{padding:9px;margin-top:8px;border-radius:8px;background:#ffeaed;color:var(--color-danger);font-size:10px;width:100%;box-sizing:border-box}.privacy{display:flex;gap:7px;margin-top:23px;padding-top:18px;border-top:1px solid var(--color-line);color:var(--color-mint);font-size:9px}.privacy span{color:var(--color-ink-soft)}
@media(max-width:650px){.check-card{grid-template-columns:1fr}.scanner,.camera-viewport{min-height:330px}.check-info{min-height:300px}.check-head h1{font-size:30px}}
</style>
