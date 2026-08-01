import { execSync } from "node:child_process";
import path from "node:path";

// Always runs, even right after a fresh 'docker compose up'

const COMPOSE_FILE = path.resolve(__dirname, "../../docker-compose.e2e.yml");
const MONGO_CONTAINER = "bankapp-e2e-mongo-1";
// Dev-only credentials, must match docker-compose.e2e.yml's mongo service.
const MONGO_USERNAME = "bankapp";
const MONGO_PASSWORD = "0ca39c46e075fcc9b4b5446eeab90e9f";

async function waitUntilHealthy(url: string, label: string, timeoutMs = 60_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(url);
      if (res.ok) return;
    } catch {
      // Not accepting connections yet - keep polling.
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  throw new Error(`${label} did not report healthy within ${timeoutMs}ms (${url})`);
}

export default async function globalSetup(): Promise<void> {
  console.log("[global-setup] Dropping notification-service's MongoDB database...");
  execSync(
    `docker exec ${MONGO_CONTAINER} mongosh -u ${MONGO_USERNAME} -p ${MONGO_PASSWORD} ` +
      `--authenticationDatabase admin --quiet --eval "db.getSiblingDB('notifications').dropDatabase()"`,
    { stdio: "inherit" },
  );

  console.log("[global-setup] Restarting core-banking and notification-service...");
  execSync(`docker compose -f "${COMPOSE_FILE}" restart core-banking notification-service`, { stdio: "inherit" });

  console.log("[global-setup] Waiting for both to report healthy again...");
  await Promise.all([
    waitUntilHealthy("http://localhost:8082/actuator/health", "core-banking"),
    waitUntilHealthy("http://localhost:8083/actuator/health", "notification-service"),
  ]);
  console.log("[global-setup] Done - backend state reset for this run.");
}
