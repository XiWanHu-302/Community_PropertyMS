<template>
  <div class="dashboard">
    <!-- ========== 欢迎横幅 + 物业介绍 ========== -->
    <div class="hero-banner">
      <div class="hero-left">
        <h1 class="hero-title">{{ greeting }}</h1>
        <p class="hero-desc">
          智慧物业，用心服务每一家。本系统覆盖楼栋管理、住户服务、费用收缴、维修工单、停车管理等全方位物业运营场景，
          助力社区管理数字化、高效化。
        </p>
        <div class="hero-features">
          <span><el-icon><OfficeBuilding /></el-icon> {{ stats.buildingCount }} 栋楼</span>
          <span><el-icon><UserFilled /></el-icon> {{ stats.householdCount }} 户在住</span>
          <span><el-icon><Van /></el-icon> {{ stats.parkingCount }} 个车位</span>
        </div>
      </div>
      <div class="hero-right">
        <!-- 替换为真实图片：/images/hero-building.jpg -->
        <img src="/images/hero-building.jpg" alt="社区" class="hero-img" @error="onImgError" />
      </div>
    </div>

    <!-- ========== 统计卡片 ========== -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="6">
        <div class="stat-card card-blue" @click="$router.push('/household/manage')">
          <div class="stat-card-icon"><el-icon :size="28"><UserFilled /></el-icon></div>
          <div class="stat-card-body">
            <div class="stat-card-value">{{ stats.householdCount }}</div>
            <div class="stat-card-label">在住总户数</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card card-green" @click="$router.push('/building/manage')">
          <div class="stat-card-icon"><el-icon :size="28"><OfficeBuilding /></el-icon></div>
          <div class="stat-card-body">
            <div class="stat-card-value">{{ stats.buildingCount }}</div>
            <div class="stat-card-label">楼栋总数</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card card-orange" @click="$router.push('/parking/manage')">
          <div class="stat-card-icon"><el-icon :size="28"><Van /></el-icon></div>
          <div class="stat-card-body">
            <div class="stat-card-value">{{ stats.parkingCount }}</div>
            <div class="stat-card-label">停车位总数</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card card-red" @click="$router.push('/repair/list')">
          <div class="stat-card-icon"><el-icon :size="28"><Tools /></el-icon></div>
          <div class="stat-card-body">
            <div class="stat-card-value">{{ stats.pendingRepair }}</div>
            <div class="stat-card-label">待维修工单</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- ========== 饼状图 + 快捷入口 ========== -->
    <el-row :gutter="16" class="charts-row">
      <el-col :xs="24" :md="8">
        <el-card shadow="hover" class="chart-card">
          <template #header><span class="chart-title">🚗 车位使用情况</span></template>
          <div ref="parkingChartRef" class="chart-box"></div>
          <div class="chart-legend">
            <span class="legend-item"><i class="dot" style="background:#67C23A"></i> 已租 {{ parkingRented }} 个</span>
            <span class="legend-item"><i class="dot" style="background:#E6A23C"></i> 空闲 {{ parkingFree }} 个</span>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="hover" class="chart-card">
          <template #header><span class="chart-title">🔧 维修工单概况</span></template>
          <div ref="repairChartRef" class="chart-box"></div>
          <div class="chart-legend">
            <span class="legend-item"><i class="dot" style="background:#67C23A"></i> 已完成 {{ repairStats.completed }}</span>
            <span class="legend-item"><i class="dot" style="background:#E6A23C"></i> 待维修 {{ repairStats.pending }}</span>
            <span class="legend-item"><i class="dot" style="background:#909399"></i> 已取消 {{ repairStats.cancelled }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card shadow="hover" class="chart-card">
          <template #header><span class="chart-title">📋 系统概况</span></template>
          <div class="overview-list">
            <div class="overview-item">
              <el-icon :size="20" color="#409EFF"><OfficeBuilding /></el-icon>
              <span class="overview-label">楼栋数量</span>
              <strong>{{ stats.buildingCount }}</strong>
            </div>
            <div class="overview-item">
              <el-icon :size="20" color="#67C23A"><UserFilled /></el-icon>
              <span class="overview-label">在住户数</span>
              <strong>{{ stats.householdCount }}</strong>
            </div>
            <div class="overview-item">
              <el-icon :size="20" color="#E6A23C"><Van /></el-icon>
              <span class="overview-label">车位总数（已租 / 空闲）</span>
              <strong>{{ parkingRented }} / {{ parkingFree }}</strong>
            </div>
            <div class="overview-item">
              <el-icon :size="20" color="#F56C6C"><Tools /></el-icon>
              <span class="overview-label">待维修工单</span>
              <strong>{{ stats.pendingRepair }}</strong>
            </div>
            <div class="overview-item">
              <el-icon :size="20" color="#909399"><Clock /></el-icon>
              <span class="overview-label">当前时间</span>
              <strong>{{ nowStr }}</strong>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ========== 页脚 ========== -->
    <div class="dashboard-footer">
      <span>小区物业管理系统 v1.0</span>
      <span style="margin:0 12px;color:#dcdfe6">|</span>
      <span>服务热线：400-888-8888</span>
      <span style="margin:0 12px;color:#dcdfe6">|</span>
      <span>服务时间：周一至周日 8:00-20:00</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import { UserFilled, OfficeBuilding, Van, Tools, Clock } from '@element-plus/icons-vue'
import state from '../../stores/user'
import request from '../../utils/request'
import request2 from '../../utils/request2'

const nowStr = computed(() => new Date().toLocaleString('zh-CN'))

// 图片加载失败时隐藏（保留CSS备用）
const onImgError = (e) => { e.target.style.display = 'none' }

const greeting = computed(() => {
  const hour = new Date().getHours()
  const timeGreet = hour < 12 ? '早上好' : hour < 18 ? '下午好' : '晚上好'
  return `${timeGreet}，${state.realName}`
})

// ==================== 统计数据 ====================
const stats = ref({ householdCount: 0, buildingCount: 0, parkingCount: 0, pendingRepair: 0 })

// ==================== 停车位数据 ====================
const parkingRented = ref(0)
const parkingFree = ref(0)
const parkingChartRef = ref(null)
let parkingChart = null

const loadParkingStats = async () => {
  try {
    const res = await request2.get('/parking-space/list')
    const all = res.data || []
    parkingRented.value = all.filter(s => s.status === 1).length
    parkingFree.value = all.filter(s => s.status === 0).length
    stats.value.parkingCount = all.length
  } catch (e) { /* ignore */ }
}

const initParkingChart = () => {
  if (!parkingChartRef.value) return
  if (parkingChart) parkingChart.dispose()
  parkingChart = echarts.init(parkingChartRef.value)
  parkingChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 个 ({d}%)' },
    series: [{
      type: 'pie', radius: ['55%', '78%'], center: ['50%', '50%'],
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
      data: [
        { value: parkingRented.value, name: '已租', itemStyle: { color: '#67C23A' } },
        { value: parkingFree.value, name: '空闲', itemStyle: { color: '#E6A23C' } }
      ]
    }]
  })
}

