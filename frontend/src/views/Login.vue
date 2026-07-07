<template>
  <div class="login-page">
    <!-- 卡通城市背景 -->
    <div class="cartoon-bg">
      <div class="sky">
        <div class="sunny"></div>
        <div class="cloud c1"></div>
        <div class="cloud c2"></div>
        <div class="cloud c3"></div>
      </div>
      <div class="city">
        <div class="house h1"><div class="roof"></div><div class="wall"><i v-for="n in 6" :key="'a'+n" class="w"></i></div><div class="door"></div></div>
        <div class="house h2"><div class="roof"></div><div class="wall"><i v-for="n in 8" :key="'b'+n" class="w"></i></div></div>
        <div class="house h3"><div class="roof"></div><div class="wall"><i v-for="n in 12" :key="'c'+n" class="w"></i></div><div class="door"></div></div>
        <div class="house h4"><div class="roof"></div><div class="wall"><i v-for="n in 4" :key="'d'+n" class="w"></i></div></div>
        <div class="house h5"><div class="roof"></div><div class="wall"><i v-for="n in 9" :key="'e'+n" class="w"></i></div></div>
        <div class="tree tr1"><div class="treetop"></div><div class="trunk"></div></div>
        <div class="tree tr2"><div class="treetop"></div><div class="trunk"></div></div>
        <div class="tree tr3"><div class="treetop"></div><div class="trunk"></div></div>
      </div>
    </div>

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
  background: linear-gradient(180deg, #e0f0ff 0%, #f0f7ff 70%, #e8f5e9 100%);
  position: relative; overflow: hidden;
}
.login-card {
  width: 420px; padding: 48px 40px 36px;
  background: #fff; border-radius: 12px;
  box-shadow: 0 8px 40px rgba(0,0,0,0.1);
  position: relative; z-index: 10;
}

/* ====== 卡通背景 ====== */
.cartoon-bg { position: absolute; bottom: 0; left: 0; right: 0; height: 55%; pointer-events: none; }
.sky { position: absolute; top: 0; left: 0; right: 0; height: 100%; }
.sunny {
  position: absolute; top: 10px; right: 15%; width: 50px; height: 50px;
  border-radius: 50%; background: #ffd666;
  box-shadow: 0 0 30px rgba(255,214,102,0.5);
}
.cloud {
  position: absolute; height: 20px; background: rgba(255,255,255,0.7);
  border-radius: 20px;
}
.cloud::before, .cloud::after {
  content: ''; position: absolute; border-radius: 50%; background: inherit;
}
.cloud::before { width: 22px; height: 22px; top: -10px; left: 10px; }
.cloud::after { width: 16px; height: 16px; top: -6px; left: 30px; }
.c1 { width: 80px; top: 30px; left: 10%; animation: drift 12s ease-in-out infinite; }
.c2 { width: 60px; top: 15px; left: 45%; animation: drift 16s ease-in-out infinite -4s; }
.c3 { width: 70px; top: 35px; left: 70%; animation: drift 14s ease-in-out infinite -8s; }
@keyframes drift {
  0%, 100% { transform: translateX(0); }
  50% { transform: translateX(20px); }
}

.city {
  position: absolute; bottom: 0; left: 0; right: 0; height: 160px;
  display: flex; align-items: flex-end; justify-content: center;
  gap: 16px; padding: 0 20px;
}

/* 楼房 */
.house { position: relative; display: flex; flex-direction: column; align-items: center; }
.roof { width: 0; height: 0; border-left: 0 solid transparent; border-right: 0 solid transparent; border-bottom: 0 solid #000; }
.wall { display: flex; flex-wrap: wrap; align-content: flex-start; justify-content: center; padding: 6px; gap: 3px; }
.w { display: block; background: rgba(255,255,255,0.7); border-radius: 2px; }

.h1 { width: 64px; }
.h1 .roof { border-left-width: 36px; border-right-width: 36px; border-bottom-width: 14px; border-bottom-color: #e8a87c; }
.h1 .wall { width: 64px; height: 90px; background: #d4956b; border-radius: 0 0 4px 4px; }
.h1 .w { width: 8px; height: 9px; }
.h1 .door { width: 14px; height: 20px; background: #b8784a; border-radius: 3px 3px 0 0; margin-top: -20px; }

.h2 { width: 52px; }
.h2 .roof { border-left-width: 30px; border-right-width: 30px; border-bottom-width: 12px; border-bottom-color: #a8c8e8; }
.h2 .wall { width: 52px; height: 70px; background: #8db5d9; border-radius: 0 0 4px 4px; }
.h2 .w { width: 7px; height: 8px; }

.h3 { width: 76px; }
.h3 .roof { border-left-width: 42px; border-right-width: 42px; border-bottom-width: 16px; border-bottom-color: #f4b084; }
.h3 .wall { width: 76px; height: 105px; background: #e89e72; border-radius: 0 0 4px 4px; }
.h3 .w { width: 9px; height: 10px; }
.h3 .door { width: 16px; height: 22px; background: #c07850; border-radius: 4px 4px 0 0; margin-top: -22px; }

.h4 { width: 44px; }
.h4 .roof { border-left-width: 26px; border-right-width: 26px; border-bottom-width: 10px; border-bottom-color: #90c695; }
.h4 .wall { width: 44px; height: 60px; background: #7ab580; border-radius: 0 0 4px 4px; }
.h4 .w { width: 6px; height: 7px; }

.h5 { width: 56px; }
.h5 .roof { border-left-width: 32px; border-right-width: 32px; border-bottom-width: 13px; border-bottom-color: #c9b8e8; }
.h5 .wall { width: 56px; height: 80px; background: #b09fd4; border-radius: 0 0 4px 4px; }
.h5 .w { width: 7px; height: 8px; }

/* 树 */
.tree { display: flex; flex-direction: column; align-items: center; align-self: flex-end; }
.treetop { border-radius: 50%; }
.trunk { background: #c49a6c; border-radius: 2px; }
.tr1 .treetop { width: 32px; height: 32px; background: #7ec8a0; }
.tr1 .trunk { width: 6px; height: 16px; }
.tr2 .treetop { width: 26px; height: 26px; background: #6db890; }
.tr2 .trunk { width: 5px; height: 12px; }
.tr3 .treetop { width: 28px; height: 28px; background: #8dd4aa; }
.tr3 .trunk { width: 5px; height: 14px; }

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
