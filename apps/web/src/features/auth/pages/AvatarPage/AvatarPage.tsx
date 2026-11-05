import { useCurrentAction } from '../../../../routes/use-current-action'
import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { PageSection } from '../../../../components/PageSection/PageSection'
import { readAvatar, saveAvatar } from '../../avatar-api'

export function AvatarPage() {
  const [file, setFile] = useState<File | null>(null)
  const [url, setUrl] = useState<string>()
  const client = useQueryClient()
  const avatar = useQuery({ queryKey: ['my-avatar'], queryFn: ({ signal }) => readAvatar(signal) })
  const begin = useCurrentAction()
  const change = useMutation({
    onMutate: begin,
    mutationFn: saveAvatar,
    onSuccess: async (_data, _variables, current) => {
      if (!current()) return
      setFile(null)
      await client.invalidateQueries({ queryKey: ['my-avatar'] })
    },
  })
  const image = file ?? avatar.data
  useEffect(() => {
    if (!image) {
      setUrl(undefined)
      return
    }
    const preview = URL.createObjectURL(image)
    setUrl(preview)
    return () => URL.revokeObjectURL(preview)
  }, [image])
  return (
    <PageSection
      eyebrow="Account"
      title="My avatar"
      description="Choose a JPEG or PNG up to 5 MiB and 4 megapixels."
    >
      {avatar.isPending && <p role="status">Loading avatar…</p>}
      {avatar.isError && (
        <p role="alert">
          {avatar.error.message}{' '}
          <button type="button" onClick={() => void avatar.refetch()}>
            Try again
          </button>
        </p>
      )}
      {url && (
        <img className="my-4 size-32 rounded-full object-cover" src={url} alt="Avatar preview" />
      )}
      {avatar.isSuccess && (
        <div className="grid max-w-md gap-4">
          <label>
            Profile image
            <input
              className="control"
              type="file"
              accept="image/jpeg,image/png"
              disabled={change.isPending}
              onChange={(event) => {
                setFile(event.target.files?.item(0) ?? null)
                event.target.value = ''
                change.reset()
              }}
            />
          </label>
          <button
            className="btn-primary"
            type="button"
            disabled={!file || change.isPending}
            onClick={() => change.mutate(file)}
          >
            Save avatar
          </button>
          {avatar.data && (
            <button type="button" disabled={change.isPending} onClick={() => change.mutate(null)}>
              Remove avatar
            </button>
          )}
        </div>
      )}
      {change.isError && <p role="alert">{change.error.message}</p>}
      {change.isSuccess && <p role="status">Avatar updated.</p>}
    </PageSection>
  )
}
