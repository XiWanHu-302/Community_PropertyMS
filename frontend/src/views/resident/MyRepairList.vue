<template>
  <div class="page">
    <el-card>
      <div class="page-card-header">
        <span style="font-weight:600">报修记录</span>
        <span class="page-card-total">共 {{ filteredData.length }} 条</span>
        <el-button type="primary" size="small" @click="goToAdd" style="float:right">我要报修</el-button>
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
      </el-row>

      <!-- 加载失败提示 -->
      <el-empty v-if="loadError && !loading" description="数据加载失败，请确认后端服务已启动">
        <el-button type="primary" @click="loadData">重新加载</el-button>
      </el-empty>

      <!-- 报修记录表格 -->
      <el-table v-if="!loadError" :data="pagedData" border stripe v-loading="loading" empty-text="暂无报修记录">
        <el-table-column prop="repairId" label="工单号" width="80" />
        <el-table-column prop="content" label="维修内容" min-width="200" show-overflow-tooltip />
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
        <el-table-column label="金额" width="100">
          <template #default="{row}">
            <span v-if="row.amount && row.amount > 0 && row.status === 1">
              ¥{{ row.amount }}
              <el-tag v-if="row.isFromFund === 1" size="small" type="warning" style="margin-left:4px">基金</el-tag>
            </span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{row}">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <template v-if="row.status === 0">
              <el-popconfirm title="确定取消该报修？" @confirm="handleCancel(row)">
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
        <el-form-item label="维修内容">{{ detail.content }}</el-form-item>
        <el-form-item label="报修日期">{{ detail.reportDate }}</el-form-item>
        <el-form-item label="状态">
          <el-tag :type="statusTagType(detail.status)" size="small">{{ detail.statusText }}</el-tag>
        </el-form-item>
        <el-form-item label="维修人">{{ detail.repairPerson || '—' }}</el-form-item>
        <el-form-item label="维修日期">{{ detail.repairDate || '—' }}</el-form-item>
        <el-form-item label="维修金额">
          <span v-if="detail.amount && detail.amount > 0">¥{{ detail.amount }}</span>
          <span v-else>—</span>
        </el-form-item>
        <el-form-item label="基金支出">
          <template v-if="detail.status === 1">{{ detail.isFromFund === 1 ? '是（已从维修基金扣减）' : '否（自费）' }}</template>
          <template v-else>—</template>
        </el-form-item>
      </el-form>
      <!-- 现场照片 -->
      <div v-if="detailImages.length > 0" style="margin-top:12px">
        <div style="font-weight:600;margin-bottom:8px;font-size:14px">现场照片（{{ detailImages.length }}张）</div>
        <div style="display:flex;gap:8px;flex-wrap:wrap">
          <el-image
            v-for="img in detailImages" :key="img.id"
            :src="'http://127.0.0.1:8080/file/preview/' + img.id"
            :preview-src-list="detailImages.map(i => 'http://127.0.0.1:8080/file/preview/' + i.id)"
            style="width:120px;height:120px;border-radius:4px;object-fit:cover"
            fit="cover"
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import request from '../../utils/request'
import { ElMessage } from 'element-plus'
import { myRepairs, cancelRepair } from '../../api/repair'

const router = useRouter()
const loading = ref(false)
const loadError = ref(false)
const tableData = ref([])

// 筛选
const filterStatus = ref(null)

// 分页
const currentPage = ref(1)
const pageSize = ref(10)

const filteredData = computed(() => tableData.value)

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

const detailImages = ref([])
const openDetail = async (row) => {
  Object.assign(detail, row)
  detailVisible.value = true
  detailImages.value = []
  try {
    const res = await request.get(`/file/list?relatedType=repair&relatedId=${row.repairId}`)
    detailImages.value = (res.data || []).filter(a => a.contentType && a.contentType.startsWith('image/'))
  } catch {}
}

// 取消报修
const handleCancel = async (row) => {
  try {
    await cancelRepair(row.repairId)
    ElMessage.success('报修已取消')
    loadData()
  } catch { /* 已弹出错误提示 */ }
}

const goToAdd = () => {
  router.push('/my-repair/add')
}

// 加载数据
const loadData = async () => {
  loading.value = true
  loadError.value = false
  try {
    const params = {}
    if (filterStatus.value !== null && filterStatus.value !== '') params.status = filterStatus.value
    const res = await myRepairs(params)
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
