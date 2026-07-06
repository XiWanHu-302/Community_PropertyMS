<template>
  <div class="page">
    <!-- 搜索区域 -->
    <el-card>
      <el-form :model="query" inline>
        <el-form-item label="楼号">
          <el-select v-model="query.buildingNo" clearable placeholder="全部" style="width: 120px">
            <el-option v-for="b in buildingList" :key="b.buildingNo" :label="b.buildingNo + '栋'" :value="b.buildingNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="层号">
          <el-input-number v-model="query.floorNo" :min="1" placeholder="不限" style="width: 120px" />
        </el-form-item>
        <el-form-item label="户号">
          <el-input-number v-model="query.unitNo" :min="1" placeholder="不限" style="width: 120px" />
        </el-form-item>
        <el-form-item label="户主姓名">
          <el-input v-model="query.ownerName" placeholder="模糊搜索" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="query.phone" placeholder="模糊搜索" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 110px">
            <el-option label="在住" :value="1" />
            <el-option label="已搬离" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="RefreshRight" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作栏 -->
    <el-card style="margin-top: 16px">
      <div class="page-card-header">
        <el-button type="primary" :icon="Plus" @click="openAdd">住户登记</el-button>
        <span class="page-card-total">
          共 {{ tableData.length }} 户
          <template v-if="tableData.length > 0">
            （在住 {{ tableData.filter(r => r.status === 1).length }}，已搬离 {{ tableData.filter(r => r.status === 0).length }}）
          </template>
        </span>
      </div>
      <el-table :data="tableData" border stripe v-loading="loading" style="margin-top: 10px">
        <el-table-column prop="buildingNo" label="楼号" width="80" />
        <el-table-column label="房号" width="100">
          <template #default="{ row }">
            {{ row.floorNo }}{{ String(row.unitNo).padStart(2, '0') }}
          </template>
        </el-table-column>
        <el-table-column prop="ownerName" label="户主姓名" width="100" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="area" label="面积(㎡)" width="90" />
        <el-table-column prop="propertyFeeRate" label="物业费单价" width="100" />
        <el-table-column prop="repairFundBalance" label="维修基金余额" width="130" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '在住' : '已搬离' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="checkInDate" label="入住日期" width="120" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 1"
              size="small"
              type="warning"
              @click="handleMoveOut(row)"
            >
              搬离
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 入住登记/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑住户' : '住户登记'"
      width="650px"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="楼号" prop="buildingNo">
              <el-select v-model="form.buildingNo" placeholder="请选择楼栋" style="width: 100%">
                <el-option v-for="b in buildingList" :key="b.buildingNo" :label="b.buildingNo + '栋'" :value="b.buildingNo" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="层号" prop="floorNo">
              <el-input-number v-model="form.floorNo" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="户号" prop="unitNo">
              <el-input-number v-model="form.unitNo" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="面积(㎡)" prop="area">
              <el-input-number v-model="form.area" :min="0.01" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="物业费单价" prop="propertyFeeRate">
              <el-input-number v-model="form.propertyFeeRate" :min="0.01" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="维修基金余额">
              <el-input-number v-model="form.repairFundBalance" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="户主姓名" prop="ownerName">
              <el-input v-model="form.ownerName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="电话" prop="phone">
              <el-input v-model="form.phone" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工作单位">
              <el-input v-model="form.workUnit" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="家庭人数">
              <el-input-number v-model="form.familySize" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="入住日期" v-if="!isEdit">
          <el-date-picker v-model="form.checkInDate" type="date" placeholder="默认今天" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗：住户信息 + 账号信息 -->
    <el-dialog v-model="detailVisible" title="住户详情" width="700px">
      <el-tabs v-model="detailTab">
        <el-tab-pane label="住户信息" name="info">
          <el-form :model="detail.household" label-width="100px" v-if="detail.household">
            <el-row :gutter="16">
              <el-col :span="12"><el-form-item label="楼号"><el-input v-model="detail.household.buildingNo" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="层号"><el-input-number v-model="detail.household.floorNo" :min="1" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="12"><el-form-item label="户号"><el-input-number v-model="detail.household.unitNo" :min="1" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="面积"><el-input-number v-model="detail.household.area" :precision="2" /></el-form-item></el-col>
            </el-row>
            <el-form-item label="户主姓名"><el-input v-model="detail.household.ownerName" /></el-form-item>
            <el-form-item label="电话">
              <el-input v-model="detail.household.phone" @input="validateHhPhone" placeholder="11位手机号" />
              <span v-if="hhPhoneError" style="color:#F56C6C;font-size:12px">{{ hhPhoneError }}</span>
            </el-form-item>
            <el-form-item label="物业费单价"><el-input-number v-model="detail.household.propertyFeeRate" :precision="2" /></el-form-item>
            <el-form-item label="维修基金余额"><el-input-number v-model="detail.household.repairFundBalance" :precision="2" /></el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="登录账号" name="account">
          <div v-if="detail.account">
            <el-form label-width="80px">
              <el-form-item label="用户名">
                <el-input v-model="detail.account.username" />
              </el-form-item>
              <el-form-item label="状态">
                <el-tag :type="detail.account.status===1?'success':'danger'" size="small">{{detail.account.status===1?'启用':'禁用'}}</el-tag>
              </el-form-item>
            </el-form>
          </div>
          <p v-else style="color:#909399">暂无关联登录账号</p>
          <el-button v-if="detail.account" size="small" type="warning" @click="handleResetPwdForResident" style="margin-top:10px">重置密码</el-button>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button type="primary" @click="saveDetail" :loading="savingDetail">保存修改</el-button>
        <el-button @click="detailVisible=false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { h } from 'vue'
