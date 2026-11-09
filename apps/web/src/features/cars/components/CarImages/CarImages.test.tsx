import { fireEvent, screen, waitFor, act } from '@testing-library/react'
import { QueryClient } from '@tanstack/react-query'
import { describe, expect, test as scenario, vi } from 'vitest'
import { car } from '../../../../test/car-fixtures'
import { openCatalogRoute } from '../../../../test/open-catalog-route'
import { session } from '../../../auth/session'
import { getCar, uploadCarImages } from '../../cars-api'

vi.mock('../../cars-api', async (original) => ({
  ...(await original<typeof import('../../cars-api')>()),
  getCar: vi.fn(),
  uploadCarImages: vi.fn(),
}))
const photo = {
  id: 'photo',
  contentType: 'image/png',
  sizeBytes: 5,
}
const file = new File(['photo'], 'argo.png', { type: 'image/png' })
async function open() {
  vi.mocked(getCar).mockResolvedValue(car)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const history = openCatalogRoute('/cars/second', client)
  const input = await screen.findByLabelText('Add photos')
  expect(screen.getByRole('button', { name: 'Upload photos' })).toBeVisible()
  return { client, input, history }
}
function submit() {
  fireEvent.submit(screen.getByRole('form', { name: 'Upload car photos' }))
}

