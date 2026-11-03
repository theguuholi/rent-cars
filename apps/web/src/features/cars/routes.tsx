import { createRoute } from '@tanstack/react-router'
import { rootRoute } from '../../routes/root'
import { CarsPage, CarRecord, CarFormPage } from './CarsPage'

const list = createRoute({
  getParentRoute: () => rootRoute,
  path: '/cars',
  staticData: { title: 'Cars' },
  component: CarsPage,
})
const create = createRoute({
  getParentRoute: () => rootRoute,
  path: '/cars/new',
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
  staticData: { title: 'Edit car' },
  component: () => <CarRecord id={edit.useParams().id} mode="edit" />,
})
const remove = createRoute({
  getParentRoute: () => rootRoute,
  path: '/cars/$id/delete',
  staticData: { title: 'Delete car' },
  component: () => <CarRecord id={remove.useParams().id} mode="delete" />,
})
export const carRoutes = [list, create, detail, edit, remove] as const
