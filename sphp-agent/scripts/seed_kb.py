"""知识库批量灌库脚本（本地/运维用，非对外 API）。

用法:
    python scripts/seed_kb.py [目录]

将目录下所有支持格式文件入库，按子目录名推断 category
（patient_edu / clinical_ref），默认灌入 docs/kb/。

设计说明:
    - 幂等：确定性 ID，重复灌入自动覆盖（upsert），不产生重复数据。
    - Windows 兼容：psycopg 异步驱动不兼容 ProactorEventLoop，
      显式切换 SelectorEventLoop（与 tests 的 conftest 同理），
      保证本地 Windows 可直接灌库。
"""

import asyncio
import selectors
import sys
from collections.abc import Coroutine
from pathlib import Path
from typing import Any

_PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(_PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(_PROJECT_ROOT))

from app.engine.rag.ingest import ingest_directory  # noqa: E402 — 项目根已插入 sys.path

_DEFAULT_ROOT = _PROJECT_ROOT / "docs" / "kb"


def _run_selector(coro: Coroutine[Any, Any, int]) -> int:
    """在 SelectorEventLoop 上运行协程，返回其结果。"""
    loop = asyncio.SelectorEventLoop(selectors.SelectSelector())
    try:
        return loop.run_until_complete(coro)
    finally:
        loop.close()


def main() -> int:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else _DEFAULT_ROOT
    total = _run_selector(ingest_directory(root))
    print(f"入库完成: {root} 共 {total} 个 chunk")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
