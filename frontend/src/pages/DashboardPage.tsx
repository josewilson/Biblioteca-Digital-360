import { useQuery } from '@tanstack/react-query'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { api } from '../lib/api'
import { formatMoney } from '../lib/format'
import type { Dashboard, RankItem } from '../lib/types'
import { Card, ErrorAlert, Loading, PageHeader } from '../components/ui'

function Kpi({ label, value, alert = false }: { label: string; value: string | number; alert?: boolean }) {
  return (
    <div className={`rounded-lg border bg-white p-4 shadow-sm ${alert ? 'border-red-300' : 'border-slate-200'}`}>
      <dt className="text-sm text-slate-600">{label}</dt>
      <dd className={`mt-1 text-2xl font-bold ${alert ? 'text-red-700' : 'text-slate-900'}`}>{value}</dd>
    </div>
  )
}

const dayLabel = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`

/** Ranking em barras. O aria-label descreve os números para quem usa leitor de tela. */
function Ranking({ title, items, empty }: { title: string; items: RankItem[]; empty: string }) {
  return (
    <Card title={title}>
      {items.length === 0 ? (
        <p className="py-6 text-center text-sm text-slate-500">{empty}</p>
      ) : (
        <figure>
          <div role="img" aria-label={`${title}: ${items.map((i) => `${i.name}, ${i.total}`).join('; ')}`} className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={items} layout="vertical" margin={{ left: 16 }}>
                <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                <XAxis type="number" allowDecimals={false} />
                <YAxis type="category" dataKey="name" width={120} tick={{ fontSize: 12 }} />
                <Tooltip />
                <Bar dataKey="total" name="Empréstimos" fill="#1d4ed8" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </figure>
      )}
    </Card>
  )
}

export function DashboardPage() {
  const { data, isLoading, error } = useQuery({ queryKey: ['dashboard'], queryFn: () => api<Dashboard>('/dashboard') })

  return (
    <>
      <PageHeader title="Painel" subtitle="Visão geral do acervo e da circulação." />
      <ErrorAlert error={error} />
      {isLoading && <Loading />}
      {data && (
        <div className="space-y-6">
          <dl className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <Kpi label="Obras cadastradas" value={data.totalBooks} />
            <Kpi label="Exemplares ativos" value={data.totalCopies} />
            <Kpi label="Usuários ativos" value={data.activeUsers} />
            <Kpi label="Empréstimos em andamento" value={data.activeLoans} />
            <Kpi label="Empréstimos atrasados" value={data.overdueLoans} alert={data.overdueLoans > 0} />
            <Kpi label="Reservas pendentes" value={data.pendingReservations} />
            <Kpi label="Multas em aberto" value={data.openFinesCount} alert={data.openFinesCount > 0} />
            <Kpi label="Valor em aberto" value={formatMoney(data.openFinesAmount)} alert={data.openFinesAmount > 0} />
          </dl>

          <Card title="Movimentação dos últimos 30 dias">
            <div
              role="img"
              aria-label={`Empréstimos e devoluções por dia nos últimos 30 dias. Total de empréstimos: ${data.movements.reduce((s, m) => s + m.loans, 0)}. Total de devoluções: ${data.movements.reduce((s, m) => s + m.returns, 0)}.`}
              className="h-72"
            >
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={data.movements}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="day" tickFormatter={dayLabel} tick={{ fontSize: 12 }} />
                  <YAxis allowDecimals={false} />
                  <Tooltip labelFormatter={(l) => dayLabel(String(l))} />
                  <Legend />
                  <Line type="monotone" dataKey="loans" name="Empréstimos" stroke="#1d4ed8" strokeWidth={2} dot={false} />
                  <Line
                    type="monotone"
                    dataKey="returns"
                    name="Devoluções"
                    stroke="#b45309"
                    strokeWidth={2}
                    strokeDasharray="6 4"
                    dot={false}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </Card>

          <div className="grid gap-6 lg:grid-cols-2">
            <Ranking title="Obras mais emprestadas" items={data.topBooks} empty="Ainda não há empréstimos." />
            <Ranking title="Categorias mais procuradas" items={data.topCategories} empty="Ainda não há empréstimos com categoria." />
          </div>
        </div>
      )}
    </>
  )
}
