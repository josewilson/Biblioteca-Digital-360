import { useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router'
import { Plus, Search } from 'lucide-react'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import type { Book, Page } from '../lib/types'
import { BookCover } from '../components/BookCover'
import { BookForm } from '../components/BookForm'
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
  Table,
  cellClass,
  inputClass,
} from '../components/ui'

/** Quantos exemplares emprestáveis estão livres / quantos existem em circulação. */
function availability(book: Book): string {
  const lendable = book.copies.filter((c) => c.kind === 'CIRCULANTE' && c.status !== 'PERDIDO' && c.status !== 'BAIXADO')
  const free = lendable.filter((c) => c.status === 'DISPONIVEL').length
  return `${free} de ${lendable.length}`
}

export function BooksPage() {
  const { isStaff } = useAuth()
  const navigate = useNavigate()
  const [text, setText] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [creating, setCreating] = useState(false)

  const { data, isLoading, error } = useQuery({
    queryKey: ['books', query, page],
    queryFn: () => api<Page<Book>>('/books', { params: { q: query, page, size: 10, sort: 'title' } }),
  })

  function search(e: FormEvent) {
    e.preventDefault()
    setPage(0)
    setQuery(text.trim())
  }

  return (
    <>
      <PageHeader
        title="Acervo"
        subtitle="Consulte as obras e a disponibilidade dos exemplares."
        actions={
          isStaff && (
            <div className="flex flex-wrap items-center gap-3">
              <Link to="/acervo/importar" className="text-sm font-medium text-brand-700 underline">
                Importar por ISBN
              </Link>
              <Button onClick={() => setCreating(true)}>
                <Plus className="h-4 w-4" aria-hidden="true" />
                Nova obra
              </Button>
            </div>
          )
        }
      />

      <form onSubmit={search} role="search" className="mb-4 flex max-w-xl items-end gap-2">
        <div className="flex-1">
          <Field label="Buscar por título, autor, ISBN ou código do exemplar">
            {(p) => <input {...p} className={inputClass} value={text} onChange={(e) => setText(e.target.value)} />}
          </Field>
        </div>
        <Button type="submit" variant="secondary">
          <Search className="h-4 w-4" aria-hidden="true" />
          Buscar
        </Button>
      </form>

      <ErrorAlert error={error} />
      {isLoading && <Loading />}
      {data && (
        <Card>
          {data.content.length === 0 ? (
            <Empty>Nenhuma obra encontrada.</Empty>
          ) : (
            <>
              <Table caption="Obras do acervo" head={['Título', 'Autores', 'Categorias', 'Para empréstimo (livres)']}>
                {data.content.map((b) => (
                  <tr key={b.id}>
                    <td className={cellClass}>
                      <div className="flex items-center gap-3">
                        <BookCover book={b} size="thumb" decorative />
                        <Link to={`/acervo/${b.id}`} className="font-serif font-medium text-brand-700 underline">
                          {b.title}
                        </Link>
                      </div>
                    </td>
                    <td className={cellClass}>{b.authors.map((a) => a.name).join(', ') || '—'}</td>
                    <td className={cellClass}>{b.categories.map((c) => c.name).join(', ') || '—'}</td>
                    <td className={cellClass}>{availability(b)}</td>
                  </tr>
                ))}
              </Table>
              <Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} />
            </>
          )}
        </Card>
      )}

      <Dialog open={creating} title="Nova obra" onClose={() => setCreating(false)}>
        <BookForm
          onCancel={() => setCreating(false)}
          onSaved={(book) => {
            setCreating(false)
            navigate(`/acervo/${book.id}`)
          }}
        />
      </Dialog>
    </>
  )
}
