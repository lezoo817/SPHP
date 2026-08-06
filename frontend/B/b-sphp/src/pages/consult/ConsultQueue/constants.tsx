/**
 * 接诊台常量：队列 Tab / 选中状态类型，以及性别、问诊状态、消息发送方映射。
 */
import type { ReactNode } from 'react';
import { ManOutlined, WomanOutlined, UserOutlined } from '@ant-design/icons';

/** 左栏队列 Tab */
export type QueueTab = 'PENDING' | 'IN_PROGRESS' | 'HISTORY';

/** 选中接诊的状态 */
export type SelectedStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';

/** 性别映射（图标 + 颜色） */
export const GENDER_MAP: Record<string, { icon: ReactNode; color: string }> = {
  MALE: { icon: <ManOutlined />, color: '#1890ff' },
  FEMALE: { icon: <WomanOutlined />, color: '#eb2f96' },
  UNKNOWN: { icon: <UserOutlined />, color: '#999' },
};

/** 问诊状态配置 */
export const STATUS_MAP: Record<string, { text: string; color: string }> = {
  PENDING: { text: '待接诊', color: 'processing' },
  IN_PROGRESS: { text: '接诊中', color: 'success' },
  COMPLETED: { text: '已完成', color: 'default' },
};

/** 消息发送方标签 */
export const SENDER_LABEL: Record<string, string> = {
  PATIENT: '患者',
  DOCTOR: '医生',
  SYSTEM: '系统',
};
