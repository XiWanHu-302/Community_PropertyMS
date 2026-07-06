<template>
  <div class="page">
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="车位管理" name="space">
    <el-card>
          <div class="page-card-header">
            <el-button type="primary" :icon="Plus" @click="openAdd">新增车位</el-button>
            <span class="page-card-total">共 {{ list.length }} 个</span>
          </div>
          <el-table :data="list" border stripe v-loading="loading" style="margin-top:10px">
            <el-table-column prop="spaceNo" label="车位编号" width="100" />
            <el-table-column prop="createTime" label="创建时间" width="170" />
            <el-table-column prop="assignedDate" label="分配时间" width="120" />
            <el-table-column prop="plateNo" label="车牌号" width="120" />
            <el-table-column prop="room" label="租用住户" min-width="160">
              <template #default="{row}">{{ row.room || '—' }}</template>
            </el-table-column>
            <el-table-column prop="ownerName" label="户主" width="100" />
            <el-table-column prop="monthlyFee" label="月费(元)" width="100" />
            <el-table-column label="状态" width="80">
              <template #default="{row}">
                <el-tag :type="row.status===1?'success':'info'" size="small">{{row.status===1?'已租':'空闲'}}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{row}">
                <el-button size="small" @click="openEdit(row)">编辑</el-button>
                <el-button v-if="row.status===1" size="small" type="warning" @click="handleRelease(row)">释放</el-button>
                <el-button v-else size="small" type="success" @click="openAssign(row)">分配</el-button>
                <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ========== Tab 2: 停车费管理 ========== -->
      <el-tab-pane label="停车费管理" name="fee">
        <el-card>
          <div class="page-card-header">
            <div>
              <el-form inline>
                <el-form-item label="年份">
                  <el-select v-model="feeYear" style="width:120px" @change="loadFeeSummary">
                    <el-option v-for="y in years" :key="y" :label="y+'年'" :value="y" />
                  </el-select>
                </el-form-item>
                <el-form-item label="月份">
                  <el-select v-model="feeMonth" style="width:100px" @change="loadFeeSummary">
                    <el-option v-for="m in 12" :key="m" :label="m+'月'" :value="m" />
                  </el-select>
                </el-form-item>
                <el-form-item label="状态">
                  <el-select v-model="feeStatusFilter" style="width:100px" @change="loadFeeSummary">
                    <el-option label="全部" value="全部" />
                    <el-option label="已缴" value="已缴" />
                    <el-option label="待缴" value="待缴" />
                    <el-option label="逾期" value="逾期" />
                  </el-select>
                </el-form-item>
              </el-form>
            </div>
            <div>
              <span class="page-card-total">
                共 {{ feeSummary.totalSpaces }} 车位 |
                已缴 <b style="color:#67C23A">{{ feeSummary.paidCount }}</b> |
                待缴 <b style="color:#E6A23C">{{ feeSummary.pendingCount }}</b> |
                逾期 <b style="color:#F56C6C">{{ feeSummary.overdueCount }}</b> |
                应收 ¥{{ feeSummary.totalReceivable }} / 已收 ¥{{ feeSummary.totalCollected }} / 未收 ¥{{ feeSummary.totalOutstanding }}
              </span>
            </div>
          </div>
          <el-table :data="feeSummary.details" border stripe v-loading="feeLoading" style="margin-top:10px">
            <el-table-column prop="spaceNo" label="车位编号" width="100" />
            <el-table-column prop="plateNo" label="车牌号" width="120" />
            <el-table-column prop="ownerName" label="户主" width="90" />
            <el-table-column prop="amount" label="应缴金额" width="110" />
            <el-table-column label="状态" width="80">
              <template #default="{row}">
                <el-tag v-if="row.statusText==='已缴' || row.statusText==='提前缴费'" type="success" size="small">{{ row.statusText }}</el-tag>
                <el-tag v-else-if="row.statusText==='逾期'" type="danger" size="small">逾期</el-tag>
                <el-tag v-else-if="row.statusText==='历史记录'" type="info" size="small">历史记录</el-tag>
                <el-tag v-else type="warning" size="small">待缴</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="payDate" label="缴费日期" width="120" />
            <el-table-column prop="handler" label="经手人" width="90" />
            <el-table-column prop="billNo" label="缴费单号" min-width="150" />
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{row}">
                <el-button v-if="row.statusText!=='已缴' && row.statusText!=='提前缴费' && row.statusText!=='历史记录'" size="small" type="success" @click="payParkingFee(row)">缴费</el-button>
                <el-tag v-else type="info" size="small">—</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- ========== 车位新增/编辑弹窗 ========== -->
    <el-dialog v-model="dialogVisible" :title="isEdit?'编辑车位':'新增车位'" width="450px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="车位编号" prop="spaceNo">
          <el-input v-model="form.spaceNo" :disabled="isEdit" placeholder="如 A001" />
          <span style="font-size:12px;color:#909399">格式：字母+3位数字，如 A001</span>
        </el-form-item>
        <el-form-item label="月费">
          <el-input-number v-model="form.monthlyFee" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- ========== 分配弹窗 ========== -->
    <el-dialog v-model="assignVisible" title="分配车位" width="480px">
      <el-form :model="assignForm" label-width="80px" inline>
        <el-form-item label="楼号">
          <el-select v-model="assignForm.buildingNo" placeholder="选择楼栋" style="width:130px">
            <el-option v-for="b in buildingList" :key="b.buildingNo" :label="b.buildingNo+'栋'" :value="b.buildingNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="层号">
          <el-input-number v-model="assignForm.floorNo" :min="1" style="width:100px" />
        </el-form-item>
        <el-form-item label="户号">
          <el-input-number v-model="assignForm.unitNo" :min="1" style="width:100px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="searchHousehold">查找</el-button>
        </el-form-item>
      </el-form>
      <el-divider />
      <el-form-item label="车牌号（可选）" v-if="foundOwner">
        <el-input v-model="assignForm.plateNo" placeholder="如 京A12345" style="width:200px" />
      </el-form-item>
      <div v-if="foundOwner" style="padding:10px;background:#f0f9eb;border-radius:4px">
        <p>找到住户：<b>{{ foundOwner.ownerName }}</b></p>
        <p>房号：<b>{{ foundOwner.room }}</b></p>
      </div>
      <div v-if="searchErr" style="padding:10px;background:#fef0f0;border-radius:4px;color:#F56C6C">{{ searchErr }}</div>
      <template #footer>
        <el-button @click="assignVisible=false">取消</el-button>
        <el-button type="primary" :disabled="!foundOwner" @click="doAssign">确认分配</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import request2 from '../../utils/request2'
