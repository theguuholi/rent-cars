import { expect, it } from 'vitest'
import { createApiProxy } from './api-proxy'

it.each([undefined, '', '  '])(
  'defaults to the Java API for an unset or empty target',
  (target) => {
    expect(createApiProxy(target)['/api'].target).toBe('http://127.0.0.1:8080')
  },
)
it('accepts the alternate backend and strips only the leading API segment', () => {
  const proxy = createApiProxy(' http://127.0.0.1:8081 ')['/api']
  expect(proxy.target).toBe('http://127.0.0.1:8081')
  expect(proxy.rewrite?.('/api/specifications/123?view=api')).toBe('/specifications/123?view=api')
  expect(proxy.rewrite?.('/apiary')).toBe('/apiary')
})
