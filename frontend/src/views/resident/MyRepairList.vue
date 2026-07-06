<template>
  <div class="page">
    <el-card>
      <div class="page-card-header">
        <span style="font-weight:600">报修记录</span>
        <span class="page-card-total">共 0 条</span>
      </div>
      <el-table :data="[]" border stripe empty-text="暂无报修记录">
        <el-table-column prop="id" label="工单号" width="100" />
        <el-table-column prop="type" label="报修类型" width="100" />
        <el-table-column prop="description" label="问题描述" min-width="160" />
        <el-table-column label="状态" width="80">
          <template #default><el-tag size="small">—</el-tag></template>
        </el-table-column>
        <el-table-column prop="createTime" label="提交时间" width="160" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
</script>
