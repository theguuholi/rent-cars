import { renderHook, act } from '@testing-library/react'
import { createMemoryHistory, useRouter } from '@tanstack/react-router'
import { describe, expect, test as scenario, vi } from 'vitest'
import { session } from '../features/auth/session'
import { useCurrentAction } from './use-current-action'

vi.mock('@tanstack/react-router', async (original) => ({
  ...(await original<typeof import('@tanstack/react-router')>()),
  useRouter: vi.fn(),
}))

describe('Action lifetime', () => {
  scenario(
    'Given two attempts, when the second starts, then only the latest result can act until navigation or a session change',
    () => {
      const history = createMemoryHistory()
      vi.mocked(useRouter).mockReturnValue({ history } as ReturnType<typeof useRouter>)
      const { result } = renderHook(useCurrentAction)
      const first = result.current(),
        latest = result.current()
      expect(first()).toBe(false)
      expect(latest()).toBe(true)
      history.push('/cars')
      expect(latest()).toBe(false)
      const next = result.current()
      act(() => session.set(null))
      expect(next()).toBe(false)
    },
  )
  scenario(
    'Given a replacement router, when the screen changes owner or unmounts, then pending attempts and old subscriptions are released',
    () => {
      const first = createMemoryHistory(),
        second = createMemoryHistory()
      const unsubscribe = vi.fn()
      const watch = vi.spyOn(session, 'subscribe').mockReturnValue(unsubscribe)
      try {
        vi.mocked(useRouter).mockReturnValue({ history: first } as ReturnType<typeof useRouter>)
        const { result, rerender, unmount } = renderHook(useCurrentAction)
        const old = result.current()
        vi.mocked(useRouter).mockReturnValue({ history: second } as ReturnType<typeof useRouter>)
        rerender()
        expect(old()).toBe(false)
        expect(first.subscribers.size).toBe(0)
        expect(unsubscribe).toHaveBeenCalledTimes(1)
        const active = result.current()
        first.push('/old')
        expect(active()).toBe(true)
        unmount()
        expect(active()).toBe(false)
        expect(second.subscribers.size).toBe(0)
        expect(unsubscribe).toHaveBeenCalledTimes(2)
      } finally {
        watch.mockRestore()
      }
    },
  )
})
