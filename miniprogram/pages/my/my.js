const api = require('../../utils/api')
const storage = require('../../utils/storage')

function statusListFromMap(map) {
  return Object.keys(map || {}).map(id => ({ id, status: map[id] }))
}

Page({
  data: {
    hasToken: false,
    user: null,
    statusList: [],
  },

  onShow() {
    this.loadMine()
  },

  async loadMine() {
    const hasToken = !!storage.getToken()
    this.setData({ hasToken, user: storage.getUser(), statusList: [] })
    if (!hasToken) return
    try {
      const user = await api.get('/auth/me')
      storage.setUser(user)
      getApp().refreshAuthState()
      this.setData({ user })
    } catch (error) {
      wx.showToast({ title: error.message || '账号信息加载失败', icon: 'none' })
      this.setData({ hasToken: false, user: null })
      return
    }
    try {
      const statuses = await api.get('/activities/registrations/me')
      this.setData({ statusList: statusListFromMap(statuses) })
    } catch (error) {
      this.setData({ statusList: [] })
    }
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/login' })
  },

  goProfile() {
    wx.navigateTo({ url: '/pages/profile/profile' })
  },

  async logout() {
    try {
      await api.post('/auth/logout', {})
    } catch (error) {
      // Local logout is still valid for demo even if the network request fails.
    }
    storage.clearSession()
    getApp().refreshAuthState()
    this.setData({ hasToken: false, user: null, statusList: [] })
    wx.showToast({ title: '已退出', icon: 'success' })
  },
})
