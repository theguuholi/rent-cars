import { describe, expect, test as scenario } from 'vitest'
import { session } from './session'
import { loginDestination, requireAdmin } from './access'
describe('Return after sign-in', () => {
  scenario.each([
    ['/specifications/new', 'ADMIN', '/specifications/new'],
    ['/categories', 'USER', '/cars'],
    [undefined, 'ADMIN', '/cars'],
    ['//outside.example/path', 'ADMIN', '/cars'],
    ['/\\outside.example', 'ADMIN', '/cars'],
    ['https://outside.example', 'ADMIN', '/cars'],
    ['/path with space', 'ADMIN', '/cars'],
    ['sub/path', 'ADMIN', '/cars'],
    ['/ok\\bad/ok', 'ADMIN', '/cars'],
  ])(
    'Given destination %s and role %s, when sign-in completes, then return to %s',
    (path, role, expected) => {
      expect(loginDestination(path, role!)).toBe(expected)
    },
  )
})

describe('Admin guard', () => {
  scenario.each([
    null,
    { accessToken: 'signed', user: { id: '1', email: 'user@example.test', role: 'USER' as const } },
  ])(
    'Given a visitor without admin privileges, when guarding a protected URL, then the redirect replaces the blocked history entry',
    (identity) => {
      session.set(identity)
      const guard = () =>
        requireAdmin({ context: { session }, location: { pathname: '/categories' } })
      expect(guard).toThrow(
        expect.objectContaining({
          options: expect.objectContaining({
            replace: true,
            to: identity ? '/forbidden' : '/login',
          }),
        }),
      )
    },
  )
})
