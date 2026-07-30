"""工具引擎：Function Calling Schema 注册中心 + C/B 端 Schema。

- schema_registry.py: ToolSchema dataclass + ToolRegistry 全局注册表
- c_schemas.py: C 端工具 Schema（30 个，启动时加载）
- b_schemas.py: B 端工具 Schema（9 个，启动时加载）

工具执行逻辑已移至 app.orchestrator.nodes.tool_executor。
"""