describe('CarImages', () => {
  scenario(
    'Given a visitor, when reading photos, then public images are shown without upload controls',
    async () => {
      vi.mocked(getCar).mockResolvedValue({ ...car, images: [photo] })
      openCatalogRoute('/cars/second', undefined, null)
      expect(await screen.findByRole('img', { name: 'Corolla photo 1' })).toHaveAttribute(
        'src',
        '/api/cars/second?imageId=photo',
      )
      expect(screen.queryByLabelText('Add photos')).not.toBeInTheDocument()
    },
  )
  scenario('Given a JPEG under the limit, when uploading, then the file is accepted', async () => {
    const { input } = await open()
    const jpeg = new File([new Uint8Array(1024 * 1024)], 'argo.jpg', { type: 'image/jpeg' })
    fireEvent.change(input, { target: { files: [jpeg] } })
    submit()
    await waitFor(() => expect(uploadCarImages).toHaveBeenCalledExactlyOnceWith('second', [jpeg]))
  })

  scenario(
    'Given a bad file after a valid one, when submitting, then the upload is rejected',
    async () => {
      const { input } = await open()
      const oversized = Object.defineProperty(
        new File(['x'], 'huge.png', { type: 'image/png' }),
        'size',
        {
          value: 5 * 1024 * 1024 + 1,
        },
      )
      fireEvent.change(input, { target: { files: [file, oversized] } })
      submit()
      expect(
        await screen.findByText('Each photo must contain data and be at most 5 MiB.'),
      ).toBeVisible()
      expect(uploadCarImages).not.toHaveBeenCalled()
    },
  )

  scenario(
    'Given a failed upload, when changing the selection, then the old error clears',
    async () => {
      const { input } = await open()
      vi.mocked(uploadCarImages).mockRejectedValueOnce(new Error('Storage unavailable'))
      fireEvent.change(input, { target: { files: [file] } })
      submit()
      expect(await screen.findByText('Storage unavailable')).toBeVisible()
      fireEvent.change(input, {
        target: { files: [new File(['new'], 'new.png', { type: 'image/png' })] },
      })
      await waitFor(() => expect(screen.queryByText('Storage unavailable')).not.toBeInTheDocument())
    },
  )

  scenario(
    'Given a form submit, when uploading, then browser navigation is prevented',
    async () => {
      await open()
      const event = new Event('submit', { bubbles: true, cancelable: true })
      screen.getByRole('form', { name: 'Upload car photos' }).dispatchEvent(event)
      expect(event.defaultPrevented).toBe(true)
    },
  )
  scenario(
    'Given selected photos, when uploading succeeds, then the gallery and cached version refresh and the selection clears',
    async () => {
      const { input, client } = await open()
      const invalidate = vi.spyOn(client, 'invalidateQueries')
      expect(screen.getByText('No photos yet.')).toBeVisible()
      let finish!: (value: typeof car) => void
      vi.mocked(uploadCarImages).mockReturnValue(
        new Promise((resolve) => {
          finish = resolve
        }),
      )
      fireEvent.change(input, { target: { files: [file] } })
      submit()
      await waitFor(() => expect(screen.getByRole('button', { name: 'Uploading…' })).toBeDisabled())
      expect(input).toBeDisabled()
      expect(uploadCarImages).toHaveBeenCalledExactlyOnceWith('second', [file])
      const updated = { ...car, version: 1, images: [photo] }
      vi.mocked(getCar).mockResolvedValue(updated)
      await act(async () => finish(updated))
      expect(await screen.findByText('Photos uploaded.')).toBeVisible()
      expect(screen.getByRole('img')).toBeVisible()
      expect(screen.queryByText('argo.png')).not.toBeInTheDocument()
      expect(client.getQueryData(['car', 'second'])).toEqual(updated)
      expect(input).toHaveValue('')
      expect(invalidate).toHaveBeenCalledWith({ queryKey: ['car', 'second'], exact: true })
      expect(invalidate).toHaveBeenCalledWith({ queryKey: ['cars'] })
    },
  )
  scenario(
    'Given two photos, when viewing the car, then each photo has its own label and URL',
    async () => {
      vi.mocked(getCar).mockResolvedValue({ ...car, images: [photo, { ...photo, id: 'rear' }] })
      openCatalogRoute('/cars/second', undefined, null)
      expect(await screen.findByRole('img', { name: 'Corolla photo 1' })).toHaveAttribute(
        'src',
        '/api/cars/second?imageId=photo',
      )
      expect(screen.getByRole('img', { name: 'Corolla photo 2' })).toHaveAttribute(
        'src',
        '/api/cars/second?imageId=rear',
      )
    },
  )
  scenario(
    'Given a failed upload, when retrying, then selected files remain available and are submitted again',
    async () => {
      const { input } = await open()
      vi.mocked(uploadCarImages)
        .mockRejectedValueOnce(new Error('Storage unavailable'))
        .mockResolvedValue(car)
      fireEvent.change(input, { target: { files: [file] } })
      submit()
      expect(await screen.findByText('Storage unavailable')).toBeVisible()
      expect(screen.getByText('argo.png')).toBeVisible()
      submit()
      expect(await screen.findByText('Photos uploaded.')).toBeVisible()
      expect(uploadCarImages).toHaveBeenCalledTimes(2)
    },
  )
  scenario.each([
    [[], 'Choose at least one photo.'],
    [[new File(['x'], 'bad.gif', { type: 'image/gif' })], 'Choose JPEG or PNG photos.'],
    [[file, new File(['x'], 'bad.gif', { type: 'image/gif' })], 'Choose JPEG or PNG photos.'],
    [
      [new File([], 'empty.png', { type: 'image/png' })],
      'Each photo must contain data and be at most 5 MiB.',
    ],
    [
      [
        Object.defineProperty(new File(['x'], 'large.png', { type: 'image/png' }), 'size', {
          value: 5 * 1024 * 1024 + 1,
        }),
      ],
      'Each photo must contain data and be at most 5 MiB.',
    ],
    [
      Array(6).fill(
        Object.defineProperty(new File(['x'], 'large.png', { type: 'image/png' }), 'size', {
          value: 5 * 1024 * 1024,
        }),
      ),
      'Choose less than 30 MiB of photos per upload.',
    ],
    [
      Array(7).fill(
        Object.defineProperty(new File(['x'], 'large.png', { type: 'image/png' }), 'size', {
          value: 5 * 1024 * 1024 - 1,
        }),
      ),
      'Choose less than 30 MiB of photos per upload.',
    ],
  ])(
    'Given invalid selection %j, when submitting, then %s is displayed without a request',
    async (files, message) => {
      const { input } = await open()
      fireEvent.change(input, { target: { files } })
      submit()
      expect(await screen.findByText(message)).toBeVisible()
      expect(input).toHaveAttribute('aria-invalid', 'true')
      expect(uploadCarImages).not.toHaveBeenCalled()
    },
  )
  scenario(
    'Given a corrected selection, when uploading, then the validation error clears',
    async () => {
      const { input } = await open()
      submit()
      expect(await screen.findByText('Choose at least one photo.')).toBeVisible()
      fireEvent.change(input, { target: { files: [file] } })
      expect(screen.queryByText('Choose at least one photo.')).not.toBeInTheDocument()
      expect(input).toHaveAttribute('aria-invalid', 'false')
      submit()
      await waitFor(() => expect(uploadCarImages).toHaveBeenCalledExactlyOnceWith('second', [file]))
    },
  )
  scenario(
    'Given a cancelled picker, when the selection is cleared, then validation asks for photos',
    async () => {
      const { input } = await open()
      fireEvent.change(input, { target: { files: null } })
      expect(await screen.findByText('Choose at least one photo.')).toBeVisible()
    },
  )
  scenario(
    'Given an upload in progress, when navigating away before completion, then the late result does not modify the new screen',
    async () => {
      const { input, history } = await open()
      let finish!: (value: typeof car) => void
      vi.mocked(uploadCarImages).mockReturnValue(
        new Promise((resolve) => {
          finish = resolve
        }),
      )
      fireEvent.change(input, { target: { files: [file] } })
      submit()
      await waitFor(() => expect(uploadCarImages).toHaveBeenCalled())
      await act(async () => history.push('/categories'))
      await act(async () => finish(car))
      expect(screen.queryByText('Photos uploaded.')).not.toBeInTheDocument()
    },
  )
  scenario(
    'Given an upload in progress, when the account changes, then its late result keeps the new selection',
    async () => {
      const { input } = await open()
      let finish!: (value: typeof car) => void
      vi.mocked(uploadCarImages).mockReturnValue(
        new Promise((resolve) => {
          finish = resolve
        }),
      )
      fireEvent.change(input, { target: { files: [file] } })
      submit()
      await waitFor(() => expect(uploadCarImages).toHaveBeenCalledTimes(1))
      await act(async () =>
        session.set({
          accessToken: 'another-admin',
          user: { id: 'another-admin', email: 'another@example.test', role: 'ADMIN' },
        }),
      )
      const next = new File(['new'], 'new.png', { type: 'image/png' })
      fireEvent.change(input, { target: { files: [next] } })
      await act(async () => finish(car))
      expect(screen.getByText('new.png')).toBeVisible()
    },
  )
})
