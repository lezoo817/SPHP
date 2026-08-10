import type { PropsWithChildren } from 'react';
import { X } from 'lucide-react';

/** 通用弹层的标题、内容与关闭回调。 */
interface DialogProps extends PropsWithChildren {
  /** 弹层标题，同时用于无障碍标签。 */
  title: string;
  /** 点击遮罩或关闭图标时执行的回调。 */
  onClose: () => void;
}

/** 展示居中的轻量表单或确认弹层。 */
export function Dialog({ title, onClose, children }: DialogProps) {
  return (
    <div className="dialog-mask" role="presentation" onMouseDown={onClose}>
      <section className="dialog" role="dialog" aria-modal="true" aria-label={title} onMouseDown={(event) => event.stopPropagation()}>
        <div className="dialog__header">
          <h2>{title}</h2>
          <button className="icon-button" type="button" aria-label="关闭" onClick={onClose}><X size={20} /></button>
        </div>
        {children}
      </section>
    </div>
  );
}
