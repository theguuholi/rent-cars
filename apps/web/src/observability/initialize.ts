export async function initializeObservability(
  dsn: string | undefined,
  tracesUrl: string | undefined,
) {
  // An extra settled value has no observable effect on optional telemetry startup.
  // Stryker disable next-line ArrayDeclaration
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
