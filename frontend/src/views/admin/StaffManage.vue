<template>
  <div class="page">
    <el-card>
      <div class="page-card-header">
        <el-button type="primary" :icon="Plus" @click="openAdd">新增员工</el-button>
        <span class="page-card-total">共 {{ tableData.length }} 人</span>
      </div>
      <el-table :data="tableData" border stripe v-loading="loading" style="margin-top:10px">
        <el-table-column prop="workerNo" label="工号" width="140" />
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="username" label="登录账号" width="180" />
        <el-table-column prop="phone" label="电话" width="140" />
        <el-table-column label="状态" width="80">
          <template #default="{row}"><el-tag :type="row.status===1?'success':'danger'" size="small">{{row.status===1?'在职':'离职'}}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createTime" label="入职时间" min-width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{row}">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button size="small" @click="handleResetPwd(row)">重置密码</el-button>
            <el-button v-if="row.status===1" size="small" type="danger" @click="handleDisable(row)">离职</el-button>
            <el-button v-else size="small" type="success" @click="handleEnable(row)">复职</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="员工详情" width="550px">
      <el-tabs v-model="detailTab">
        <el-tab-pane label="员工信息" name="info">
          <el-form :model="detail" label-width="80px">
            <el-form-item label="工号"><el-input :model-value="detail.workerNo" disabled /></el-form-item>
            <el-form-item label="姓名" prop="realName"><el-input v-model="detail.realName" /></el-form-item>
            <el-form-item label="电话">
              <el-input v-model="detail.phone" @input="validatePhone" placeholder="11位手机号" />
              <span v-if="phoneError" style="color:#F56C6C;font-size:12px">{{ phoneError }}</span>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="登录账号" name="account">
          <el-form :model="detail" label-width="80px" v-if="detail.username">
            <el-form-item label="用户名">
              <el-input v-model="detail.username" />
            </el-form-item>
            <el-form-item label="状态">
              <el-tag :type="detail.userStatus===1?'success':'danger'" size="small">{{detail.userStatus===1?'启用':'禁用'}}</el-tag>
            </el-form-item>
          </el-form>
          <p v-else style="color:#909399">暂无登录账号</p>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button type="primary" @click="saveDetail" :loading="saving">保存修改</el-button>
        <el-button @click="detailVisible=false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新增员工 -->
    <el-dialog v-model="addVisible" title="新增员工" width="450px" @close="resetAdd">
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="80px">
        <el-form-item label="姓名" prop="realName"><el-input v-model="addForm.realName" /></el-form-item>
        <el-form-item label="电话" prop="phone"><el-input v-model="addForm.phone" /></el-form-item>
        <el-form-item label="用户名"><el-input v-model="addForm.username" placeholder="留空自动生成" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible=false">取消</el-button>
        <el-button type="primary" :loading="adding" @click="handleAdd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, h } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import request from '../../utils/request'

const tableData = ref([]), loading = ref(false)
const load = async () => { loading.value=true; try { tableData.value=(await request.get('/staff/list')).data||[] } finally { loading.value=false } }

// ===== 详情 =====
const detailVisible = ref(false), detailTab = ref('info'), saving = ref(false), phoneError = ref('')
const detail = reactive({ workerNo:'',realName:'',phone:'',username:'',userId:null,userStatus:null })

const validatePhone = () => {
  if (detail.phone && !/^1[3-9]\d{9}$/.test(detail.phone)) {
    phoneError.value = '请输入正确的11位手机号'
  } else {
    phoneError.value = ''
  }
}
const openDetail = (row) => {
  Object.assign(detail, row)
  detailVisible.value = true; detailTab.value = 'info'
}
const saveDetail = async () => {
  // 前端电话校验
  if (detail.phone && !/^1[3-9]\d{9}$/.test(detail.phone)) {
    ElMessage.warning('请输入正确的11位手机号'); return
  }
  saving.value=true
  try {
    await request.put(`/staff/${detail.workerNo}`, {
      realName: detail.realName, phone: detail.phone, username: detail.username
    })
    ElMessage.success('保存成功')
    detailVisible.value=false; load()
  } catch(e) { /* 错误已拦截 */ }
  finally { saving.value=false }
}

// ===== 新增 =====
const addVisible = ref(false), adding = ref(false), addFormRef = ref(null)
const addForm = reactive({ realName:'', phone:'', username:'' })
const addRules = {
  realName:[{required:true,message:'请输入姓名',trigger:'blur'}],
  phone:[{required:true,message:'请输入电话',trigger:'blur'},{pattern:/^1[3-9]\d{9}$/,message:'请输入正确的11位手机号',trigger:['blur','change']}]
}
const resetAdd = () => { if(addFormRef.value)addFormRef.value.resetFields(); addForm.realName='';addForm.phone='';addForm.username='' }
const openAdd = () => { resetAdd(); addVisible.value=true }
const handleAdd = async () => {
  if(!(await addFormRef.value.validate().catch(()=>false))) return
  adding.value=true
  try {
    const res = await request.post('/staff',{...addForm})
    const d=res.data||{}
    const copyText = `工号：${d.workerNo}  账号：${d.username}  密码：123456`
    ElMessageBox({
      title: '员工创建成功',
      message: h('div', { style: 'line-height:2' }, [
        h('p', ['工号：', h('b', { style: 'color:#409EFF;font-size:16px' }, d.workerNo)]),
        h('p', ['登录账号：', h('b', { style: 'color:#409EFF;font-size:16px' }, d.username)]),
        h('p', ['初始密码：', h('b', { style: 'color:#E6A23C;font-size:16px' }, '123456')]),
        h('p', { style: 'color:#909399;font-size:12px;margin-top:8px' }, '请将账号信息告知员工，首次登录后可自行修改密码')
      ]),
      confirmButtonText: '复制账号信息',
      cancelButtonText: '关闭',
      showCancelButton: true,
      beforeClose: (action, instance, done) => {
        if (action === 'confirm') {
          navigator.clipboard.writeText(copyText).then(() => ElMessage.success('已复制')).catch(() => ElMessage.warning('复制失败'))
        }
        done()
      }
    })
    addVisible.value=false; load()
  } finally { adding.value=false }
}

// ===== 操作 =====
const handleResetPwd = async row => {
  if(!row.username) return ElMessage.warning('该员工无登录账号')
  await ElMessageBox.confirm(`重置 ${row.username} 的密码为 123456？`,'确认',{type:'warning'})
  await request.put(`/staff/${row.workerNo}/reset-pwd`)
  ElMessage.success('密码已重置为 123456')
}
const handleDisable = async row => {
  try { await request.put(`/staff/${row.workerNo}/disable`); ElMessage.success('已离职'); load() }
  catch(e) { console.error('离职失败', e); ElMessage.error(e?.response?.data?.message || e?.message || '操作失败') }
}
const handleEnable = async row => {
  try { await request.put(`/staff/${row.workerNo}/enable`); ElMessage.success('已复职'); load() }
  catch(e) { console.error('复职失败', e); ElMessage.error(e?.response?.data?.message || e?.message || '操作失败') }
}

onMounted(load)
</script>

