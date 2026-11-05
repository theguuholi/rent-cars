import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { QueryClient } from '@tanstack/react-query'
import { afterEach, beforeEach, describe, expect, test as scenario, vi } from 'vitest'
import { openCatalogRoute } from '../../../../test/open-catalog-route'
import { car } from '../../../../test/car-fixtures'
import { getCar, listCars } from '../../cars-api'
import { listCarImages, uploadCarImage, deleteCarImage } from '../../car-images-api'
vi.mock('../../cars-api')
vi.mock('../../car-images-api', async (original) => ({
  ...(await original<object>()),
  listCarImages: vi.fn(),
  uploadCarImage: vi.fn(),
  deleteCarImage: vi.fn(),
}))
const file = new File(['image'], 'car-photo.png', { type: 'image/png' })
function selectPhoto() {
  fireEvent.change(screen.getByLabelText('Add car photo'), {
    target: { files: { item: () => file } },
  })
}
describe('CarGallery', () => {
  beforeEach(() => {
    vi.mocked(getCar).mockResolvedValue(car)
    vi.mocked(listCars).mockResolvedValue([])
    vi.mocked(listCarImages).mockResolvedValue([])
    vi.mocked(uploadCarImage).mockResolvedValue(undefined)
    vi.mocked(deleteCarImage).mockResolvedValue(undefined)
  })
  afterEach(() => {
    vi.resetAllMocks()
    vi.unstubAllGlobals()
  })
  describe('Loading and public browsing', () => {
    scenario(
      'Given a pending gallery, when the car detail opens, then show loading without empty or upload controls',
      async () => {
        vi.mocked(listCarImages).mockReturnValue(new Promise(() => {}))
        openCatalogRoute('/cars/second')
        expect(await screen.findByText('Loading car photos…')).toBeVisible()
        expect(screen.queryByText('No photos yet.')).not.toBeInTheDocument()
        expect(screen.queryByLabelText('Add car photo')).not.toBeInTheDocument()
        expect(listCarImages).toHaveBeenCalledWith(car.id, expect.any(AbortSignal))
      },
    )
    scenario(
      'Given a failed load, when the visitor retries, then show the empty gallery',
      async () => {
        vi.mocked(listCarImages).mockRejectedValueOnce(new Error('Gallery offline'))
        openCatalogRoute('/cars/second', undefined, null)
        expect(await screen.findByText('Gallery offline')).toBeVisible()
        expect(screen.getByRole('alert')).toHaveTextContent('Gallery offline Try again')
        fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
        expect(await screen.findByText('No photos yet.')).toBeVisible()
        expect(screen.queryByText('Gallery offline')).not.toBeInTheDocument()
        expect(listCarImages).toHaveBeenCalledTimes(2)
      },
    )
    scenario.each([
      null,
      {
        accessToken: 'driver',
        user: { id: 'driver', email: 'driver@test', role: 'USER' as const },
      },
    ])(
      'Given a visitor or regular user, when viewing photos, then expose public images without administrative controls',
      async (identity) => {
        vi.mocked(listCarImages).mockResolvedValue([{ id: 'one' }, { id: 'two' }])
        openCatalogRoute('/cars/second', undefined, identity)
        const first = await screen.findByRole('img', { name: 'Car photo 1' })
        expect(first).toHaveAttribute('src', `/api/cars/${car.id}/images/one`)
        expect(screen.getByRole('img', { name: 'Car photo 2' })).toHaveAttribute(
          'src',
          `/api/cars/${car.id}/images/two`,
        )
        expect(screen.queryByRole('button', { name: /Remove photo/ })).not.toBeInTheDocument()
        expect(screen.queryByLabelText('Add car photo')).not.toBeInTheDocument()
        expect(screen.queryByText('No photos yet.')).not.toBeInTheDocument()
        expect(
          within(screen.getByRole('list', { name: 'Car photos' })).getAllByRole('listitem'),
        ).toHaveLength(2)
      },
    )
  })
  describe('Upload', () => {
    scenario(
      'Given an empty admin gallery, when no file is selected or selection is cancelled, then disable upload',
      async () => {
        openCatalogRoute('/cars/second')
        const input = await screen.findByLabelText('Add car photo')
        expect(input).toHaveAttribute('accept', 'image/jpeg,image/png')
        expect(input).toHaveAttribute('type', 'file')
        expect(screen.getByRole('button', { name: 'Upload photo' })).toBeDisabled()
        selectPhoto()
        expect(screen.getByText('car-photo.png')).toBeVisible()
        expect(screen.getByRole('button', { name: 'Upload photo' })).toBeEnabled()
        fireEvent.change(input, { target: { files: { item: () => null } } })
        expect(screen.getByRole('button', { name: 'Upload photo' })).toBeDisabled()
        expect(screen.queryByText('car-photo.png')).not.toBeInTheDocument()
        selectPhoto()
        fireEvent.change(input, { target: { files: null } })
        expect(screen.getByRole('button', { name: 'Upload photo' })).toBeDisabled()
        expect(uploadCarImage).not.toHaveBeenCalled()
      },
    )
    scenario(
      'Given an upload in progress, when it succeeds, then prevent duplicate writes and refresh only this gallery',
      async () => {
        let finish!: () => void
        vi.mocked(uploadCarImage).mockImplementationOnce(
          () =>
            new Promise((resolve) => {
              finish = resolve
            }),
        )
        vi.mocked(listCarImages).mockResolvedValue([{ id: 'existing' }])
        const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
        openCatalogRoute('/cars/second', client)
        const input = await screen.findByLabelText('Add car photo')
        selectPhoto()
        expect((input as HTMLInputElement).value).toBe('')
        fireEvent.click(screen.getByRole('button', { name: 'Upload photo' }))
        await waitFor(() => expect(input).toBeDisabled())
        expect(screen.getByRole('button', { name: 'Upload photo' })).toBeDisabled()
        expect(screen.getByRole('button', { name: 'Remove photo 1' })).toBeDisabled()
        expect(uploadCarImage).toHaveBeenCalledExactlyOnceWith(car.id, file)
        vi.mocked(listCarImages).mockResolvedValue([{ id: 'existing' }, { id: 'new' }])
        const invalidate = vi.spyOn(client, 'invalidateQueries')
        await act(async () => finish())
        expect(await screen.findByRole('img', { name: 'Car photo 2' })).toHaveAttribute(
          'src',
          `/api/cars/${car.id}/images/new`,
        )
        expect(screen.getByText('Car photos updated.')).toBeVisible()
        expect(screen.queryByText('car-photo.png')).not.toBeInTheDocument()
        expect(screen.getByRole('button', { name: 'Upload photo' })).toBeDisabled()
        expect(invalidate).toHaveBeenCalledExactlyOnceWith({ queryKey: ['car-images', car.id] })
      },
    )
    scenario(
      'Given a rejected upload, when retrying or selecting another file, then retain the draft and clear stale errors',
      async () => {
        vi.mocked(uploadCarImage).mockRejectedValueOnce(new Error('Upload failed'))
        openCatalogRoute('/cars/second')
        await screen.findByLabelText('Add car photo')
        selectPhoto()
        fireEvent.click(screen.getByRole('button', { name: 'Upload photo' }))
        expect(await screen.findByText('Upload failed')).toBeVisible()
        expect(screen.getByText('car-photo.png')).toBeVisible()
        expect(screen.getByRole('button', { name: 'Upload photo' })).toBeEnabled()
        selectPhoto()
        expect(screen.queryByText('Upload failed')).not.toBeInTheDocument()
        fireEvent.click(screen.getByRole('button', { name: 'Upload photo' }))
        expect(await screen.findByText('Car photos updated.')).toBeVisible()
        expect(uploadCarImage).toHaveBeenCalledTimes(2)
      },
    )
    scenario(
      'Given an upload started on a previous route, when it finishes after navigation, then do not mutate the next screen cache',
      async () => {
        let finish!: () => void
        vi.mocked(uploadCarImage).mockImplementationOnce(
          () =>
            new Promise((resolve) => {
              finish = resolve
            }),
        )
        const client = new QueryClient()
        const history = openCatalogRoute('/cars/second', client)
        await screen.findByLabelText('Add car photo')
        selectPhoto()
        fireEvent.click(screen.getByRole('button', { name: 'Upload photo' }))
        await waitFor(() => expect(uploadCarImage).toHaveBeenCalled())
        await act(async () => history.push('/cars'))
        const invalidate = vi.spyOn(client, 'invalidateQueries')
        await act(async () => finish())
        expect(invalidate).not.toHaveBeenCalled()
      },
    )
  })
  describe('Removal', () => {
    scenario(
      'Given two photos, when the admin removes the second, then delete that exact image and retain the first',
      async () => {
        vi.mocked(listCarImages).mockResolvedValue([{ id: 'one' }, { id: 'two' }])
        openCatalogRoute('/cars/second')
        const remove = await screen.findByRole('button', { name: 'Remove photo 2' })
        vi.mocked(listCarImages).mockResolvedValue([{ id: 'one' }])
        fireEvent.click(remove)
        await waitFor(() =>
          expect(screen.queryByRole('img', { name: 'Car photo 2' })).not.toBeInTheDocument(),
        )
        expect(deleteCarImage).toHaveBeenCalledExactlyOnceWith(car.id, 'two')
        expect(screen.getByRole('img', { name: 'Car photo 1' })).toHaveAttribute(
          'src',
          `/api/cars/${car.id}/images/one`,
        )
        expect(uploadCarImage).not.toHaveBeenCalled()
      },
    )
  })
})