// ==================== 维修工单数据 ====================
const repairStats = ref({ pending: 0, completed: 0, cancelled: 0 })
const repairChartRef = ref(null)
let repairChart = null

const loadRepairStats = async () => {
  try {
    const res = await request.get('/repair/stats')
    if (res.data) {
      repairStats.value = {
        pending: res.data.pendingCount || 0,
        completed: res.data.completedCount || 0,
        cancelled: res.data.cancelledCount || 0
      }
      stats.value.pendingRepair = repairStats.value.pending
    }
  } catch (e) { /* ignore */ }
}

const initRepairChart = () => {
  if (!repairChartRef.value) return
  if (repairChart) repairChart.dispose()
  repairChart = echarts.init(repairChartRef.value)
  repairChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 单 ({d}%)' },
    series: [{
      type: 'pie', radius: ['55%', '78%'], center: ['50%', '50%'],
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
      data: [
        { value: repairStats.value.completed, name: '已完成', itemStyle: { color: '#67C23A' } },
        { value: repairStats.value.pending, name: '待维修', itemStyle: { color: '#E6A23C' } },
        { value: repairStats.value.cancelled, name: '已取消', itemStyle: { color: '#909399' } }
      ]
    }]
  })
}

// 响应式更新图表
watch([parkingRented, parkingFree], async () => { await nextTick(); initParkingChart() })
watch(repairStats, async () => { await nextTick(); initRepairChart() }, { deep: true })

