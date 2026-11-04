import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useRouter } from '@tanstack/react-router'
import { Layout } from '../../../../components/Layout/Layout'
import { session } from '../../session'

export function SessionLayout() {
  const client = useQueryClient()
  const router = useRouter()
  useEffect(
    () =>
      session.subscribe(() => {
        // Removing queries also cancels their in-flight fetches. No account inherits the previous cache.
        client.clear()
        void router.invalidate()
      }),
    [client, router],
  )
  return <Layout />
}
