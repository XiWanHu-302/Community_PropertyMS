<template>
  <div class="page">
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- ========== 月度报表 ========== -->
      <el-tab-pane label="月度报表" name="report">
        <el-card>
          <div class="page-card-header">
            <div>
              <el-form inline>
                <el-form-item label="年份">
                  <el-select v-model="year" style="width:120px" @change="load">
                    <el-option v-for="y in years" :key="y" :label="y+'年'" :value="y" />
                  </el-select>
                </el-form-item>
                <el-form-item label="月份">
                  <el-select v-model="month" style="width:100px" @change="load">
                    <el-option v-for="m in 12" :key="m" :label="m+'月'" :value="m" />
                  </el-select>
                </el-form-item>
                <el-form-item label="状态">
                  <el-select v-model="statusFilter" style="width:100px" @change="load">
                    <el-option label="全部" value="全部" />
                    <el-option label="已缴" value="已缴" />
                    <el-option label="待缴" value="待缴" />
                    <el-option label="逾期" value="逾期" />
                  </el-select>
                </el-form-item>
                <el-form-item label="搜索住号">
                  <el-input v-model="searchRoom" placeholder="如 28 或 28-1301" style="width:160px" clearable />
                </el-form-item>
              </el-form>
            </div>
            <div>
              <span class="page-card-total">
                共 {{ filteredReport.length }} 户 |
                已缴 <b style="color:#67C23A">{{ filteredReport.filter(r=>r.statusText==='已缴').length }}</b> |
                待缴 <b style="color:#E6A23C">{{ filteredReport.filter(r=>r.statusText==='待缴').length }}</b> |
                逾期 <b style="color:#F56C6C">{{ filteredReport.filter(r=>r.statusText==='逾期').length }}</b>
              </span>
            </div>
          </div>
          <el-table :data="filteredReport" border stripe v-loading="loading" style="margin-top:10px">
            <el-table-column prop="room" label="住号" width="110" />
            <el-table-column prop="ownerName" label="户主" width="100" />
            <el-table-column prop="amount" label="应缴金额" width="110" />
            <el-table-column label="状态" width="80">
              <template #default="{row}">
                <el-tag v-if="row.statusText==='已缴'" type="success" size="small">已缴</el-tag>
                <el-tag v-else-if="row.statusText==='逾期'" type="danger" size="small">逾期</el-tag>
                <el-tag v-else type="warning" size="small">待缴</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="payDate" label="缴费日期" width="120" />
            <el-table-column prop="handler" label="经手人" width="90" />
            <el-table-column prop="billNo" label="缴费单号" min-width="170" />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{row}">
                <el-button v-if="row.statusText!=='已缴'" size="small" type="success" @click="payOne(row)">缴费</el-button>
                <el-tag v-else type="info" size="small">—</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ========== 缴费设置 ========== -->
      <el-tab-pane label="缴费设置" name="settings">
        <el-card>
          <el-form label-width="140px">
            <el-form-item label="每月缴费截止日">
              <el-input-number v-model="deadlineDay" :min="1" :max="28" style="width:120px" />
              <span style="margin-left:8px;color:#909399">号（每月几号为缴费截止日，超过即为逾期）</span>
            </el-form-item>
            <el-form-item label="临期提醒天数">
              <span style="color:#909399">截止日前 7 天开始提醒</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveDeadline">保存设置</el-button>
              <span v-if="deadlineDay !== savedDeadlineDay" style="margin-left:12px;color:#E6A23C;font-size:13px">
                ⚠ 有未保存的修改
              </span>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request2 from '../../utils/request2'

const activeTab = ref('report')
const now = new Date()
const year = ref(now.getFullYear()), month = ref(now.getMonth() + 1)
const years = Array.from({length:5}, (_,i) => now.getFullYear() - 2 + i)
const report = ref([]), loading = ref(false)
const statusFilter = ref('全部')
const searchRoom = ref('')

// 前端按住号过滤
const filteredReport = computed(() => {
  if (!searchRoom.value) return report.value
  const kw = searchRoom.value.trim()
  return report.value.filter(r => r.room && r.room.includes(kw))
})
const deadlineDay = ref(1)
const savedDeadlineDay = ref(1)   // 已保存的值，用于对比是否修改

// 月度报表
const load = async () => {
  loading.value = true
  try { report.value = (await request2.get(`/property-fee/report?year=${year.value}&month=${month.value}&statusFilter=${statusFilter.value}`)).data || [] }
  catch(e) { report.value = [] }
  finally { loading.value = false }
}

// 缴费设置
const loadDeadline = async () => {
  try { const res = await request2.get('/property-fee/deadline'); deadlineDay.value = res.data.deadlineDay; savedDeadlineDay.value = res.data.deadlineDay }
  catch(e) {}
}
const saveDeadline = async () => {
  await request2.put('/property-fee/deadline', { deadlineDay: deadlineDay.value })
  savedDeadlineDay.value = deadlineDay.value
  ElMessage.success(`截止日已更新为每月 ${deadlineDay.value} 号`)
  load()
}

// 切换页签自动刷新 + 未保存提醒
const confirmLeave = async () => {
  if (deadlineDay.value !== savedDeadlineDay.value) {
    try {
      await ElMessageBox.confirm('截止日有未保存的修改，是否保存？', '提示', {
        confirmButtonText: '保存', cancelButtonText: '不保存', type: 'warning'
      })
      await saveDeadline()
    } catch(e) {
      // 选了"不保存"，恢复原值
      deadlineDay.value = savedDeadlineDay.value
    }
  }
}
const onTabChange = async (tab) => {
  if (activeTab.value === 'settings') await confirmLeave()
  activeTab.value = tab
  if (tab === 'report') load()
}

// 缴费
const payOne = async (row) => {
  try {
    const res = await request2.post('/property-fee/pay', { householdId: row.householdId, duration: 1, handler: '管理员' })
    ElMessage.success(`已缴 ¥${res.data.total}，单号：${res.data.billNo}`)
    load()
  } catch(e) {}
}

onMounted(() => { load(); loadDeadline() })
</script>

<style scoped>
.reminder-card { border-left: 4px solid #F56C6C }
.reminder-header { display:flex; align-items:center }
</style>
