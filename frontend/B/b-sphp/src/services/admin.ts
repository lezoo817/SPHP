/**
 * 医院/科室/医生管理 API 服务层
 */
import { request } from '@umijs/max';

/** 查询医院信息 */
export async function getHospitalInfo(): Promise<API.HospitalInfo> {
  const res = await request('/api/b/admin/hospitals');
  // 后端返回 Result<HospitalVO>，提取 data 字段
  return (res as API.Result<API.HospitalInfo>).data;
}

/** 编辑医院信息 */
export async function updateHospital(
  id: number,
  data: API.UpdateHospitalReq,
): Promise<void> {
  return request(`/api/b/admin/hospitals/${id}`, {
    method: 'PUT',
    data,
  });
}

/** 查询科室列表（分页） */
export async function getDepartments(
  params: API.DepartmentListParams,
): Promise<API.PageResult<API.Department>> {
  const res = await request('/api/b/admin/departments', { params });
  return (res as API.Result<API.PageResult<API.Department>>).data;
}

/** 新增科室 */
export async function createDepartment(
  data: API.UpsertDepartmentReq,
): Promise<void> {
  return request('/api/b/admin/departments', {
    method: 'POST',
    data,
  });
}

/** 编辑科室 */
export async function updateDepartment(
  id: number,
  data: Partial<API.UpsertDepartmentReq>,
): Promise<void> {
  return request(`/api/b/admin/departments/${id}`, {
    method: 'PUT',
    data,
  });
}

/** 启用/停用科室 */
export async function updateDepartmentStatus(
  id: number,
  status: 'ENABLED' | 'DISABLED',
): Promise<void> {
  return request(`/api/b/admin/departments/${id}/status`, {
    method: 'PUT',
    data: { status },
  });
}

/** 查询医生列表（分页） */
export async function getDoctors(
  params: API.DoctorListParams,
): Promise<API.PageResult<API.Doctor>> {
  const res = await request('/api/b/admin/doctors', { params });
  return (res as API.Result<API.PageResult<API.Doctor>>).data;
}

/** 新增医生（自动开通登录账号） */
export async function createDoctor(
  data: API.CreateDoctorReq,
): Promise<void> {
  return request('/api/b/admin/doctors', {
    method: 'POST',
    data,
  });
}

/** 编辑医生基本信息 */
export async function updateDoctorProfile(
  id: number,
  data: API.UpdateDoctorProfileReq,
): Promise<void> {
  return request(`/api/b/admin/doctors/${id}`, {
    method: 'PUT',
    data,
  });
}

/** 修改医生登录账号 */
export async function updateDoctorAccount(
  id: number,
  account: string,
): Promise<void> {
  return request(`/api/b/admin/doctors/${id}/account`, {
    method: 'PUT',
    data: { account },
  });
}

/** 重置医生密码 */
export async function resetDoctorPassword(
  id: number,
  password: string,
): Promise<void> {
  return request(`/api/b/admin/doctors/${id}/password`, {
    method: 'PUT',
    data: { password },
  });
}

/** 启用/停用/暂停医生 */
export async function updateDoctorStatus(
  id: number,
  status: 'ENABLED' | 'DISABLED' | 'SUSPENDED',
): Promise<void> {
  return request(`/api/b/admin/doctors/${id}/status`, {
    method: 'PUT',
    data: { status },
  });
}

// ===================== 排班与号源管理 =====================

/** 查询排班列表（分页） */
export async function getSchedules(
  params: API.ScheduleListParams,
): Promise<API.PageResult<API.Schedule>> {
  const res = await request('/api/b/admin/schedules', { params });
  return (res as API.Result<API.PageResult<API.Schedule>>).data;
}

/** 创建排班（仅 ADMIN） */
export async function createSchedule(data: API.CreateScheduleReq): Promise<void> {
  await request('/api/b/admin/schedules', { method: 'POST', data });
}