import { Search, RefreshRight, Plus } from '@element-plus/icons-vue'
import { queryHouseholds, addHousehold, updateHousehold, moveOut } from '../../api/household'
import { getBuildingList } from '../../api/building'
import request from '../../utils/request'
import request2 from '../../utils/request2'

// ========== 楼栋下拉数据 ==========
const buildingList = ref([])

const loadBuildings = async () => {
  try {
    const res = await getBuildingList()
    // 按楼号数值排序（VARCHAR 直接排序会是 1,10,2,28，转为数字排序：1,2,10,28）
    buildingList.value = (res.data || []).sort(
      (a, b) => parseInt(a.buildingNo) - parseInt(b.buildingNo)
    )
  } catch (e) { /* ignore */ }
}

// ========== 搜索条件 ==========
const query = reactive({
  buildingNo: '',
  floorNo: null,
  unitNo: null,
  ownerName: '',
  phone: '',
  status: null
})

// ========== 表格 ==========
const tableData = ref([])
const loading = ref(false)

const handleSearch = async () => {
  loading.value = true
  try {
    // 过滤掉 null/空字符串，避免传给后端假值
    const params = {}
    Object.keys(query).forEach(key => {
      if (query[key] !== null && query[key] !== '') {
        params[key] = query[key]
      }
    })
    const res = await queryHouseholds(params)
    tableData.value = res.data
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  query.buildingNo = ''
  query.floorNo = null
  query.unitNo = null
  query.ownerName = ''
  query.phone = ''
  query.status = null
  handleSearch()
}

// ========== 弹窗表单 ==========
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = reactive({
  householdId: null,
  buildingNo: '',
  floorNo: 1,
  unitNo: 1,
  area: null,
  propertyFeeRate: null,
  repairFundBalance: 0,
  ownerName: '',
  phone: '',
  workUnit: '',
  familySize: 1,
  checkInDate: null
})

const rules = {
  buildingNo: [{ required: true, message: '请输入楼号', trigger: 'blur' }],
  floorNo: [{ required: true, message: '请输入层号', trigger: 'blur' }],
  unitNo: [{ required: true, message: '请输入户号', trigger: 'blur' }],
  area: [{ required: true, message: '请输入面积', trigger: 'blur' }],
  propertyFeeRate: [{ required: true, message: '请输入物业费单价', trigger: 'blur' }],
  ownerName: [{ required: true, message: '请输入户主姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的11位手机号', trigger: ['blur', 'change'] }
  ]
}

const resetForm = () => {
  if (formRef.value) formRef.value.resetFields()
  Object.assign(form, {
    householdId: null, buildingNo: '', floorNo: 1, unitNo: 1,
    area: null, propertyFeeRate: null, repairFundBalance: 0,
    ownerName: '', phone: '', workUnit: '', familySize: 1, checkInDate: null
  })
}

const openAdd = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row) => {
  isEdit.value = true
  Object.keys(form).forEach(key => {
    if (row[key] !== undefined) form[key] = row[key]
  })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value) {
      await updateHousehold({ ...form })
      ElMessage.success('修改成功')
    } else {
      const res = await addHousehold({ ...form })
      // 弹窗展示自动生成的登录账号，支持复制
      const d = res.data || {}
      showAccountDialog(d.username, d.password || '123456')
    }
    dialogVisible.value = false
    handleSearch()
  } catch (e) {
    // 错误已在拦截器处理
  } finally {
    submitting.value = false
  }
}

// ========== 展示账号弹窗（含复制按钮） ==========

const showAccountDialog = (username, password) => {
  const copyText = `登录账号：${username}\n初始密码：${password}`
  ElMessageBox({
    title: '住户登记成功',
    message: h('div', { style: 'line-height: 2' }, [
      h('p', ['登录账号：', h('b', { style: 'color:#409EFF;font-size:16px' }, username)]),
      h('p', ['初始密码：', h('b', { style: 'color:#E6A23C;font-size:16px' }, password)]),
      h('p', { style: 'color:#909399;font-size:12px;margin-top:8px' }, '请将账号信息告知住户，首次登录后可自行修改密码')
    ]),
    confirmButtonText: '复制账号信息',
    cancelButtonText: '关闭',
    showCancelButton: true,
    beforeClose: (action, instance, done) => {
      if (action === 'confirm') {
        navigator.clipboard.writeText(copyText).then(() => {
          ElMessage.success('已复制到剪贴板')
        }).catch(() => {
          ElMessage.warning('复制失败，请手动复制')
        })
      }
      done()
    }
  })
}

