import { render, screen, within } from '@testing-library/react'
import { describe, expect, test as scenario } from 'vitest'
import { PageSection } from './PageSection'

describe('PageSection', () => {
  scenario(
    'Given two sections, when both render, then each region is named by its own visible heading',
    () => {
      render(
        <>
          <PageSection eyebrow="Catalog" title="Categories">
            Body styles
          </PageSection>
          <PageSection
            eyebrow="Catalog"
            title="Specifications"
            description="Vehicle equipment"
            action={<button>Create</button>}
          >
            Available features
          </PageSection>
        </>,
      )
      const categories = screen.getByRole('region', { name: 'Categories' })
      const specifications = screen.getByRole('region', { name: 'Specifications' })
      expect(categories).not.toHaveAttribute(
        'aria-labelledby',
        specifications.getAttribute('aria-labelledby'),
      )
      expect(within(categories).getByText('Body styles')).toBeVisible()
      expect(within(categories).queryByRole('button')).not.toBeInTheDocument()
      expect(within(specifications).getByText('Vehicle equipment')).toBeVisible()
      expect(within(specifications).getByRole('button', { name: 'Create' })).toBeVisible()
    },
  )
})
