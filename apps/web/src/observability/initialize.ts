export async function initializeObservability(
  dsn: string | undefined,
  tracesUrl: string | undefined,
) {
  const tasks: Promise<unknown>[] = []
  if (dsn) {
    tasks.push(
      import('./error-reporting').then(({ startErrorReporting }) => startErrorReporting(dsn)),
    )
  }
  if (tracesUrl) {
    tasks.push(import('./tracing').then(({ startTracing }) => startTracing(tracesUrl)))
  }
  await Promise.allSettled(tasks)
}
