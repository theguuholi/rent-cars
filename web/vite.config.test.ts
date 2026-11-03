// @vitest-environment node
import { resolveConfig } from 'vite'
import { afterEach, expect, describe, test as scenario, vi } from 'vitest'

describe('Vite configuration', () => {
  afterEach(() => vi.unstubAllEnvs())

  scenario(
    'Given API_TARGET points to Go, when Vite loads its dev config, then the proxy targets Go',
    async () => {
      vi.stubEnv('API_TARGET', 'http://127.0.0.1:8081')
      const config = await resolveConfig({}, 'serve', 'development')
      expect(config.server.proxy?.['/api']).toMatchObject({ target: 'http://127.0.0.1:8081' })
    },
  )
})
