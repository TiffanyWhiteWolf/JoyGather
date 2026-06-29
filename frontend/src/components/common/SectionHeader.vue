<script setup lang="ts">
import { ArrowRight } from 'lucide-vue-next'
defineProps<{ eyebrow?: string; title: string; description?: string; link?: string }>()
</script>
<template><div class="section-header"><div><span v-if="eyebrow" class="eyebrow">{{ eyebrow }}</span><h2>{{ title }}</h2><p v-if="description">{{ description }}</p></div><RouterLink v-if="link" :to="link" class="text-link">查看全部 <ArrowRight :size="17" /></RouterLink></div></template>
