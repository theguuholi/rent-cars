const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const editableAmount = new Intl.NumberFormat('pt-BR', {
  useGrouping: false,
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

/** Parse ungrouped Brazilian reais into the PostgreSQL integer range, without floating-point arithmetic. */
export function parseBrl(text: string): number | null {
  const match = /^(\d+)(?:,(\d{1,2}))?$/.exec(text.trim())
  if (!match) return null
  const cents = Number(match[1] + (match[2] ?? '').padEnd(2, '0'))
  return cents <= 2147483647 ? cents : null
}

export function formatBrl(cents: number): string {
  return currency.format(cents / 100)
}

export function formatBrlInput(cents: number): string {
  return editableAmount.format(cents / 100)
}
