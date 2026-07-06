<template>
  <el-container class="layout-container">
    <!-- ==================== 顶部导航栏 ==================== -->
    <el-header class="top-header" height="60px">
      <div class="header-inner">
        <!-- Logo 区域 -->
        <div class="logo-area" @click="router.push('/dashboard')">
          <span class="logo-icon">🏠</span>
          <span class="logo-text">物业管理系统</span>
        </div>

        <!-- 水平导航菜单 -->
        <el-menu
          :default-active="activeMenu"
          mode="horizontal"
          :ellipsis="false"
          router
          class="top-menu"
        >
          <template v-for="item in menuItems" :key="item.path">
            <!-- 有子菜单：hover 展开下拉 -->
            <el-sub-menu v-if="item.children" :index="item.path">
              <template #title>
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.title }}</span>
              </template>
              <el-menu-item
                v-for="child in item.children"
                :key="child.path"
                :index="child.path"
              >
                {{ child.title }}
              </el-menu-item>
            </el-sub-menu>

            <!-- 无子菜单：直接菜单项 -->
            <el-menu-item v-else :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>

        <!-- 右侧用户区域 -->
        <div class="user-area">
          <el-tag :type="roleTagType" size="small">{{ roleLabel }}</el-tag>
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="username">
              {{ state.username }} <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </el-header>

    <!-- ==================== 面包屑栏 ==================== -->
    <div class="breadcrumb-bar">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/' }">
          <el-icon style="vertical-align:middle;margin-right:4px"><HomeFilled /></el-icon>
          首页
        </el-breadcrumb-item>
        <el-breadcrumb-item v-if="currentTitle">
          {{ currentTitle }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- ==================== 主内容区（全宽） ==================== -->
    <el-main class="main-content">
      <router-view v-slot="{ Component }">
        <transition name="fade-slide" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </el-main>

    <!-- ==================== 页脚 ==================== -->
    <el-footer class="app-footer" height="40px">
      <span>© 2026 小区物业管理系统 v1.0</span>
    </el-footer>

    <!-- 个人中心弹窗（独立组件） -->
    <ProfileDialog v-model="profileVisible" />
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ArrowDown, HomeFilled, UserFilled, Money, Tools, DataAnalysis, Clock } from '@element-plus/icons-vue'
import { clearUser } from '../stores/user'
import state from '../stores/user'
import ProfileDialog from '../components/ProfileDialog.vue'

const router = useRouter()
const route = useRoute()

// 当前激活的菜单项
const activeMenu = computed(() => route.path)

// 当前页面标题
const currentTitle = computed(() => route.meta?.title || '')

// ==================== 角色标签 ====================
const roleLabel = computed(() => {
  const map = { admin: '管理员', maintenance: '维修员', resident: '业主' }
  return map[state.role] || state.role
})

const roleTagType = computed(() => {
  const map = { admin: 'danger', maintenance: 'warning', resident: 'success' }
  return map[state.role] || 'info'
})

