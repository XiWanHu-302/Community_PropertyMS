<template>
  <div class="page">
    <el-card>
      <div class="page-card-header">
        <span style="font-weight:600">报修申请</span>
      </div>
      <el-form label-width="80px" style="max-width:500px;margin-top:16px">
        <el-form-item label="报修类型">
          <el-select placeholder="请选择" style="width:100%">
            <el-option label="水电维修" value="water" />
            <el-option label="门窗维修" value="door" />
            <el-option label="管道疏通" value="pipe" />
          </el-select>
        </el-form-item>
        <el-form-item label="问题描述">
          <el-input type="textarea" :rows="4" placeholder="请描述需要维修的问题…" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" disabled>提交报修（功能开发中）</el-button>
        </el-form-item>
      </el-form>
      <el-empty description="在线报修功能开发中，敬请期待" />
    </el-card>
  </div>
</template>

<script setup>
</script>
