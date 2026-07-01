const api = require('../../utils/api')
const storage = require('../../utils/storage')

function priceText(activity) {
  const price = Number(activity && activity.price ? activity.price : 0)
  return price > 0 ? `￥${price.toFixed(0)}` : '免费'
}

Page({
  data: {
    id: '',
    activity: null,
    priceText: '免费',
    registrationStatus: '',
    canCancel: false,
    hasToken: false,
    loading: true,
    submitting: false,
    error: '',
  },

  onLoad(options) {
    this.setData({ id: options.id || '' })
    this.loadDetail()
  },

  reload() {
    this.loadDetail()
  },

  async loadDetail() {
    const hasToken = !!storage.getToken()
    this.setData({ loading: true, error: '', hasToken })
    try {
      const activity = await api.get(`/activities/${this.data.id}`)
      let registrationStatus = ''
      if (hasToken) {
        try {
          const statuses = await api.get('/activities/registrations/me')
          registrationStatus = statuses && statuses[this.data.id] ? statuses[this.data.id] : ''
        } catch (error) {
          registrationStatus = ''
        }
      }
      this.setData({
        activity,
        priceText: priceText(activity),
        registrationStatus,
        canCancel: registrationStatus === '已报名' || registrationStatus === '候补中',
      })
    } catch (error) {
      this.setData({ error: error.message || '活动加载失败' })
    } finally {
      this.setData({ loading: false })
    }
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/login' })
  },

  async joinActivity() {
    this.setData({ submitting: true })
    try {
      const result = await api.post(`/activities/${this.data.id}/registrations`, { fields: {} })
      const status = result && result.status ? result.status : '已报名'
      wx.showToast({ title: status, icon: 'success' })
      this.setData({ registrationStatus: status, canCancel: status === '已报名' || status === '候补中' })
      this.loadDetail()
    } catch (error) {
      wx.showToast({ title: error.message || '报名失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },

  async cancelRegistration() {
    this.setData({ submitting: true })
    try {
      await api.del(`/activities/${this.data.id}/registrations/me`)
      wx.showToast({ title: '已取消', icon: 'success' })
      this.setData({ registrationStatus: '', canCancel: false })
      this.loadDetail()
    } catch (error) {
      wx.showToast({ title: error.message || '取消失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },
})
