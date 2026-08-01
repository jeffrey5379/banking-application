export const environment = {
  // How often notification.service.ts proactively closes
  // and reopens its SSE connection, well ahead of anything else
  notificationRenewMs: 4 * 60 * 1000,
};
