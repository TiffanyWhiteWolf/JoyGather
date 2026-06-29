<script setup lang="ts">
import { MapPin, Navigation } from 'lucide-vue-next'
import type { Activity } from '@/types'
defineProps<{ activities: Activity[]; compact?: boolean }>()
</script>
<template>
  <div :class="['city-map', { compact }]">
    <div class="map-grid"></div><div class="map-river river-one"></div><div class="map-river river-two"></div>
    <span v-for="(road, i) in 7" :key="i" class="map-road" :style="{ transform: `rotate(${i * 23 - 62}deg)`, top: `${8 + i * 13}%`, left: '-10%' }"></span>
    <RouterLink v-for="(activity, i) in activities" :key="activity.id" :to="`/activities/${activity.id}`" class="map-pin" :style="{ left: `${activity.longitude}%`, top: `${activity.latitude}%` }"><MapPin :size="16" fill="currentColor"/><span>{{ i + 1 }}</span><div class="pin-tip">{{ activity.title }}</div></RouterLink>
    <button class="locate-btn"><Navigation :size="17" />定位到我</button><div class="map-legend"><span></span> {{ activities.length }} 场活动在附近</div>
  </div>
</template>
