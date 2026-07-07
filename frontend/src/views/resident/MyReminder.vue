<template>
  <div class="page">
    <el-card v-loading="loading">
      <template #header>
        <span style="font-weight:600;font-size:16px">催缴提醒</span>
        <span style="margin-left:12px;color:#909399;font-size:13px" v-if="reminders.overdueCount + reminders.nearlyDueCount > 0">
          逾期 {{ reminders.overdueCount }} 笔 / 临期 {{ reminders.nearlyDueCount }} 笔
        </span>
      </template>

      <!-- 无提醒 -->
      <el-empty v-if="reminders.overdueCount === 0 && reminders.nearlyDueCount === 0"
        description="暂无催缴提醒，全部费用已缴清" />

      <!-- 逾期费用 -->
      <div v-if="reminders.overdueCount > 0" style="margin-bottom:20px">
        <h4 style="color:#F56C6C;margin-bottom:8px">
          <el-icon><WarningFilled /></el-icon> 已逾期费用（{{ reminders.overdueCount }} 笔）
        </h4>
        <div v-for="(r, i) in reminders.overdue" :key="'od'+i"
          class="reminder-item overdue-item">
          <div class="reminder-info">
            <span class="reminder-type">{{ r.feeType }}</span>
            <span>{{ r.year }}年{{ r.month }}月</span>
            <span class="reminder-amount">¥{{ r.amount }}</span>
          </div>
          <el-tag type="danger" size="small">已逾期 {{ r.daysOverdue }} 天</el-tag>
        </div>
      </div>

      <!-- 临期费用 -->
      <div v-if="reminders.nearlyDueCount > 0">
        <h4 style="color:#E6A23C;margin-bottom:8px">
          <el-icon><Clock /></el-icon> 临近截止日（{{ reminders.nearlyDueCount }} 笔）
        </h4>
        <div v-for="(r, i) in reminders.nearlyDue" :key="'nd'+i"
          class="reminder-item nearly-due-item">
          <div class="reminder-info">
            <span class="reminder-type">{{ r.feeType }}</span>
            <span>{{ r.year }}年{{ r.month }}月</span>
            <span class="reminder-amount">¥{{ r.amount }}</span>
          </div>
          <el-tag type="warning" size="small">距截止 {{ r.daysUntilDue }} 天</el-tag>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { WarningFilled, Clock } from '@element-plus/icons-vue'
import request2 from '../../utils/request2'
import state from '../../stores/user'

const householdId = Number(state.refId)
const loading = ref(false)
const reminders = ref({ overdueCount: 0, nearlyDueCount: 0, overdue: [], nearlyDue: [] })

const load = async () => {
  loading.value = true
  try {
    const res = await request2.get(`/property-fee/reminders/${householdId}`)
    reminders.value = res.data || { overdueCount: 0, nearlyDueCount: 0, overdue: [], nearlyDue: [] }
  } catch (e) {
    reminders.value = { overdueCount: 0, nearlyDueCount: 0, overdue: [], nearlyDue: [] }
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.reminder-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-radius: 6px;
  margin-bottom: 6px;
}
.overdue-item {
  background: #fef0f0;
  border-left: 3px solid #F56C6C;
}
.nearly-due-item {
  background: #fdf6ec;
  border-left: 3px solid #E6A23C;
}
.reminder-info {
  display: flex;
  gap: 16px;
  align-items: center;
  font-size: 14px;
  color: #303133;
}
.reminder-type {
  font-weight: 600;
  min-width: 120px;
}
.reminder-amount {
  font-weight: 600;
  color: #409EFF;
}
</style>
