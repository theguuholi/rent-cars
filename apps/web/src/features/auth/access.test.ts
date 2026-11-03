import { describe, expect, test as scenario } from 'vitest'
import { loginDestination } from './access'
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
