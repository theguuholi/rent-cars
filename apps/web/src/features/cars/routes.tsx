import { requireAdmin } from '../auth/access'
import { createRoute } from '@tanstack/react-router'
import { rootRoute } from '../../routes/root'
import { CarsPage } from './pages/CarsPage/CarsPage'
import { CarRecord } from './pages/CarRecord/CarRecord'
import { CarFormPage } from './pages/CarFormPage/CarFormPage'
import { z } from 'zod'
import type { CarSearch } from './cars-api'

const carSearchSchema: z.ZodType<Partial<CarSearch>> = z.object({
  name: z.string().min(1).optional().catch(undefined),
  brand: z.string().min(1).optional().catch(undefined),
  category: z.string().min(1).optional().catch(undefined),
  page: z.coerce.number().int().nonnegative().optional().catch(undefined),
  size: z.coerce
    .number()
    .refine((size) => [1, 6, 12, 24].includes(size))
    .optional()
    .catch(undefined),
  sort: z.enum(['name,asc', 'name,desc', 'brand,asc', 'brand,desc']).optional().catch(undefined),
})

const list = createRoute({
  getParentRoute: () => rootRoute,
  path: '/cars',
  validateSearch: carSearchSchema,
  staticData: { title: 'Cars' },
  component: CarsPage,
})
const create = createRoute({
  getParentRoute: () => rootRoute,
  path: '/cars/new',
  beforeLoad: requireAdmin,
  staticData: { title: 'New car' },
  component: () => <CarFormPage />,
})
const detail = createRoute({
  getParentRoute: () => rootRoute,
  path: '/cars/$id',
  staticData: { title: 'Car details' },
  component: () => <CarRecord id={detail.useParams().id} mode="detail" />,
})
const edit = createRoute({
  getParentRoute: () => rootRoute,
  path: '/cars/$id/edit',
  beforeLoad: requireAdmin,
  staticData: { title: 'Edit car' },
  component: () => <CarRecord id={edit.useParams().id} mode="edit" />,
})
const remove = createRoute({
  getParentRoute: () => rootRoute,
  path: '/cars/$id/delete',
  beforeLoad: requireAdmin,
  staticData: { title: 'Delete car' },
  component: () => <CarRecord id={remove.useParams().id} mode="delete" />,
})
export const carRoutes = [list, create, detail, edit, remove] as const
