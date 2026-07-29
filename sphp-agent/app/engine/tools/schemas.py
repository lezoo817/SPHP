"""工具参数 OpenAPI Schema 集中定义。

从各工具的 param_schema 中提取，便于统一管理和复用。
当前 param_schema 在 c_tools.py / b_tools.py 中内联定义，
后续可全部迁移至此。
"""

# TODO: 将 c_tools / b_tools 中的 param_schema dict 迁移至此，
# 注册时直接引用，避免散落两处。
