import { useLayoutEffect, useRef } from 'react'
import { useRouter } from '@tanstack/react-router'
import { session } from '../features/auth/session'

/** A late result may refresh server data, but only its originating screen may change the UI. */
export function useCurrentAction() {
  const router = useRouter()
  const active = useRef<symbol | undefined>(undefined)
  useLayoutEffect(() => {
    const invalidate = () => {
      active.current = undefined
    }
    const leave = router.history.subscribe(invalidate)
    const changeAccount = session.subscribe(invalidate)
    return () => {
      invalidate()
      leave()
      changeAccount()
    }
  }, [router])
  return () => {
    const started = Symbol()
    active.current = started
    return () => active.current === started
  }
}
