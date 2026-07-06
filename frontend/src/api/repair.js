import request from '../utils/request'

/** 管理员/维修员：查询所有工单（支持多条件筛选） */
export function listRepairs(params) {
  return request.get('/repair/list', { params })
}

/** 住户：查看自己的报修记录 */
export function myRepairs(params) {
  return request.get('/repair/my', { params })
}

/** 维修员：查看分配给我的工单 */
export function myTasks(params) {
  return request.get('/repair/tasks', { params })
}

/** 查询单个工单详情 */
export function getRepair(id) {
  return request.get(`/repair/${id}`)
}

/** 住户：提交报修 */
export function addRepair(data) {
  return request.post('/repair', data)
}

/** 分配维修人员 */
export function assignRepair(id, data) {
  return request.put(`/repair/${id}/assign`, data)
}

/** 标记维修完成 */
export function completeRepair(id, data) {
  return request.put(`/repair/${id}/complete`, data)
}

/** 取消工单 */
export function cancelRepair(id) {
  return request.put(`/repair/${id}/cancel`)
}

/** 获取工单统计 */
export function getRepairStats() {
  return request.get('/repair/stats')
}

/** 获取在职维修员列表（供分配下拉框使用） */
export function getStaffList() {
  return request.get('/repair/staff-list')
}
