import { useEffect, useState } from 'react';

/**
 * 倒计时 Hook：每秒刷新，返回目标时刻（毫秒时间戳）距当前的剩余毫秒数。
 *
 * @param targetMs 目标时刻的时间戳（Date.parse 结果）；传 null 表示不生效
 * @returns 剩余毫秒数，小于等于 0 表示已到期
 */
export default function useCountdown(targetMs: number | null): number {
  const [remainMs, setRemainMs] = useState<number>(() =>
    targetMs === null ? 0 : Math.max(0, targetMs - Date.now()),
  );

  useEffect(() => {
    if (targetMs === null) {
      setRemainMs(0);
      return undefined;
    }
    // 先立即同步一次，再按秒 tick，保证渲染即准确
    const tick = () => setRemainMs(Math.max(0, targetMs - Date.now()));
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [targetMs]);

  return remainMs;
}