/** 查询排班号源时段配置 */
export async function getScheduleSlots(id: number): Promise<API.SlotConfig[]> {
  const res = await request(`/api/b/admin/schedules/${id}/slots`);
  return (res as API.Result<API.SlotConfig[]>).data;
}

/** 配置号源时段（仅 ADMIN，仅 DRAFT） */
export async function configureScheduleSlots(
  id: number,
  slotConfigs: API.SlotConfigItem[],
): Promise<void> {
  await request(`/api/b/admin/schedules/${id}/slots`, {
    method: 'PUT',
    data: { slotConfigs },
  });
}

/** 发布排班（仅 ADMIN） */
export async function publishSchedule(id: number): Promise<void> {
  await request(`/api/b/admin/schedules/${id}/publish`, { method: 'PUT' });
}

/** 取消发布（PUBLISHED）或作废（DRAFT）排班（仅 ADMIN） */
export async function unpublishSchedule(id: number): Promise<void> {
  await request(`/api/b/admin/schedules/${id}/unpublish`, { method: 'PUT' });
}

/** 查询锁定号源看板（分页，date 必填） */
export async function getLockedSlots(
  params: API.LockedSlotsParams,
): Promise<API.PageResult<API.LockedSlot>> {
  const res = await request('/api/b/admin/slots/locked', { params });
  return (res as API.Result<API.PageResult<API.LockedSlot>>).data;
}

/** 手动释放锁定号源（仅 ADMIN） */
export async function forceReleaseSlot(slotId: number): Promise<void> {
  await request(`/api/b/admin/slots/${slotId}/force-release`, { method: 'POST' });
}

/** 查询号源池（按日期+班次汇总，仅 PUBLISHED，按数据权限过滤） */
export async function getSourcePool(
  params: API.SourcePoolParams,
): Promise<API.PageResult<API.SourcePoolVO>> {
  const res = await request('/api/b/admin/source-pool', { params });
  return (res as API.Result<API.PageResult<API.SourcePoolVO>>).data;
}

// ===================== 接诊台 =====================

/** 查询待接诊队列 */
export async function getQueue(
  params: API.QueueListParams,
): Promise<API.PageResult<API.QueueItem>> {
  const res = await request('/api/b/doctor/queue', { params });
  return (res as API.Result<API.PageResult<API.QueueItem>>).data;
}

/** 患者详情 */
export async function getPatientDetail(
  consultId: number,
): Promise<API.PatientDetail> {
  const res = await request(`/api/b/doctor/queue/${consultId}`);
  return (res as API.Result<API.PatientDetail>).data;
}

/** 开始接诊 */
export async function startConsult(
  consultId: number,
): Promise<API.ConsultStart> {
  const res = await request(`/api/b/doctor/consult/${consultId}/start`, {
    method: 'POST',
  });
  return (res as API.Result<API.ConsultStart>).data;
}

/** 结束问诊 */
export async function endConsult(
  consultId: number,
): Promise<API.ConsultEnd> {
  const res = await request(`/api/b/doctor/consult/${consultId}/end`, {
    method: 'POST',
  });
  return (res as API.Result<API.ConsultEnd>).data;
}

/** 保存病历 */
export async function saveNote(
  consultId: number,
  data: API.NoteSaveReq,
): Promise<API.NoteSave> {
  const res = await request(`/api/b/doctor/consult/${consultId}/note`, {
    method: 'PUT',
    data,
  });
  return (res as API.Result<API.NoteSave>).data;
}

/** 查询消息历史 */
export async function getMessages(
  consultationId: number,
  params?: { page?: number; size?: number },
): Promise<API.PageResult<API.MessageVO>> {
  const res = await request(
    `/api/b/doctor/consult/${consultationId}/messages`,
    { params },
  );
  return (res as API.Result<API.PageResult<API.MessageVO>>).data;
}

/** 发送问诊消息（B端代理） */
export async function sendMessage(
  consultationId: number,
  data: API.MessageSendReq,
): Promise<API.MessageVO> {
  const res = await request(
    `/api/b/doctor/consult/${consultationId}/message`,
    { method: 'POST', data },
  );
  return (res as API.Result<API.MessageVO>).data;
}

