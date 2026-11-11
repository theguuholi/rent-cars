import { init } from '@sentry/react'

export function startErrorReporting(dsn: string | undefined) {
  if (dsn) init({ dsn })
}