import { getBuildingList } from '../../api/building'

const activeTab = ref('space')
const years = Array.from({length:5}, (_,i) => new Date().getFullYear() - 2 + i)

// ==================== 车位管理 ====================
const list = ref([]), loading = ref(false)
const loadSpaces = async () => { loading.value=true; try { list.value=(await request2.get('/parking-space/list')).data||[] } finally { loading.value=false } }

const dialogVisible = ref(false), isEdit = ref(false), formRef = ref(null)
const form = reactive({ spaceNo:'', monthlyFee:300 })
const rules = {
  spaceNo: [
    { required:true, message:'请输入车位编号', trigger:'blur' },
    { pattern:/^[A-Z]\d{3}$/, message:'格式：字母+3位数字，如 A001', trigger:'blur' }
  ]
}
const openAdd = () => { isEdit.value=false; form.spaceNo=''; form.monthlyFee=300; dialogVisible.value=true }
const openEdit = (row) => { isEdit.value=true; form.spaceNo=row.spaceNo; form.monthlyFee=row.monthlyFee; dialogVisible.value=true }
const handleSave = async () => {
  if (!(await formRef.value.validate().catch(()=>false))) return
  if (isEdit.value) { await request2.put('/parking-space',{...form}); ElMessage.success('修改成功') }
  else { await request2.post('/parking-space',{...form}); ElMessage.success('添加成功') }
  dialogVisible.value=false; loadSpaces()
}

