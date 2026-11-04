import { describe, expect, test as scenario } from 'vitest'
import { checkEditConflict, StaleEdit } from './edit-conflict'

describe('Version conflict responses', () => {
  scenario.each([400, 404, 422, 500])(
    'Given a problem response with status %i, when checking for a stale version, then normal error handling continues',
    (status) => {
      expect(() =>
        checkEditConflict(
          new Response(null, { status, headers: { 'Content-Type': 'application/problem+json' } }),
        ),
      ).not.toThrow()
    },
  )
  scenario(
    'Given a stale form, when the API reports a conflicting version, then ask to reopen the record',
    () => {
      const check = () =>
        checkEditConflict(
          new Response(null, {
            status: 409,
            headers: { 'Content-Type': 'application/problem+json' },
          }),
        )
      expect(check).toThrow(StaleEdit)
      expect(check).toThrow('The record changed. Reopen the editor to review its current values.')
    },
  )
})
