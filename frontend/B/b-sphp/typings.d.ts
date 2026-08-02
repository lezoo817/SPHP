import '@umijs/max/typings';

// 注意：本文件因上方 import 被 TS 视为模块，须用 declare global 声明全局命名空间，
// 否则 API.* 类型在其它文件中不可见（TS2503）。
declare global {
  namespace API {
    /** 后端统一返回格式 */
    interface Result<T> {
      code: string;
      message: string;
      data: T;
      traceId: string;
    }

    /** 用户信息（从 /api/b/auth/token/parse 返回） */
    interface User {
      id?: number;
      name?: string;
      roles: string[];
      deptId?: number;
      doctorId?: number;
      hospitalId: number;
    }

    /** Token 解析响应（/api/b/auth/token/parse） */
    interface TokenParseVO {
      userId: number;
      account: string;
      roles: string[];
      deptId: number | null;
      doctorId: number | null;
      hospitalId: number;
      tokenExpiresAt: string | null;
    }

    /** 通用分页参数 */
    interface PageParams {
      page?: number;
      size?: number;
    }

    /** 通用分页响应 */
    interface PageResult<T> {
      list: T[];
      total: number;
      page: number;
      size: number;
    }

    /** 医院信息 */
    interface HospitalInfo {
      id: number;
      name: string;
      level: string;
      address?: string;
      contact?: string;
      description?: string;
      status: 'ENABLED' | 'DISABLED';
    }

    /** 更新医院信息请求 */
    interface UpdateHospitalReq {
      name: string;
      level: string;
      description?: string;
      address?: string;
      contact?: string;
    }

    /** 科室 */
    interface Department {
      id: number;
      name: string;
      hospitalId: number;
      headDoctorId?: number;
      headDoctorName?: string;
      description?: string;
      status: 'ENABLED' | 'DISABLED';
    }

    /** 科室列表查询参数 */
    interface DepartmentListParams extends PageParams {
      name?: string;
      headDoctorName?: string;
      status?: string;
    }

    /** 新增/编辑科室请求 */
    interface UpsertDepartmentReq {
      name: string;
      headDoctorId?: number;
      description?: string;
    }

    /** 更新科室状态请求 */
    interface UpdateDepartmentStatusReq {
      status: 'ENABLED' | 'DISABLED';
    }

    /** 医生 */
    interface Doctor {
      id: number;
      name: string;
      deptName: string;
      title: string;
      specialty?: string;
      licenseNo?: string;
      phone?: string;
      registrationFeeCent: number;
      status: 'ENABLED' | 'DISABLED' | 'SUSPENDED';
    }

    /** 医生列表查询参数 */
    interface DoctorListParams extends PageParams {
      deptId?: number;
      name?: string;
      status?: string;
    }

    /** 新增医生请求 */
    interface CreateDoctorReq {
      name: string;
      deptId: number;
      title: string;
      specialty?: string;
      licenseNo?: string;
      phone?: string;
      registrationFeeCent: number;
      account: string;
      password: string;
      status: 'ENABLED' | 'DISABLED' | 'SUSPENDED';
    }

    /** 编辑医生资料请求 */
    interface UpdateDoctorProfileReq {
      name: string;
      title: string;
      specialty?: string;
      introduction?: string;
    }

    /** 修改医生账号请求 */
    interface UpdateDoctorAccountReq {
      account: string;
    }

    /** 重置医生密码请求 */
    interface ResetDoctorPasswordReq {
      password: string;
    }

    /** 更新医生状态请求 */
    interface UpdateDoctorStatusReq {
      status: 'ENABLED' | 'DISABLED' | 'SUSPENDED';
    }

    /** 排班列表项 */
    interface Schedule {
      id: number;
      doctorId: number;
      doctorName: string;
      deptId: number;
      deptName: string;
      scheduleDate: string; // yyyy-MM-dd
      shift: 'MORNING' | 'AFTERNOON';
      totalSlots: number;
      bookedCount: number;
      remainCount: number;
      lockedCount: number;
      status: 'DRAFT' | 'PUBLISHED' | 'CANCELLED';
      publishedAt?: string;
    }

    /** 排班列表查询参数 */
    interface ScheduleListParams extends PageParams {
      date?: string;
      deptId?: number;
      doctorId?: number;
      status?: string;
    }

    /** 创建排班请求 */
    interface CreateScheduleReq {
      doctorId: number;
      scheduleDate: string; // yyyy-MM-dd
      shift: 'MORNING' | 'AFTERNOON';
      totalSlots: number;
    }

    /** 号源时段配置项（后端返回） */
    interface SlotConfig {
      id: number;
      startTime: string; // HH:mm
      endTime: string; // HH:mm
      totalCount: number;
      remainCount: number;
    }

    /** 号源时段配置请求项 */
    interface SlotConfigItem {
      startTime: string; // HH:mm
      endTime: string; // HH:mm
      count: number;
    }

    /** 锁定号源看板查询参数 */
    interface LockedSlotsParams extends PageParams {
      date: string; // yyyy-MM-dd（必填）
      deptId?: number;
    }

    /** 锁定号源看板项 */
    interface LockedSlot {
      slotId: number;
      scheduleId: number;
      doctorName: string;
      patientName: string;
      lockedAt: string;
      status: string;
      expireAt: string; // lockedAt + 15min
    }
  }
}
