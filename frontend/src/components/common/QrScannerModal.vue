<script setup lang="ts">
import { Html5Qrcode } from 'html5-qrcode'
import { onMounted, onUnmounted, ref } from 'vue'

const emit = defineEmits<{ close: []; scanned: [userId: string, nickname: string] }>()

const error = ref('')
const scanning = ref(false)
const readerId = 'qr-reader'
let html5QrCode: Html5Qrcode | null = null

onMounted(async () => {
  try {
    scanning.value = true
    html5QrCode = new Html5Qrcode(readerId)
    await html5QrCode.start(
      { facingMode: 'environment' },
      { fps: 10, qrbox: { width: 250, height: 250 } },
      (decodedText: string) => {
        try {
          const data = JSON.parse(decodedText)
          if (data.type === 'friend_request' && data.userId) {
            stop()
            emit('scanned', data.userId, data.nickname || '')
          }
        } catch {
          // Not our QR code format, ignore
        }
      },
      () => { /* ignore scan errors */ },
    )
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : '无法启动摄像头'
    if (msg.includes('NotAllowedError') || msg.includes('Permission')) {
      error.value = '请允许摄像头权限以扫描二维码'
    } else {
      error.value = msg
    }
    scanning.value = false
  }
})

function stop() {
  scanning.value = false
  if (html5QrCode) {
    try {
      html5QrCode.stop().catch(() => {})
    } catch {
      // ignore
    }
  }
}

function close() {
  stop()
  emit('close')
}

onUnmounted(() => stop())
</script>

<template>
  <div class="scanner-backdrop" @click.self="close">
    <div class="scanner-modal">
      <div class="scanner-header">
        <h3>扫描好友二维码</h3>
        <button class="scanner-close" @click="close">&times;</button>
      </div>
      <div v-if="error" class="scanner-error">{{ error }}</div>
      <div id="qr-reader" class="scanner-viewport"></div>
      <p class="scanner-tip">将二维码对准框内即可自动识别</p>
    </div>
  </div>
</template>

<style scoped>
.scanner-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
}
.scanner-modal {
  width: 360px;
  max-width: 95vw;
  padding: 20px;
  background: #fff;
  border-radius: var(--radius-lg);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);
}
.scanner-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.scanner-header h3 {
  margin: 0;
  font-size: 16px;
}
.scanner-close {
  padding: 4px 10px;
  border: 0;
  background: none;
  font-size: 22px;
  cursor: pointer;
  color: var(--color-ink-soft);
}
.scanner-error {
  padding: 10px;
  margin-bottom: 10px;
  border-radius: 8px;
  background: #ffeaed;
  color: var(--color-danger);
  font-size: 12px;
}
.scanner-viewport {
  width: 100%;
  border-radius: 12px;
  overflow: hidden;
}
.scanner-tip {
  margin: 10px 0 0;
  text-align: center;
  color: var(--color-ink-soft);
  font-size: 12px;
}
</style>
