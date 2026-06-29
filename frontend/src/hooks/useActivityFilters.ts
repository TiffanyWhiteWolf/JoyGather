import { computed, ref, type Ref } from 'vue'
import type { Activity, ActivityCategory } from '@/types'

export function useActivityFilters(source?: Ref<Activity[]>) {
  const activities = source ?? ref<Activity[]>([])
  const keyword = ref('')
  const categories = ref<ActivityCategory[]>([])
  const maxDistance = ref(20)
  const onlyFree = ref(false)
  const district = ref('全部城区')
  const timeRange = ref('全部时间')

  function matchesTime(dateText: string) {
    if (timeRange.value === '全部时间') return true
    const match = dateText.match(/(\d{2})月(\d{2})日/)
    if (!match) return true
    const now = new Date()
    const activityDate = new Date(now.getFullYear(), Number(match[1]) - 1, Number(match[2]))
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    const days = Math.round((activityDate.getTime() - today.getTime()) / 86400000)
    if (timeRange.value === '今天') return days === 0
    const daysUntilSunday = (7 - today.getDay()) % 7
    if (timeRange.value === '本周') return days >= 0 && days <= daysUntilSunday
    return days >= 0 && days <= daysUntilSunday && [0, 6].includes(activityDate.getDay())
  }

  const filteredActivities = computed(() => activities.value.filter((activity) => {
    const haystack = `${activity.title}${activity.summary}${activity.tags.join('')}`.toLowerCase()
    return haystack.includes(keyword.value.toLowerCase())
      && (!categories.value.length || categories.value.includes(activity.category))
      && activity.distance <= maxDistance.value
      && (!onlyFree.value || activity.price === 0)
      && (district.value === '全部城区' || activity.district === district.value)
      && matchesTime(activity.date)
  }))

  function toggleCategory(category: ActivityCategory) {
    categories.value = categories.value.includes(category)
      ? categories.value.filter(item => item !== category)
      : [...categories.value, category]
  }

  return { keyword, categories, maxDistance, onlyFree, district, timeRange, toggleCategory, filteredActivities }
}
