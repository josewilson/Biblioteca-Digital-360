const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
const date = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short' })

export const formatMoney = (value: number): string => money.format(value)

export function formatDateTime(iso: string | null | undefined): string {
  return iso ? dateTime.format(new Date(iso)) : '—'
}

export function formatDate(iso: string | null | undefined): string {
  return iso ? date.format(new Date(iso)) : '—'
}

/** Formata uma célula de relatório: datas ISO, dinheiro e nulos ficam legíveis; o resto vira texto. */
export function formatCell(column: string, value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T/.test(value)) return formatDateTime(value)
  if (typeof value === 'number' && /^(valor)$/.test(column)) return formatMoney(value)
  return String(value)
}