// ========== 搬离（先检查未缴费用） ==========
const handleMoveOut = async (row) => {
  // 1. 查询未缴费用
  let unpaid = { totalUnpaid: 0, details: [] }
  try {
    const res = await request2.get(`/property-fee/unpaid/${row.householdId}`)
    unpaid = res.data || unpaid
  } catch(e) {
    ElMessage.error('查询未缴费用失败，请确认 property-service 已启动')
    return
  }

  // 2. 有未缴费用 → 必须缴清才能搬离
  if (unpaid.totalUnpaid > 0) {
    const items = unpaid.details.map(d =>
      `<p>${d.type}：${d.year}年${d.month}月 — ¥${d.amount}${d.spaceNo ? '（' + d.spaceNo + '）' : ''}</p>`
    ).join('')
    try {
      await ElMessageBox.confirm(
        `<div style="line-height:2">
          <p style="color:#F56C6C;font-weight:bold">${row.ownerName}（${row.buildingNo}栋${row.floorNo}层${String(row.unitNo).padStart(2,'0')}户号）有以下未缴费用：</p>
          ${items}
          <p style="margin-top:10px;font-weight:bold">合计：¥${unpaid.totalUnpaid}</p>
          <p style="color:#E6A23C;margin-top:8px">必须一次性缴清所有费用后才能搬离。是否缴费并搬离？</p>
        </div>`,
        '搬离前需缴清费用',
        { confirmButtonText: '缴费并搬离', cancelButtonText: '取消', type: 'warning',
          dangerouslyUseHTMLString: true }
      )
    } catch(e) {
      return  // 用户取消
    }
    // 3. 逐笔缴费
    let payErrors = 0
    for (const d of unpaid.details) {
      try {
        if (d.type === '物业费') {
          await request2.post('/property-fee/pay', { householdId: row.householdId, duration: 1, handler: '管理员' })
        } else {
          await request2.post('/parking-fee/pay', { spaceNo: d.spaceNo, duration: 1, handler: '管理员' })
        }
      } catch(e) { payErrors++ }
    }
    if (payErrors > 0) {
      ElMessage.warning(`部分费用缴纳失败（${payErrors}笔），请检查后重试`)
      return
    }
    ElMessage.success(`已缴清 ¥${unpaid.totalUnpaid}`)
  }

  // 4. 执行搬离
  try {
    await ElMessageBox.confirm(
      `确定将 ${row.ownerName}（${row.buildingNo}栋${row.floorNo}层${String(row.unitNo).padStart(2,'0')}户号）标记为搬离吗？`,
      '搬离确认', { type: 'warning' }
    )
  } catch(e) { return }
  await moveOut(row.householdId)
  ElMessage.success('搬离处理成功')
  handleSearch()
}

// ===== 详情弹窗（住户信息+账号） =====
const detailVisible = ref(false), detailTab = ref('info'), detail = reactive({household:null,account:null}), savingDetail = ref(false), hhPhoneError = ref('')

const validateHhPhone = () => {
  const p = detail.household?.phone
  if (p && !/^1[3-9]\d{9}$/.test(p)) hhPhoneError.value = '请输入正确的11位手机号'
  else hhPhoneError.value = ''
}
const openDetail = async (row) => {
  try {
    const res = await request.get(`/household/${row.householdId}/detail`)
    const d = res.data || {}
    detail.household = d.household ? {...d.household} : null
    detail.account = d.account || null
  } catch(e) { detail.household = {...row}; detail.account = null }
  detailVisible.value = true
  detailTab.value = 'info'
}
const saveDetail = async () => {
  if (hhPhoneError.value) { ElMessage.warning('请修正电话号码'); return }
  savingDetail.value = true
  try {
    await updateHousehold({...detail.household})
    // 如果改了用户名
    if (detail.account && detail.account.username) {
      await request.put('/auth/update-username-by-admin', {
        userId: detail.account.userId,
        username: detail.account.username
      })
    }
    ElMessage.success('保存成功')
    detailVisible.value = false; handleSearch()
  } catch(e) { /* 错误已拦截 */ }
  finally { savingDetail.value = false }
}
const handleResetPwdForResident = async () => {
  if(!detail.account) return
  await ElMessageBox.confirm(`重置 ${detail.account.username} 的密码为 123456？`,'确认',{type:'warning'})
  await request.put(`/household/${detail.household.householdId}/reset-pwd`)
  ElMessage.success('已重置')
}

onMounted(() => {
  loadBuildings()
  handleSearch()
})
</script>