// ===================== 接诊历史 =====================

/** 查询当前医生的历史接诊记录 */
export async function getConsultHistory(
  params?: { page?: number; size?: number },
): Promise<API.PageResult<API.ConsultHistoryItem>> {
  const res = await request('/api/b/doctor/consult/history', { params });
  return (res as API.Result<API.PageResult<API.ConsultHistoryItem>>).data;
}

/** 查询历史接诊详情（病历全文 + 关联处方） */
export async function getConsultHistoryDetail(
  consultId: number,
): Promise<API.ConsultHistoryDetail> {
  const res = await request(`/api/b/doctor/consult/${consultId}/history-detail`);
  return (res as API.Result<API.ConsultHistoryDetail>).data;
}

// ===================== 处方管理 =====================

/** 查询处方列表（分页） */
export async function getPrescriptions(
  params: API.PrescriptionListParams,
): Promise<API.PageResult<API.Prescription>> {
  const res = await request('/api/b/prescriptions', { params });
  return (res as API.Result<API.PageResult<API.Prescription>>).data;
}

/** 处方详情 */
export async function getPrescriptionDetail(
  id: number,
): Promise<API.PrescriptionDetail> {
  const res = await request(`/api/b/prescriptions/${id}`);
  return (res as API.Result<API.PrescriptionDetail>).data;
}

/** 提交处方 */
export async function submitPrescription(
  data: API.PrescriptionSubmitReq,
): Promise<API.PrescriptionSubmitResult> {
  const res = await request('/api/b/prescriptions', {
    method: 'POST',
    data,
  });
  return (res as API.Result<API.PrescriptionSubmitResult>).data;
}

/** 查询待审核处方列表（分页） */
export async function getPendingAudits(
  params: API.PageParams,
): Promise<API.PageResult<API.PendingAuditItem>> {
  const res = await request('/api/b/prescriptions/pending-audit', { params });
  return (res as API.Result<API.PageResult<API.PendingAuditItem>>).data;
}

/** 审核处方 */
export async function auditPrescription(
  id: number,
  data: API.AuditReq,
): Promise<void> {
  await request(`/api/b/prescriptions/${id}/audit`, {
    method: 'PUT',
    data,
  });
}

// ===================== 处方模板 =====================

/** 查询处方模板列表（分页） */
export async function getTemplates(
  params: API.TemplateListParams,
): Promise<API.PageResult<API.PrescriptionTemplate>> {
  const res = await request('/api/b/prescription-templates', { params });
  return (res as API.Result<API.PageResult<API.PrescriptionTemplate>>).data;
}

/** 保存处方模板 */
export async function saveTemplate(
  data: API.SaveTemplateReq,
): Promise<API.PrescriptionTemplate> {
  const res = await request('/api/b/prescription-templates', {
    method: 'POST',
    data,
  });
  return (res as API.Result<API.PrescriptionTemplate>).data;
}

/** 删除处方模板 */
export async function deleteTemplate(id: number): Promise<void> {
  await request(`/api/b/prescription-templates/${id}`, {
    method: 'DELETE',
  });
}

// ===================== 药品库存管理 =====================

/** 查询药品目录（分页） */
export async function getDrugs(
  params: API.DrugListParams,
): Promise<API.PageResult<API.Drug>> {
  const res = await request('/api/b/admin/drugs', { params });
  return (res as API.Result<API.PageResult<API.Drug>>).data;
}

/** 查询单个药品（新建处方模板自动带出药品名称/规格） */
export async function getDrugById(id: number): Promise<API.Drug> {
  const res = await request(`/api/b/prescription-templates/drugs/${id}`);
  return (res as API.Result<API.Drug>).data;
}

/** 新增药品 */
export async function createDrug(data: API.CreateDrugReq): Promise<void> {
  await request('/api/b/admin/drugs', {
    method: 'POST',
    data,
  });
}

