/**
 * 药品新增/编辑弹窗。
 */
import { Modal } from 'antd';
import { ProForm, ProFormText, ProFormSelect } from '@ant-design/pro-components';
import { STATUS_OPTIONS } from './constants';

interface Props {
  open: boolean;
  editingDrug: API.Drug | null;
  submitting: boolean;
  onCancel: () => void;
  onSubmit: (values: API.CreateDrugReq) => void;
}

export default function DrugFormModal({
  open,
  editingDrug,
  submitting,
  onCancel,
  onSubmit,
}: Props) {
  return (
    <Modal
      title={editingDrug ? '编辑药品' : '新增药品'}
      open={open}
      footer={null}
      destroyOnClose
      onCancel={onCancel}
      width={560}
    >
      <ProForm<API.CreateDrugReq>
        initialValues={
          editingDrug
            ? {
                name: editingDrug.name,
                specification: editingDrug.specification,
                unit: editingDrug.unit,
                indication: editingDrug.indication,
                manufacturer: editingDrug.manufacturer,
                approvalNumber: editingDrug.approvalNumber,
                status: editingDrug.status,
              }
            : { status: 'ENABLED' }
        }
        onFinish={onSubmit}
        submitter={{
          submitButtonProps: { loading: submitting },
        }}
      >
        <ProFormText
          name="name"
          label="药品名称"
          rules={[
            { required: true, message: '请输入药品名称' },
            { max: 100, message: '最多 100 个字符' },
          ]}
        />
        <ProFormText
          name="specification"
          label="规格"
          rules={[
            { required: true, message: '请输入规格' },
            { max: 50, message: '最多 50 个字符' },
          ]}
          placeholder="如：0.25g×24粒"
        />
        <ProFormText
          name="unit"
          label="单位"
          rules={[
            { required: true, message: '请输入单位' },
            { max: 10, message: '最多 10 个字符' },
          ]}
          placeholder="如：盒、瓶"
        />
        <ProFormText
          name="manufacturer"
          label="生产厂家"
          rules={[{ max: 200, message: '最多 200 个字符' }]}
        />
        <ProFormText
          name="approvalNumber"
          label="批准文号"
          rules={[
            { required: true, message: '请输入批准文号' },
            { max: 50, message: '最多 50 个字符' },
          ]}
          placeholder="如：国药准字H12345678"
        />
        <ProFormText
          name="indication"
          label="适应症"
          rules={[{ max: 500, message: '最多 500 个字符' }]}
        />
        <ProFormSelect
          name="status"
          label="状态"
          rules={[{ required: true, message: '请选择状态' }]}
          options={STATUS_OPTIONS}
        />
      </ProForm>
    </Modal>
  );
}
