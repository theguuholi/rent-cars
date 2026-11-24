import type { ComponentPropsWithoutRef } from 'react'

export function CatalogTable(props: ComponentPropsWithoutRef<'table'>) {
  return (
    <table
      className="w-full border-collapse bg-white text-left text-sm [&_thead]:bg-surface [&_thead]:text-muted [&_th]:px-5 [&_th]:py-4 [&_td]:px-5 [&_td]:py-4 [&_td]:wrap-anywhere [&_th]:wrap-anywhere [&_tbody_tr]:border-b [&_tbody_tr]:border-line [&_tr[data-selected=true]]:bg-brand-soft [&_tr[data-selected=true]]:shadow-[inset_3px_0_0_var(--color-brand)] max-sm:[&_thead]:sr-only max-sm:[&_tbody_tr]:grid max-sm:[&_tbody_tr]:gap-1 max-sm:[&_tbody_tr]:p-4 max-sm:[&_tbody_th]:p-1 max-sm:[&_tbody_td]:p-1"
      {...props}
    />
  )
}
