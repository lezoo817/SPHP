/**
 * B 端业务 API 服务层（医院/科室/医生、排班、接诊、处方、药品、患者、统计）。
 *
 * 统一走 Umi request：需要返回体的接口经 requestData 自动解包 Result<T> 的 data；
 * 写操作不关心返回体时直接调用 request。
 */
import { request } from '@umijs/max';
import { requestData } from './http';

/** 查询医院信息 */
export function getHospitalInfo(): Promise<API.HospitalInfo> {
  return requestData<API.HospitalInfo>('/api/b/admin/hospitals');
}

/** 编辑医院信息 */
export function updateHospital(
  id: number,
  data: API.UpdateHospitalReq,
): Promise<void> {
  return request(`/api/b/admin/hospitals/${id}`, {
    method: 'PUT',
    data,
  });
}

/** 查询科室列表（分页） */
export function getDepartments(
  params: API.DepartmentListParams,
): Promise<API.PageResult<API.Department>> {
  return requestData<API.PageResult<API.Department>>('/api/b/admin/departments', {
    params,
  });
}

/** 新增科室 */
export function createDepartment(
  data: API.UpsertDepartmentReq,
): Promise<void> {
  return request('/api/b/admin/departments', {
    method: 'POST',
    data,
  });
}

/** 编辑科室 */
export function updateDepartment(
  id: number,
  data: Partial<API.UpsertDepartmentReq>,
): Promise<void> {
  return request(`/api/b/admin/departments/${id}`, {
    method: 'PUT',
    data,
  });
}

/** 启用/停用科室 */
export function updateDepartmentStatus(
  id: number,
  status: 'ENABLED' | 'DISABLED',
): Promise<void> {
  return request(`/api/b/admin/departments/${id}/status`, {
    method: 'PUT',
    data: { status },
  });
}

/** 查询医生列表（分页） */
export function getDoctors(
  params: API.DoctorListParams,
): Promise<API.PageResult<API.Doctor>> {
  return requestData<API.PageResult<API.Doctor>>('/api/b/admin/doctors', {
    params,
  });
}

/** 新增医生（自动开通登录账号） */
export function createDoctor(
  data: API.CreateDoctorReq,
): Promise<void> {
  return request('/api/b/admin/doctors', {
    method: 'POST',
    data,
  });
}

/** 编辑医生基本信息 */
export function updateDoctorProfile(
  id: number,
  data: API.UpdateDoctorProfileReq,
): Promise<void> {
  return request(`/api/b/admin/doctors/${id}`, {
    method: 'PUT',
    data,
  });
}

/** 修改医生登录账号 */
export function updateDoctorAccount(
  id: number,
  account: string,
): Promise<void> {
  return request(`/api/b/admin/doctors/${id}/account`, {
    method: 'PUT',
    data: { account },
  });
}

/** 重置医生密码 */
export function resetDoctorPassword(
  id: number,
  password: string,
): Promise<void> {
  return request(`/api/b/admin/doctors/${id}/password`, {
    method: 'PUT',
    data: { password },
  });
}

