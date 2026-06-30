<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import QRCode from 'qrcode'

const props = defineProps<{ userId: string; nickname: string }>()

const canvasRef = ref<HTMLCanvasElement | null>(null)

function generate() {
  if (!canvasRef.value) return
  const payload = JSON.stringify({ type: 'friend_request', userId: props.userId, nickname: props.nickname })
  QRCode.toCanvas(canvasRef.value, payload, {
    width: 220,
    margin: 2,
    color: { dark: '#2d2d2d', light: '#ffffff' },
    errorCorrectionLevel: 'M',
  })
}

onMounted(generate)
watch(() => props.userId, generate)
</script>

<template>
  <div class="qr-code-card">
    <canvas ref="canvasRef" class="qr-canvas"></canvas>
    <p class="qr-hint">扫一扫添加好友</p>
  </div>
</template>

<style scoped>
.qr-code-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 16px;
  background: #fff;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
}
.qr-canvas {
  display: block;
  border-radius: 12px;
}
.qr-hint {
  margin: 0;
  color: var(--color-ink-soft);
  font-size: 12px;
}
</style>