// ==================== 菜单项（按角色区分） ====================
const menuItems = computed(() => {
  const role = state.role

  // ---- 管理员菜单 ----
  if (role === 'admin') {
    return [
      { path: '/dashboard', title: '首页', icon: HomeFilled },
      {
        path: '/building',
        title: '基础数据',
        icon: UserFilled,
        children: [
          { path: '/building/manage', title: '楼栋管理' },
          { path: '/household/manage', title: '住户管理' },
          { path: '/staff/manage', title: '员工管理' }
        ]
      },
      {
        path: '/fee',
        title: '费用管理',
        icon: Money,
        children: [
          { path: '/property-fee/manage', title: '物业费管理' },
          { path: '/parking/manage', title: '停车位管理' }
        ]
      },
      {
        path: '/repair',
        title: '维修管理',
        icon: Tools,
        children: [
          { path: '/repair/list', title: '维修工单' }
        ]
      },
      {
        path: '/report',
        title: '统计报表',
        icon: DataAnalysis,
        children: [
          { path: '/report/property-fee', title: '物业费报表' },
          { path: '/report/parking-fee', title: '停车费报表' }
        ]
      }
    ]
  }

  // ---- 维修员菜单 ----
  if (role === 'maintenance') {
    return [
      { path: '/dashboard', title: '首页', icon: HomeFilled },
      {
        path: '/repair',
        title: '维修管理',
        icon: Tools,
        children: [
          { path: '/repair/my-tasks', title: '我的工单' }
        ]
      }
    ]
  }

  // ---- 业主菜单 ----
  if (role === 'resident') {
    return [
      { path: '/dashboard', title: '首页', icon: HomeFilled },
      {
        path: '/my-fee',
        title: '我的缴费',
        icon: Money,
        children: [
          { path: '/my-property-fee', title: '物业费' },
          { path: '/my-parking-fee', title: '停车费' }
        ]
      },
      {
        path: '/my-repair',
        title: '报修',
        icon: Tools,
        children: [
          { path: '/my-repair/add', title: '我要报修' },
          { path: '/my-repair/list', title: '报修记录' }
        ]
      },
      { path: '/my-reminder', title: '催缴提醒', icon: Clock }
    ]
  }

  return []
})

// ==================== 下拉菜单 ====================
const profileVisible = ref(false)

const handleCommand = (cmd) => {
  if (cmd === 'profile') { profileVisible.value = true }
  else if (cmd === 'logout') handleLogout()
}

const handleLogout = () => { clearUser(); router.push('/login') }
</script>

<style scoped>
/* ====== 全视图高度 flex 列布局 ====== */
.layout-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ====== 顶部导航栏 ====== */
.top-header {
  padding: 0;
  height: 60px;
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  position: relative;
  z-index: 100;
  flex-shrink: 0;
}

.header-inner {
  display: flex;
  align-items: center;
  height: 100%;
  padding: 0 20px;
  max-width: 100%;
}

/* Logo 区域 */
.logo-area {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  flex-shrink: 0;
  margin-right: 16px;
}

.logo-icon {
  font-size: 22px;
}

.logo-text {
  font-size: 17px;
  font-weight: 700;
  color: #303133;
  white-space: nowrap;
}

/* 水平菜单 */
.top-menu {
  flex: 1;
  border-bottom: none !important;
  background: transparent;
  min-width: 0;
}

/* 菜单项高亮效果：底部蓝色下划线 */
.top-menu .el-menu-item.is-active {
  color: #409EFF;
  border-bottom: 2px solid #409EFF;
  background: transparent !important;
}

/* 子菜单父级高亮 */
.top-menu .el-sub-menu.is-active > .el-sub-menu__title {
  color: #409EFF;
  border-bottom: 2px solid #409EFF;
}

/* hover 底色效果 */
.top-menu .el-menu-item:hover,
.top-menu .el-sub-menu > .el-sub-menu__title:hover {
  background-color: #f5f7fa !important;
}

/* 用户区域 */
.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  margin-left: 16px;
}

.username {
  font-weight: 600;
  color: #303133;
  cursor: pointer;
  white-space: nowrap;
}

/* ====== 面包屑栏 ====== */
.breadcrumb-bar {
  padding: 10px 24px;
  background: #fafafa;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

/* ====== 主内容区（flex:1 填满剩余空间，全宽） ====== */
.main-content {
  flex: 1;
  background-color: #f0f2f5;
  padding: 20px 24px;
  overflow-y: auto;
}

/* ====== 页脚 ====== */
.app-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border-top: 1px solid #e6e6e6;
  color: #909399;
  font-size: 12px;
  flex-shrink: 0;
}

/* ====== 页面过渡动画 ====== */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.25s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* ====== 响应式：窄屏 Logo 文字隐藏 ====== */
@media (max-width: 900px) {
  .logo-text {
    display: none;
  }
  .header-inner {
    padding: 0 10px;
  }
  .main-content {
    padding: 12px 10px;
  }
  .breadcrumb-bar {
    padding: 8px 12px;
  }
}
</style>
