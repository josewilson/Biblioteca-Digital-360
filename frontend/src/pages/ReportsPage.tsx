import { useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Download } from 'lucide-react'
import { api, downloadFile } from '../lib/api'
import { formatCell } from '../lib/format'
import type { ReportTable } from '../lib/types'
import { Alert, Button, Card, Empty, ErrorAlert, Field, Loading, PageHeader, Table, cellClass, inputClass } from '../components/ui'

const REPORTS = [
  { slug: 'loans', label: 'Empréstimos por período', period: true },
  { slug: 'returns', label: 'Devoluções por período', period: true },
  { slug: 'overdue', label: 'Atrasos (agora)', period: false },
  { slug: 'fines', label: 'Multas por período', period: true },
  { slug: 'payments', label: 'Pagamentos por período', period: true },
  { slug: 'top-books', label: 'Obras mais emprestadas', period: true },
  { slug: 'availability', label: 'Disponibilidade do acervo', period: false },
  { slug: 'users', label: 'Usuários por perfil', period: false },
]

/** Data local no formato AAAA-MM-DD (o que o servidor espera). */
const isoDate = (d: Date) => d.toLocaleDateString('sv-SE')

const header = (column: string) => {
  const text = column.replace(/_/g, ' ')
  return text.charAt(0).toUpperCase() + text.slice(1)
}

interface Query {
  slug: string
  from?: string
  to?: string
}

export function ReportsPage() {
  const today = new Date()
  const [slug, setSlug] = useState('loans')
  const [from, setFrom] = useState(isoDate(new Date(today.getTime() - 29 * 86_400_000)))
  const [to, setTo] = useState(isoDate(today))
  const [query, setQuery] = useState<Query | null>(null)
  const [downloadError, setDownloadError] = useState<unknown>(null)
  const [downloading, setDownloading] = useState(false)

  const report = REPORTS.find((r) => r.slug === slug) ?? REPORTS[0]!
  const dateError = report.period && from > to ? 'A data inicial não pode ser depois da final.' : undefined

  const result = useQuery({
    queryKey: ['report', query],
    queryFn: () => api<ReportTable>(`/reports/${query?.slug}`, { params: { from: query?.from, to: query?.to } }),
    enabled: query !== null,
  })

  const params = () => (report.period ? { from, to } : {})

  function run(e: FormEvent) {
    e.preventDefault()
    if (!dateError) setQuery({ slug, ...params() })
  }

  async function download() {
    setDownloadError(null)
    setDownloading(true)
    try {
      await downloadFile(`/reports/${slug}`, { ...params(), format: 'csv' }, `relatorio-${slug}.csv`)
    } catch (e) {
      setDownloadError(e)
    } finally {
      setDownloading(false)
    }
  }

  return (
    <>
      <PageHeader title="Relatórios" subtitle="Consulte na tela ou baixe em CSV para abrir no Excel." />
      <div className="space-y-6">
        <Card>
          <form onSubmit={run} noValidate className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4 lg:items-start">
            <Field label="Relatório">
              {(p) => (
                <select {...p} className={inputClass} value={slug} onChange={(e) => setSlug(e.target.value)}>
                  {REPORTS.map((r) => (
                    <option key={r.slug} value={r.slug}>
                      {r.label}
                    </option>
                  ))}
                </select>
              )}
            </Field>
            <Field label="De" hint={report.period ? undefined : 'Este relatório não usa período.'}>
              {(p) => (
                <input {...p} type="date" className={inputClass} value={from} disabled={!report.period} onChange={(e) => setFrom(e.target.value)} />
              )}
            </Field>
            <Field label="Até" error={dateError}>
              {(p) => (
                <input {...p} type="date" className={inputClass} value={to} disabled={!report.period} onChange={(e) => setTo(e.target.value)} />
              )}
            </Field>
            <div className="flex items-end gap-2 lg:pt-6">
              <Button type="submit">Gerar</Button>
              <Button variant="secondary" loading={downloading} disabled={!!dateError} onClick={() => void download()}>
                <Download className="h-4 w-4" aria-hidden="true" />
                CSV
              </Button>
            </div>
          </form>
          <div className="mt-3">
            <ErrorAlert error={downloadError} />
          </div>
        </Card>

        {result.isFetching && <Loading />}
        <ErrorAlert error={result.error} />
        {result.data && !result.isFetching && (
          <Card title={REPORTS.find((r) => r.slug === result.data.report)?.label ?? 'Resultado'}>
            {result.data.truncated && (
              <div className="mb-3">
                <Alert tone="amber">Há mais linhas do que o limite. Reduza o período para ver tudo.</Alert>
              </div>
            )}
            {result.data.rows.length === 0 ? (
              <Empty>Nenhum registro no período.</Empty>
            ) : (
              <Table caption="Resultado do relatório" head={result.data.columns.map(header)}>
                {result.data.rows.map((row, i) => (
                  <tr key={i}>
                    {row.map((value, j) => (
                      <td key={j} className={cellClass}>
                        {formatCell(result.data.columns[j] ?? '', value)}
                      </td>
                    ))}
                  </tr>
                ))}
              </Table>
            )}
          </Card>
        )}
      </div>
    </>
  )
}
