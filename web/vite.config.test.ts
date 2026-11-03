// @vitest-environment node
import { resolveConfig } from 'vite'
import { afterEach, expect, it, vi } from 'vitest'

afterEach(() => vi.unstubAllEnvs())

it('uses API_TARGET for the local dev proxy', async () => {
  vi.stubEnv('API_TARGET', 'http://127.0.0.1:8081')
  const config = await resolveConfig({}, 'serve', 'development')
  expect(config.server.proxy?.['/api']).toMatchObject({ target: 'http://127.0.0.1:8081' })
})
