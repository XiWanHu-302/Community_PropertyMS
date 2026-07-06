<template>
  <div class="page">
    <!-- 筛选栏 -->
    <el-card style="margin-bottom:16px">
      <el-row :gutter="12" align="middle">
        <el-col :span="4">
          <el-form-item label="年份" label-width="50px" style="margin-bottom:0">
            <el-input-number v-model="year" :min="2020" :max="2100" style="width:100%" />
          </el-form-item>
        </el-col>
        <el-col :span="3">
          <el-radio-group v-model="period" @change="loadData">
            <el-radio-button value="month">按月</el-radio-button>
            <el-radio-button value="quarter">按季度</el-radio-button>
            <el-radio-button value="year">按年</el-radio-button>
          </el-radio-group>
        </el-col>
        <el-col :span="3">
          <el-select v-if="period === 'month'" v-model="month" placeholder="选择月份" style="width:100%">
            <el-option v-for="m in 12" :key="m" :label="m + '月'" :value="m" />
          </el-select>
          <el-select v-if="period === 'quarter'" v-model="quarter" placeholder="选择季度" style="width:100%">
            <el-option label="第一季度 (1-3月)" :value="1" />
            <el-option label="第二季度 (4-6月)" :value="2" />
            <el-option label="第三季度 (7-9月)" :value="3" />
            <el-option label="第四季度 (10-12月)" :value="4" />
          </el-select>
        </el-col>
        <el-col :span="3">
          <el-button type="primary" @click="loadData">查询</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 加载失败 -->
    <el-empty v-if="loadError" description="数据加载失败，请确认后端服务已启动">
      <el-button type="primary" @click="loadData">重新加载</el-button>
    </el-empty>

    <template v-if="!loadError">
      <!-- 汇总统计卡片 -->
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="6">
          <div class="stat-card" style="border-left-color:#409EFF">
            <div class="stat-label">应交总额</div>
            <div class="stat-value">¥{{ fmt(data.totalReceivable) }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card" style="border-left-color:#67C23A">
            <div class="stat-label">实收总额</div>
            <div class="stat-value">¥{{ fmt(data.totalCollected) }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card" style="border-left-color:#F56C6C">
            <div class="stat-label">未交总额</div>
            <div class="stat-value">¥{{ fmt(data.totalOutstanding) }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card" style="border-left-color:#E6A23C">
            <div class="stat-label">回收率</div>
            <div class="stat-value">{{ collectionRate }}%</div>
          </div>
        </el-col>
      </el-row>

      <!-- 缴费状态分布 -->
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="8">
          <el-tag type="success" size="large">已缴：{{ data.paidCount }} 个车位</el-tag>
        </el-col>
        <el-col :span="8">
          <el-tag type="warning" size="large">待缴：{{ data.pendingCount }} 个车位</el-tag>
        </el-col>
        <el-col :span="8">
          <el-tag type="danger" size="large">逾期：{{ data.overdueCount }} 个车位</el-tag>
        </el-col>
      </el-row>

      <!-- 明细表格 -->
      <el-card>
        <div class="page-card-header">
          <span style="font-weight:600">{{ periodLabel }}明细</span>
          <span class="page-card-total">共 {{ data.details ? data.details.length : 0 }} 个车位</span>
        </div>
        <el-table :data="data.details" border stripe v-loading="loading" style="margin-top:10px"
          :empty-text="data.details && data.details.length === 0 ? '该时段暂无数据' : ''"
          show-summary>
          <el-table-column prop="spaceNo" label="车位编号" width="100" />
          <el-table-column prop="plateNo" label="车牌号" width="120">
            <template #default="{row}">{{ row.plateNo || '—' }}</template>
          </el-table-column>
          <el-table-column prop="room" label="所属住号" width="100" />
          <el-table-column prop="ownerName" label="户主" width="90" />
          <el-table-column label="应缴金额" width="120" sortable prop="amount">
            <template #default="{row}">¥{{ fmt(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="已缴金额" width="120">
            <template #default="{row}">¥{{ fmt(row.collectedAmount || 0) }}</template>
          </el-table-column>
          <el-table-column label="未缴金额" width="120">
            <template #default="{row}">¥{{ fmt((row.amount || 0) - (row.collectedAmount || 0)) }}</template>
          </el-table-column>
          <el-table-column prop="statusText" label="缴费状态" width="110">
            <template #default="{row}">
              <el-tag :type="statusTag(row.statusText)" size="small">{{ row.statusText }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="统计月数" width="90">
            <template #default="{row}">{{ row.monthCount || 1 }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import request2 from '../../utils/request2'

const loading = ref(false)
const loadError = ref(false)

const year = ref(new Date().getFullYear())
const month = ref(new Date().getMonth() + 1)
const quarter = ref(Math.floor(new Date().getMonth() / 3) + 1)
const period = ref('month')

const data = reactive({
  totalReceivable: 0, totalCollected: 0, totalOutstanding: 0,
  paidCount: 0, pendingCount: 0, overdueCount: 0,
  details: []
})

const periodLabel = computed(() => {
  if (period.value === 'year') return year.value + '年全年'
  if (period.value === 'quarter') return year.value + '年第' + quarter.value + '季度'
  return year.value + '年' + month.value + '月'
})

const collectionRate = computed(() => {
  if (!data.totalReceivable || data.totalReceivable === 0) return 0
  return ((data.totalCollected / data.totalReceivable) * 100).toFixed(1)
})

const statusTag = (status) => {
  if (status === '已缴') return 'success'
  if (status.includes('逾期')) return 'danger'
  if (status.includes('待缴')) return 'warning'
  return 'info'
}

const fmt = (val) => {
  if (val == null || val === 0) return '0.00'
  return Number(val).toFixed(2)
}

const loadData = async () => {
  loading.value = true
  loadError.value = false
  try {
    const params = { year: year.value, period: period.value }
    if (period.value === 'month') params.month = month.value
    if (period.value === 'quarter') params.quarter = quarter.value
    const res = await request2.get('/parking-fee/summary', { params })
    if (res.data) Object.assign(data, res.data)
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

watch([year], () => loadData())

onMounted(() => loadData())
</script>

<style scoped>
.stat-card {
  background: #fff; border-radius: 6px; padding: 20px 24px;
  border-left: 4px solid #ccc; box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}
.stat-label { font-size: 13px; color: #909399; margin-bottom: 8px; }
.stat-value { font-size: 26px; font-weight: 700; color: #303133; }
</style>
