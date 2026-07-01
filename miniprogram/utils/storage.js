const TOKEN_KEY = 'joygather:token'
const USER_KEY = 'joygather:user'

function getToken() {
  return wx.getStorageSync(TOKEN_KEY) || ''
}

function setSession(token, user) {
  wx.setStorageSync(TOKEN_KEY, token)
  wx.setStorageSync(USER_KEY, user)
}

function getUser() {
  return wx.getStorageSync(USER_KEY) || null
}

function setUser(user) {
  wx.setStorageSync(USER_KEY, user)
}

function clearSession() {
  wx.removeStorageSync(TOKEN_KEY)
  wx.removeStorageSync(USER_KEY)
}

module.exports = {
  TOKEN_KEY,
  USER_KEY,
  getToken,
  setSession,
  getUser,
  setUser,
  clearSession,
}
