const api = require('../../utils/api')
const storage = require('../../utils/storage')

function interestsFromText(value) {
  return String(value || '')
    .split(/[,，、\s]+/)
    .map(item => item.trim())
    .filter(Boolean)
}

function formFromUser(user) {
  return {
    nickname: user && user.nickname ? user.nickname : '',
    city: user && user.city ? user.city : '杭州',
    bio: user && user.bio ? user.bio : '',
    interestsText: user && user.interests ? user.interests.join(', ') : '',
  }
}

Page({
  data: {
    saving: false,
    form: formFromUser(null),
    user: null,
  },

  onLoad() {
    if (!storage.getToken()) {
      wx.redirectTo({ url: '/pages/login/login' })
      return
    }
    this.loadProfile()
  },

  async loadProfile() {
    try {
      const user = await api.get('/auth/me')
      storage.setUser(user)
      this.setData({ user, form: formFromUser(user) })
    } catch (error) {
      wx.showToast({ title: error.message || '资料加载失败', icon: 'none' })
    }
  },

  onNicknameInput(event) {
    this.setData({ 'form.nickname': event.detail.value })
  },

  onCityInput(event) {
    this.setData({ 'form.city': event.detail.value })
  },

  onBioInput(event) {
    this.setData({ 'form.bio': event.detail.value })
  },

  onInterestsInput(event) {
    this.setData({ 'form.interestsText': event.detail.value })
  },

  async submitProfile(event) {
    const value = event.detail.value || {}
    const current = this.data.user || {}
    const nickname = (value.nickname || this.data.form.nickname || '').trim()
    if (!nickname) {
      wx.showToast({ title: '昵称不能为空', icon: 'none' })
      return
    }
    this.setData({ saving: true })
    try {
      const updated = await api.put('/users/me', {
        nickname,
        avatar: current.avatar,
        gender: current.gender,
        birthday: current.birthday,
        city: (value.city || this.data.form.city || '').trim(),
        bio: value.bio || this.data.form.bio || '',
        interests: interestsFromText(value.interestsText || this.data.form.interestsText),
        merchantName: current.merchantName,
        merchantNickname: current.merchantNickname,
        merchantFields: current.merchantFields || [],
      })
      storage.setUser(updated)
      getApp().refreshAuthState()
      wx.showToast({ title: '已保存', icon: 'success' })
      wx.navigateBack()
    } catch (error) {
      wx.showToast({ title: error.message || '保存失败', icon: 'none' })
    } finally {
      this.setData({ saving: false })
    }
  },
})
