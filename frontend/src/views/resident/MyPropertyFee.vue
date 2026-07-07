<template>
  <div class="page">
    <el-card>
      <div class="page-card-header">
        <div>
          <el-form inline>
            <el-form-item label="年份">
              <el-select v-model="year" style="width:120px" @change="load">
                <el-option v-for="y in years" :key="y" :label="y+'年'" :value="y" />
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
          </el-form>
        </div>
        <div style="display:flex;align-items:center;gap:10px">
          <span class="page-card-total">
            共 {{ list.length }} 笔 |
            已缴 <b style="color:#67C23A">{{ list.filter(r => r.isPaid === 1).length }}</b> |
            待缴 <b style="color:#E6A23C">{{ list.filter(r => r.isPaid === 0).length }}</b> |
            逾期 <b style="color:#F56C6C">{{ list.filter(r => r.isPaid === -1).length }}</b>
          </span>
          <el-select v-model="duration" style="width:110px">
            <el-option label="1 个月" :value="1" />
            <el-option label="6 个月" :value="6" />
            <el-option label="12 个月" :value="12" />
          </el-select>
          <el-button type="success" :loading="paying" @click="payOne">
            缴费 {{ duration }} 个月
          </el-button>
        </div>
      </div>

      <!-- 最早未缴提示 -->
      <el-alert v-if="firstUnpaid" type="warning" :closable="false" show-icon style="margin-bottom:10px">
        最早未缴：{{ firstUnpaid.year }}年{{ firstUnpaid.month }}月（{{ firstUnpaid.isPaid === -1 ? '已逾期' : '待缴' }}），从该月起依次缴纳 {{ duration }} 个月
      </el-alert>

      <el-table :data="list" border stripe v-loading="loading" style="margin-top:10px">
        <el-table-column label="费用月份" width="130">
          <template #default="{row}">{{ row.year }}年{{ row.month }}月</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额(元)" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <el-tag v-if="row.isPaid === 1" type="success" size="small">已缴</el-tag>
            <el-tag v-else-if="row.isPaid === -1" type="danger" size="small">逾期</el-tag>
            <el-tag v-else type="warning" size="small">待缴</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="payDate" label="缴费日期" width="120" />
        <el-table-column prop="billNo" label="缴费单号" min-width="200" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request2 from '../../utils/request2'
import state from '../../stores/user'

const now = new Date()
const year = ref(now.getFullYear())
const years = Array.from({ length: 5 }, (_, i) => now.getFullYear() - 2 + i)
const statusFilter = ref('全部')
const duration = ref(1)
const list = ref([])
const loading = ref(false)
const paying = ref(false)
const householdId = Number(state.refId)

// 最早未缴记录
const firstUnpaid = computed(() => {
  const unpaid = list.value.filter(r => r.isPaid !== 1).sort((a, b) => a.year - b.year || a.month - b.month)
  return unpaid.length > 0 ? unpaid[0] : null
})

const load = async () => {
  if (!householdId) return
  loading.value = true
  try {
    const res = await request2.get(`/property-fee/list/${householdId}`, {
      params: { year: year.value }
    })
    const all = res.data || []
    if (statusFilter.value === '全部') {
      list.value = all
    } else if (statusFilter.value === '已缴') {
      list.value = all.filter(r => r.isPaid === 1)
    } else if (statusFilter.value === '待缴') {
      list.value = all.filter(r => r.isPaid === 0)
    } else if (statusFilter.value === '逾期') {
      list.value = all.filter(r => r.isPaid === -1)
    }
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

const payOne = async () => {
  paying.value = true
  try {
    const res = await request2.post('/property-fee/pay', {
      householdId: householdId,
      duration: duration.value,
      handler: '在线缴费'
    })
    const d = res.data
    ElMessage.success(`缴费成功！共 ${d.monthCount} 个月，金额 ¥${d.total}，单号：${d.billNo}`)
    load()
  } catch (e) { /* 拦截器已处理 */ }
  finally { paying.value = false }
}

onMounted(load)
</script>
