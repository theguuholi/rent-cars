import { restore, onDeviceChange } from './features/auth/renewal'
import { retryQuery } from './features/auth/protected-fetch'
import './styles.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createAppRouter } from './router'
import { RouterProvider } from '@tanstack/react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AppErrorBoundary } from './components/AppErrorBoundary/AppErrorBoundary'
import { initializeObservability } from './observability/initialize'

const telemetryReady = initializeObservability(
  import.meta.env.VITE_SENTRY_DSN,
  import.meta.env.VITE_OTEL_TRACES_URL,
)

const router = createAppRouter()
const queryClient = new QueryClient({ defaultOptions: { queries: { retry: retryQuery } } })

window.addEventListener('storage', onDeviceChange)
const root = createRoot(document.getElementById('root')!)
async function mount() {
  try {
    await telemetryReady
    await restore()
  } catch {
    root.render(
      <button onClick={() => void mount()}>Could not restore your session. Try again</button>,
    )
    return
  }
  root.render(
    <StrictMode>
      <AppErrorBoundary>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </AppErrorBoundary>
    </StrictMode>,
  )
}
void mount()
