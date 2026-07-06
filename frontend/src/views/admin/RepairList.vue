<template>
  <div class="page">
    <el-card>
      <div class="page-card-header">
        <el-button type="primary" :icon="Plus">新增工单</el-button>
        <span class="page-card-total">共 0 条</span>
      </div>
      <el-empty description="维修工单功能开发中，敬请期待" />
    </el-card>
  </div>
</template>

<script setup>
import { Plus } from '@element-plus/icons-vue'
</script>
