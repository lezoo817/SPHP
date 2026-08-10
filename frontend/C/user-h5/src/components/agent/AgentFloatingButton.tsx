import { useCallback, useEffect, useRef, useState } from 'react';
import { Sparkles } from 'lucide-react';

/** 悬浮球位置持久化键名。 */
const FLOAT_POS_KEY = 'sphp_c_agent_float_pos';
/** 拖动位移小于该阈值视为点击（避免误触）。 */
const DRAG_THRESHOLD = 6;
/** 贴边留白。 */
const EDGE_PADDING = 12;

interface FloatPos { x: number; y: number; }

/** 读取持久化的悬浮球位置；不存在或越界时返回 null（用默认位置）。 */
function loadPos(): FloatPos | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(FLOAT_POS_KEY);
    if (!raw) return null;
    const pos = JSON.parse(raw) as FloatPos;
    if (!Number.isFinite(pos.x) || !Number.isFinite(pos.y)) return null;
    return pos;
  } catch {
    return null;
  }
}

/** 保存悬浮球位置到 localStorage。 */
function savePos(pos: FloatPos): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(FLOAT_POS_KEY, JSON.stringify(pos));
  } catch {
    /* 忽略写入失败（隐私模式等） */
  }
}

/** 计算默认位置：右下角，避开底部 Tab 栏。 */
function defaultPos(): FloatPos {
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  return { x: vw - 74, y: vh - 144 };
}

/** 将位置限制在视口内。 */
function clampPos(pos: FloatPos, size = 56): FloatPos {
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  return {
    x: Math.max(EDGE_PADDING, Math.min(pos.x, vw - size - EDGE_PADDING)),
    y: Math.max(EDGE_PADDING, Math.min(pos.y, vh - size - EDGE_PADDING)),
  };
}

/** AI 悬浮入口的渲染参数。 */
interface AgentFloatingButtonProps {
  /** 未发生拖动时打开 AI 助手的回调。 */
  onClick: () => void;
}

/**
 * AI 助手悬浮球（可拖动）。
 *
 * 使用 Pointer Events 统一鼠标与触屏拖拽；位移小于阈值视为点击并触发 onClick。
 * 拖动结束后水平贴边吸附（靠左贴左、靠右贴右），位置持久化到 localStorage。
 * 在 /agent 页与 /login 页不展示。
 */
export function AgentFloatingButton({ onClick }: AgentFloatingButtonProps) {
  const [pos, setPos] = useState<FloatPos>(() => loadPos() || (typeof window === 'undefined' ? { x: 0, y: 0 } : defaultPos()));
  const [dragging, setDragging] = useState(false);

  // 拖拽状态：起点、是否已越过点击阈值、pointerId
  const dragRef = useRef<{ startX: number; startY: number; moved: boolean; pointerId: number } | null>(null);

  /** 初始化 / 视口变化时校正越界位置。 */
  useEffect(() => {
    setPos((prev) => clampPos(prev));
    const handleResize = () => setPos((prev) => clampPos(prev));
    window.addEventListener('resize', handleResize);
    window.addEventListener('orientationchange', handleResize);
    return () => {
      window.removeEventListener('resize', handleResize);
      window.removeEventListener('orientationchange', handleResize);
    };
  }, []);

  /** pointerdown：记录起点，准备拖拽。 */
  const handlePointerDown = useCallback((event: React.PointerEvent<HTMLButtonElement>) => {
    dragRef.current = { startX: event.clientX, startY: event.clientY, moved: false, pointerId: event.pointerId };
    event.currentTarget.setPointerCapture(event.pointerId);
  }, []);

  /** pointermove：超过阈值后进入拖拽态并跟随指针。 */
  const handlePointerMove = useCallback((event: React.PointerEvent<HTMLButtonElement>) => {
    const drag = dragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    const dx = event.clientX - drag.startX;
    const dy = event.clientY - drag.startY;
    if (!drag.moved && Math.hypot(dx, dy) < DRAG_THRESHOLD) return;
    if (!drag.moved) {
      drag.moved = true;
      setDragging(true);
    }
    // 以指针为中心移动悬浮球
    setPos(clampPos({ x: event.clientX - 28, y: event.clientY - 28 }));
  }, []);

  /** pointerup：结束拖拽，未移动则触发点击；贴边吸附并持久化。 */
  const handlePointerUp = useCallback(
    (event: React.PointerEvent<HTMLButtonElement>) => {
      const drag = dragRef.current;
      if (drag) {
        event.currentTarget.releasePointerCapture(drag.pointerId);
        const moved = drag.moved;
        dragRef.current = null;
        if (moved) {
          // 贴边吸附：靠左贴左、靠右贴右
          setPos((prev) => {
            const vw = window.innerWidth;
            const snapped = { ...prev, x: prev.x + 28 < vw / 2 ? EDGE_PADDING : vw - 56 - EDGE_PADDING };
            const final = clampPos(snapped);
            savePos(final);
            return final;
          });
          setDragging(false);
          return;
        }
      }
      setDragging(false);
      onClick();
    },
    [onClick],
  );

  /** pointercancel：恢复非拖拽态，不触发点击。 */
  const handlePointerCancel = useCallback(() => {
    dragRef.current = null;
    setDragging(false);
  }, []);

  return (
    <button
      type="button"
      className={`agent-float${dragging ? ' is-dragging' : ''}`}
      aria-label="打开 AI 助手"
      style={{ left: pos.x, top: pos.y, right: 'auto', bottom: 'auto' }}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerCancel={handlePointerCancel}
    >
      <Sparkles size={26} />
      <span className="agent-float__label">AI</span>
    </button>
  );
}
