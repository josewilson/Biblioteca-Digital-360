import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import { formatDateTime } from '../lib/format'
import type { Book, Page, Reservation, ReservationStatus } from '../lib/types'
import { BookPicker, UserSelect, useUserNames } from '../components/pickers'
import {
  Alert,
  Button,
  Card,
  ConfirmDialog,
  Empty,
  ErrorAlert,
  Field,
  Loading,
  PageHeader,
  Pagination,
  StatusBadge,
  Table,
  cellClass,
  workTitleClass,
  inputClass,
} from '../components/ui'

const OPEN: ReservationStatus[] = ['NA_FILA', 'DISPONIVEL']

function NewReservation() {
  const queryClient = useQueryClient()
  const [userId, setUserId] = useState<number | null>(null)
  const [book, setBook] = useState<Book | null>(null)

  const create = useMutation({
    mutationFn: () => api<Reservation>('/reservations', { method: 'POST', body: { userId, bookId: book?.id } }),
    onSuccess: () => {
      setBook(null)
      void queryClient.invalidateQueries({ queryKey: ['reservations'] })
    },
  })

  return (
    <Card title="Nova reserva">
      <div className="space-y-4">
        <UserSelect value={userId} onChange={setUserId} />
        {book ? (
          <p className="text-sm">
            Obra: <strong>{book.title}</strong>{' '}
            <Button variant="ghost" onClick={() => setBook(null)}>
              trocar
            </Button>
          </p>
        ) : (
          <BookPicker onPick={setBook} />
        )}
        <ErrorAlert error={create.error} />
        {create.isSuccess && create.data && (
          <Alert tone="green">
            Reserva registrada
            {create.data.queuePosition ? `: ${create.data.queuePosition}º da fila.` : '.'}
          </Alert>
        )}
        <Button disabled={!userId || !book} loading={create.isPending} onClick={() => create.mutate()}>
          Reservar
        </Button>
      </div>
    </Card>
  )
}

export function ReservationsPage() {
  const queryClient = useQueryClient()
  const userName = useUserNames()
  const [status, setStatus] = useState<ReservationStatus | ''>('')
  const [page, setPage] = useState(0)
  const [cancelling, setCancelling] = useState<Reservation | null>(null)

  const list = useQuery({
    queryKey: ['reservations', status, page],
    queryFn: () => api<Page<Reservation>>('/reservations', { params: { status, page, size: 10, sort: 'id,desc' } }),
  })

  const cancel = useMutation({
    mutationFn: (r: Reservation) => api<Reservation>(`/reservations/${r.id}/cancel`, { method: 'POST' }),
    onSuccess: () => {
      setCancelling(null)
      void queryClient.invalidateQueries({ queryKey: ['reservations'] })
      void queryClient.invalidateQueries({ queryKey: ['books'] })
    },
  })

  return (
    <>
      <PageHeader title="Reservas" subtitle="Fila de espera por obra, em ordem de chegada." />
      <div className="space-y-6">
        <NewReservation />
        <Card title="Reservas">
          <div className="mb-4 max-w-xs">
            <Field label="Situação">
              {(p) => (
                <select
                  {...p}
                  className={inputClass}
                  value={status}
                  onChange={(e) => {
                    setPage(0)
                    setStatus(e.target.value as ReservationStatus | '')
                  }}
                >
                  <option value="">Todas</option>
                  <option value="NA_FILA">Na fila</option>
                  <option value="DISPONIVEL">Disponível para retirada</option>
                  <option value="RETIRADA">Retirada</option>
                  <option value="CANCELADA">Cancelada</option>
                  <option value="EXPIRADA">Expirada</option>
                </select>
              )}
            </Field>
          </div>
          {list.isLoading && <Loading />}
          <ErrorAlert error={list.error} />
          {list.data && list.data.content.length === 0 && <Empty>Nenhuma reserva.</Empty>}
          {list.data && list.data.content.length > 0 && (
            <>
              <Table caption="Reservas" head={['Obra', 'Leitor', 'Situação', 'Retirar até', 'Ações']}>
                {list.data.content.map((r) => (
                  <tr key={r.id}>
                    <td className={`${cellClass} ${workTitleClass}`}>{r.bookTitle}</td>
                    <td className={cellClass}>{userName(r.userId)}</td>
                    <td className={cellClass}>
                      <StatusBadge value={r.status} />
                      {r.queuePosition && <span className="ml-2 text-xs text-slate-600">{r.queuePosition}º da fila</span>}
                    </td>
                    <td className={cellClass}>{formatDateTime(r.expiresAt)}</td>
                    <td className={cellClass}>
                      {OPEN.includes(r.status) && (
                        <Button variant="secondary" onClick={() => setCancelling(r)}>
                          Cancelar
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </Table>
              <Pagination page={list.data.number} totalPages={list.data.totalPages} onChange={setPage} />
            </>
          )}
        </Card>
      </div>

      <ConfirmDialog
        open={cancelling !== null}
        title="Cancelar reserva"
        message={
          cancelling && (
            <>
              Cancelar a reserva de <strong>{cancelling.bookTitle}</strong> de <strong>{userName(cancelling.userId)}</strong>?
              {cancelling.status === 'DISPONIVEL' && ' O exemplar guardado passa para o próximo da fila.'}
            </>
          )
        }
        confirmLabel="Cancelar reserva"
        danger
        loading={cancel.isPending}
        error={cancel.error}
        onConfirm={() => cancelling && cancel.mutate(cancelling)}
        onCancel={() => setCancelling(null)}
      />
    </>
  )
}
