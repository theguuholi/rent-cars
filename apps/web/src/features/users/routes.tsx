import { createRoute } from '@tanstack/react-router'
import { requireAdmin } from '../auth/access'
import { rootRoute } from '../../routes/root'
import { UsersPage } from './pages/UsersPage/UsersPage'
import { UserRecordPage } from './pages/UserRecordPage/UserRecordPage'
import { UserFormPage } from './pages/UserFormPage/UserFormPage'

const list = createRoute({
  getParentRoute: () => rootRoute,
  path: '/users',
  beforeLoad: requireAdmin,
  staticData: { title: 'Users' },
  component: UsersPage,
})
const create = createRoute({
  getParentRoute: () => rootRoute,
  path: '/users/new',
  beforeLoad: requireAdmin,
  staticData: { title: 'New user' },
  component: UserFormPage,
})
const detail = createRoute({
  getParentRoute: () => rootRoute,
  path: '/users/$id',
  beforeLoad: requireAdmin,
  staticData: { title: 'User details' },
  component: () => <UserRecordPage id={detail.useParams().id} mode="detail" />,
})
const edit = createRoute({
  getParentRoute: () => rootRoute,
  path: '/users/$id/edit',
  beforeLoad: requireAdmin,
  staticData: { title: 'Edit user' },
  component: () => <UserRecordPage id={edit.useParams().id} mode="edit" />,
})
const remove = createRoute({
  getParentRoute: () => rootRoute,
  path: '/users/$id/delete',
  beforeLoad: requireAdmin,
  staticData: { title: 'Delete user' },
  component: () => <UserRecordPage id={remove.useParams().id} mode="delete" />,
})
export const userRoutes = [list, create, detail, edit, remove] as const
