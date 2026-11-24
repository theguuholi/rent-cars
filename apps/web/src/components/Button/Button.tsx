import type { ComponentPropsWithoutRef } from 'react'

type Props = ComponentPropsWithoutRef<'button'> & {
  variant?: 'primary' | 'secondary' | 'danger'
}

export function Button({ variant = 'primary', type = 'button', ...props }: Props) {
  return <button className="action" data-variant={variant} type={type} {...props} />
}
