import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { formatDateTime, formatMoney } from '../lib/format'
import type { Fine, FineStatus, Page, Payment, PaymentMethod } from '../lib/types'
import { waiveSchema, type WaiveForm } from '../schemas'
import { useUserNames } from '../components/pickers'
import {
  Button,
  Card,
  Dialog,
  Empty,
  ErrorAlert,
  Field,
  Loading,
  PageHeader,
  Pagination,
  StatusBadge,
  Table,
  cellClass,
  inputClass,
} from '../components/ui'

const METHODS: { value: PaymentMethod; label: string }[] = [
  { value: 'DINHEIRO', label: 'Dinheiro' },
  { value: 'PIX', label: 'Pix' },
  { value: 'CARTAO_DEBITO', label: 'Cartão de débito' },
  { value: 'CARTAO_CREDITO', label: 'Cartão de crédito' },
]

function PayDialog({ fine, who, onClose }: { fine: Fine | null; who: string; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [method, setMethod] = useState<PaymentMethod>('DINHEIRO')
  const pay = useMutation({
    mutationFn: (f: Fine) => api<Payment>(`/fines/${f.id}/payments`, { method: 'POST', body: { method } }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['fines'] }),
  })

  function close() {
    pay.reset()
    onClose()
  }

  return (
    <Dialog open={fine !== null} title={pay.data ? 'Comprovante de pagamento' : 'Receber pagamento'} onClose={close}>
      {fine && !pay.data && (
        <div className="space-y-4">
          <p className="text-sm">
            Multa de <strong>{who}</strong>: <strong>{formatMoney(fine.amount)}</strong> ({fine.daysLate} dia(s) de atraso).
            O valor é cobrado por inteiro.
          </p>
          <Field label="Forma de pagamento">
            {(p) => (
              <select {...p} className={inputClass} value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
                {METHODS.map((m) => (
                  <option key={m.value} value={m.value}>
                    {m.label}
                  </option>
                ))}
              </select>
            )}
          </Field>
          <ErrorAlert error={pay.error} />
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={close}>
              Cancelar
            </Button>
            <Button loading={pay.isPending} onClick={() => pay.mutate(fine)}>
              Confirmar pagamento
            </Button>
          </div>
        </div>
      )}
      {pay.data && (
        <div className="space-y-4">
          <dl className="grid grid-cols-2 gap-2 text-sm">
            <dt className="text-slate-500">Comprovante nº</dt>
            <dd>{pay.data.id}</dd>
            <dt className="text-slate-500">Leitor</dt>
            <dd>{who}</dd>
            <dt className="text-slate-500">Valor</dt>
            <dd>{formatMoney(pay.data.amount)}</dd>
            <dt className="text-slate-500">Forma</dt>
            <dd>{METHODS.find((m) => m.value === pay.data.method)?.label}</dd>
            <dt className="text-slate-500">Data</dt>
            <dd>{formatDateTime(pay.data.paidAt)}</dd>
          </dl>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => window.print()}>
              Imprimir
            </Button>
            <Button onClick={close}>Fechar</Button>
          </div>
        </div>
      )}
    </Dialog>
  )
}

function WaiveDialog({ fine, who, onClose }: { fine: Fine | null; who: string; onClose: () => void }) {
  const queryClient = useQueryClient()
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<WaiveForm>({ resolver: zodResolver(waiveSchema), defaultValues: { reason: '' } })
  const waive = useMutation({
    mutationFn: (v: WaiveForm) => api<Fine>(`/fines/${fine?.id}/waive`, { method: 'POST', body: v }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['fines'] })
      close()
    },
  })

  function close() {
    reset()
    waive.reset()
    onClose()
  }

  return (
    <Dialog open={fine !== null} title="Isentar multa" onClose={close}>
      {fine && (
        <form onSubmit={handleSubmit((v) => waive.mutate(v))} noValidate className="space-y-4">
          <p className="text-sm">
            Isentar a multa de <strong>{formatMoney(fine.amount)}</strong> de <strong>{who}</strong>. A justificativa e quem
            autorizou ficam registradas.
          </p>
          <Field label="Justificativa" error={errors.reason?.message}>
            {(p) => <textarea {...p} rows={3} className={inputClass} {...register('reason')} />}
          </Field>
          <ErrorAlert error={waive.error} />
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={close}>
              Cancelar
            </Button>
            <Button type="submit" variant="danger" loading={waive.isPending}>
              Isentar multa
            </Button>
          </div>
        </form>
      )}
    </Dialog>
  )
}

export function FinesPage() {
  const { canWaive } = useAuth()
  const [status, setStatus] = useState<FineStatus | ''>('ABERTA')
  const [page, setPage] = useState(0)
  const [paying, setPaying] = useState<Fine | null>(null)
  const [waiving, setWaiving] = useState<Fine | null>(null)

  const list = useQuery({
    queryKey: ['fines', status, page],
    queryFn: () => api<Page<Fine>>('/fines', { params: { status, page, size: 10, sort: 'id,desc' } }),
  })
  const userName = useUserNames((list.data?.content ?? []).map((item) => item.userId))

  return (
    <>
      <PageHeader title="Multas" subtitle="Receba pagamentos e, com autorização, isente multas." />
      <Card>
        <div className="mb-4 max-w-xs">
          <Field label="Situação">
            {(p) => (
              <select
                {...p}
                className={inputClass}
                value={status}
                onChange={(e) => {
                  setPage(0)
                  setStatus(e.target.value as FineStatus | '')
                }}
              >
                <option value="ABERTA">Em aberto</option>
                <option value="PAGA">Pagas</option>
                <option value="ISENTA">Isentas</option>
                <option value="">Todas</option>
              </select>
            )}
          </Field>
        </div>
        {list.isLoading && <Loading />}
        <ErrorAlert error={list.error} />
        {list.data && list.data.content.length === 0 && <Empty>Nenhuma multa.</Empty>}
        {list.data && list.data.content.length > 0 && (
          <>
            <Table caption="Multas" head={['Leitor', 'Valor', 'Dias de atraso', 'Situação', 'Ações']}>
              {list.data.content.map((f) => (
                <tr key={f.id}>
                  <td className={cellClass}>{userName(f.userId)}</td>
                  <td className={cellClass}>{formatMoney(f.amount)}</td>
                  <td className={cellClass}>{f.daysLate}</td>
                  <td className={cellClass}>
                    <StatusBadge value={f.status} />
                    {f.waiverReason && <span className="ml-2 text-xs text-slate-600">{f.waiverReason}</span>}
                  </td>
                  <td className={`${cellClass} space-x-2 whitespace-nowrap`}>
                    {f.status === 'ABERTA' && (
                      <>
                        <Button onClick={() => setPaying(f)}>Receber</Button>
                        {canWaive && (
                          <Button variant="secondary" onClick={() => setWaiving(f)}>
                            Isentar
                          </Button>
                        )}
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </Table>
            <Pagination page={list.data.number} totalPages={list.data.totalPages} onChange={setPage} />
          </>
        )}
      </Card>
      <PayDialog fine={paying} who={paying ? userName(paying.userId) : ''} onClose={() => setPaying(null)} />
      <WaiveDialog fine={waiving} who={waiving ? userName(waiving.userId) : ''} onClose={() => setWaiving(null)} />
    </>
  )
}
