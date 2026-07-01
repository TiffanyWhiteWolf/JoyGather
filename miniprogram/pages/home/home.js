const api = require('../../utils/api')

function normalizeActivity(item) {
  const price = Number(item.price || 0)
  return {
    ...item,
    priceText: price > 0 ? `￥${price.toFixed(0)}` : '免费',
  }
}

Page({
  data: {
    loading: true,
    error: '',
    activities: [],
  },

  onLoad() {
    this.loadActivities()
  },

  onPullDownRefresh() {
    this.loadActivities().finally(() => wx.stopPullDownRefresh())
  },

  refresh() {
    this.loadActivities()
  },

  async loadActivities() {
    this.setData({ loading: true, error: '' })
    try {
      const activities = await api.get('/activities?sort=latest&size=20')
      this.setData({ activities: (activities || []).map(normalizeActivity) })
    } catch (error) {
      this.setData({ error: error.message || '活动加载失败' })
    } finally {
      this.setData({ loading: false })
    }
  },

  openDetail(event) {
    const id = event.currentTarget.dataset.id
    wx.navigateTo({ url: `/pages/activity-detail/activity-detail?id=${id}` })
  },
})