const assignVisible = ref(false), assignSpaceNo = ref('')
const assignForm = reactive({ buildingNo:'', floorNo:null, unitNo:null, plateNo:'' })
const foundOwner = ref(null), searchErr = ref('')
const buildingList = ref([])

const openAssign = async (row) => {
  assignSpaceNo.value = row.spaceNo
  assignForm.buildingNo=''; assignForm.floorNo=null; assignForm.unitNo=null; assignForm.plateNo=''
  foundOwner.value=null; searchErr.value=''
  assignVisible.value=true
  try { buildingList.value = (await getBuildingList()).data||[] } catch(e){}
}
const searchHousehold = async () => {
  if (!assignForm.buildingNo || !assignForm.floorNo || !assignForm.unitNo) { searchErr.value='请完整填写楼号、层号、户号'; return }
  try { const res = await request2.get('/parking-space/find-household', { params: assignForm }); foundOwner.value = res.data; searchErr.value = '' }
  catch(e) { foundOwner.value=null; searchErr.value='未找到该房号对应的在住住户' }
}
const doAssign = async () => {
  if (!foundOwner.value) return
  const plateParam = assignForm.plateNo ? `?plateNo=${encodeURIComponent(assignForm.plateNo)}` : ''
  await request2.put(`/parking-space/${assignSpaceNo.value}/assign/${foundOwner.value.householdId}${plateParam}`)
  ElMessage.success('分配成功'); assignVisible.value=false; loadSpaces()
}
const handleRelease = async (row) => {
  await ElMessageBox.confirm('确定释放该车位？','确认',{type:'warning'})
  await request2.put(`/parking-space/${row.spaceNo}/release`); ElMessage.success('已释放'); loadSpaces()
}
const handleDelete = async (row) => {
  await ElMessageBox.confirm('确定删除车位 '+row.spaceNo+' 吗？','确认',{type:'warning'})
  await request2.delete(`/parking-space/${row.spaceNo}`); ElMessage.success('已删除'); loadSpaces()
}

// ==================== 停车费管理 ====================
const now = new Date()
const feeYear = ref(now.getFullYear()), feeMonth = ref(now.getMonth() + 1)
const feeStatusFilter = ref('全部')
const feeLoading = ref(false)
const feeSummary = reactive({ totalSpaces:0, paidCount:0, pendingCount:0, overdueCount:0, totalReceivable:0, totalCollected:0, totalOutstanding:0, details:[] })

const loadFeeSummary = async () => {
  feeLoading.value = true
  try {
    const res = await request2.get(`/parking-fee/report?year=${feeYear.value}&month=${feeMonth.value}&statusFilter=${feeStatusFilter.value}`)
    Object.assign(feeSummary, res.data)
  } catch(e) { Object.assign(feeSummary, { totalSpaces:0, paidCount:0, pendingCount:0, overdueCount:0, totalReceivable:0, totalCollected:0, totalOutstanding:0, details:[] }) }
  finally { feeLoading.value = false }
}

const payParkingFee = async (row) => {
  try {
    const res = await request2.post('/parking-fee/pay', { spaceNo: row.spaceNo, duration: 1, handler: '管理员' })
    ElMessage.success(`已缴 ¥${res.data.total}，单号：${res.data.billNo}`)
    loadFeeSummary()
  } catch(e) {}
}

const onTabChange = (tab) => {
  if (tab === 'fee') { loadFeeSummary() }
}

onMounted(loadSpaces)
</script>

<style scoped>
.reminder-card { border-left: 4px solid #F56C6C }
.reminder-header { display:flex; align-items:center }
.reminder-items { display:flex; flex-wrap:wrap; gap:8px }
.reminder-item {
  display:flex; align-items:center; gap:12px;
  padding:8px 14px; border-radius:6px; font-size:13px;
  flex:1; min-width:300px; max-width:420px;
}
.overdue-item { background:#fef0f0; border:1px solid #fde2e2 }
.nearly-due-item { background:#fdf6ec; border:1px solid #faecd8 }
</style>
