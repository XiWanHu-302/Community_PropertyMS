import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

// 创建 axios 实例，配置基础路径
const request = axios.create({
  baseURL: '/api',                    // 通过 Vite 代理转发到后端，解决跨域
  timeout: 10000                       // 超时 10 秒
})

// ==================== 请求拦截器 ====================
// 每次发请求前，从 localStorage 取出 token 塞到请求头
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// ==================== 响应拦截器 ====================
// 统一处理错误（401 跳登录页、500 提示等）
request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  error => {
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        // Token 过期或无效，清空登录信息，跳登录页
        localStorage.removeItem('token')
        localStorage.removeItem('role')
        localStorage.removeItem('realName')
        router.push('/login')
        ElMessage.error('登录已过期，请重新登录')
      } else if (status === 403) {
        ElMessage.error('没有权限执行此操作')
      } else {
        ElMessage.error('服务器异常，请稍后重试')
      }
    } else {
      ElMessage.error('网络异常，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

export default request
