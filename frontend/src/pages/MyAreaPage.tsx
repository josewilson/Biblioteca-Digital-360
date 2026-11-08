import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import { formatDateTime, formatMoney } from '../lib/format'
import type { Fine, Loan, Page, Reservation } from '../lib/types'
import { DueStamp } from '../components/DueStamp'
import {
  Button,
  Card,
  ConfirmDialog,
  Empty,
  ErrorAlert,
  Loading,
  PageHeader,
  StatusBadge,
  Table,
  cellClass,
  workTitleClass,
} from '../components/ui'

export function MyAreaPage() {
  const queryClient = useQueryClient()
  const [cancelling, setCancelling] = useState<Reservation | null>(null)

  const loans = useQuery({
    queryKey: ['me', 'loans'],
    queryFn: () => api<Page<Loan>>('/me/loans', { params: { size: 20, sort: 'loanedAt,desc' } }),
  })
  const reservations = useQuery({
    queryKey: ['me', 'reservations'],
    queryFn: () => api<Page<Reservation>>('/me/reservations', { params: { size: 20, sort: 'id,desc' } }),
  })
  const fines = useQuery({
    queryKey: ['me', 'fines'],
    queryFn: () => api<Page<Fine>>('/me/fines', { params: { size: 20, sort: 'id,desc' } }),
  })

  const cancel = useMutation({
    mutationFn: (r: Reservation) => api<Reservation>(`/me/reservations/${r.id}/cancel`, { method: 'POST' }),
    onSuccess: () => {
      setCancelling(null)
      void queryClient.invalidateQueries({ queryKey: ['me', 'reservations'] })
    },
  })

  return (
    <>
      <PageHeader title="Minha área" subtitle="Seus empréstimos, reservas e multas." />
      <div className="space-y-6">
        <Card title="Meus empréstimos">
          {loans.isLoading && <Loading />}
          <ErrorAlert error={loans.error} />
          {loans.data && loans.data.content.length === 0 && <Empty>Você ainda não pegou livros emprestados. Procure uma obra no Acervo e peça no balcão.</Empty>}
          {loans.data && loans.data.content.length > 0 && (
            <Table caption="Meus empréstimos" head={['Obra', 'Emprestado em', 'Prazo']}>
              {loans.data.content.map((l) => (
                <tr key={l.id}>
                  <td className={`${cellClass} ${workTitleClass}`}>{l.bookTitle}</td>
                  <td className={cellClass}>{formatDateTime(l.loanedAt)}</td>
                  <td className={cellClass}>
                    <DueStamp dueAt={l.dueAt} returnedAt={l.returnedAt} />
                  </td>
                </tr>
              ))}
            </Table>
          )}
        </Card>

        <Card title="Minhas reservas">
          {reservations.isLoading && <Loading />}
          <ErrorAlert error={reservations.error} />
          {reservations.data && reservations.data.content.length === 0 && <Empty>Nenhuma reserva.</Empty>}
          {reservations.data && reservations.data.content.length > 0 && (
            <Table caption="Minhas reservas" head={['Obra', 'Situação', 'Retirar até', 'Ações']}>
              {reservations.data.content.map((r) => (
                <tr key={r.id}>
                  <td className={`${cellClass} ${workTitleClass}`}>{r.bookTitle}</td>
                  <td className={cellClass}>
                    <StatusBadge value={r.status} />
                    {r.queuePosition && <span className="ml-2 text-xs text-slate-600">{r.queuePosition}º da fila</span>}
                  </td>
                  <td className={cellClass}>{formatDateTime(r.expiresAt)}</td>
                  <td className={cellClass}>
                    {(r.status === 'NA_FILA' || r.status === 'DISPONIVEL') && (
                      <Button variant="secondary" onClick={() => setCancelling(r)}>
                        Cancelar
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </Table>
          )}
        </Card>

        <Card title="Minhas multas">
          {fines.isLoading && <Loading />}
          <ErrorAlert error={fines.error} />
          {fines.data && fines.data.content.length === 0 && <Empty>Você não tem multas.</Empty>}
          {fines.data && fines.data.content.length > 0 && (
            <Table caption="Minhas multas" head={['Valor', 'Dias de atraso', 'Situação']}>
              {fines.data.content.map((f) => (
                <tr key={f.id}>
                  <td className={cellClass}>{formatMoney(f.amount)}</td>
                  <td className={cellClass}>{f.daysLate}</td>
                  <td className={cellClass}>
                    <StatusBadge value={f.status} />
                  </td>
                </tr>
              ))}
            </Table>
          )}
          <p className="mt-3 text-xs text-slate-500">O pagamento é feito no balcão da biblioteca.</p>
        </Card>
      </div>

      <ConfirmDialog
        open={cancelling !== null}
        title="Cancelar reserva"
        message={cancelling && <>Cancelar sua reserva de <strong>{cancelling.bookTitle}</strong>?</>}
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
