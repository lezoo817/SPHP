import type { PropsWithChildren } from 'react';
import { X } from 'lucide-react';

interface DialogProps extends PropsWithChildren {
  title: string;
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
