export default function access(initialState: { currentUser?: API.User }) {
  const { currentUser } = initialState;
  const roles = currentUser?.roles ?? [];

  return {
    isAuthenticated: !!currentUser,
    isAdmin: roles.includes('ADMIN'),
    isDeptHead: roles.includes('DEPT_HEAD'),
    canAudit: roles.includes('ADMIN') || roles.includes('DEPT_HEAD'),
  };
}