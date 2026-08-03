# SDD ledger — plan: docs/superpowers/plans/2026-08-03-drug-inventory.md

> 适配说明：CLAUDE.md 红线禁止 git 写操作与 AI 编译，本任务不建 worktree、不 commit、不跑 mvn。implementer 只创建文件，controller 逐一核对产出文件。验收（mvn compile + Apifox 冒烟）由用户手动执行。

## 任务状态

- Task 1: complete (8 文件已创建，controller 逐文件核对与简报一致；@MapperScan("com.sphp.admin.**.mapper") 已覆盖 pharmacy.mapper，无需 @Mapper；review clean)
- Task 2: complete (6 文件已创建，controller 读取 DrugServiceImpl/DrugAdminController 与简报一致；concerns 均设计内；review clean)
- Task 3: complete (7 文件已创建，controller 读取 InventoryServiceImpl/InventoryController 与简报一致；computeStatus NPE concern 因列 NOT NULL 判定无效；review clean)

## 最终整体审查

- 21 个文件全部创建且与计划逐字一致；两个 Service 接口签名与实现完全匹配；@MapperScan("com.sphp.admin.**.mapper") 覆盖 pharmacy.mapper。
- 全部 implementer concern 已裁决（见各任务行）。无 Critical/Important 遗留。
- 因 CLAUDE.md 红线：未 commit、未编译、未建 worktree。SDD workspace（briefs/reports/ledger）保留至用户手动提交后。

## Minor/Deferred

- (none)
