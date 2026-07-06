<template>
  <div class="dashboard">
    <h2>{{ greeting }}</h2>

    <!-- 统计卡片（所有角色统一显示） -->
    <el-row :gutter="20" class="stats-row">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card stat-blue">
          <div class="stat-inner">
            <div class="stat-info">
              <div class="stat-label">在住总户数</div>
              <div class="stat-value">{{ stats.householdCount }}</div>
            </div>
            <el-icon class="stat-icon"><UserFilled /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card stat-green">
          <div class="stat-inner">
            <div class="stat-info">
              <div class="stat-label">楼栋总数</div>
              <div class="stat-value">{{ stats.buildingCount }}</div>
            </div>
            <el-icon class="stat-icon"><OfficeBuilding /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card stat-orange">
          <div class="stat-inner">
            <div class="stat-info">
              <div class="stat-label">停车位总数</div>
              <div class="stat-value">{{ stats.parkingCount }}</div>
            </div>
            <el-icon class="stat-icon"><Van /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="stat-card stat-red">
          <div class="stat-inner">
            <div class="stat-info">
              <div class="stat-label">待维修工单</div>
              <div class="stat-value">{{ stats.pendingRepair }}</div>
            </div>
            <el-icon class="stat-icon"><Tools /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { UserFilled, OfficeBuilding, Van, Tools } from '@element-plus/icons-vue'
import state from '../../stores/user'
import request from '../../utils/request'

const greeting = computed(() => {
  const map = { admin: '管理员', maintenance: '维修员', resident: '业主' }
  return `欢迎回来，${map[state.role] || ''} ${state.realName}`
})

const stats = ref({
  householdCount: 0,
  buildingCount: 0,
  parkingCount: 0,
  pendingRepair: 0
})

const loadStats = async () => {
  try {
    const res = await request.get('/dashboard/stats')
    if (res.data) {
      stats.value = { ...stats.value, ...res.data }
    }
  } catch (e) { /* ignore */ }
}

// 所有角色统一加载统计数据
onMounted(loadStats)
</script>

<style scoped>
.dashboard h2 {
  margin: 0 0 20px 0;
  color: #303133;
  font-weight: 600;
}

.stats-row { margin-bottom: 20px; }

.stat-card {
  margin-bottom: 16px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.stat-card:hover { transform: translateY(-2px); }

.stat-inner {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-label { font-size: 14px; color: #909399; margin-bottom: 8px; }
.stat-value { font-size: 32px; font-weight: 700; }
.stat-icon { font-size: 36px; opacity: 0.3; }

.stat-blue .stat-value, .stat-blue .stat-icon { color: #409EFF; }
.stat-green .stat-value, .stat-green .stat-icon { color: #67C23A; }
.stat-orange .stat-value, .stat-orange .stat-icon { color: #E6A23C; }
.stat-red .stat-value, .stat-red .stat-icon { color: #F56C6C; }
</style>
