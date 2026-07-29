// Swapped in for the "e2e" build configuration (see angular.json), so
// 04-balance-updates.spec.ts's SSE-renewal test doesn't have to wait the real 4 minutes to
// observe one. Short enough to keep that test fast; long enough that no other e2e test's own
// login-to-completion window should ever overlap a renewal - each test's timer restarts fresh at
// its own login (see notification.service.ts's startEventSource), so what matters is a single
// test's own duration staying well under this, not the whole suite's.
export const environment = {
  notificationRenewMs: 20 * 1000,
};
