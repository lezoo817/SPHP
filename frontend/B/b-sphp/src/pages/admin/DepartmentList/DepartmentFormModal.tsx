/**
 * 科室新增/编辑弹窗。
 *
 * - 新增时科室尚不存在，任何医生都还不属于本科室（后端会拒绝跨科负责人），
 *   故禁用科室主任选择器并提示先创建科室后到编辑中设置负责人；
 * - 编辑时可远程搜索选择本科室医生作为负责人。
 */
import { Modal } from 'antd';
import { ProForm, ProFormText, ProFormSelect } from '@ant-design/pro-components';
import { getDoctors } from '@/services/admin';

interface Props {
  open: boolean;
  editingDept: API.Department | null;
  submitting: boolean;
  onCancel: () => void;
  onSubmit: (values: API.UpsertDepartmentReq) => void;
}

/** 远程搜索医生（按姓名/科室过滤），用于科室主任下拉 */
async function fetchDoctors(name?: string, deptId?: number) {
  try {
    const res = await getDoctors({ name, deptId, page: 1, size: 100 });
    return (res.list ?? []).map((doc) => ({
      label: `${doc.name}（${doc.title}）`,
      value: doc.id,
    }));
  } catch {
    return [];
  }
}

export default function DepartmentFormModal({
  open,
  editingDept,
  submitting,
  onCancel,
  onSubmit,
}: Props) {
  return (
    <Modal
      title={editingDept ? '编辑科室' : '新增科室'}
      open={open}
      footer={null}
      destroyOnHidden
      onCancel={onCancel}
      width={520}
    >
      <ProForm<API.UpsertDepartmentReq>
        initialValues={
          editingDept
            ? {
                name: editingDept.name,
                headDoctorId: editingDept.headDoctorId,
                location: editingDept.location,
              }
            : undefined
        }
        onFinish={onSubmit}
        submitter={{
          submitButtonProps: { loading: submitting },
        }}
      >
        <ProFormText
          name="name"
          label="科室名称"
          rules={[
            { required: true, message: '请输入科室名称' },
            { max: 100, message: '最多 100 个字符' },
          ]}
        />
        <ProFormSelect
          name="headDoctorId"
          label="科室主任"
          placeholder={
            editingDept
              ? '请选择科室主任（可选）'
              : '新增科室暂无负责人可选，创建后可在编辑中设置'
          }
          showSearch
          disabled={!editingDept}
          request={(input) => fetchDoctors(input?.key ?? '', editingDept?.id)}
          debounceTime={300}
        />
        <ProFormText
          name="location"
          label="科室位置"
          rules={[{ max: 500, message: '最多 500 个字符' }]}
        />
      </ProForm>
    </Modal>
  );
}
