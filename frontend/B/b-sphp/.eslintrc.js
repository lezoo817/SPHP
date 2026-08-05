module.exports = {
  extends: require.resolve('@umijs/max/eslint'),
  rules: {
    // function 声明会被提升（hoisting），先引用后声明是安全的
    '@typescript-eslint/no-use-before-define': ['error', { functions: false }],
  },
};
