import { useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { Book, Page, UserRow } from '../lib/types'
import { Button, Empty, Field, Loading, inputClass, ErrorAlert } from './ui'

const usersQuery = () => ({
  queryKey: ['users', 'all'],
  // ponytail: só os 200 primeiros por nome; se a base crescer, trocar por busca no servidor.
  queryFn: () => api<Page<UserRow>>('/users', { params: { size: 200, sort: 'fullName' } }),
})

/** Mapa id -> nome para mostrar o leitor em vez de "#7" nas tabelas. */
export function useUserNames(): (id: number) => string {
  const { data } = useQuery(usersQuery())
  const names = new Map((data?.content ?? []).map((u) => [u.id, u.fullName]))
  return (id) => names.get(id) ?? `Leitor #${id}`
}

export function UserSelect({ value, onChange }: { value: number | null; onChange: (id: number | null) => void }) {
  const { data, isLoading, error } = useQuery(usersQuery())
  return (
    <div className="space-y-2">
      <Field label="Leitor">
        {(props) => (
          <select
            {...props}
            className={inputClass}
            value={value ?? ''}
            disabled={isLoading}
            onChange={(e) => onChange(e.target.value ? Number(e.target.value) : null)}
          >
            <option value="">{isLoading ? 'Carregando…' : 'Escolha o leitor'}</option>
            {(data?.content ?? [])
              .filter((u) => u.active)
              .map((u) => (
                <option key={u.id} value={u.id}>
                  {u.fullName} ({u.email})
                </option>
              ))}
          </select>
        )}
      </Field>
      <ErrorAlert error={error} />
    </div>
  )
}

/** Busca obras pelo título e devolve a escolhida. */
export function BookPicker({ onPick }: { onPick: (book: Book) => void }) {
  const [text, setText] = useState('')
  const [term, setTerm] = useState('')
  const { data, isFetching, error } = useQuery({
    queryKey: ['books', 'picker', term],
    queryFn: () => api<Page<Book>>('/books', { params: { title: term, size: 8 } }),
    enabled: term.length >= 2,
  })

  function search(e: FormEvent) {
    e.preventDefault()
    setTerm(text.trim())
  }

  return (
    <div className="space-y-2">
      <form onSubmit={search} className="flex items-end gap-2">
        <div className="flex-1">
          <Field label="Buscar obra pelo título" hint="Digite pelo menos 2 letras.">
            {(props) => <input {...props} className={inputClass} value={text} onChange={(e) => setText(e.target.value)} />}
          </Field>
        </div>
        <Button type="submit" variant="secondary" disabled={text.trim().length < 2}>
          Buscar
        </Button>
      </form>
      <ErrorAlert error={error} />
      {isFetching && <Loading />}
      {data && data.content.length === 0 && <Empty>Nenhuma obra encontrada.</Empty>}
      {data && data.content.length > 0 && (
        <ul className="divide-y divide-slate-100 rounded-md border border-slate-200">
          {data.content.map((b) => (
            <li key={b.id} className="flex items-center justify-between gap-2 px-3 py-2 text-sm">
              <span>{b.title}</span>
              <Button variant="ghost" onClick={() => onPick(b)}>
                Escolher
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/** Grupo de caixas de marcar (várias escolhas), acessível por <fieldset>. */
export function CheckGroup({
  legend,
  options,
  value,
  onChange,
  error,
}: {
  legend: string
  options: { value: string; label: string }[]
  value: string[]
  onChange: (value: string[]) => void
  error?: string
}) {
  function toggle(v: string) {
    onChange(value.includes(v) ? value.filter((x) => x !== v) : [...value, v])
  }
  return (
    <fieldset className="space-y-1">
      <legend className="text-sm font-medium text-slate-700">{legend}</legend>
      {options.length === 0 && <p className="text-xs text-slate-500">Nenhum item cadastrado ainda.</p>}
      <div className="flex flex-wrap gap-x-4 gap-y-1">
        {options.map((o) => (
          <label key={o.value} className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={value.includes(o.value)} onChange={() => toggle(o.value)} />
            {o.label}
          </label>
        ))}
      </div>
      {error && <p className="text-xs text-red-700">{error}</p>}
    </fieldset>
  )
}
