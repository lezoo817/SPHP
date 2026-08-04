import { useEffect } from 'react';
import { useLocation, useNavigate, useParams } from 'umi';
import { buildLegacyReportRedirectPath } from '../../utils/medical-record';

/** 将旧报告页面链接无感跳转至新的病历报告页面。 */
export default function LegacyReportsRedirectPage() {
  const { reportId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    // 保留旧链接的来源、就诊人及日期筛选参数，避免用户返回后丢失上下文。
    navigate(buildLegacyReportRedirectPath(reportId, location.search), { replace: true });
  }, [location.search, navigate, reportId]);

  return null;
}
