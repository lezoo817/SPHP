/**
 * 医生管理页
 * - ProTable 列表，支持科室过滤、名称/状态搜索
 * - ADMIN 角色可新增/编辑/修改账号/重置密码/切换状态
 * - DEPT_HEAD 角色仅可编辑资料
 */
import { Button, Modal, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import { useRef, useState } from 'react';
import type { ActionType } from '@ant-design/pro-components';
import {
  getDoctors,
  createDoctor,
  updateDoctorProfile,
  updateDoctorAccount,
  resetDoctorPassword,
  updateDoctorStatus,
} from '@/services/admin';
import { useHasRole } from '@/hooks/useCurrentUser';
import { getErrorMessage } from '@/utils/error';
import { getColumns } from './columns';
import { STATUS_MAP, type DoctorStatus } from './constants';
import AddDoctorModal from './AddDoctorModal';
import EditProfileModal from './EditProfileModal';
import EditAccountModal from './EditAccountModal';
import ResetPasswordModal from './ResetPasswordModal';
import ChangeStatusModal from './ChangeStatusModal';
import { PAGE_SIZE_DEFAULT } from '@/constants/pageSize';
import { ROLE_ADMIN, ROLE_DEPT_HEAD } from '@/constants/businessStatus';

export default function DoctorList() {
  const isAdmin = useHasRole(ROLE_ADMIN);
  const isDeptHead = useHasRole(ROLE_DEPT_HEAD);
  const actionRef = useRef<ActionType>();

  // 弹窗开关与当前选中医生
  const [addModalOpen, setAddModalOpen] = useState(false);
  const [editProfileOpen, setEditProfileOpen] = useState(false);
  const [editAccountOpen, setEditAccountOpen] = useState(false);
  const [resetPwdOpen, setResetPwdOpen] = useState(false);
  const [changeStatusOpen, setChangeStatusOpen] = useState(false);
  const [selectedDoctor, setSelectedDoctor] = useState<API.Doctor | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // ========== 弹窗开关 ==========

  /** 打开新增弹窗 */
  const handleAdd = () => {
    setSelectedDoctor(null);
    setAddModalOpen(true);
  };

  /** 打开编辑资料弹窗 */
  const handleEditProfile = (record: API.Doctor) => {
    setSelectedDoctor(record);
    setEditProfileOpen(true);
  };

  /** 打开修改账号弹窗 */
  const handleEditAccount = (record: API.Doctor) => {
    setSelectedDoctor(record);
    setEditAccountOpen(true);
  };

  /** 打开重置密码弹窗 */
  const handleResetPwd = (record: API.Doctor) => {
    setSelectedDoctor(record);
    setResetPwdOpen(true);
  };

  /** 打开切换状态弹窗 */
  const handleChangeStatus = (record: API.Doctor) => {
    setSelectedDoctor(record);
    setChangeStatusOpen(true);
  };

  // ========== 提交逻辑 ==========

  /** 新增医生 */
  const handleAddSubmit = async (values: API.CreateDoctorReq) => {
    setSubmitting(true);
    try {
      await createDoctor(values);
      await message.success('医生新增成功');
      setAddModalOpen(false);
      actionRef.current?.reload();
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '新增失败，请重试'));
    } finally {
      setSubmitting(false);
    }
  };

  /** 编辑资料 */
  const handleEditProfileSubmit = async (
    values: API.UpdateDoctorProfileReq,
  ) => {
    if (!selectedDoctor) return;
    setSubmitting(true);
    try {
      await updateDoctorProfile(selectedDoctor.id, values);
      message.success('资料更新成功');
      setEditProfileOpen(false);
      actionRef.current?.reload();
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '更新失败，请重试'));
    } finally {
      setSubmitting(false);
    }
  };

  /** 修改账号 */
  const handleEditAccountSubmit = async (values: API.UpdateDoctorAccountReq) => {
    if (!selectedDoctor) return;
    setSubmitting(true);
    try {
      await updateDoctorAccount(selectedDoctor.id, values.account);
      message.success('账号修改成功');
      setEditAccountOpen(false);
      actionRef.current?.reload();
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '修改失败，请重试'));
    } finally {
      setSubmitting(false);
    }
  };

  /** 重置密码（二次确认在弹窗提交时通过 Modal.confirm 处理） */
  const handleResetPwdSubmit = (values: API.ResetDoctorPasswordReq) => {
    if (!selectedDoctor) return;

    Modal.confirm({
      title: '确认重置密码',
      content: `确定要将医生「${selectedDoctor.name}」的密码重置为「${values.password}」吗？重置后用户将无法使用旧密码登录。`,
      okText: '确认重置',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        setSubmitting(true);
        try {
          await resetDoctorPassword(selectedDoctor.id, values.password);
          message.success('密码重置成功');
          setResetPwdOpen(false);
          actionRef.current?.reload();
        } catch (err: unknown) {
          message.error(getErrorMessage(err, '重置失败，请重试'));
        } finally {
          setSubmitting(false);
        }
      },
    });
  };

  /** 切换状态 */
  const handleChangeStatusSubmit = async (values: { status: DoctorStatus }) => {
    if (!selectedDoctor) return;
    setSubmitting(true);
    try {
      await updateDoctorStatus(selectedDoctor.id, values.status);
      const label = STATUS_MAP[values.status]?.text || values.status;
      message.success(`状态已切换为「${label}」`);
      setChangeStatusOpen(false);
      actionRef.current?.reload();
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '状态切换失败'));
    } finally {
      setSubmitting(false);
    }
  };

  const columns = getColumns({
    isAdmin,
    isDeptHead,
    onEditProfile: handleEditProfile,
    onEditAccount: handleEditAccount,
    onResetPwd: handleResetPwd,
    onChangeStatus: handleChangeStatus,
  });

  return (
    <>
      <ProTable<API.Doctor, API.DoctorListParams>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const { current, pageSize, ...rest } = params;
          const res = await getDoctors({
            page: current,
            size: pageSize,
            deptId: rest.deptId,
            name: rest.name,
            status: rest.status,
          });
          return {
            data: res.list,
            total: res.total,
            success: true,
          };
        }}
        search={{
          labelWidth: 'auto',
          defaultCollapsed: true,
          optionRender: (searchConfig, formProps, dom) => [...dom.reverse()],
        }}
        toolBarRender={() =>
          isAdmin
            ? [
                <Button
                  key="add"
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={handleAdd}
                >
                  新增医生
                </Button>,
              ]
            : []
        }
        pagination={{ pageSize: PAGE_SIZE_DEFAULT }}
      />

      <AddDoctorModal
        open={addModalOpen}
        submitting={submitting}
        onCancel={() => setAddModalOpen(false)}
        onSubmit={handleAddSubmit}
      />
      {selectedDoctor && (
        <>
          <EditProfileModal
            open={editProfileOpen}
            doctor={selectedDoctor}
            submitting={submitting}
            onCancel={() => setEditProfileOpen(false)}
            onSubmit={handleEditProfileSubmit}
          />
          <EditAccountModal
            open={editAccountOpen}
            doctor={selectedDoctor}
            submitting={submitting}
            onCancel={() => setEditAccountOpen(false)}
            onSubmit={handleEditAccountSubmit}
          />
          <ResetPasswordModal
            open={resetPwdOpen}
            doctor={selectedDoctor}
            submitting={submitting}
            onCancel={() => setResetPwdOpen(false)}
            onSubmit={handleResetPwdSubmit}
          />
          <ChangeStatusModal
            open={changeStatusOpen}
            doctor={selectedDoctor}
            submitting={submitting}
            onCancel={() => setChangeStatusOpen(false)}
            onSubmit={handleChangeStatusSubmit}
          />
        </>
      )}
    </>
  );
}
