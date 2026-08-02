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