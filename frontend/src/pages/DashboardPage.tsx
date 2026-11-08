import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
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

const dayLabel = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`
const plural = (n: number, one: string, many: string) => (n === 1 ? one : many)

/**
 * O que pede ação. Se há pendência, vira carimbo vermelho com atalho para a tela que resolve;
 * se não há, fica quieto. (O painel abre pelo que precisa ser feito, não por números de vitrine.)
 */
function Attention({
  count,
  sentence,
  detail,
  to,
  action,
  attention,
}: {
  count: number
  sentence: string
  detail?: string
  to: string
  action: string
  attention: boolean
}) {
  return (
    <div
      data-attention={attention}
      className={`rounded border bg-white p-5 ${attention ? 'border-2 border-stamp' : 'border-slate-200'}`}
    >
      <p className={`font-serif text-2xl font-semibold ${attention ? 'text-stamp' : 'text-slate-800'}`}>
        <span className="tabular-nums">{count}</span> {sentence}
      </p>
      {detail && <p className="mt-1 text-slate-600">{detail}</p>}
      {attention && (
        <Link to={to} className="mt-3 inline-block font-bold text-brand-700 underline underline-offset-4">
          {action}
        </Link>
      )}
    </div>
  )
}

/** Ranking em barras. O aria-label descreve os números para quem usa leitor de tela. */
function Ranking({ title, items, empty }: { title: string; items: RankItem[]; empty: string }) {
  return (
    <Card title={title}>
      {items.length === 0 ? (
        <p className="py-6 text-center text-slate-600">{empty}</p>
      ) : (
        <div role="img" aria-label={`${title}: ${items.map((i) => `${i.name}, ${i.total}`).join('; ')}`} className="h-64">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={items} layout="vertical" margin={{ left: 16 }}>
              <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="#d5dcd8" />
              <XAxis type="number" allowDecimals={false} />
              <YAxis type="category" dataKey="name" width={120} tick={{ fontSize: 13 }} />
              <Tooltip />
              <Bar dataKey="total" name="Empréstimos" fill="#24584a" isAnimationActive={false} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </Card>
  )
}

export function DashboardPage() {
  const { data, isLoading, error } = useQuery({ queryKey: ['dashboard'], queryFn: () => api<Dashboard>('/dashboard') })

  const totals: [string, string | number][] = data
    ? [
        ['Obras cadastradas', data.totalBooks],
        ['Exemplares ativos', data.totalCopies],
        ['Usuários ativos', data.activeUsers],
        ['Empréstimos em andamento', data.activeLoans],
        ['Reservas pendentes', data.pendingReservations],
        ['Valor de multas em aberto', formatMoney(data.openFinesAmount)],
      ]
    : []

  return (
    <>
      <PageHeader title="Painel" subtitle="O que precisa de atenção hoje e como está a circulação." />
      <ErrorAlert error={error} />
      {isLoading && <Loading />}
      {data && (
        <div className="space-y-8">
          <section aria-labelledby="atencao" className="grid gap-4 md:grid-cols-2">
            <h2 id="atencao" className="sr-only">
              O que pede ação
            </h2>
            <Attention
              count={data.overdueLoans}
              sentence={data.overdueLoans === 0 ? 'empréstimos atrasados' : plural(data.overdueLoans, 'empréstimo atrasado', 'empréstimos atrasados')}
              detail={data.overdueLoans === 0 ? 'Todos os livros emprestados estão no prazo.' : undefined}
              to="/balcao"
              action="Ver empréstimos em andamento"
              attention={data.overdueLoans > 0}
            />
            <Attention
              count={data.openFinesCount}
              sentence={data.openFinesCount === 0 ? 'multas em aberto' : plural(data.openFinesCount, 'multa em aberto', 'multas em aberto')}
              detail={data.openFinesCount > 0 ? `Somam ${formatMoney(data.openFinesAmount)}.` : 'Nenhuma cobrança pendente.'}
              to="/multas"
              action="Receber ou isentar multas"
              attention={data.openFinesCount > 0}
            />
          </section>

          <Card title="Acervo e circulação">
            <dl className="grid gap-x-10 sm:grid-cols-2">
              {totals.map(([label, value]) => (
                <div key={label} className="flex items-baseline justify-between gap-4 border-b border-slate-200 py-2.5">
                  <dt className="text-slate-600">{label}</dt>
                  <dd className="text-lg font-bold tabular-nums">{value}</dd>
                </div>
              ))}
            </dl>
          </Card>

          <Card title="Movimentação dos últimos 30 dias">
            <div
              role="img"
              aria-label={`Empréstimos e devoluções por dia nos últimos 30 dias. Total de empréstimos: ${data.movements.reduce((s, m) => s + m.loans, 0)}. Total de devoluções: ${data.movements.reduce((s, m) => s + m.returns, 0)}.`}
              className="h-72"
            >
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={data.movements}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#d5dcd8" />
                  <XAxis dataKey="day" tickFormatter={dayLabel} tick={{ fontSize: 13 }} />
                  <YAxis allowDecimals={false} />
                  <Tooltip labelFormatter={(l) => dayLabel(String(l))} />
                  <Legend />
                  <Line type="monotone" dataKey="loans" name="Empréstimos" stroke="#24584a" strokeWidth={2.5} dot={false} isAnimationActive={false} />
                  <Line
                    type="monotone"
                    dataKey="returns"
                    name="Devoluções"
                    stroke="#7a4b00"
                    strokeWidth={2.5}
                    strokeDasharray="6 4"
                    isAnimationActive={false}
                    dot={false}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </Card>

          <div className="grid gap-6 lg:grid-cols-2">
            <Ranking title="Obras mais emprestadas" items={data.topBooks} empty="Ainda não há empréstimos registrados." />
            <Ranking title="Categorias mais procuradas" items={data.topCategories} empty="Ainda não há empréstimos de obras com categoria." />
          </div>
        </div>
      )}
    </>
  )
}
