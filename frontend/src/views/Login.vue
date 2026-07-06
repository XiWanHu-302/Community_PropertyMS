<template>
  <div class="login-container">
    <!-- 登录卡片 -->
    <el-card class="login-card">
      <template #header>
        <h2 class="login-title">住宅小区物业管理系统</h2>
      </template>

      <!-- 登录表单 -->
      <el-form
        ref="formRef"
        :model="loginForm"
        :rules="rules"
        label-width="0"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            style="width: 100%"
            @click="handleLogin"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import request from '../utils/request'
import { setUser } from '../stores/user'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

// 设置页面标题
onMounted(() => {
  document.title = '登录 - 小区物业管理系统'
})

// 表单数据
const loginForm = reactive({
  username: 'admin',
  password: '123456'
})

// 表单校验规则
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 4, message: '密码长度不少于4位', trigger: 'blur' }
  ]
}

/**
 * 登录操作
 */
const handleLogin = async () => {
  // 表单校验
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    // 调后端登录接口
    const res = await request.post('/auth/login', {
      username: loginForm.username,
      password: loginForm.password
    })
    const { token, role, realName: loginRealName } = res.data
    // 先把 token 写入 localStorage，/auth/me 的请求拦截器才能带上
    localStorage.setItem('token', token)

    let username = loginForm.username, phone = '', refId = '', realName = loginRealName
    try {
      const meRes = await request.get('/auth/me')
      if (meRes.data) {
        username = meRes.data.username || username
        realName = meRes.data.realName || realName
        phone = meRes.data.phone || ''
        refId = meRes.data.refId || ''
      }
    } catch(e) { /* ignore */ }

    setUser(token, role, username, realName, phone, refId)
    ElMessage.success('登录成功！欢迎 ' + realName)

    // 跳转到首页
    router.push('/')

  } catch (error) {
    // 错误已在 request 拦截器中提示，这里只取消 loading
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 420px;
  border-radius: 8px;
}

.login-title {
  text-align: center;
  color: #303133;
  margin: 0;
}
</style>
