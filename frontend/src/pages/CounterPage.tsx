import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import { formatDateTime, formatMoney } from '../lib/format'
import type { Book, Loan, Page, ReturnResult } from '../lib/types'
import { BookPicker, UserSelect, useUserNames } from '../components/pickers'
import {
  Alert,
  Badge,
  Button,
  Card,
  ConfirmDialog,
  Empty,
  ErrorAlert,
  Loading,
  PageHeader,
  Pagination,
  StatusBadge,
  Table,
  cellClass,
} from '../components/ui'

function NewLoan() {
  const queryClient = useQueryClient()
  const [userId, setUserId] = useState<number | null>(null)
  const [book, setBook] = useState<Book | null>(null)
  const [copyId, setCopyId] = useState<number | null>(null)

  const lend = useMutation({
    mutationFn: () => api<Loan>('/loans', { method: 'POST', body: { userId, copyId } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['loans'] })
      void queryClient.invalidateQueries({ queryKey: ['books'] })
      void queryClient.invalidateQueries({ queryKey: ['book'] })
      setBook(null)
      setCopyId(null)
    },
  })

  const lendable = (book?.copies ?? []).filter((c) => c.kind === 'CIRCULANTE')

  return (
    <Card title="Novo empréstimo">
      <div className="space-y-4">
        <UserSelect value={userId} onChange={setUserId} />
        {book ? (
          <fieldset className="space-y-2">
            <legend className="text-sm font-medium">
              Exemplar de <strong>{book.title}</strong>{' '}
              <Button variant="ghost" onClick={() => { setBook(null); setCopyId(null) }}>
                trocar obra
              </Button>
            </legend>
            {lendable.length === 0 && <p className="text-sm text-slate-600">Esta obra não tem exemplar emprestável.</p>}
            {lendable.map((c) => {
              const selectable = c.status === 'DISPONIVEL' || c.status === 'RESERVADO'
              return (
                <label key={c.id} className="flex items-center gap-2 text-sm">
                  <input
                    type="radio"
                    name="copy"
                    disabled={!selectable}
                    checked={copyId === c.id}
                    onChange={() => setCopyId(c.id)}
                  />
                  {c.barcode} <StatusBadge value={c.status} />
                  {c.status === 'RESERVADO' && <span className="text-xs text-slate-500">(só o dono da reserva retira)</span>}
                </label>
              )
            })}
          </fieldset>
        ) : (
          <BookPicker onPick={setBook} />
        )}
        <ErrorAlert error={lend.error} />
        {lend.isSuccess && lend.data && (
          <Alert tone="green">
            Empréstimo registrado: <strong>{lend.data.bookTitle}</strong>. Devolver até {formatDateTime(lend.data.dueAt)}.
          </Alert>
        )}
        <Button disabled={!userId || !copyId} loading={lend.isPending} onClick={() => lend.mutate()}>
          Emprestar
        </Button>
      </div>
    </Card>
  )
}

function ActiveLoans() {
  const queryClient = useQueryClient()
  const userName = useUserNames()
  const [page, setPage] = useState(0)
  const [returning, setReturning] = useState<Loan | null>(null)
  const [result, setResult] = useState<ReturnResult | null>(null)

  const loans = useQuery({
    queryKey: ['loans', 'active', page],
    queryFn: () => api<Page<Loan>>('/loans', { params: { activeOnly: true, page, size: 10, sort: 'dueAt,asc' } }),
  })

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['loans'] })
    void queryClient.invalidateQueries({ queryKey: ['books'] })
    void queryClient.invalidateQueries({ queryKey: ['book'] })
  }

  const giveBack = useMutation({
    mutationFn: (loan: Loan) => api<ReturnResult>(`/loans/${loan.id}/return`, { method: 'POST' }),
    onSuccess: (r) => {
      setReturning(null)
      setResult(r)
      refresh()
    },
  })
  const renew = useMutation({
    mutationFn: (loan: Loan) => api<Loan>(`/loans/${loan.id}/renew`, { method: 'POST' }),
    onSuccess: refresh,
  })

  return (
    <Card title="Empréstimos em andamento">
      {result && (
        <div className="mb-3">
          {result.fine ? (
            <Alert tone="amber">
              Devolução de <strong>{result.loan.bookTitle}</strong> registrada com multa de{' '}
              <strong>{formatMoney(result.fine.amount)}</strong> ({result.fine.daysLate} dia(s) de atraso). Cobre em Multas.
            </Alert>
          ) : (
            <Alert tone="green">
              Devolução de <strong>{result.loan.bookTitle}</strong> registrada.
            </Alert>
          )}
        </div>
      )}
      <ErrorAlert error={renew.error} />
      {loans.isLoading && <Loading />}
      <ErrorAlert error={loans.error} />
      {loans.data && loans.data.content.length === 0 && <Empty>Nenhum empréstimo em andamento.</Empty>}
      {loans.data && loans.data.content.length > 0 && (
        <>
          <Table caption="Empréstimos em andamento" head={['Obra', 'Exemplar', 'Leitor', 'Vencimento', 'Ações']}>
            {loans.data.content.map((l) => {
              const overdue = new Date(l.dueAt).getTime() < Date.now()
              return (
                <tr key={l.id}>
                  <td className={cellClass}>{l.bookTitle}</td>
                  <td className={cellClass}>{l.barcode}</td>
                  <td className={cellClass}>{userName(l.userId)}</td>
                  <td className={cellClass}>
                    {formatDateTime(l.dueAt)} {overdue && <Badge tone="red">Atrasado</Badge>}
                  </td>
                  <td className={`${cellClass} space-x-2 whitespace-nowrap`}>
                    <Button variant="secondary" onClick={() => { setResult(null); setReturning(l) }}>
                      Devolver
                    </Button>
                    <Button variant="ghost" disabled={overdue} loading={renew.isPending && renew.variables?.id === l.id} onClick={() => { setResult(null); renew.mutate(l) }}>
                      Renovar
                    </Button>
                  </td>
                </tr>
              )
            })}
          </Table>
          <Pagination page={loans.data.number} totalPages={loans.data.totalPages} onChange={setPage} />
        </>
      )}

      <ConfirmDialog
        open={returning !== null}
        title="Registrar devolução"
        message={
          returning && (
            <>
              Confirmar a devolução de <strong>{returning.bookTitle}</strong> (exemplar {returning.barcode}) por{' '}
              <strong>{userName(returning.userId)}</strong>? Se houver atraso, a multa é gerada na hora.
            </>
          )
        }
        confirmLabel="Registrar devolução"
        loading={giveBack.isPending}
        error={giveBack.error}
        onConfirm={() => returning && giveBack.mutate(returning)}
        onCancel={() => setReturning(null)}
      />
    </Card>
  )
}

export function CounterPage() {
  return (
    <>
      <PageHeader title="Balcão" subtitle="Empréstimos, devoluções e renovações." />
      <div className="space-y-6">
        <NewLoan />
        <ActiveLoans />
      </div>
    </>
  )
}
