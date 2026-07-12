import request from '../utils/request'

/** 查询全部楼栋 */
export function getBuildingList() {
  return request.get('/building/list')
}

/** 新增楼栋 */
export function addBuilding(data) {
  return request.post('/building', data)
}

/** 修改楼栋 */
export function updateBuilding(data) {
  return request.put('/building', data)
}

/** 删除楼栋 */
export function deleteBuilding(buildingNo) {
  return request.delete(`/building/${buildingNo}`)
}

/** 批量删除楼栋 */
export function deleteBatchBuilding(buildingNos) {
  return request.post('/building/batch-delete', { buildingNos })
}
