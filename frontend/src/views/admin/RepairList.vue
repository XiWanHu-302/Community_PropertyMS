<template>
  <div class="page">
    <!-- 统计卡片 -->
    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="8">
        <div class="stat-card" style="border-left-color:#E6A23C">
          <div class="stat-value">{{ stats.pendingCount }}</div>
          <div class="stat-title">待维修</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card" style="border-left-color:#67C23A">
          <div class="stat-value">{{ stats.completedCount }}</div>
          <div class="stat-title">已完成</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card" style="border-left-color:#909399">
          <div class="stat-value">{{ stats.cancelledCount }}</div>
          <div class="stat-title">已取消</div>
        </div>
      </el-col>
    </el-row>

    <el-card>
      <div class="page-card-header">
        <span style="font-weight:600">维修工单</span>
        <span class="page-card-total">共 {{ filteredData.length }} 条</span>
      </div>

      <!-- 筛选栏 -->
      <el-row :gutter="12" style="margin: 12px 0">
        <el-col :span="4">
          <el-select v-model="filterStatus" placeholder="工单状态" clearable style="width:100%">
            <el-option label="待维修" :value="0" />
            <el-option label="已完成" :value="1" />
            <el-option label="已取消" :value="2" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="filterBuilding" placeholder="楼号" clearable style="width:100%">
            <el-option v-for="b in buildingOptions" :key="b" :label="b + '号楼'" :value="b" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-input v-model="filterKeyword" placeholder="搜索内容/户主/住号" clearable @input="onSearchInput" />
        </el-col>
        <el-col :span="4">
          <el-button type="primary" @click="loadData">查询</el-button>
        </el-col>
      </el-row>

      <!-- 工单表格 -->
      <el-table :data="pagedData" border stripe v-loading="loading" style="margin-top:10px">
        <el-table-column prop="repairId" label="工单号" width="80" />
        <el-table-column prop="room" label="住号" width="100" />
        <el-table-column prop="ownerName" label="户主" width="90" />
        <el-table-column prop="content" label="维修内容" min-width="160" show-overflow-tooltip />
        <el-table-column prop="reportDate" label="报修日期" width="110" />
        <el-table-column label="状态" width="90">
          <template #default="{row}">
            <el-tag :type="statusTagType(row.status)" size="small">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="repairPerson" label="维修人" width="100">
          <template #default="{row}">{{ row.repairPerson || '—' }}</template>
        </el-table-column>
        <el-table-column prop="repairDate" label="维修日期" width="110">
          <template #default="{row}">{{ row.repairDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="金额" width="90">
          <template #default="{row}">{{ row.amount ? '¥' + row.amount : '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{row}">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 0" size="small" type="warning" @click="openAssign(row)">分配</el-button>
            <template v-if="row.status === 0">
              <el-popconfirm title="确定取消该工单？" @confirm="handleCancel(row)">
                <template #reference><el-button size="small" type="danger">取消</el-button></template>
              </el-popconfirm>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-if="filteredData.length > 0"
        style="margin-top:16px;justify-content:flex-end"
        background layout="total, prev, pager, next"
        :total="filteredData.length"
        v-model:current-page="currentPage"
        :page-size="pageSize"
      />
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="工单详情" width="550px">
      <el-form :model="detail" label-width="100px">
        <el-form-item label="工单号">{{ detail.repairId }}</el-form-item>
        <el-form-item label="住号">{{ detail.room }}</el-form-item>
        <el-form-item label="户主">{{ detail.ownerName }}</el-form-item>
        <el-form-item label="联系电话">{{ detail.phone }}</el-form-item>
        <el-form-item label="维修内容">{{ detail.content }}</el-form-item>
        <el-form-item label="报修日期">{{ detail.reportDate }}</el-form-item>
        <el-form-item label="状态">
          <el-tag :type="statusTagType(detail.status)" size="small">{{ detail.statusText }}</el-tag>
        </el-form-item>
        <el-form-item label="维修人">{{ detail.repairPerson || '—' }}</el-form-item>
        <el-form-item label="维修日期">{{ detail.repairDate || '—' }}</el-form-item>
        <el-form-item label="维修金额">{{ detail.amount ? '¥' + detail.amount : '—' }}</el-form-item>
        <el-form-item label="基金支出">{{ detail.isFromFund === 1 ? '是' : '否' }}</el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 分配维修员弹窗 -->
    <el-dialog v-model="assignVisible" title="分配维修员" width="450px">
      <el-form :model="assignForm" label-width="80px">
        <el-form-item label="工单号">{{ assignForm.repairId }}</el-form-item>
        <el-form-item label="住号">{{ assignForm.room }}</el-form-item>
        <el-form-item label="维修内容">{{ assignForm.content }}</el-form-item>
        <el-form-item label="指派给" prop="repairPerson">
          <el-select v-model="assignForm.repairPerson" placeholder="请选择维修员" style="width:100%">
            <el-option v-for="s in staffList" :key="s.workerNo" :label="s.realName" :value="s.realName" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="handleAssign">确定分配</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listRepairs, getRepairStats, getStaffList, assignRepair, cancelRepair } from '../../api/repair'

