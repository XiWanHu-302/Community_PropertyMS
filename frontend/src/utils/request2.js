import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

/** property-service 直连（CORS 已开启，不依赖 Vite 代理） */
const request2 = axios.create({
  baseURL: 'http://127.0.0.1:8082',
  timeout: 10000
})

request2.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
}, error => Promise.reject(error))

request2.interceptors.response.use(
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
      const s = error.response.status
      if (s === 401) { localStorage.clear(); router.push('/login'); ElMessage.error('登录已过期') }
      else if (s === 403) ElMessage.error('没有权限')
      else ElMessage.error('服务器异常')
    } else ElMessage.error('网络异常')
    return Promise.reject(error)
  }
)

export default request2