onMounted(async () => {
  try {
    const res = await request.get('/dashboard/stats')
    if (res.data) { stats.value.householdCount = res.data.householdCount || 0; stats.value.buildingCount = res.data.buildingCount || 0 }
  } catch (e) { /* ignore */ }
  await Promise.all([loadParkingStats(), loadRepairStats()])
  await nextTick()
  initParkingChart()
  initRepairChart()
})
</script>

<style scoped>
.dashboard { max-width: 100%; }

/* ====== 欢迎横幅 ====== */
.hero-banner {
  display: flex;
  background: linear-gradient(135deg, #409EFF 0%, #337ecc 50%, #1a5fa8 100%);
  border-radius: 12px;
  padding: 32px 40px;
  margin-bottom: 20px;
  color: #fff;
  overflow: hidden;
  position: relative;
}
.hero-left { flex: 1; z-index: 1; }
.hero-title { font-size: 26px; font-weight: 700; margin: 0 0 12px 0; }
.hero-desc { font-size: 14px; opacity: 0.85; line-height: 1.8; margin-bottom: 16px; max-width: 600px; }
.hero-features { display: flex; gap: 24px; font-size: 14px; }
.hero-features span { display: flex; align-items: center; gap: 6px; opacity: 0.9; }

/* 右侧配图 */
.hero-right { width: 260px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; }
.hero-img {
  width: 100%; height: auto; max-height: 180px;
  border-radius: 8px; object-fit: cover; opacity: 0.9;
}

/* ====== 统计卡片 ====== */
.stats-row { margin-bottom: 20px; }
.stat-card {
  display: flex; align-items: center; gap: 14px;
  background: #fff; border-radius: 10px; padding: 20px 18px;
  cursor: pointer; transition: all 0.25s ease;
  border-left: 4px solid transparent;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  margin-bottom: 12px;
}
.stat-card:hover { transform: translateY(-3px); box-shadow: 0 6px 20px rgba(0,0,0,0.1); }
.stat-card-icon {
  width: 52px; height: 52px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.card-blue .stat-card-icon { background: #ecf5ff; color: #409EFF; }
.card-blue { border-left-color: #409EFF; }
.card-green .stat-card-icon { background: #f0f9eb; color: #67C23A; }
.card-green { border-left-color: #67C23A; }
.card-orange .stat-card-icon { background: #fdf6ec; color: #E6A23C; }
.card-orange { border-left-color: #E6A23C; }
.card-red .stat-card-icon { background: #fef0f0; color: #F56C6C; }
.card-red { border-left-color: #F56C6C; }
.stat-card-value { font-size: 28px; font-weight: 700; color: #303133; line-height: 1.1; }
.stat-card-label { font-size: 13px; color: #909399; margin-top: 2px; }

/* ====== 饼状图 ====== */
.charts-row { margin-bottom: 20px; }
.chart-card { height: 100%; }
.chart-title { font-weight: 600; }
.chart-box { width: 100%; height: 220px; }
.chart-legend { display: flex; justify-content: center; gap: 16px; margin-top: 4px; font-size: 13px; color: #606266; }
.legend-item { display: flex; align-items: center; gap: 4px; }
.dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; }

/* ====== 系统概况列表 ====== */
.overview-list { display: flex; flex-direction: column; gap: 0; }
.overview-item {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 0; border-bottom: 1px solid #f0f0f0;
  font-size: 14px; color: #303133;
}
.overview-item:last-child { border-bottom: none; }
.overview-label { flex: 1; color: #909399; }

/* ====== 页脚 ====== */
.dashboard-footer {
  text-align: center; padding: 20px 0 0;
  color: #909399; font-size: 13px;
}

/* ====== 响应式 ====== */
@media (max-width: 768px) {
  .hero-banner { flex-direction: column; padding: 20px; }
  .hero-right { display: none; }
  .hero-title { font-size: 20px; }
  .hero-features { flex-wrap: wrap; gap: 10px; }
}
</style>
