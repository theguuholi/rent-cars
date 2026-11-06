import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, test as scenario } from 'vitest'
import { CarPhoto } from './CarPhoto'

describe('CarPhoto', () => {
  scenario(
    'Given an image, when loading fails and the visitor retries, then the image is requested again',
    () => {
      render(<CarPhoto carId="car" imageId="photo" label="Argo photo 1" />)
      expect(screen.getByRole('link', { name: 'Open Argo photo 1' })).toHaveAttribute(
        'href',
        '/api/cars/car?imageId=photo',
      )
      fireEvent.error(screen.getByRole('img', { name: 'Argo photo 1' }))
      expect(screen.getByRole('status')).toHaveTextContent('Could not load Argo photo 1.')
      fireEvent.click(screen.getByRole('button', { name: 'Retry Argo photo 1' }))
      expect(screen.getByRole('img')).toHaveAttribute('src', '/api/cars/car?imageId=photo')
    },
  )
})