/** 更新药品 */
export async function updateDrug(
  id: number,
  data: Partial<API.CreateDrugReq>,
): Promise<void> {
  await request(`/api/b/admin/drugs/${id}`, {
    method: 'PUT',
    data,
  });
}

/** 启用/停用药品 */
export async function updateDrugStatus(
  id: number,
  status: 'ENABLED' | 'DISABLED',
): Promise<void> {
  await request(`/api/b/admin/drugs/${id}/status`, {
    method: 'PUT',
    data: { status },
  });
}

/** 查询库存列表（分页） */
export async function getInventoryList(
  params: API.InventoryListParams,
): Promise<API.PageResult<API.InventoryItem>> {
  const res = await request('/api/b/admin/inventory', { params });
  return (res as API.Result<API.PageResult<API.InventoryItem>>).data;
}

/** 更新库存 */
export async function updateInventory(
  id: number,
  data: API.UpdateInventoryReq,
): Promise<void> {
  await request(`/api/b/admin/inventory/${id}`, {
    method: 'PUT',
    data,
  });
}

/** 查询低库存预警列表 */
export async function getInventoryAlerts(): Promise<API.InventoryItem[]> {
  const res = await request('/api/b/admin/inventory/alerts');
  return (res as API.Result<API.InventoryItem[]>).data;
}

/** 手动释放锁定库存（仅 ADMIN） */
export async function unlockInventory(
  id: number,
  data: API.UnlockInventoryReq,
): Promise<void> {
  await request(`/api/b/admin/inventory/${id}/unlock`, {
    method: 'POST',
    data,
  });
}

// ===================== 患者管理 =====================

/** 查询患者列表（分页） */
export async function getPatientList(
  params: API.PatientListParams,
): Promise<API.PageResult<API.PatientListItem>> {
  const res = await request('/api/b/admin/patients', { params });
  return (res as API.Result<API.PageResult<API.PatientListItem>>).data;
}

/** 患者详情 */
export async function getPatientInfo(
  id: number,
): Promise<API.PatientDetailInfo> {
  const res = await request(`/api/b/admin/patients/${id}`);
  return (res as API.Result<API.PatientDetailInfo>).data;
}

/** 查询患者就诊记录（分页） */
export async function getPatientVisits(
  id: number,
  params: API.PageParams,
): Promise<API.PageResult<API.PatientVisitItem>> {
  const res = await request(`/api/b/admin/patients/${id}/visits`, { params });
  return (res as API.Result<API.PageResult<API.PatientVisitItem>>).data;
}

/** 查询患者历史处方（分页） */
export async function getPatientPrescriptions(
  id: number,
  params: API.PageParams,
): Promise<API.PageResult<API.PatientPrescriptionItem>> {
  const res = await request(`/api/b/admin/patients/${id}/prescriptions`, { params });
  return (res as API.Result<API.PageResult<API.PatientPrescriptionItem>>).data;
}

/** 查询患者当前用药与随访 */
export async function getPatientMedications(
  id: number,
): Promise<API.PatientMedicationResult> {
  const res = await request(`/api/b/admin/patients/${id}/medications`);
  return (res as API.Result<API.PatientMedicationResult>).data;
}

// ===================== 统计报表 =====================

/** 运营总览 */
export async function getStatisticsOverview(
  params?: API.StatisticsParams,
): Promise<API.StatisticsOverview> {
  const res = await request('/api/b/admin/statistics/overview', { params });
  return (res as API.Result<API.StatisticsOverview>).data;
}

/** 按科室统计 */
export async function getDepartmentStats(
  params?: API.StatisticsParams,
): Promise<API.DepartmentStatItem[]> {
  const res = await request('/api/b/admin/statistics/department', { params });
  return (res as API.Result<API.DepartmentStatItem[]>).data;
}

/** 按日期统计 */
export async function getDailyStats(
  params: API.StatisticsParams,
): Promise<API.DailyStatItem[]> {
  const res = await request('/api/b/admin/statistics/daily', { params });
  return (res as API.Result<API.DailyStatItem[]>).data;
}