const DEFAULT_BASE_URL = process.env.SMART_NOTEBOOK_BASE_URL || 'http://localhost:8080/api';
const POLL_INTERVAL_MS = Number(process.env.E2E_POLL_INTERVAL_MS || 2000);
const POLL_TIMEOUT_MS = Number(process.env.E2E_POLL_TIMEOUT_MS || 120000);
const QUERY_LATENCY_BUDGET_MS = Number(process.env.E2E_QUERY_LATENCY_BUDGET_MS || 15000);
const ENFORCE_LATENCY_BUDGET = (process.env.E2E_ENFORCE_LATENCY_BUDGET || 'false').toLowerCase() === 'true';
const TEST_FILE_BASE_CONTENT =
  'Cloud computing provides on-demand access to computing resources over the internet.';

function logStep(step, passed, detail = '') {
  const status = passed ? 'PASS' : 'FAIL';
  const message = detail ? ` - ${detail}` : '';
  console.log(`[${status}] ${step}${message}`);
}

function sleep(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

function safeStringify(value) {
  if (value === null || value === undefined) {
    return 'null';
  }

  if (typeof value === 'string') {
    return value;
  }

  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

async function readResponse(response) {
  const rawBody = await response.text();

  if (!rawBody) {
    return null;
  }

  try {
    return JSON.parse(rawBody);
  } catch {
    return rawBody;
  }
}

async function request(url, options, expectedStatusCodes) {
  let response;

  try {
    response = await fetch(url, options);
  } catch (error) {
    throw new Error(
      `backend unreachable at ${url}. Error: ${error instanceof Error ? error.message : String(error)}`
    );
  }

  const body = await readResponse(response);

  if (!expectedStatusCodes.includes(response.status)) {
    throw new Error(`status=${response.status}, body=${safeStringify(body)}`);
  }

  return { status: response.status, body };
}

async function runStep(stepName, action) {
  try {
    const result = await action();
    const detail = typeof result?.detail === 'string' ? result.detail : '';
    logStep(stepName, true, detail);
    return result?.value;
  } catch (error) {
    logStep(stepName, false, error instanceof Error ? error.message : String(error));
    throw error;
  }
}

function createUploadFormData(filePayload) {
  const formData = new FormData();
  const blob = new Blob([filePayload.content], { type: 'text/plain' });

  formData.append('file', blob, filePayload.fileName);
  formData.append('title', filePayload.title);

  return formData;
}

function printSummary(summary, overallPass) {
  console.log('\n================ FINAL SUMMARY ================');
  console.log(`overallResult: ${overallPass ? 'PASS' : 'FAIL'}`);
  console.log(`notebookId: ${summary.notebookId ?? 'N/A'}`);
  console.log(`documentId: ${summary.documentId ?? 'N/A'}`);
  console.log(`taskId: ${summary.taskId ?? 'N/A'}`);
  console.log(`final task status: ${summary.finalTaskStatus ?? 'N/A'}`);
  console.log(`query latency ms: ${summary.queryLatencyMs ?? 'N/A'}`);
  console.log(`query latency budget ms: ${summary.queryLatencyBudgetMs}`);
  console.log(`latency budget result: ${summary.queryLatencyBudgetResult}`);
  console.log(`query confidence: ${summary.queryConfidence ?? 'N/A'}`);
  console.log(`number of citations: ${summary.citationCount ?? 'N/A'}`);
  console.log(`duplicate-upload test result: ${summary.duplicateUploadResult}`);
  console.log(`model management tests: ${summary.modelManagementResult ?? 'NOT_RUN'}`);
  console.log('===============================================\n');
}

export async function runSmartNotebookE2E(config = {}) {
  const baseUrl = config.baseUrl ?? DEFAULT_BASE_URL;
  const pollIntervalMs = config.pollIntervalMs ?? POLL_INTERVAL_MS;
  const pollTimeoutMs = config.pollTimeoutMs ?? POLL_TIMEOUT_MS;
  const latencyBudgetMs = config.queryLatencyBudgetMs ?? QUERY_LATENCY_BUDGET_MS;
  const runMarker = config.runMarker ?? `${Date.now()}-${Math.floor(Math.random() * 1e6)}`;
  const uploadFile = {
    fileName: `cloud-computing-e2e-${runMarker}.txt`,
    title: `Cloud Computing E2E Document ${runMarker}`,
    content: `${TEST_FILE_BASE_CONTENT}\nRun marker: ${runMarker}`,
  };

  const summary = {
    notebookId: null,
    documentId: null,
    taskId: null,
    finalTaskStatus: null,
    queryLatencyMs: null,
    queryLatencyBudgetMs: latencyBudgetMs,
    queryLatencyBudgetResult: 'NOT_EVALUATED',
    queryConfidence: null,
    citationCount: 0,
    duplicateUploadResult: 'NOT_RUN',
    modelManagementResult: 'NOT_RUN',
  };

  let overallPass = false;

  try {
    await runStep('A) Preflight GET /health', async () => {
      const health = await request(`${baseUrl}/health`, { method: 'GET' }, [200, 503]);
      const body = typeof health.body === 'object' && health.body !== null ? health.body : {};
      const appStatus = body.status;
      const workerStatus = body.worker?.status;

      if (workerStatus !== 'UP') {
        throw new Error(
          `worker not ready (http=${health.status}, appStatus=${appStatus ?? 'UNKNOWN'}, workerStatus=${workerStatus ?? 'MISSING'}, body=${safeStringify(body)})`
        );
      }

      return {
        detail: `reachable (http=${health.status}, appStatus=${appStatus ?? 'UNKNOWN'}, workerStatus=${workerStatus})`,
      };
    });

    const notebookId = await runStep('B1) Create notebook', async () => {
      const createNotebookPayload = {
        name: `E2E Notebook ${new Date().toISOString()}`,
        description: 'Frontend integration test',
      };

      const createNotebook = await request(
        `${baseUrl}/notebooks`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(createNotebookPayload),
        },
        [201]
      );

      const createdNotebookId =
        typeof createNotebook.body === 'object' && createNotebook.body !== null ? createNotebook.body.id : null;

      if (!createdNotebookId) {
        throw new Error(`status=201, body=${safeStringify(createNotebook.body)}`);
      }

      summary.notebookId = createdNotebookId;
      return { detail: `notebookId=${createdNotebookId}`, value: createdNotebookId };
    });

    const uploadResult = await runStep('B2) Upload document', async () => {
      const uploadDocument = await request(
        `${baseUrl}/notebooks/${notebookId}/documents/upload`,
        {
          method: 'POST',
          body: createUploadFormData(uploadFile),
        },
        [202]
      );

      const documentId =
        typeof uploadDocument.body === 'object' && uploadDocument.body !== null ? uploadDocument.body.documentId : null;
      const taskId = typeof uploadDocument.body === 'object' && uploadDocument.body !== null ? uploadDocument.body.taskId : null;

      if (!documentId || !taskId) {
        throw new Error(`status=202, body=${safeStringify(uploadDocument.body)}`);
      }

      summary.documentId = documentId;
      summary.taskId = taskId;
      return { detail: `documentId=${documentId}, taskId=${taskId}`, value: { documentId, taskId } };
    });

    const { documentId, taskId } = uploadResult;

    let firstFailure = null;
    const rememberFailure = (error) => {
      if (!firstFailure) {
        firstFailure = error;
      }
    };

    try {
      await runStep('B3) Poll task status', async () => {
        const pollingStart = Date.now();
        let lastTaskResponse = null;

        while (Date.now() - pollingStart <= pollTimeoutMs) {
          const taskStatus = await request(
            `${baseUrl}/tasks/${taskId}/status`,
            { method: 'GET' },
            [200]
          );

          lastTaskResponse = taskStatus;
          const status = typeof taskStatus.body === 'object' && taskStatus.body !== null ? taskStatus.body.status : null;
          summary.finalTaskStatus = status ?? 'UNKNOWN';

          if (status === 'COMPLETED') {
            return { detail: 'status=COMPLETED' };
          }

          if (status === 'FAILED' || status === 'DEAD_LETTER') {
            throw new Error(`status=200, body=${safeStringify(taskStatus.body)}`);
          }

          await sleep(pollIntervalMs);
        }

        const lastStatusCode = lastTaskResponse ? lastTaskResponse.status : 'N/A';
        const lastBody = lastTaskResponse ? safeStringify(lastTaskResponse.body) : 'null';
        throw new Error(`timed out after ${pollTimeoutMs}ms, last=status=${lastStatusCode}, body=${lastBody}`);
      });
    } catch (error) {
      rememberFailure(error);
    }

    if (summary.finalTaskStatus === 'COMPLETED') {
      try {
        await runStep('B4) Query with citations + latency budget', async () => {
          const query = await request(
            `${baseUrl}/query`,
            {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                question: 'What is cloud computing?',
                topK: 5,
                documentIds: [documentId],
              }),
            },
            [200]
          );

          const answer = typeof query.body === 'object' && query.body !== null ? query.body.answer : null;
          const citations = typeof query.body === 'object' && query.body !== null ? query.body.citations : null;
          const confidence = typeof query.body === 'object' && query.body !== null ? query.body.confidence : null;
          const latencyMs = typeof query.body === 'object' && query.body !== null ? query.body.latencyMs : null;

          const hasAnswer = typeof answer === 'string' && answer.trim().length > 0;
          const hasCitations = Array.isArray(citations) && citations.length >= 1;
          const confidenceIsNumber = typeof confidence === 'number' && !Number.isNaN(confidence);
          const latencyIsNumber = typeof latencyMs === 'number' && !Number.isNaN(latencyMs);

          if (!hasAnswer || !hasCitations || !confidenceIsNumber || !latencyIsNumber) {
            throw new Error(`status=200, body=${safeStringify(query.body)}`);
          }

          if (latencyMs > latencyBudgetMs) {
            const message = `query latency ${latencyMs}ms exceeded budget ${latencyBudgetMs}ms`;
            summary.queryLatencyBudgetResult = `OVER_BUDGET (${latencyMs}ms > ${latencyBudgetMs}ms)`;
            if (ENFORCE_LATENCY_BUDGET) {
              throw new Error(message);
            }
            console.warn(`[WARN] ${message}`);
          } else {
            summary.queryLatencyBudgetResult = `WITHIN_BUDGET (${latencyMs}ms <= ${latencyBudgetMs}ms)`;
          }

          summary.queryLatencyMs = latencyMs;
          summary.queryConfidence = confidence;
          summary.citationCount = citations.length;
          return { detail: `latency=${latencyMs}ms, confidence=${confidence}, citations=${citations.length}` };
        });
      } catch (error) {
        rememberFailure(error);
      }
    } else {
      const reason = `skipped because task status is ${summary.finalTaskStatus ?? 'UNKNOWN'}`;
      logStep('B4) Query with citations + latency budget', false, reason);
      rememberFailure(new Error(reason));
    }

    try {
      await runStep('B5) List notebooks contains created notebook', async () => {
        const listNotebooks = await request(`${baseUrl}/notebooks`, { method: 'GET' }, [200]);

        if (!Array.isArray(listNotebooks.body)) {
          throw new Error(`status=200, body=${safeStringify(listNotebooks.body)}`);
        }

        const notebookFound = listNotebooks.body.some((item) => item && item.id === notebookId);
        if (!notebookFound) {
          throw new Error(`status=200, body=${safeStringify(listNotebooks.body)}`);
        }

        return { detail: `notebookId=${notebookId}` };
      });
    } catch (error) {
      rememberFailure(error);
    }

    try {
      await runStep('B6) List documents contains uploaded document', async () => {
        const listDocuments = await request(
          `${baseUrl}/notebooks/${notebookId}/documents?page=0&size=20`,
          { method: 'GET' },
          [200]
        );

        const documentsPayload = listDocuments.body;
        const documents =
          typeof documentsPayload === 'object' && documentsPayload !== null && Array.isArray(documentsPayload.documents)
            ? documentsPayload.documents
            : typeof documentsPayload === 'object' &&
                documentsPayload !== null &&
                Array.isArray(documentsPayload.content)
              ? documentsPayload.content
              : null;

        if (!documents) {
          throw new Error(`status=200, body=${safeStringify(listDocuments.body)}`);
        }

        const documentFound = documents.some((item) => item && item.id === documentId);
        if (!documentFound) {
          throw new Error(`status=200, body=${safeStringify(listDocuments.body)}`);
        }

        return { detail: `documentId=${documentId}` };
      });
    } catch (error) {
      rememberFailure(error);
    }

    try {
      await runStep('C1) Duplicate upload returns 409 DUPLICATE_DOCUMENT', async () => {
        const duplicateUpload = await request(
          `${baseUrl}/notebooks/${notebookId}/documents/upload`,
          {
            method: 'POST',
            body: createUploadFormData(uploadFile),
          },
          [409]
        );

        const duplicateCode =
          typeof duplicateUpload.body === 'object' && duplicateUpload.body !== null ? duplicateUpload.body.code : null;

        if (duplicateCode !== 'DUPLICATE_DOCUMENT') {
          summary.duplicateUploadResult = `FAIL (status=409, body=${safeStringify(duplicateUpload.body)})`;
          throw new Error(`status=409, body=${safeStringify(duplicateUpload.body)}`);
        }

        summary.duplicateUploadResult = 'PASS (409 DUPLICATE_DOCUMENT)';
        return { detail: 'code=DUPLICATE_DOCUMENT' };
      });
    } catch (error) {
      rememberFailure(error);
    }

    // ─── D) Model Management Tests ───
    let modelMgmtPassed = 0;
    let modelMgmtTotal = 5;

    try {
      await runStep('D1) List local models', async () => {
        const models = await request(`${baseUrl}/models/local`, { method: 'GET' }, [200]);

        if (!Array.isArray(models.body)) {
          throw new Error(`status=200, body=${safeStringify(models.body)}`);
        }

        modelMgmtPassed++;
        return { detail: `count=${models.body.length}` };
      });
    } catch (error) {
      rememberFailure(error);
    }

    try {
      await runStep('D2) List providers', async () => {
        const providers = await request(`${baseUrl}/providers`, { method: 'GET' }, [200]);

        if (!Array.isArray(providers.body)) {
          throw new Error(`status=200, body=${safeStringify(providers.body)}`);
        }

        const providerNames = providers.body.map((p) => p.providerName).join(', ');
        modelMgmtPassed++;
        return { detail: `providers=[${providerNames}]` };
      });
    } catch (error) {
      rememberFailure(error);
    }

    try {
      await runStep('D3) Get global pipeline config', async () => {
        const config = await request(`${baseUrl}/pipeline-config`, { method: 'GET' }, [200]);

        const body = config.body;
        if (typeof body !== 'object' || body === null) {
          throw new Error(`status=200, body=${safeStringify(body)}`);
        }

        const extraction = body.extraction?.modelName || 'none';
        const embedding = body.embedding?.modelName || 'none';
        const chat = body.chat?.modelName || 'none';
        modelMgmtPassed++;
        return { detail: `extraction=${extraction}, embedding=${embedding}, chat=${chat}` };
      });
    } catch (error) {
      rememberFailure(error);
    }

    try {
      await runStep('D4) Get quotas', async () => {
        const quotas = await request(`${baseUrl}/quotas`, { method: 'GET' }, [200]);

        if (!Array.isArray(quotas.body)) {
          throw new Error(`status=200, body=${safeStringify(quotas.body)}`);
        }

        modelMgmtPassed++;
        return { detail: `count=${quotas.body.length}` };
      });
    } catch (error) {
      rememberFailure(error);
    }

    try {
      await runStep('D5) Get hardware info', async () => {
        const hardware = await request(`${baseUrl}/models/local/hardware`, { method: 'GET' }, [200]);

        const body = hardware.body;
        if (typeof body !== 'object' || body === null) {
          throw new Error(`status=200, body=${safeStringify(body)}`);
        }

        const ram = body.totalRamGb || 'unknown';
        const tier = body.recommendedTier || 'unknown';
        modelMgmtPassed++;
        return { detail: `ram=${ram}GB, tier=${tier}` };
      });
    } catch (error) {
      rememberFailure(error);
    }

    summary.modelManagementResult = `${modelMgmtPassed}/${modelMgmtTotal} passed`;

    if (firstFailure) {
      throw firstFailure;
    }

    overallPass = true;
  } catch {
    overallPass = false;
  } finally {
    printSummary(summary, overallPass);
  }

  return { success: overallPass, summary };
}

const shouldAutoRun =
  typeof process !== 'undefined' &&
  Array.isArray(process.argv) &&
  process.argv[1] &&
  import.meta.url.endsWith(process.argv[1]);

if (shouldAutoRun) {
  runSmartNotebookE2E().then((result) => {
    if (!result.success) {
      process.exitCode = 1;
    }
  });
}
