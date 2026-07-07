<template>
  <div class="login-page">
    <!-- 卡片居中 -->
    <div class="login-card">
      <!-- 顶部标题 -->
      <div class="card-header">
        <div class="logo-icon">
          <el-icon :size="36"><OfficeBuilding /></el-icon>
        </div>
        <h1>住宅小区物业管理系统</h1>
        <p>智慧物业 · 用心服务每一家</p>
      </div>

      <!-- 表单 -->
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" class="login-btn" @click="handleLogin">
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 底部版权 -->
      <div class="card-footer">© 2026 小区物业管理系统</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, OfficeBuilding } from '@element-plus/icons-vue'
import request from '../utils/request'
import { setUser } from '../stores/user'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

onMounted(() => { document.title = '登录 - 小区物业管理系统' })

const form = reactive({ username: 'admin', password: '123456' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 4, message: '密码长度不少于4位', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const res = await request.post('/auth/login', { username: form.username, password: form.password })
    const { token, role, realName: loginRealName } = res.data
    localStorage.setItem('token', token)
    let username = form.username, phone = '', refId = '', realName = loginRealName
    try {
      const meRes = await request.get('/auth/me')
      if (meRes.data) { username = meRes.data.username || username; realName = meRes.data.realName || realName; phone = meRes.data.phone || ''; refId = meRes.data.refId || '' }
    } catch (e) { /* ignore */ }
    setUser(token, role, username, realName, phone, refId)
    ElMessage.success('登录成功！欢迎 ' + realName)
    router.push('/')
  } catch (error) { /* 拦截器已处理 */ }
  finally { loading.value = false }
}
</script>

<style scoped>
.login-page {
  height: 100vh; display: flex; align-items: center; justify-content: center;
  background: #fff;
}
.login-card {
  width: 420px; padding: 48px 40px 36px;
  background: #fff; border-radius: 12px;
  box-shadow: 0 8px 40px rgba(0,0,0,0.1);
  position: relative; z-index: 10;
}

.card-header { text-align: center; margin-bottom: 36px; }
.logo-icon {
  width: 64px; height: 64px; border-radius: 16px;
  background: #ecf5ff; color: #409EFF;
  display: flex; align-items: center; justify-content: center;
  margin: 0 auto 16px;
}
.card-header h1 { font-size: 20px; font-weight: 600; color: #303133; margin: 0 0 8px 0; }
.card-header p { font-size: 13px; color: #909399; margin: 0; }

.login-btn { width: 100%; height: 44px; font-size: 15px; letter-spacing: 4px; }

.card-footer { text-align: center; margin-top: 32px; color: #c0c4cc; font-size: 12px; }
</style>
