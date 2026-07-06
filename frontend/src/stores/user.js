import { reactive, computed } from 'vue'

/**
 * 用户状态存储（用 reactive 实现，不引入 Pinia 以减少依赖）
 * 全局共享当前登录用户信息
 */

// 响应式用户状态
const state = reactive({
  token: localStorage.getItem('token') || '',
  role: localStorage.getItem('role') || '',
  username: localStorage.getItem('username') || '',
  realName: localStorage.getItem('realName') || '',
  phone: localStorage.getItem('phone') || '',
  refId: localStorage.getItem('refId') || ''
})

/** 保存登录信息 */
export function setUser(token, role, username, realName, phone, refId) {
  state.token = token; state.role = role; state.username = username; state.realName = realName
  state.phone = phone || ''; state.refId = refId || ''
  localStorage.setItem('token', token); localStorage.setItem('role', role)
  localStorage.setItem('username', username); localStorage.setItem('realName', realName)
  localStorage.setItem('phone', phone||''); localStorage.setItem('refId', refId||'')
}

/** 清除登录信息 */
export function clearUser() {
  state.token = ''; state.role = ''; state.username = ''; state.realName = ''; state.phone = ''; state.refId = ''
  localStorage.removeItem('token'); localStorage.removeItem('role'); localStorage.removeItem('username')
  localStorage.removeItem('realName'); localStorage.removeItem('phone'); localStorage.removeItem('refId')
}

/** 是否已登录 */
export function isLoggedIn() {
  return !!state.token
}

/** 获取当前角色 */
export function getRole() {
  return state.role
}

export default state
