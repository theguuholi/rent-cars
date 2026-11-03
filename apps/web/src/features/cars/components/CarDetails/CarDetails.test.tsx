import { screen } from '@testing-library/react'
import { describe, expect, test as scenario, vi } from 'vitest'
import { openCatalogRoute } from '../../../../test/open-catalog-route'
import { car } from '../../../../test/car-fixtures'
import { getCar } from '../../cars-api'
import { listCategories } from '../../../categories/categories-api'
import { listSpecifications } from '../../../specifications/specifications-api'
vi.mock('../../cars-api')
vi.mock('../../../categories/categories-api')
vi.mock('../../../specifications/specifications-api')
describe('CarDetails', () => {
  scenario(
    'Given a fleet visitor, when opening a detail directly, then its category and equipment come from one public request',
    async () => {
      vi.mocked(getCar).mockResolvedValue(car)
      openCatalogRoute('/cars/second', undefined, null)
      expect(await screen.findByText('GPS')).toBeVisible()
      expect(screen.getByText('Sedan')).toBeVisible()
      expect(getCar).toHaveBeenCalledExactlyOnceWith('second', expect.any(AbortSignal))
      expect(listCategories).not.toHaveBeenCalled()
      expect(listSpecifications).not.toHaveBeenCalled()
    },
  )
})
