import { expect, describe, test as scenario } from 'vitest'
import { createApiProxy } from './api-proxy'

describe('Development API proxy', () => {
  scenario.each([undefined, '', '  '])(
    'Given API target %j, when the proxy is configured, then it uses the local Java backend',
    (target) => {
      expect(createApiProxy(target)['/api'].target).toBe('http://127.0.0.1:8080')
    },
  )
  scenario(
    'Given an alternate backend, when the proxy rewrites a request, then only the leading API segment is removed',
    () => {
      const proxy = createApiProxy(' http://127.0.0.1:8081 ')['/api']
      expect(proxy.target).toBe('http://127.0.0.1:8081')
      expect(proxy.rewrite?.('/api/specifications/123?view=api')).toBe(
        '/specifications/123?view=api',
      )
      expect(proxy.rewrite?.('/apiary')).toBe('/apiary')
    },
  )
})
