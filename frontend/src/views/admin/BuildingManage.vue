<template>
  <div class="page">
    <!-- 表格 -->
    <el-card>
      <div class="page-card-header">
        <el-button type="primary" :icon="Plus" @click="openAdd">新增楼栋</el-button>
        <span class="page-card-total">共 {{ tableData.length }} 栋楼</span>
      </div>
      <el-table :data="tableData" border stripe v-loading="loading" style="margin-top: 10px">
        <el-table-column prop="buildingNo" label="楼号" width="100" />
        <el-table-column prop="floorCount" label="总层数" width="100" />
        <el-table-column prop="unitsPerFloor" label="每层户数" width="120" />
        <el-table-column prop="createTime" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑楼栋' : '新增楼栋'"
      width="500px"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="楼号" prop="buildingNo">
          <el-input
            v-model="form.buildingNo"
            placeholder="如：28"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item label="总层数" prop="floorCount">
          <el-input-number v-model="form.floorCount" :min="1" :max="99" />
        </el-form-item>
        <el-form-item label="每层户数" prop="unitsPerFloor">
          <el-input-number v-model="form.unitsPerFloor" :min="1" :max="20" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getBuildingList, addBuilding, updateBuilding, deleteBuilding } from '../../api/building'

// ========== 表格数据 ==========
const tableData = ref([])
const loading = ref(false)

const loadData = async () => {
  loading.value = true
  try {
    const res = await getBuildingList()
    tableData.value = res.data
  } finally {
    loading.value = false
  }
}

// ========== 新增/编辑弹窗 ==========
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = reactive({
  buildingNo: '',
  floorCount: 1,
  unitsPerFloor: 2
})

const rules = {
  buildingNo: [
    { required: true, message: '请输入楼号', trigger: 'blur' },
    { pattern: /^\d{1,5}$/, message: '楼号只能为数字', trigger: 'blur' }
  ],
  floorCount: [
    { required: true, message: '请输入总层数', trigger: 'blur' }
  ],
  unitsPerFloor: [
    { required: true, message: '请输入每层户数', trigger: 'blur' }
  ]
}

const resetForm = () => {
  if (formRef.value) formRef.value.resetFields()
  form.buildingNo = ''
  form.floorCount = 1
  form.unitsPerFloor = 2
}

const openAdd = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row) => {
  isEdit.value = true
  form.buildingNo = row.buildingNo
  form.floorCount = row.floorCount
  form.unitsPerFloor = row.unitsPerFloor
  dialogVisible.value = true
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value) {
      await updateBuilding({ ...form })
      ElMessage.success('修改成功')
    } else {
      await addBuilding({ ...form })
      ElMessage.success('添加成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
    // 错误已在拦截器处理
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(
    `确定删除 ${row.buildingNo} 号楼吗？`,
    '删除确认',
    { type: 'warning' }
  )
  await deleteBuilding(row.buildingNo)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>


