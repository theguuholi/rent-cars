import { describe, expect, test as scenario, vi } from 'vitest'
import { session } from './session'
describe('Session subscription', () => {
  scenario(
    'Given two observers, when one leaves before the next session change, then only the remaining observer receives that change',
    () => {
      const left = vi.fn(),
        active = vi.fn()
      const unsubscribe = session.subscribe(left),
        finish = session.subscribe(active)
      unsubscribe()
      session.set(null)
      expect(left).not.toHaveBeenCalled()
      expect(active).toHaveBeenCalledOnce()
      finish()
    },
  )
})
