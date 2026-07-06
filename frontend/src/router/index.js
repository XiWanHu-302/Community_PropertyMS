import { createRouter, createWebHistory } from 'vue-router'

// 视图组件（懒加载）
const Login = () => import('../views/Login.vue')
const MainLayout = () => import('../layouts/MainLayout.vue')

// 管理员页面
const BuildingManage = () => import('../views/admin/BuildingManage.vue')
const HouseholdManage = () => import('../views/admin/HouseholdManage.vue')
const StaffManage = () => import('../views/admin/StaffManage.vue')

// ==================== 路由表 ====================
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      // 首页（三种角色共用）
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/admin/Dashboard.vue'),
        meta: { title: '首页', roles: ['admin', 'maintenance', 'resident'] }
      },
      // ---- 管理员专有页面 ----
      {
        path: 'building/manage',
        name: 'BuildingManage',
        component: BuildingManage,
        meta: { title: '楼栋管理', roles: ['admin'] }
      },
      {
        path: 'household/manage',
        name: 'HouseholdManage',
        component: HouseholdManage,
        meta: { title: '住户管理', roles: ['admin'] }
      },
      {
        path: 'staff/manage',
        name: 'StaffManage',
        component: StaffManage,
        meta: { title: '员工管理', roles: ['admin'] }
      },
      // ---- 费用管理（管理员） ----
      {
        path: 'property-fee/manage',
        name: 'PropertyFeeManage',
        component: () => import('../views/admin/PropertyFeeManage.vue'),
        meta: { title: '物业费管理', roles: ['admin'] }
      },
      {
        path: 'parking/manage',
        name: 'ParkingSpaceManage',
        component: () => import('../views/admin/ParkingSpaceManage.vue'),
        meta: { title: '停车位管理', roles: ['admin'] }
      },
      // ---- 维修管理（管理员） ----
      {
        path: 'repair/list',
        name: 'RepairList',
        component: () => import('../views/admin/RepairList.vue'),
        meta: { title: '维修工单', roles: ['admin'] }
      },
      // ---- 统计报表（管理员） ----
      {
        path: 'report/property-fee',
        name: 'ReportPropertyFee',
        component: () => import('../views/admin/ReportPropertyFee.vue'),
        meta: { title: '物业费报表', roles: ['admin'] }
      },
      {
        path: 'report/parking-fee',
        name: 'ReportParkingFee',
        component: () => import('../views/admin/ReportParkingFee.vue'),
        meta: { title: '停车费报表', roles: ['admin'] }
      },
      // ---- 维修员页面 ----
      {
        path: 'repair/my-tasks',
        name: 'MyRepairTasks',
        component: () => import('../views/maintenance/MyTasks.vue'),
        meta: { title: '我的工单', roles: ['maintenance'] }
      },
      // ---- 业主页面 ----
      {
        path: 'my-property-fee',
        name: 'MyPropertyFee',
        component: () => import('../views/resident/MyPropertyFee.vue'),
        meta: { title: '物业费', roles: ['resident'] }
      },
      {
        path: 'my-parking-fee',
        name: 'MyParkingFee',
        component: () => import('../views/resident/MyParkingFee.vue'),
        meta: { title: '停车费', roles: ['resident'] }
      },
      {
        path: 'my-repair/add',
        name: 'MyRepairAdd',
        component: () => import('../views/resident/MyRepairAdd.vue'),
        meta: { title: '我要报修', roles: ['resident'] }
      },
      {
        path: 'my-repair/list',
        name: 'MyRepairList',
        component: () => import('../views/resident/MyRepairList.vue'),
        meta: { title: '报修记录', roles: ['resident'] }
      },
      {
        path: 'my-reminder',
        name: 'MyReminder',
        component: () => import('../views/resident/MyReminder.vue'),
        meta: { title: '催缴提醒', roles: ['resident'] }
      }
    ]
  },
  // 404
  {
    path: '/:pathMatch(.*)*',
    redirect: '/login'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// ==================== 路由守卫 ====================
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const role = localStorage.getItem('role')

  // 1. 去登录页：已登录直接跳首页，未登录放行
  if (to.path === '/login') {
    if (token) {
      next('/')
    } else {
      next()
    }
    return
  }

  // 2. 去其他页面：没登录跳登录页
  if (!token) {
    next('/login')
    return
  }

  // 3. 角色权限校验：如果路由定义了 roles，检查当前角色是否在允许列表中
  if (to.meta && to.meta.roles && Array.isArray(to.meta.roles)) {
    if (!to.meta.roles.includes(role)) {
      // 角色不匹配，跳首页
      next('/dashboard')
      return
    }
  }

  // 4. 设置页面标题
  if (to.meta && to.meta.title) {
    document.title = to.meta.title + ' - 小区物业管理系统'
  }

  next()
})

export default router
