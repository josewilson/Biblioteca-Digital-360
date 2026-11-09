import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import { ROLE_LABELS, type Policy } from '../lib/types'
import { policySchema, type PolicyForm } from '../schemas'
import { Alert, Button, Card, Empty, ErrorAlert, Field, Loading, PageHeader, inputClass } from '../components/ui'

const FIELDS: { name: Exclude<keyof PolicyForm, 'dailyRate'>; label: string }[] = [
  { name: 'maxLoans', label: 'Livros ao mesmo tempo' },
  { name: 'loanDays', label: 'Prazo do empréstimo (dias)' },
  { name: 'maxRenewals', label: 'Renovações permitidas' },
  { name: 'renewalDays', label: 'Dias a mais por renovação' },
  { name: 'reservationPickupDays', label: 'Dias para retirar a reserva' },
]

function PolicyCard({ policy }: { policy: Policy }) {
  const queryClient = useQueryClient()
  const roleLabel = ROLE_LABELS[policy.role] ?? policy.role
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<PolicyForm>({
    resolver: zodResolver(policySchema),
    defaultValues: {
      maxLoans: String(policy.maxLoans),
      loanDays: String(policy.loanDays),
      maxRenewals: String(policy.maxRenewals),
      renewalDays: String(policy.renewalDays),
      reservationPickupDays: String(policy.reservationPickupDays),
      dailyRate: policy.dailyRate.toFixed(2).replace('.', ','),
    },
  })
  const save = useMutation({
    mutationFn: (v: PolicyForm) =>
      api<Policy>(`/policies/${policy.role}`, {
        method: 'PUT',
        body: {
          maxLoans: Number(v.maxLoans),
          loanDays: Number(v.loanDays),
          maxRenewals: Number(v.maxRenewals),
          renewalDays: Number(v.renewalDays),
          reservationPickupDays: Number(v.reservationPickupDays),
          dailyRate: v.dailyRate.replace(',', '.'),   // vai como texto: o servidor faz a conta com BigDecimal
        },
      }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['policies'] }),
  })

  return (
    <Card title={roleLabel}>
      <form onSubmit={handleSubmit((v) => save.mutate(v))} noValidate className="space-y-3">
        <div className="grid gap-3 sm:grid-cols-2">
          {FIELDS.map(({ name, label }) => (
            <Field key={name} label={label} error={errors[name]?.message}>
              {(p) => <input {...p} inputMode="numeric" className={inputClass} {...register(name)} />}
            </Field>
          ))}
          <Field label="Multa por dia de atraso (R$)" error={errors.dailyRate?.message}>
            {(p) => <input {...p} inputMode="decimal" className={inputClass} {...register('dailyRate')} />}
          </Field>
        </div>
        <ErrorAlert error={save.error} />
        {save.isSuccess && <Alert tone="green">Regras de {roleLabel} salvas.</Alert>}
        <Button type="submit" loading={save.isPending}>
          Salvar regras de {roleLabel}
        </Button>
      </form>
    </Card>
  )
}

export function PoliciesPage() {
  const list = useQuery({ queryKey: ['policies'], queryFn: () => api<Policy[]>('/policies') })
  return (
    <>
      <PageHeader
        title="Regras de empréstimo"
        subtitle="Valem para os próximos empréstimos e renovações. A multa usa o valor em vigor no dia da devolução."
      />
      {list.isLoading && <Loading />}
      <ErrorAlert error={list.error} />
      {list.data && list.data.length === 0 && <Empty>Nenhum perfil com empréstimo configurado.</Empty>}
      <div className="grid gap-6 lg:grid-cols-2">
        {list.data?.map((p) => <PolicyCard key={p.role} policy={p} />)}
      </div>
    </>
  )
}
