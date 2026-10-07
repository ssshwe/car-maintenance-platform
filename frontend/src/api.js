export async function api(path, method = 'GET', data) {
  const token = sessionStorage.getItem('maintenance_token')
  const response = await fetch('/api/v1' + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(data !== undefined ? { body: JSON.stringify(data) } : {})
  })
  const body = await response.json().catch(() => ({ message: '服务连接异常，请确认后台已启动' }))
  if (!response.ok || body.code !== 'OK') {
    const error = new Error(body.message || '操作失败')
    error.code = body.code
    error.status = response.status
    if (body.code === 'UNAUTHORIZED') window.dispatchEvent(new Event('session-expired'))
    throw error
  }
  return body.data
}
export const money = value => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
export const dateTime = value => value ? String(value).replace('T', ' ').slice(0, 16) : '—'
export const states = {
  PENDING_REVIEW: '待平台审核', READY: '待到店保养', REJECTED: '审核未通过', IN_SERVICE: '保养进行中', COMPLETED: '已完成',
  IN_PROGRESS: '服务进行中', PENDING: '待审核', APPROVED: '审核通过', ON_SHELF: '已上架', OFF_SHELF: '已下架',
  BOOKED: '已预约', CHECKED_IN: '已到店', UNUSED: '待核销', USED: '已核销', OPEN: '开放', CLOSED: '关闭'
}
