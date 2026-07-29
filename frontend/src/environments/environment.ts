// Angular's build-time equivalent of a Java properties file: swapped per angular.json build
// configuration via fileReplacements (see environment.e2e.ts, used only by the "e2e" config).
export const environment = {
  // How often notification.service.ts proactively closes and reopens its SSE connection, well
  // ahead of anything else (idle timeouts, etc.) getting a chance to kill it first - see that
  // file's own comment on MAX_CONNECTION_AGE_MS for the full reasoning.
  notificationRenewMs: 4 * 60 * 1000,
};
