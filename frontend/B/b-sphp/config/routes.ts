const routes = [
  { path: '/login', component: 'login', layout: false },
  {
    path: '/',
    component: '@/layouts/MainLayout',
    routes: [
      // 管理员专用
      {
        path: '/admin',
        access: 'isAdmin',
        routes: [
          { path: '/admin/hospital', component: 'admin/HospitalInfo' },
          { path: '/admin/departments', component: 'admin/DepartmentList' },
          { path: '/admin/doctors', component: 'admin/DoctorList' },
          // 知识库管理：拖放入库文档，供 Agent RAG 检索
          { path: '/admin/knowledge', component: 'knowledge' },
        ],
      },
      // 排班管理（所有人）
      {
        path: '/schedule',
        routes: [
          { path: '/schedule/list', component: 'schedule/ScheduleList' },
          {
            path: '/schedule/detail/:id',
            component: 'schedule/ScheduleDetail',
          },
          {
            path: '/schedule/source-pool',
            component: 'schedule/SourcePool',
          },
          {
            path: '/schedule/locked',
            component: 'schedule/LockedSlotsBoard',
          },
        ],
      },
      // 接诊台（所有人）
      {
        path: '/consult',
        routes: [
          { path: '/consult', redirect: '/consult/registration' },
          { path: '/consult/registration', component: 'consult/ConsultQueue' },
          { path: '/consult/online', component: 'consult/OnlineConsultation' },
          // 兼容旧书签，挂号接诊旧路径继续可访问。
          { path: '/consult/queue', redirect: '/consult/registration' },
          { path: '/consult/detail/:id', component: 'consult/ConsultDetail' },
        ],
      },
      // 处方管理（所有人）
      {
        path: '/prescription',
        routes: [
          { path: '/prescription/list', component: 'prescription/PrescriptionList' },
          {
            path: '/prescription/pending-audit',
            component: 'prescription/PendingAudit',
            access: 'canAudit',
          },
          {
            path: '/prescription/templates',
            component: 'prescription/PrescriptionTemplates',
          },
        ],
      },
      // 药品库存（仅 ADMIN）
      {
        path: '/drug',
        access: 'isAdmin',
        routes: [
          { path: '/drug/catalog', component: 'drug/DrugCatalog' },
          { path: '/drug/inventory', component: 'drug/InventoryList' },
          { path: '/drug/alerts', component: 'drug/StockAlerts' },
        ],
      },
      // 患者管理（所有人，数据权限由后端控制）
      {
        path: '/patient',
        routes: [
          { path: '/patient/list', component: 'patient/PatientList' },
          { path: '/patient/detail/:id', component: 'patient/PatientDetail' },
        ],
      },
      // 统计报表（仅 ADMIN）
      {
        path: '/statistics',
        access: 'isAdmin',
        routes: [
          { path: '/statistics/overview', component: 'statistics/Overview' },
          {
            path: '/statistics/department',
            component: 'statistics/DepartmentStats',
          },
          {
            path: '/statistics/daily',
            component: 'statistics/DailyReport',
          },
        ],
      },
      // AI 辅助助手（所有人）
      { path: '/agent', component: 'agent' },
      { path: '/', redirect: '/consult/registration' },
    ],
  },
];

export default routes;
