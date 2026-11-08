import { formatDate } from '../lib/format'

const DAY = 86_400_000

/** Dias de calendário (no fuso do navegador) de hoje até a data: negativo se já passou. */
function calendarDaysUntil(iso: string, now: Date): number {
  const due = new Date(iso)
  const a = Date.UTC(due.getFullYear(), due.getMonth(), due.getDate())
  const b = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate())
  return Math.round((a - b) / DAY)
}

/**
 * O carimbo de prazo: o elemento de identidade da interface (docs/DESIGN.md).
 * Diz o estado do empréstimo com TEXTO (não só cor): atrasado e vence logo ganham carimbo; o resto fica discreto.
 */
export function DueStamp({ dueAt, returnedAt, now = new Date() }: { dueAt: string; returnedAt?: string | null; now?: Date }) {
  const date = formatDate(dueAt)

  if (returnedAt) {
    return (
      <span className="text-slate-600">
        Devolvido em <time dateTime={returnedAt}>{formatDate(returnedAt)}</time>
      </span>
    )
  }

  const days = calendarDaysUntil(dueAt, now)
  const overdue = new Date(dueAt).getTime() < now.getTime()

  if (overdue) {
    const late = Math.max(0, -days)
    return (
      <span className="inline-flex flex-wrap items-center gap-x-2 gap-y-1">
        <span className="rounded-sm border-2 border-stamp px-2 py-0.5 font-bold text-stamp outline outline-1 outline-offset-2 outline-stamp/40">
          {late === 0 ? 'Atrasado' : `Atrasado há ${late} ${late === 1 ? 'dia' : 'dias'}`}
        </span>
        <span className="text-sm text-slate-600">
          venceu <time dateTime={dueAt}>{date}</time>
        </span>
      </span>
    )
  }

  if (days <= 1) {
    return (
      <span className="inline-flex flex-wrap items-center gap-x-2 gap-y-1">
        <span className="rounded-sm border-2 border-due bg-due-bg px-2 py-0.5 font-bold text-due">
          {days === 0 ? 'Vence hoje' : 'Vence amanhã'}
        </span>
        <span className="text-sm text-slate-600">
          <time dateTime={dueAt}>{date}</time>
        </span>
      </span>
    )
  }

  return (
    <span className="text-slate-800">
      Devolver até <time dateTime={dueAt}>{date}</time>
    </span>
  )
}
