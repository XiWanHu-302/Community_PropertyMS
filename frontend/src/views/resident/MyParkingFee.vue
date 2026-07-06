<template>
  <div class="page">
    <!-- ========== 已租车位：每个车位独立显示 ========== -->
    <template v-for="sp in mySpaces" :key="sp.spaceNo">
      <el-card class="space-card">
        <div class="page-card-header">
          <div>
            <span style="font-weight:600">我的车位：{{ sp.spaceNo }}</span>
            <el-tag v-if="sp.plateNo" style="margin-left:12px" size="small">{{ sp.plateNo }}</el-tag>
            <span style="margin-left:12px;color:#909399;font-size:13px">月费 ¥{{ sp.monthlyFee }}</span>
          </div>
          <div style="display:flex;align-items:center;gap:10px">
            <span class="page-card-total">共 {{ (feeMap[sp.spaceNo] || []).length }} 笔</span>
            <el-select v-model="feeDuration" style="width:110px">
              <el-option label="1 个月" :value="1" />
              <el-option label="6 个月" :value="6" />
              <el-option label="12 个月" :value="12" />
            </el-select>
            <el-button type="success" :loading="paying" @click="payFee(sp.spaceNo)">
              {{ firstUnpaidMap[sp.spaceNo] ? `缴费 ${feeDuration} 个月` : `预缴 ${feeDuration} 个月` }}
            </el-button>
          </div>
        </div>
        <el-alert v-if="firstUnpaidMap[sp.spaceNo]" type="warning" :closable="false" show-icon style="margin-bottom:10px">
          最早未缴：{{ firstUnpaidMap[sp.spaceNo].year }}年{{ firstUnpaidMap[sp.spaceNo].month }}月（{{ firstUnpaidMap[sp.spaceNo].isPaid === -1 ? '已逾期' : '待缴' }}），从该月起依次缴纳 {{ feeDuration }} 个月
        </el-alert>
        <el-alert v-else type="success" :closable="false" show-icon style="margin-bottom:10px">
          当前无欠费，可预缴未来月份
        </el-alert>
        <el-table :data="feeMap[sp.spaceNo] || []" border stripe v-loading="feeLoading" style="margin-top:10px">
          <el-table-column prop="spaceNo" label="车位编号" width="100" />
          <el-table-column label="费用月份" width="130">
            <template #default="{row}">{{ row.year }}年{{ row.month }}月</template>
          </el-table-column>
          <el-table-column prop="amount" label="金额(元)" width="110" />
          <el-table-column label="状态" width="90">
            <template #default="{row}">
              <el-tag v-if="row.isPaid === 1" :type="row.statusText === '提前缴费' ? 'info' : 'success'" size="small">{{ row.statusText || '已缴' }}</el-tag>
              <el-tag v-else-if="row.isPaid === -1" type="danger" size="small">逾期</el-tag>
              <el-tag v-else-if="row.statusText === '历史记录'" type="info" size="small">历史记录</el-tag>
              <el-tag v-else type="warning" size="small">待缴</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="payDate" label="缴费日期" width="120" />
          <el-table-column prop="billNo" label="缴费单号" min-width="200" />
        </el-table>
      </el-card>
    </template>

    <!-- ========== 可租车位列表（始终显示） ========== -->
    <el-card style="margin-top:16px">
      <div class="page-card-header">
        <span style="font-weight:600">可租车位</span>
        <span class="page-card-total">共 {{ freeSpaces.length }} 个空闲车位</span>
      </div>
      <el-table :data="freeSpaces" border stripe v-loading="spaceLoading" style="margin-top:10px">
        <el-table-column prop="spaceNo" label="车位编号" width="110" />
        <el-table-column prop="monthlyFee" label="月费(元)" width="120" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="100">
          <template #default="{row}">
            <el-button size="small" type="primary" @click="openRent(row)">租用</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!spaceLoading && freeSpaces.length === 0" description="暂无空闲车位" style="margin-top:20px" />
    </el-card>

    <!-- 租用确认弹窗 -->
    <el-dialog v-model="rentVisible" title="租用车位" width="400px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="车位编号">{{ rentSpace.spaceNo }}</el-descriptions-item>
        <el-descriptions-item label="月费">¥{{ rentSpace.monthlyFee }}/月</el-descriptions-item>
      </el-descriptions>
      <el-form style="margin-top:12px">
        <el-form-item label="车牌号（选填）">
          <el-input v-model="rentPlateNo" placeholder="如 京A12345" style="width:200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rentVisible = false">取消</el-button>
        <el-button type="primary" :loading="renting" @click="doRent">确认租用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request2 from '../../utils/request2'
