const baseUrl = process.env.SMART_NOTEBOOK_BASE_URL || 'http://localhost:8080/api';
const timeoutMs = Number(process.env.READINESS_TIMEOUT_MS || 120000);
const pollIntervalMs = Number(process.env.READINESS_POLL_INTERVAL_MS || 2000);

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function fetchHealth() {
  const response = await fetch(`${baseUrl}/health`, { method: 'GET' });
  const bodyText = await response.text();

  let body = null;
  try {
    body = bodyText ? JSON.parse(bodyText) : null;
  } catch {
    body = bodyText;
  }

  return { statusCode: response.status, body };
}

async function waitForReady() {
  const start = Date.now();
  let last = null;

  while (Date.now() - start <= timeoutMs) {
    try {
      last = await fetchHealth();
      const payload = typeof last.body === 'object' && last.body !== null ? last.body : {};
      const appStatus = payload.status;
      const workerStatus = payload.worker?.status;

      if (appStatus === 'UP' && workerStatus === 'UP') {
        console.log(
          `Ready: http=${last.statusCode}, appStatus=${appStatus}, workerStatus=${workerStatus}`
        );
        return;
      }
    } catch (error) {
      last = { statusCode: 'ERR', body: String(error) };
    }

    await sleep(pollIntervalMs);
  }

  const detail = last
    ? `http=${last.statusCode}, body=${typeof last.body === 'string' ? last.body : JSON.stringify(last.body)}`
    : 'no health response';
  throw new Error(`Readiness timeout after ${timeoutMs}ms (${detail})`);
}

waitForReady().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