const loading = ref(false)
const tableData = ref([])
const staffList = ref([])
const buildingOptions = ref(['28', '29', '30'])

// 统计
const stats = reactive({ pendingCount: 0, completedCount: 0, cancelledCount: 0 })

// 筛选
const filterStatus = ref(null)
const filterBuilding = ref('')
const filterKeyword = ref('')
let searchTimer = null

// 分页
const currentPage = ref(1)
const pageSize = ref(10)

// 前端筛选后的数据
const filteredData = computed(() => {
  let list = tableData.value
  if (filterBuilding.value) {
    list = list.filter(r => r.buildingNo === filterBuilding.value)
  }
  if (filterKeyword.value) {
    const kw = filterKeyword.value.toLowerCase()
    list = list.filter(r =>
      (r.content && r.content.toLowerCase().includes(kw)) ||
      (r.ownerName && r.ownerName.toLowerCase().includes(kw)) ||
      (r.room && r.room.toLowerCase().includes(kw))
    )
  }
  return list
})

// 分页后的数据
const pagedData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredData.value.slice(start, start + pageSize.value)
})

// 状态标签颜色
const statusTagType = (status) => {
  if (status === 0) return 'warning'
  if (status === 1) return 'success'
  return 'info'
}

// 详情弹窗
const detailVisible = ref(false)
const detail = reactive({})

const openDetail = (row) => {
  Object.assign(detail, row)
  detailVisible.value = true
}

// 分配弹窗
const assignVisible = ref(false)
const assigning = ref(false)
const assignForm = reactive({ repairId: null, room: '', content: '', repairPerson: '' })

const openAssign = (row) => {
  assignForm.repairId = row.repairId
  assignForm.room = row.room
  assignForm.content = row.content
  assignForm.repairPerson = ''
  assignVisible.value = true
}

const handleAssign = async () => {
  if (!assignForm.repairPerson) {
    ElMessage.warning('请选择维修员')
    return
  }
  assigning.value = true
  try {
    await assignRepair(assignForm.repairId, { repairPerson: assignForm.repairPerson })
    ElMessage.success('分配成功')
    assignVisible.value = false
    loadData()
    loadStats()
  } finally {
    assigning.value = false
  }
}

// 取消工单
const handleCancel = async (row) => {
  try {
    await cancelRepair(row.repairId)
    ElMessage.success('已取消')
    loadData()
    loadStats()
  } catch { /* 已弹出错误提示 */ }
}

// 搜索防抖
const onSearchInput = () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => loadData(), 400)
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const params = {}
    if (filterStatus.value !== null && filterStatus.value !== '') params.status = filterStatus.value
    const res = await listRepairs(params)
    tableData.value = Array.isArray(res.data) ? res.data : []
    currentPage.value = 1
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

// 加载统计
const loadStats = async () => {
  try {
    const res = await getRepairStats()
    if (res.data) Object.assign(stats, res.data)
  } catch { /* ignore */ }
}

// 加载维修员列表
const loadStaff = async () => {
  try {
    const res = await getStaffList()
    staffList.value = Array.isArray(res.data) ? res.data : []
  } catch { /* ignore */ }
}

// 状态筛选变化时重新请求后端
watch(filterStatus, () => {
  currentPage.value = 1
  loadData()
})

onMounted(() => {
  loadData()
  loadStats()
  loadStaff()
})
</script>

<style scoped>
.stat-card {
  background:#fff; border-radius:6px; padding:16px 20px;
  border-left:4px solid #ccc; box-shadow:0 1px 4px rgba(0,0,0,0.08);
}
.stat-value { font-size:28px; font-weight:700; color:#303133; }
.stat-title { font-size:13px; color:#909399; margin-top:4px; }
</style>