import state from '../../stores/user'

const householdId = Number(state.refId)

// ==================== 车位信息 ====================
const mySpaces = ref([])        // 已租车位列表
const freeSpaces = ref([])      // 空闲车位列表
const spaceLoading = ref(false)
const feeLoading = ref(false)
const paying = ref(false)
const feeDuration = ref(1)

// 每个车位的费用列表
const feeMap = reactive({})
// 每个车位的最早未缴
const firstUnpaidMap = computed(() => {
  const map = {}
  for (const sp of mySpaces.value) {
    const fees = feeMap[sp.spaceNo] || []
    const unpaid = fees.filter(r => r.isPaid !== 1 && r.statusText !== '历史记录')
      .sort((a, b) => a.year - b.year || a.month - b.month)
    if (unpaid.length > 0) map[sp.spaceNo] = unpaid[0]
  }
  return map
})

const loadSpaces = async () => {
  spaceLoading.value = true
  try {
    const res = await request2.get('/parking-space/list')
    const all = res.data || []
    // 所有已租的车位
    mySpaces.value = all.filter(s => s.householdId === householdId && s.status === 1)
    freeSpaces.value = all.filter(s => s.status === 0)
  } catch (e) {
    mySpaces.value = []
    freeSpaces.value = []
  } finally {
    spaceLoading.value = false
  }
}

const loadFeesForSpace = async (spaceNo) => {
  feeLoading.value = true
  try {
    const res = await request2.get(`/parking-fee/list/${spaceNo}`, {
      params: { year: null }
    })
    feeMap[spaceNo] = res.data || []
  } catch (e) {
    feeMap[spaceNo] = []
  } finally {
    feeLoading.value = false
  }
}

const payFee = async (spaceNo) => {
  paying.value = true
  try {
    const res = await request2.post('/parking-fee/pay', {
      spaceNo: spaceNo,
      duration: feeDuration.value,
      handler: '在线缴费'
    })
    ElMessage.success(`缴费成功！共 ${res.data.monthCount} 个月，金额 ¥${res.data.total}，单号：${res.data.billNo}`)
    await loadFeesForSpace(spaceNo)
  } catch (e) { /* 拦截器已处理 */ }
  finally { paying.value = false }
}

// ==================== 租用车位 ====================
const rentVisible = ref(false)
const renting = ref(false)
const rentSpace = ref({})
const rentPlateNo = ref('')

const openRent = (space) => {
  rentSpace.value = space
  rentPlateNo.value = ''
  rentVisible.value = true
}

const doRent = async () => {
  renting.value = true
  try {
    let url = `/parking-space/${rentSpace.value.spaceNo}/assign/${householdId}`
    if (rentPlateNo.value) url += `?plateNo=${encodeURIComponent(rentPlateNo.value)}`
    await request2.put(url)
    ElMessage.success('租用成功！')
    rentVisible.value = false
    await loadSpaces()
    for (const sp of mySpaces.value) await loadFeesForSpace(sp.spaceNo)
  } catch (e) { /* 拦截器已处理 */ }
  finally { renting.value = false }
}

onMounted(async () => {
  await loadSpaces()
  for (const sp of mySpaces.value) await loadFeesForSpace(sp.spaceNo)
})
</script>

<style scoped>
.space-card { margin-bottom: 16px; }
</style>
