/**
 * 排班详情页时段列表列配置。
 *
 * 操作列随编辑权限切换：可编辑（草稿+ADMIN）/ 已发布禁用 / 占位。
 */
import { Button, Space, Tooltip } from 'antd';
import type { TableColumnsType } from 'antd';

interface ColumnsDeps {
  canEdit: boolean;
  isPublished: boolean;
  onEdit: (index: number) => void;
  onDelete: (index: number) => void;
}

export function getSlotColumns(deps: ColumnsDeps): TableColumnsType<API.SlotConfig> {
  const { canEdit, isPublished, onEdit, onDelete } = deps;

  return [
    { title: '开始时间', dataIndex: 'startTime', width: 130 },
    { title: '结束时间', dataIndex: 'endTime', width: 130 },
    {
      title: '号源数',
      dataIndex: 'totalCount',
      width: 110,
      align: 'right',
    },
    {
      title: '剩余号源',
      dataIndex: 'remainCount',
      width: 110,
      align: 'right',
      render: (_: unknown, record: API.SlotConfig) => record.remainCount ?? '-',
    },
    {
      title: '操作',
      width: 140,
      render: (_: unknown, __: API.SlotConfig, index: number) =>
        canEdit ? (
          <Space size={0}>
            <Button type="link" size="small" onClick={() => onEdit(index)}>
              编辑
            </Button>
            <Button type="link" size="small" danger onClick={() => onDelete(index)}>
              删除
            </Button>
          </Space>
        ) : isPublished ? (
          <Tooltip title="排班已发布，不可修改">
            <Space size={0}>
              <Button type="link" size="small" disabled>
                编辑
              </Button>
              <Button type="link" size="small" danger disabled>
                删除
              </Button>
            </Space>
          </Tooltip>
        ) : (
          <span style={{ color: '#999' }}>-</span>
        ),
    },
  ];
}
