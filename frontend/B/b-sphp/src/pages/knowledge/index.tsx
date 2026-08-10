/**
 * 知识库管理页（ADMIN 专属）。
 *
 * 拖放/点击上传文档 → Agent 切分向量化写入 pgvector → 后续对话 rag_node
 * 自动检索注入。入库走 Agent /api/knowledge/ingest，需 B 端 ADMIN 角色。
 *
 * 路由层已用 access:isAdmin 拦截，此处 useHasRole 做双重防御兜底。
 */
import {
  Alert,
  Button,
  Card,
  Divider,
  Form,
  Input,
  message,
  Modal,
  Result,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
  type UploadFile,
} from 'antd';
import { BookOutlined, DeleteOutlined, InboxOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { deleteKnowledge, ingestKnowledge, listKnowledge } from '@/services/agent';
import { useHasRole } from '@/hooks/useCurrentUser';
import { getErrorMessage } from '@/utils/error';
import { PAGE_SIZE_DEFAULT } from '@/constants/pageSize';
import { ROLE_ADMIN } from '@/constants/businessStatus';
import type {
  KnowledgeCategory,
  KnowledgeDocument,
  KnowledgeIngestResult,
} from '@/typings/agent';

const { Dragger } = Upload;
const { Text, Paragraph } = Typography;

/** 分类选项（与 Agent knowledge.py _CATEGORIES 对齐）。 */
const CATEGORIES: { label: string; value: KnowledgeCategory }[] = [
  { label: '患者科普（patient_edu）', value: 'patient_edu' },
  { label: '临床参考（clinical_ref）', value: 'clinical_ref' },
];

/** 受限的文件后缀白名单（accept 属性同步约束）。 */
const ACCEPT_EXT = '.txt,.md,.pdf,.csv';

/** 从 Upload 变更事件中提取 fileList（Form 受控所需）。 */
function normFile(e: unknown): UploadFile[] | undefined {
  if (Array.isArray(e)) return e;
  if (e && typeof e === 'object' && 'fileList' in e) {
    return (e as { fileList: UploadFile[] }).fileList;
  }
  return undefined;
}

export default function KnowledgePage() {
  const isAdmin = useHasRole(ROLE_ADMIN);
  const queryClient = useQueryClient();
  const [form] = Form.useForm<{ title: string; category: KnowledgeCategory; source?: string; file?: UploadFile[] }>();
  const [result, setResult] = useState<KnowledgeIngestResult | null>(null);

  // ── 文档列表查询 ──
  const [page, setPage] = useState(1);
  const [categoryFilter, setCategoryFilter] = useState<KnowledgeCategory | undefined>();

  const { data: listData, isLoading: listLoading } = useQuery({
    queryKey: ['knowledge-docs', page, categoryFilter],
    queryFn: () => listKnowledge({ page, page_size: 10, category: categoryFilter }),
    enabled: isAdmin,
  });

  // ── 删除 mutation ──
  const deleteMutation = useMutation({
    mutationFn: deleteKnowledge,
    onSuccess: (data) => {
      message.success(`已删除文档及 ${data.chunk_count} 个知识片段`);
      void queryClient.invalidateQueries({ queryKey: ['knowledge-docs'] });
    },
    onError: (err: unknown) => {
      message.error(getErrorMessage(err, '删除失败'));
    },
  });

  /** 删除确认弹窗 */
  const handleDelete = (doc: KnowledgeDocument) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定删除文档「${doc.title}」？此操作不可逆，将同时删除 ${doc.chunk_count} 个知识片段。`,
      okType: 'danger',
      okText: '确认删除',
      cancelText: '取消',
      onOk: () => deleteMutation.mutate(doc.id),
    });
  };

  /** 入库 mutation：成功后展示结果并清空文件选择，失败提示错误。 */
  const { mutate, isPending } = useMutation({
    mutationFn: ingestKnowledge,
    onSuccess: (data) => {
      if (data.status === 'indexed') {
        message.success(`入库成功，切分为 ${data.chunk_count} 个知识片段`);
      } else {
        message.warning('文件已接收但未产生可用知识片段，请检查文件内容');
      }
      setResult(data);
      form.setFieldValue('file', undefined);
      // 入库成功后自动刷新文档列表
      void queryClient.invalidateQueries({ queryKey: ['knowledge-docs'] });
    },
    onError: (err: unknown) => {
      message.error(getErrorMessage(err, '入库失败，请重试'));
    },
  });

  // 非 ADMIN 兜底（路由 access:isAdmin 已拦截，此处防御性二次校验）
  if (!isAdmin) {
    return (
      <Card title="知识库管理">
        <Alert
          message="无访问权限"
          description="知识库管理仅对管理员开放"
          type="warning"
          showIcon
        />
      </Card>
    );
  }

  /** 提交入库：校验表单后从 fileList 取出原始 File 调用 mutation。 */
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const fileList = values.file ?? [];
      const file = fileList[0]?.originFileObj;
      if (!file) {
        message.error('请先选择文档文件');
        return;
      }
      mutate({
        file,
        title: values.title,
        category: values.category,
        source: values.source,
      });
    } catch {
      // validateFields 抛错时 Form 已自动标注字段错误，无需额外处理
    }
  };

  return (
    <Card
      title={
        <Space>
          <BookOutlined />
          <span>知识库管理</span>
        </Space>
      }
    >
      <Alert
        type="info"
        showIcon
        message="上传的文档将自动切分、向量化并写入知识库"
        description={
          <Paragraph style={{ marginBottom: 0 }}>
            支持 .txt / .md / .pdf / .csv，单文件 ≤ 10MB。入库后，AI 助手在对话中会自动检索相关知识
            并引用到回答中，无需额外操作。
          </Paragraph>
        }
        style={{ marginBottom: 24 }}
      />

      <Form
        form={form}
        layout="vertical"
        initialValues={{ category: 'patient_edu' }}
        style={{ maxWidth: 640 }}
      >
        <Form.Item label="文档文件" required>
          <Form.Item
            name="file"
            valuePropName="fileList"
            getValueFromEvent={normFile}
            noStyle
            rules={[{ required: true, message: '请选择文档文件' }]}
          >
            <Dragger
              accept={ACCEPT_EXT}
              maxCount={1}
              multiple={false}
              beforeUpload={() => false}
            >
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
              <p className="ant-upload-hint">支持 .txt / .md / .pdf / .csv，单文件 ≤ 10MB</p>
            </Dragger>
          </Form.Item>
        </Form.Item>

        <Form.Item
          name="title"
          label="文档标题"
          rules={[
            { required: true, message: '请输入文档标题' },
            { max: 100, message: '最多 100 个字符' },
          ]}
        >
          <Input placeholder="如：高血压患者教育手册" />
        </Form.Item>

        <Form.Item name="category" label="分类" rules={[{ required: true }]}>
          <Select options={CATEGORIES} />
        </Form.Item>

        <Form.Item
          name="source"
          label="来源"
          rules={[{ max: 200, message: '最多 200 个字符' }]}
        >
          <Input placeholder="如：《中国高血压防治指南2024》" />
        </Form.Item>

        <Form.Item>
          <Button type="primary" loading={isPending} onClick={handleSubmit}>
            开始入库
          </Button>
        </Form.Item>
      </Form>

      {result && (
        <Result
          status={result.status === 'indexed' ? 'success' : 'warning'}
          title={result.status === 'indexed' ? '文档入库成功' : '未产生可用知识片段'}
          subTitle={
            <Space direction="vertical" style={{ alignItems: 'flex-start' }}>
              <Text>文档标题：{result.title}</Text>
              <Text>文档 ID：{result.document_id}</Text>
              <Text>知识片段数：{result.chunk_count}</Text>
              {result.status === 'indexed' && (
                <Text type="secondary">
                  该知识已生效，AI 助手对话时会自动检索引用。
                </Text>
              )}
            </Space>
          }
          style={{ marginTop: 16 }}
        />
      )}

      <Divider />

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Typography.Title level={5} style={{ marginBottom: 0 }}>
          已入库文档
        </Typography.Title>
        <Select<KnowledgeCategory | undefined>
          allowClear
          placeholder="全部分类"
          style={{ width: 140 }}
          value={categoryFilter}
          onChange={(val) => { setCategoryFilter(val); setPage(1); }}
          options={[
            { label: '患者科普', value: 'patient_edu' },
            { label: '临床参考', value: 'clinical_ref' },
          ]}
        />
      </div>

      <Table<KnowledgeDocument>
        dataSource={listData?.items}
        loading={listLoading}
        rowKey="id"
        pagination={{
          total: listData?.total,
          current: page,
          pageSize: PAGE_SIZE_DEFAULT,
          onChange: (p) => setPage(p),
          showTotal: (total) => `共 ${total} 篇`,
        }}
        columns={[
          {
            title: '文档标题',
            dataIndex: 'title',
            ellipsis: true,
          },
          {
            title: '分类',
            dataIndex: 'category',
            width: 120,
            render: (cat: KnowledgeCategory) => (
              <Tag color={cat === 'patient_edu' ? 'green' : 'blue'}>
                {cat === 'patient_edu' ? '患者科普' : '临床参考'}
              </Tag>
            ),
          },
          {
            title: '来源',
            dataIndex: 'source',
            ellipsis: true,
            render: (src: string | null) => src || '—',
          },
          {
            title: '片段数',
            dataIndex: 'chunk_count',
            width: 80,
            align: 'center',
          },
          {
            title: '操作',
            width: 80,
            align: 'center',
            render: (_, doc) => (
              <Button
                danger
                type="link"
                size="small"
                icon={<DeleteOutlined />}
                onClick={() => handleDelete(doc)}
                loading={deleteMutation.isPending}
              >
                删除
              </Button>
            ),
          },
        ]}
      />
    </Card>
  );
}
