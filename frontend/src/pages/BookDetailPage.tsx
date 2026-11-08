import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import type { Book, Copy, CopyStatus, Reservation } from '../lib/types'
import {
  Alert,
  Button,
  Card,
  ConfirmDialog,
  Dialog,
  ErrorAlert,
  Field,
  Loading,
  PageHeader,
  StatusBadge,
  Table,
  cellClass,
  inputClass,
} from '../components/ui'

const MANUAL_STATUSES: { value: CopyStatus; label: string }[] = [
  { value: 'DISPONIVEL', label: 'Disponível' },
  { value: 'PERDIDO', label: 'Perdido' },
  { value: 'BAIXADO', label: 'Baixado' },
]

function AddCopyDialog({ bookId, open, onClose }: { bookId: number; open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient()
  const { register, handleSubmit, reset, formState } = useForm({
    defaultValues: { barcode: '', kind: 'CIRCULANTE', condition: 'BOM', location: '' },
  })
  const add = useMutation({
    mutationFn: (v: { barcode: string; kind: string; condition: string; location: string }) =>
      api<Copy>(`/books/${bookId}/copies`, { method: 'POST', body: { ...v, location: v.location || null } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['book', bookId] })
      reset()
      onClose()
    },
  })
  return (
    <Dialog open={open} title="Acrescentar exemplar" onClose={onClose}>
      <form onSubmit={handleSubmit((v) => add.mutate(v))} className="space-y-3">
        <Field label="Código do exemplar" error={formState.errors.barcode?.message}>
          {(p) => <input {...p} className={inputClass} {...register('barcode', { required: 'Informe o código.' })} />}
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Tipo">
            {(p) => (
              <select {...p} className={inputClass} {...register('kind')}>
                <option value="CIRCULANTE">Emprestável</option>
                <option value="CONSULTA">Consulta</option>
              </select>
            )}
          </Field>
          <Field label="Estado">
            {(p) => (
              <select {...p} className={inputClass} {...register('condition')}>
                <option value="NOVO">Novo</option>
                <option value="BOM">Bom</option>
                <option value="REGULAR">Regular</option>
                <option value="RUIM">Ruim</option>
              </select>
            )}
          </Field>
        </div>
        <Field label="Localização">{(p) => <input {...p} className={inputClass} {...register('location')} />}</Field>
        <ErrorAlert error={add.error} />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" loading={add.isPending}>
            Acrescentar
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

export function BookDetailPage() {
  const { id } = useParams()
  const bookId = Number(id)
  const { isStaff } = useAuth()
  const queryClient = useQueryClient()
  const [adding, setAdding] = useState(false)
  const [pending, setPending] = useState<{ copy: Copy; status: CopyStatus } | null>(null)

  const { data: book, isLoading, error } = useQuery({
    queryKey: ['book', bookId],
    queryFn: () => api<Book>(`/books/${bookId}`),
    enabled: Number.isFinite(bookId),
  })

  const changeStatus = useMutation({
    mutationFn: ({ copy, status }: { copy: Copy; status: CopyStatus }) =>
      api<Copy>(`/copies/${copy.id}/status`, { method: 'PATCH', body: { status } }),
    onSuccess: () => {
      setPending(null)
      void queryClient.invalidateQueries({ queryKey: ['book', bookId] })
    },
  })

  const reserve = useMutation({
    mutationFn: () => api<Reservation>('/me/reservations', { method: 'POST', body: { bookId } }),
  })

  if (isLoading) return <Loading />
  if (error) return <ErrorAlert error={error} />
  if (!book) return null

  return (
    <>
      <PageHeader
        title={book.title}
        subtitle={[book.authors.map((a) => a.name).join(', '), book.publisher, book.publicationYear]
          .filter(Boolean)
          .join(', ')}
        actions={
          <Link to="/acervo" className="text-sm font-medium text-brand-700 underline">
            Voltar ao acervo
          </Link>
        }
      />

      <div className="space-y-6">
        <Card title="Dados da obra">
          <div className="flex flex-col gap-4 sm:flex-row">
            {book.coverUrl && (
              <img
                src={book.coverUrl}
                alt={`Capa de ${book.title}`}
                referrerPolicy="no-referrer"
                className="h-48 w-32 rounded object-cover"
              />
            )}
            <dl className="grid flex-1 gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-slate-500">ISBN</dt>
                <dd>{book.isbn ?? '—'}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Edição</dt>
                <dd>{book.edition ?? '—'}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Idioma</dt>
                <dd>{book.language ?? '—'}</dd>
              </div>
              <div>
                <dt className="text-slate-500">Categorias</dt>
                <dd>{book.categories.map((c) => c.name).join(', ') || '—'}</dd>
              </div>
              {book.description && (
                <div className="sm:col-span-2">
                  <dt className="text-slate-500">Descrição</dt>
                  <dd className="whitespace-pre-line">{book.description}</dd>
                </div>
              )}
            </dl>
          </div>
        </Card>

        {!isStaff && (
          <Card title="Reservar">
            <p className="mb-3 text-sm text-slate-600">
              Se todos os exemplares para empréstimo estiverem em uso, você pode entrar na fila de espera.
            </p>
            <Button loading={reserve.isPending} onClick={() => reserve.mutate()}>
              Reservar esta obra
            </Button>
            <div className="mt-3">
              <ErrorAlert error={reserve.error} />
              {reserve.data && (
                <Alert tone="green">
                  Reserva feita.{' '}
                  {reserve.data.queuePosition ? `Você é o ${reserve.data.queuePosition}º da fila.` : 'Acompanhe em Minha área.'}
                </Alert>
              )}
            </div>
          </Card>
        )}

        <Card
          title="Exemplares"
          actions={isStaff && <Button variant="secondary" onClick={() => setAdding(true)}>Acrescentar exemplar</Button>}
        >
          <Table caption="Exemplares da obra" head={isStaff ? ['Código', 'Tipo', 'Situação', 'Localização', 'Alterar situação'] : ['Código', 'Tipo', 'Situação', 'Localização']}>
            {book.copies.map((c) => (
              <tr key={c.id}>
                <td className={cellClass}>{c.barcode}</td>
                <td className={cellClass}>
                  <StatusBadge value={c.kind} />
                </td>
                <td className={cellClass}>
                  <StatusBadge value={c.status} />
                </td>
                <td className={cellClass}>{c.location ?? '—'}</td>
                {isStaff && (
                  <td className={cellClass}>
                    {c.status === 'EMPRESTADO' || c.status === 'RESERVADO' ? (
                      <span className="text-xs text-slate-500">Pelo empréstimo/reserva</span>
                    ) : (
                      <select
                        aria-label={`Situação do exemplar ${c.barcode}`}
                        className={`${inputClass} w-auto`}
                        value={c.status}
                        onChange={(e) => {
                          const status = e.target.value as CopyStatus
                          if (status !== c.status) {
                            changeStatus.reset()
                            setPending({ copy: c, status })
                          }
                        }}
                      >
                        {MANUAL_STATUSES.map((s) => (
                          <option key={s.value} value={s.value}>
                            {s.label}
                          </option>
                        ))}
                      </select>
                    )}
                  </td>
                )}
              </tr>
            ))}
          </Table>
        </Card>
      </div>

      <AddCopyDialog bookId={bookId} open={adding} onClose={() => setAdding(false)} />
      <ConfirmDialog
        open={pending !== null}
        title="Alterar situação do exemplar"
        message={
          pending && (
            <>
              Marcar o exemplar <strong>{pending.copy.barcode}</strong> como <strong>{pending.status.toLowerCase()}</strong>?
              {pending.status !== 'DISPONIVEL' && ' Ele sai de circulação.'}
            </>
          )
        }
        confirmLabel="Confirmar"
        danger={pending?.status !== 'DISPONIVEL'}
        loading={changeStatus.isPending}
        error={changeStatus.error}
        onConfirm={() => pending && changeStatus.mutate(pending)}
        onCancel={() => setPending(null)}
      />
    </>
  )
}
