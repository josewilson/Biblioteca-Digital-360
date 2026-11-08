import { useEffect, useId, useRef, type ButtonHTMLAttributes, type ReactNode } from 'react'
import { Loader2 } from 'lucide-react'
import { ApiError } from '../lib/api'

// ---------- botões e campos ----------

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-brand-600 text-white hover:bg-brand-700 disabled:bg-slate-300',
  secondary: 'bg-white text-slate-800 border border-slate-300 hover:bg-slate-100 disabled:text-slate-400',
  danger: 'bg-red-700 text-white hover:bg-red-800 disabled:bg-slate-300',
  ghost: 'text-brand-700 hover:bg-brand-50 disabled:text-slate-400',
}

export function Button({
  variant = 'primary',
  loading = false,
  className = '',
  children,
  disabled,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; loading?: boolean }) {
  return (
    <button
      type="button"
      disabled={disabled || loading}
      className={`inline-flex items-center justify-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors disabled:cursor-not-allowed ${VARIANTS[variant]} ${className}`}
      {...props}
    >
      {loading && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
      {children}
    </button>
  )
}

export const inputClass =
  'block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm placeholder:text-slate-400 disabled:bg-slate-100 aria-[invalid=true]:border-red-600'

interface FieldProps {
  label: string
  error?: string
  hint?: string
  children: (props: { id: string; 'aria-invalid'?: true; 'aria-describedby'?: string }) => ReactNode
}

/** Rótulo + campo + erro/dica, tudo ligado por id (leitores de tela anunciam o erro junto do campo). */
export function Field({ label, error, hint, children }: FieldProps) {
  const id = useId()
  const messageId = `${id}-msg`
  const hasMessage = !!(error || hint)
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      {children({
        id,
        ...(error ? { 'aria-invalid': true as const } : {}),
        ...(hasMessage ? { 'aria-describedby': messageId } : {}),
      })}
      {hasMessage && (
        <p id={messageId} className={`text-xs ${error ? 'text-red-700' : 'text-slate-500'}`}>
          {error ?? hint}
        </p>
      )}
    </div>
  )
}

// ---------- estrutura ----------

export function Card({ title, actions, children }: { title?: string; actions?: ReactNode; children: ReactNode }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white shadow-sm">
      {(title || actions) && (
        <header className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 px-4 py-3">
          {title && <h2 className="text-base font-semibold">{title}</h2>}
          {actions}
        </header>
      )}
      <div className="p-4">{children}</div>
    </section>
  )
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-slate-600">{subtitle}</p>}
      </div>
      {actions}
    </div>
  )
}

type Tone = 'neutral' | 'green' | 'amber' | 'red' | 'blue'

const TONES: Record<Tone, string> = {
  neutral: 'bg-slate-100 text-slate-800',
  green: 'bg-green-100 text-green-900',
  amber: 'bg-amber-100 text-amber-900',
  red: 'bg-red-100 text-red-900',
  blue: 'bg-blue-100 text-blue-900',
}