/** 启用/停用/暂停医生 */
export function updateDoctorStatus(
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
export function getSchedules(
  params: API.ScheduleListParams,
): Promise<API.PageResult<API.Schedule>> {
  return requestData<API.PageResult<API.Schedule>>('/api/b/admin/schedules', {
    params,
  });
}

/** 创建排班（仅 ADMIN） */
export function createSchedule(data: API.CreateScheduleReq): Promise<void> {
  return request('/api/b/admin/schedules', { method: 'POST', data });
}

/** 查询排班号源时段配置 */
export function getScheduleSlots(id: number): Promise<API.SlotConfig[]> {
  return requestData<API.SlotConfig[]>(`/api/b/admin/schedules/${id}/slots`);
}

/** 配置号源时段（仅 ADMIN，仅 DRAFT） */
export function configureScheduleSlots(
  id: number,
  slotConfigs: API.SlotConfigItem[],
): Promise<void> {
  return request(`/api/b/admin/schedules/${id}/slots`, {
    method: 'PUT',
    data: { slotConfigs },
  });
}

/** 发布排班（仅 ADMIN） */
export function publishSchedule(id: number): Promise<void> {
  return request(`/api/b/admin/schedules/${id}/publish`, { method: 'PUT' });
}

/** 取消发布（PUBLISHED）或作废（DRAFT）排班（仅 ADMIN） */
export function unpublishSchedule(id: number): Promise<void> {
  return request(`/api/b/admin/schedules/${id}/unpublish`, { method: 'PUT' });
}

/** 批量排班预览（仅 ADMIN） */
export function previewBatchSchedule(
  data: API.BatchScheduleReq,
): Promise<API.BatchPreviewResp> {
  return requestData<API.BatchPreviewResp>(
    '/api/b/admin/schedules/batch/preview',
    { method: 'POST', data },
  );
}

/** 批量排班提交（仅 ADMIN） */
export function createBatchSchedule(
  data: API.BatchScheduleReq,
): Promise<API.BatchCreateReport> {
  return requestData<API.BatchCreateReport>('/api/b/admin/schedules/batch', {
    method: 'POST',
    data,
  });
}

/** 批量发布排班（仅 ADMIN） */
export function batchPublishSchedules(
  data: API.BatchPublishReq,
): Promise<API.BatchPublishReport> {
  return requestData<API.BatchPublishReport>(
    '/api/b/admin/schedules/batch-publish',
    { method: 'POST', data },
  );
}

/** 查询锁定号源看板（分页，date 必填） */
export function getLockedSlots(
  params: API.LockedSlotsParams,
): Promise<API.PageResult<API.LockedSlot>> {
  return requestData<API.PageResult<API.LockedSlot>>('/api/b/admin/slots/locked', {
    params,
  });
}

/** 手动释放锁定号源（仅 ADMIN） */
export function forceReleaseSlot(slotId: number): Promise<void> {
  return request(`/api/b/admin/slots/${slotId}/force-release`, { method: 'POST' });
}

/** 查询号源池（按日期+班次汇总，仅 PUBLISHED，按数据权限过滤） */
export function getSourcePool(
  params: API.SourcePoolParams,
): Promise<API.PageResult<API.SourcePoolVO>> {
  return requestData<API.PageResult<API.SourcePoolVO>>('/api/b/admin/source-pool', {
    params,
  });
}

// ===================== 接诊台 =====================

/** 查询待接诊队列 */
export function getQueue(
  params: API.QueueListParams,
): Promise<API.PageResult<API.QueueItem>> {
  return requestData<API.PageResult<API.QueueItem>>('/api/b/doctor/queue', {
    params,
  });
}

/** 查询无挂号在线问诊列表。 */
export function getOnlineConsultations(
  params: { status: string; page?: number; size?: number },
): Promise<API.PageResult<API.OnlineConsultationItem>> {
  return requestData<API.PageResult<API.OnlineConsultationItem>>(
    '/api/b/doctor/online-consultations',
    { params },
  );
}

/** 查询无挂号在线问诊详情。 */
export function getOnlineConsultationDetail(
  consultId: number,
): Promise<API.OnlineConsultationDetail> {
  return requestData<API.OnlineConsultationDetail>(
    `/api/b/doctor/online-consultations/${consultId}`,
  );
}

/** 开始编辑在线问诊回复。 */
export function startOnlineConsultation(consultId: number): Promise<API.ConsultStart> {
  return requestData<API.ConsultStart>(
    `/api/b/doctor/online-consultations/${consultId}/start`,
    { method: 'POST' },
  );
}

/** 提交一次性医生回复并完成在线问诊。 */
export function replyOnlineConsultation(
  consultId: number,
  content: string,
): Promise<API.OnlineConsultationReplyResult> {
  return requestData<API.OnlineConsultationReplyResult>(
    `/api/b/doctor/online-consultations/${consultId}/reply`,
    { method: 'POST', data: { content } },
  );
}

/** 患者详情 */
export function getPatientDetail(
  consultId: number,
): Promise<API.PatientDetail> {
  return requestData<API.PatientDetail>(`/api/b/doctor/queue/${consultId}`);
}

/** 开始接诊 */
export function startConsult(
  consultId: number,
): Promise<API.ConsultStart> {
  return requestData<API.ConsultStart>(`/api/b/doctor/consult/${consultId}/start`, {
    method: 'POST',
  });
}

/** 结束问诊 */
export function endConsult(
  consultId: number,
): Promise<API.ConsultEnd> {
  return requestData<API.ConsultEnd>(`/api/b/doctor/consult/${consultId}/end`, {
    method: 'POST',
  });
}

/** 保存病历 */
export function saveNote(
  consultId: number,
  data: API.NoteSaveReq,
): Promise<API.NoteSave> {
  return requestData<API.NoteSave>(`/api/b/doctor/consult/${consultId}/note`, {
    method: 'PUT',
    data,
  });
}

/** 查询消息历史 */
export function getMessages(
  consultationId: number,
  params?: { page?: number; size?: number },
): Promise<API.PageResult<API.MessageVO>> {
  return requestData<API.PageResult<API.MessageVO>>(
    `/api/b/doctor/consult/${consultationId}/messages`,
    { params },
  );
}

/** 发送问诊消息（B端代理） */
export function sendMessage(
  consultationId: number,
  data: API.MessageSendReq,
): Promise<API.MessageVO> {
  return requestData<API.MessageVO>(
    `/api/b/doctor/consult/${consultationId}/message`,
    { method: 'POST', data },
  );
}

// ===================== 接诊历史 =====================

/** 查询当前医生的历史接诊记录 */
export function getConsultHistory(
  params?: { page?: number; size?: number },
): Promise<API.PageResult<API.ConsultHistoryItem>> {
  return requestData<API.PageResult<API.ConsultHistoryItem>>(
    '/api/b/doctor/consult/history',
    { params },
  );
}

/** 查询历史接诊详情（病历全文 + 关联处方） */
export function getConsultHistoryDetail(
  consultId: number,
): Promise<API.ConsultHistoryDetail> {
  return requestData<API.ConsultHistoryDetail>(
    `/api/b/doctor/consult/${consultId}/history-detail`,
  );
}

// ===================== 处方管理 =====================

/** 查询处方列表（分页） */
export function getPrescriptions(
  params: API.PrescriptionListParams,
): Promise<API.PageResult<API.Prescription>> {
  return requestData<API.PageResult<API.Prescription>>('/api/b/prescriptions', {
    params,
  });
}

/** 处方详情 */
export function getPrescriptionDetail(
  id: number,
): Promise<API.PrescriptionDetail> {
  return requestData<API.PrescriptionDetail>(`/api/b/prescriptions/${id}`);
}

/** 提交处方 */
export function submitPrescription(
  data: API.PrescriptionSubmitReq,
): Promise<API.PrescriptionSubmitResult> {
  return requestData<API.PrescriptionSubmitResult>('/api/b/prescriptions', {
    method: 'POST',
    data,
  });
}

/** 查询待审核处方列表（分页） */
export function getPendingAudits(
  params: API.PageParams,
): Promise<API.PageResult<API.PendingAuditItem>> {
  return requestData<API.PageResult<API.PendingAuditItem>>(
    '/api/b/prescriptions/pending-audit',
    { params },
  );
}

/** 审核处方 */
export function auditPrescription(
  id: number,
  data: API.AuditReq,
): Promise<void> {
  return request(`/api/b/prescriptions/${id}/audit`, {
    method: 'PUT',
    data,
  });
}

// ===================== 处方模板 =====================

/** 查询处方模板列表（分页） */
export function getTemplates(
  params: API.TemplateListParams,
): Promise<API.PageResult<API.PrescriptionTemplate>> {
  return requestData<API.PageResult<API.PrescriptionTemplate>>(
    '/api/b/prescription-templates',
    { params },
  );
}

/** 保存处方模板 */
export function saveTemplate(
  data: API.SaveTemplateReq,
): Promise<API.PrescriptionTemplate> {
  return requestData<API.PrescriptionTemplate>(
    '/api/b/prescription-templates',
    { method: 'POST', data },
  );
}

/** 删除处方模板 */
export function deleteTemplate(id: number): Promise<void> {
  return request(`/api/b/prescription-templates/${id}`, {
    method: 'DELETE',
  });
}

/** 更新处方模板 */
export function updateTemplate(
  id: number,
  data: API.SaveTemplateReq,
): Promise<API.PrescriptionTemplate> {
  return requestData<API.PrescriptionTemplate>(
    `/api/b/prescription-templates/${id}`,
    { method: 'PUT', data },
  );
}

// ===================== 药品库存管理 =====================

/** 查询药品目录（分页） */
export function getDrugs(
  params: API.DrugListParams,
): Promise<API.PageResult<API.Drug>> {
  return requestData<API.PageResult<API.Drug>>('/api/b/admin/drugs', {
    params,
  });
}

/** 查询单个药品（新建处方模板自动带出药品名称/规格） */
export function getDrugById(id: number): Promise<API.Drug> {
  return requestData<API.Drug>(`/api/b/prescription-templates/drugs/${id}`);
}

/** 新增药品 */
export function createDrug(data: API.CreateDrugReq): Promise<void> {
  return request('/api/b/admin/drugs', {
    method: 'POST',
    data,
  });
}

/** 更新药品 */
export function updateDrug(
  id: number,
  data: Partial<API.CreateDrugReq>,
): Promise<void> {
  return request(`/api/b/admin/drugs/${id}`, {
    method: 'PUT',
    data,
  });
}

/** 启用/停用药品 */
export function updateDrugStatus(
  id: number,
  status: 'ENABLED' | 'DISABLED',
): Promise<void> {
  return request(`/api/b/admin/drugs/${id}/status`, {
    method: 'PUT',
    data: { status },
  });
}

/** 查询库存列表（分页） */
export function getInventoryList(
  params: API.InventoryListParams,
): Promise<API.PageResult<API.InventoryItem>> {
  return requestData<API.PageResult<API.InventoryItem>>('/api/b/admin/inventory', {
    params,
  });
}

/** 更新库存 */
export function updateInventory(
  id: number,
  data: API.UpdateInventoryReq,
): Promise<void> {
  return request(`/api/b/admin/inventory/${id}`, {
    method: 'PUT',
    data,
  });
}

/** 查询低库存预警列表 */
export function getInventoryAlerts(
  params?: { pharmacyId?: number },
): Promise<API.InventoryItem[]> {
  return requestData<API.InventoryItem[]>('/api/b/admin/inventory/alerts', {
    params,
  });
}

/** 查询药房列表（供下拉筛选） */
export function getPharmacies(): Promise<API.PharmacyItem[]> {
  return requestData<API.PharmacyItem[]>('/api/b/admin/pharmacies');
}

/** 手动释放锁定库存（仅 ADMIN） */
export function unlockInventory(
  id: number,
  data: API.UnlockInventoryReq,
): Promise<void> {
  return request(`/api/b/admin/inventory/${id}/unlock`, {
    method: 'POST',
    data,
  });
}

// ===================== 患者管理 =====================

/** 查询患者列表（分页） */
export function getPatientList(
  params: API.PatientListParams,
): Promise<API.PageResult<API.PatientListItem>> {
  return requestData<API.PageResult<API.PatientListItem>>('/api/b/admin/patients', {
    params,
  });
}

/** 患者详情 */
export function getPatientInfo(
  id: number,
): Promise<API.PatientDetailInfo> {
  return requestData<API.PatientDetailInfo>(`/api/b/admin/patients/${id}`);
}

/** 查询患者就诊记录（分页） */
export function getPatientVisits(
  id: number,
  params: API.PageParams,
): Promise<API.PageResult<API.PatientVisitItem>> {
  return requestData<API.PageResult<API.PatientVisitItem>>(
    `/api/b/admin/patients/${id}/visits`,
    { params },
  );
}

/** 查询患者历史处方（分页） */
export function getPatientPrescriptions(
  id: number,
  params: API.PageParams,
): Promise<API.PageResult<API.PatientPrescriptionItem>> {
  return requestData<API.PageResult<API.PatientPrescriptionItem>>(
    `/api/b/admin/patients/${id}/prescriptions`,
    { params },
  );
}

/** 查询患者当前用药与随访 */
export function getPatientMedications(
  id: number,
): Promise<API.PatientMedicationResult> {
  return requestData<API.PatientMedicationResult>(
    `/api/b/admin/patients/${id}/medications`,
  );
}

// ===================== 统计报表 =====================

/** 运营总览 */
export function getStatisticsOverview(
  params?: API.StatisticsParams,
): Promise<API.StatisticsOverview> {
  return requestData<API.StatisticsOverview>('/api/b/admin/statistics/overview', {
    params,
  });
}

/** 按科室统计 */
export function getDepartmentStats(
  params?: API.StatisticsParams,
): Promise<API.DepartmentStatItem[]> {
  return requestData<API.DepartmentStatItem[]>(
    '/api/b/admin/statistics/department',
    { params },
  );
}

/** 按日期统计 */
export function getDailyStats(
  params: API.StatisticsParams,
): Promise<API.DailyStatItem[]> {
  return requestData<API.DailyStatItem[]>('/api/b/admin/statistics/daily', {
    params,
  });
}
