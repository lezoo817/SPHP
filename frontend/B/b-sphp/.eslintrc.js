module.exports = {
  extends: require.resolve('@umijs/max/eslint'),
  rules: {
    // function 声明会被提升（hoisting），先引用后声明是安全的
    '@typescript-eslint/no-use-before-define': ['error', { functions: false }],
    // 工程红线：hooks 依赖必须完整，防止闭包过期值
    'react-hooks/exhaustive-deps': 'error',
  },
};