export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${TONES[tone]}`}>{children}</span>
}

const STATUS_TONES: Record<string, Tone> = {
  DISPONIVEL: 'green',
  EMPRESTADO: 'amber',
  RESERVADO: 'blue',
  PERDIDO: 'red',
  BAIXADO: 'neutral',
  ABERTA: 'red',
  PAGA: 'green',
  ISENTA: 'neutral',
  NA_FILA: 'amber',
  RETIRADA: 'green',
  CANCELADA: 'neutral',
  EXPIRADA: 'red',
  CONSULTA: 'blue',
  CIRCULANTE: 'neutral',
}

const STATUS_LABELS: Record<string, string> = {
  DISPONIVEL: 'Disponível',
  EMPRESTADO: 'Emprestado',
  RESERVADO: 'Reservado',
  PERDIDO: 'Perdido',
  BAIXADO: 'Baixado',
  ABERTA: 'Em aberto',
  PAGA: 'Paga',
  ISENTA: 'Isenta',
  NA_FILA: 'Na fila',
  RETIRADA: 'Retirada',
  CANCELADA: 'Cancelada',
  EXPIRADA: 'Expirada',
  CONSULTA: 'Consulta',
  CIRCULANTE: 'Emprestável',
}

/** Estado sempre com texto (não só cor), para quem não distingue cores. */
export function StatusBadge({ value }: { value: string }) {
  return <Badge tone={STATUS_TONES[value] ?? 'neutral'}>{STATUS_LABELS[value] ?? value}</Badge>
}

// ---------- mensagens ----------

export function Alert({ tone = 'blue', children }: { tone?: 'blue' | 'green' | 'red' | 'amber'; children: ReactNode }) {
  const colors = {
    blue: 'border-blue-300 bg-blue-50 text-blue-900',
    green: 'border-green-300 bg-green-50 text-green-900',
    red: 'border-red-300 bg-red-50 text-red-900',
    amber: 'border-amber-300 bg-amber-50 text-amber-900',
  }
  return (
    <div role={tone === 'red' ? 'alert' : 'status'} className={`rounded-md border px-3 py-2 text-sm ${colors[tone]}`}>
      {children}
    </div>
  )
}

export function ErrorAlert({ error }: { error: unknown }) {
  if (!error) return null
  const message = error instanceof Error ? error.message : 'Erro inesperado.'
  const fields = error instanceof ApiError ? error.fieldErrors : []
  return (
    <Alert tone="red">
      <p>{message}</p>
      {fields.length > 0 && (
        <ul className="mt-1 list-disc pl-5">
          {fields.map((f) => (
            <li key={f}>{f}</li>
          ))}
        </ul>
      )}
    </Alert>
  )
}

export function Loading({ label = 'Carregando…' }: { label?: string }) {
  return (
    <p role="status" className="flex items-center gap-2 py-6 text-sm text-slate-600">
      <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
      {label}
    </p>
  )
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="py-6 text-center text-sm text-slate-500">{children}</p>
}

// ---------- tabela e paginação ----------

export function Table({ caption, head, children }: { caption: string; head: string[]; children: ReactNode }) {
  return (
    <div className="overflow-x-auto">
      <table className="min-w-full text-left text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead className="border-b border-slate-200 text-xs uppercase text-slate-600">
          <tr>
            {head.map((h) => (
              <th key={h} scope="col" className="px-3 py-2 font-semibold">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">{children}</tbody>
      </table>
    </div>
  )
}

export const cellClass = 'px-3 py-2 align-middle'

export function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number
  totalPages: number
  onChange: (page: number) => void
}) {
  if (totalPages <= 1) return null
  return (
    <nav aria-label="Paginação" className="mt-4 flex items-center justify-between text-sm">
      <Button variant="secondary" disabled={page === 0} onClick={() => onChange(page - 1)}>
        Anterior
      </Button>
      <span aria-live="polite">
        Página {page + 1} de {totalPages}
      </span>
      <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        Próxima
      </Button>
    </nav>
  )
}

// ---------- diálogo ----------

/** Diálogo nativo (<dialog>): o navegador já prende o foco dentro dele e fecha com Esc. */
export function Dialog({
  open,
  title,
  onClose,
  children,
}: {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
}) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.hasAttribute('open')) dialog.showModal()
    if (!open && dialog.hasAttribute('open')) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onClose={onClose}
      className="m-auto w-full max-w-lg rounded-lg p-0 shadow-xl backdrop:bg-slate-900/50"
    >
      {open && (
        <div className="space-y-4 p-5">
          <h2 id={titleId} className="text-lg font-semibold">
            {title}
          </h2>
          {children}
        </div>
      )}
    </dialog>
  )
}

/** Confirmação de operação crítica. */
export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel,
  danger = false,
  loading = false,
  error,
  onConfirm,
  onCancel,
}: {
  open: boolean
  title: string
  message: ReactNode
  confirmLabel: string
  danger?: boolean
  loading?: boolean
  error?: unknown
  onConfirm: () => void
  onCancel: () => void
}) {
  return (
    <Dialog open={open} title={title} onClose={onCancel}>
      <div className="text-sm text-slate-700">{message}</div>
      <ErrorAlert error={error} />
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onCancel}>
          Cancelar
        </Button>
        <Button variant={danger ? 'danger' : 'primary'} loading={loading} onClick={onConfirm}>
          {confirmLabel}
        </Button>
      </div>
    </Dialog>
  )
}
