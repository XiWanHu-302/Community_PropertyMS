<template>
  <div class="page">
    <el-card>
      <div class="page-card-header">
        <span style="font-weight:600">报修申请</span>
      </div>

      <!-- 最近一次报修后的提示 -->
      <el-alert v-if="lastRepair" :title="'您最近一次报修（工单号：' + lastRepair.repairId + '）状态：' + lastRepair.statusText"
        :type="lastRepair.status === 0 ? 'warning' : lastRepair.status === 1 ? 'success' : 'info'"
        :closable="false" show-icon style="margin:12px 0" />

      <!-- 数据加载失败提示 -->
      <el-empty v-if="loadError" description="数据加载失败，请确认后端服务已启动">
        <el-button type="primary" @click="loadLastRepair">重试</el-button>
      </el-empty>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" style="max-width:600px;margin-top:16px">
        <el-form-item label="报修类型">
          <el-select v-model="form.repairType" placeholder="请选择报修类型" style="width:100%">
            <el-option label="水电维修" value="水电维修" />
            <el-option label="门窗维修" value="门窗维修" />
            <el-option label="管道疏通" value="管道疏通" />
            <el-option label="墙面修补" value="墙面修补" />
            <el-option label="电器维修" value="电器维修" />
            <el-option label="防水堵漏" value="防水堵漏" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>

        <el-form-item label="问题描述" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="5"
            placeholder="请详细描述需要维修的问题，如：位置、现象、严重程度等…"
            maxlength="500" show-word-limit />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit" :disabled="!hasHousehold">
            {{ hasHousehold ? '提交报修' : '未关联住户信息' }}
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 提交成功提示 -->
      <el-result v-if="submitSuccess" icon="success" title="报修提交成功" sub-title="您的报修申请已提交，请等待物业安排维修人员处理。">
        <template #extra>
          <el-button type="primary" @click="handleNewRepair">继续报修</el-button>
          <el-button @click="goToList">查看报修记录</el-button>
        </template>
      </el-result>

      <!-- 空状态 -->
      <div v-if="!submitSuccess && !hasHousehold" style="text-align:center;padding:40px">
        <el-empty description="未关联住户信息，无法提交报修">
          <template #extra>
            <span style="color:#909399;font-size:13px">请联系物业管理员核实住户信息</span>
          </template>
        </el-empty>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { addRepair, myRepairs } from '../../api/repair'

const router = useRouter()
const formRef = ref(null)
const submitting = ref(false)
const submitSuccess = ref(false)
const hasHousehold = ref(true)
const loadError = ref(false)
const lastRepair = ref(null)

const form = reactive({
  repairType: '',
  content: ''
})

const rules = {
  content: [
    { required: true, message: '请输入问题描述', trigger: 'blur' },
    { min: 5, message: '问题描述至少5个字', trigger: 'blur' },
    { max: 500, message: '问题描述不能超过500字', trigger: 'blur' }
  ]
}

// 拼接报修内容（类型 + 描述）
const buildContent = () => {
  const parts = []
  if (form.repairType) parts.push('【' + form.repairType + '】')
  parts.push(form.content)
  return parts.join(' ')
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch { return }

  submitting.value = true
  try {
    const content = buildContent()
    await addRepair({ content })
    ElMessage.success('报修提交成功')
    submitSuccess.value = true
    loadLastRepair()
  } catch {
    // 错误已由拦截器处理
    hasHousehold.value = false
  } finally {
    submitting.value = false
  }
}

const handleReset = () => {
  form.repairType = ''
  form.content = ''
  formRef.value?.resetFields()
}

const handleNewRepair = () => {
  submitSuccess.value = false
  handleReset()
}

const goToList = () => {
  router.push('/my-repair/list')
}

// 加载最近一次报修
const loadLastRepair = async () => {
  loadError.value = false
  try {
    const res = await myRepairs({})
    const list = Array.isArray(res.data) ? res.data : []
    if (list.length > 0) {
      lastRepair.value = list[0]
    }
  } catch {
    loadError.value = true
  }
}

onMounted(() => {
  loadLastRepair()
})
</script>
