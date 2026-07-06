import request from '../utils/request'

/** 多条件查询住户 */
export function queryHouseholds(params) {
  return request.post('/household/list', params)
}

/** 查询住户详情 */
export function getHousehold(id) {
  return request.get(`/household/${id}`)
}

/** 新增住户 */
export function addHousehold(data) {
  return request.post('/household', data)
}

/** 修改住户 */
export function updateHousehold(data) {
  return request.put('/household', data)
}

/** 搬离 */
export function moveOut(id) {
  return request.put(`/household/${id}/move-out`)
}

/** 重新入住 */
export function moveBack(id) {
  return request.put(`/household/${id}/move-back`)
}
