<script setup lang="ts">
import { RouterView } from 'vue-router'
import { useAppStore } from '@/stores/app'

const app = useAppStore()
</script>

<template>
  <RouterView />
  <Transition name="toast">
    <div v-if="app.toast" class="global-toast">✓ {{ app.toast }}</div>
  </Transition>
</template>
