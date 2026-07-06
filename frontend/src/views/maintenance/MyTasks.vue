<template>
  <div class="page">
    <el-card>
      <div class="page-card-header">
        <span style="font-weight:600">我的工单</span>
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
        <el-col :span="6">
          <el-input v-model="filterKeyword" placeholder="搜索内容/户主/住号" clearable />
        </el-col>
      </el-row>

      <!-- 加载失败提示 -->
      <el-empty v-if="loadError && !loading" description="数据加载失败，请确认后端服务已启动">
        <el-button type="primary" @click="loadData">重新加载</el-button>
      </el-empty>

      <!-- 工单表格 -->
      <el-table v-if="!loadError" :data="pagedData" border stripe v-loading="loading" style="margin-top:10px">
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
        <el-table-column prop="repairDate" label="维修日期" width="110">
          <template #default="{row}">{{ row.repairDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="金额" width="90">
          <template #default="{row}">{{ row.amount ? '¥' + row.amount : '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{row}">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 0" size="small" type="success" @click="openComplete(row)">完工</el-button>
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
        <el-form-item label="基金支出">
          <template v-if="detail.status === 1">{{ detail.isFromFund === 1 ? '是' : '否' }}</template>
          <template v-else>—</template>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 完工登记弹窗 -->
    <el-dialog v-model="completeVisible" title="完工登记" width="480px">
      <el-form ref="completeFormRef" :model="completeForm" :rules="completeRules" label-width="120px">
        <el-form-item label="工单号">{{ completeForm.repairId }}</el-form-item>
        <el-form-item label="住号">{{ completeForm.room }}</el-form-item>
        <el-form-item label="维修内容">{{ completeForm.content }}</el-form-item>
        <el-form-item label="维修日期" prop="repairDate">
          <el-date-picker v-model="completeForm.repairDate" type="date" placeholder="选择日期"
            value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="维修金额（元）" prop="amount">
          <el-input-number v-model="completeForm.amount" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="从维修基金支出">
          <el-switch v-model="completeForm.isFromFund" :active-value="1" :inactive-value="0" />
          <span style="margin-left:8px;color:#909399;font-size:12px">
            {{ completeForm.isFromFund === 1 ? '将从住户维修基金余额中扣减' : '住户自费' }}
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="primary" :loading="completing" @click="handleComplete">确认完工</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { myTasks, completeRepair } from '../../api/repair'

const loading = ref(false)
const loadError = ref(false)
const tableData = ref([])

// 筛选
const filterStatus = ref(null)
const filterKeyword = ref('')

// 分页
const currentPage = ref(1)
const pageSize = ref(10)

// 前端筛选
const filteredData = computed(() => {
  let list = tableData.value
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

const pagedData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredData.value.slice(start, start + pageSize.value)
})

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

// 完工弹窗
const completeVisible = ref(false)
const completing = ref(false)
const completeFormRef = ref(null)
const completeForm = reactive({
  repairId: null, room: '', content: '',
  repairDate: '', amount: 0, isFromFund: 0
})

const completeRules = {
  repairDate: [{ required: true, message: '请选择维修日期', trigger: 'change' }],
  amount: [{ required: true, message: '请输入维修金额', trigger: 'blur' }]
}

const openComplete = (row) => {
  completeForm.repairId = row.repairId
  completeForm.room = row.room
  completeForm.content = row.content
  completeForm.repairDate = new Date().toISOString().slice(0, 10)
  completeForm.amount = 0
  completeForm.isFromFund = 0
  completeVisible.value = true
}

const handleComplete = async () => {
  if (!completeFormRef.value) return
  try {
    await completeFormRef.value.validate()
  } catch { return }

  completing.value = true
  try {
    await completeRepair(completeForm.repairId, {
      repairDate: completeForm.repairDate,
      amount: completeForm.amount,
      isFromFund: completeForm.isFromFund
    })
    ElMessage.success('维修已完工')
    completeVisible.value = false
    loadData()
  } finally {
    completing.value = false
  }
}

// 加载数据
const loadData = async () => {
  loading.value = true
  loadError.value = false
  try {
    const params = {}
    if (filterStatus.value !== null && filterStatus.value !== '') params.status = filterStatus.value
    const res = await myTasks(params)
    tableData.value = Array.isArray(res.data) ? res.data : []
    currentPage.value = 1
  } catch {
    tableData.value = []
    loadError.value = true
  } finally {
    loading.value = false
  }
}

watch(filterStatus, () => {
  currentPage.value = 1
  loadData()
})

onMounted(() => loadData())
</script>
