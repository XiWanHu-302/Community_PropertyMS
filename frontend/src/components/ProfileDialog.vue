<template>
  <!-- 个人中心弹窗 -->
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="个人中心"
    width="500px"
    @open="loadProfile"
  >
    <el-descriptions :column="2" border>
      <el-descriptions-item label="用户名">
        <el-input v-model="profile.username" size="small" style="width:160px" />
        <div style="color:#E6A23C;font-size:11px">修改用户名后需重新登录</div>
      </el-descriptions-item>
      <el-descriptions-item label="角色">{{ roleLabel }}</el-descriptions-item>
      <el-descriptions-item label="真实姓名" v-if="state.role !== 'admin'">
        <el-input v-model="profile.realName" size="small" style="width:140px" />
      </el-descriptions-item>
      <el-descriptions-item label="电话" v-if="state.role !== 'admin'">
        <el-input v-model="profile.phone" size="small" style="width:140px" placeholder="11位手机号" @input="validateProfilePhone" />
        <div v-if="profilePhoneError" style="color:#F56C6C;font-size:12px">{{ profilePhoneError }}</div>
      </el-descriptions-item>
    </el-descriptions>
    <el-divider />
    <!-- 修改密码 -->
    <el-form :model="pwdForm" :rules="pwdRules" ref="pwdFormRef" label-width="80px">
      <el-form-item label="原密码">
        <el-input v-model="pwdForm.oldPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="不修改则留空" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="saveProfile" :loading="savingProfile">保存</el-button>
      <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../utils/request'
import { clearUser } from '../stores/user'
import state from '../stores/user'

defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])

const router = useRouter()

// 角色标签
const roleLabel = computed(() => {
  const map = { admin: '管理员', maintenance: '维修员', resident: '业主' }
  return map[state.role] || state.role
})

// ==================== 表单数据 ====================
const savingProfile = ref(false)
const profilePhoneError = ref('')
const profile = reactive({ username: '', realName: '', phone: '' })
const pwdForm = reactive({ oldPassword: '', newPassword: '' })
const pwdFormRef = ref(null)
const pwdRules = {
  newPassword: [{ min: 4, message: '密码不能少于4位', trigger: 'blur' }]
}

// 加载当前用户信息
const loadProfile = () => {
  profile.username = state.username
  profile.realName = state.realName
  profile.phone = state.phone
}

// 电话校验
const validateProfilePhone = () => {
  if (profile.phone && !/^1[3-9]\d{9}$/.test(profile.phone))
    profilePhoneError.value = '请输入正确的11位手机号'
  else
    profilePhoneError.value = ''
}

// 保存
const saveProfile = async () => {
  savingProfile.value = true
  try {
    let nameChanged = false, otherChanged = false

    // 1. 姓名、电话、密码（不涉及 JWT）
    if (state.role !== 'admin' && profile.realName !== state.realName) {
      await request.put('/auth/update-realname', { realName: profile.realName })
      state.realName = profile.realName; otherChanged = true
    }
    if (state.role !== 'admin' && profile.phone !== state.phone) {
      if (profilePhoneError.value) { ElMessage.warning('请输入正确的11位手机号'); return }
      await request.put('/auth/update-phone', { phone: profile.phone })
      state.phone = profile.phone; otherChanged = true
    }
    if (pwdForm.oldPassword || pwdForm.newPassword) {
      if (!pwdForm.oldPassword) { ElMessage.warning('请输入原密码'); return }
      if (!pwdForm.newPassword || pwdForm.newPassword.length < 4) { ElMessage.warning('新密码不能少于4位'); return }
      await request.put('/auth/update-password', pwdForm)
      // 清空密码字段
      pwdForm.oldPassword = ''
      pwdForm.newPassword = ''
      otherChanged = true
    }

    // 2. 用户名修改——token 立即失效，放最后
    if (profile.username !== state.username) {
      await request.put('/auth/update-username', { username: profile.username })
      nameChanged = true
    }

    if (nameChanged || otherChanged) {
      ElMessage.success('修改成功，请重新登录')
      setTimeout(() => { clearUser(); router.push('/login') }, 1500)
    } else {
      emit('update:modelValue', false)
    }
  } catch (e) { /* 拦截器已处理 */ }
  finally { savingProfile.value = false }
}
</script>
