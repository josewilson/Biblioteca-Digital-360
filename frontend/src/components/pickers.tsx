import { useEffect, useState, type FormEvent } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { Book, Page, UserRow } from '../lib/types'
import { Button, Empty, Field, Loading, inputClass, ErrorAlert } from './ui'

const PICKER_SIZE = 20

function useDebounced<T>(value: T, ms = 250): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), ms)
    return () => clearTimeout(timer)
  }, [value, ms])
  return debounced
}

/** Nomes dos leitores que aparecem na tela (id -> nome), para não mostrar "#7" nas tabelas. */
export function useUserNames(ids: number[]): (id: number) => string {
  const unique = [...new Set(ids)].sort((a, b) => a - b)
  const { data } = useQuery({
    queryKey: ['users', 'lookup', unique],
    queryFn: () => api<UserRow[]>('/users/lookup', { params: { ids: unique.join(',') } }),
    enabled: unique.length > 0,
  })
  const names = new Map((data ?? []).map((u) => [u.id, u.fullName]))
  return (id) => names.get(id) ?? `Leitor #${id}`
}

/** Escolhe um leitor com busca no servidor: funciona igual com 50 ou 50 mil usuários. */
export function UserSelect({
  value,
  onChange,
  label = 'Leitor',
  emptyLabel = 'Escolha o leitor',
}: {
  value: number | null
  onChange: (id: number | null) => void
  label?: string
  emptyLabel?: string
}) {
  const [text, setText] = useState('')
  const term = useDebounced(text.trim())
  const { data, isLoading, error } = useQuery({
    queryKey: ['users', 'search', term],
    queryFn: () => api<Page<UserRow>>('/users', { params: { q: term, size: PICKER_SIZE, sort: 'fullName' } }),
    placeholderData: keepPreviousData,
  })
  const hidden = (data?.totalElements ?? 0) - (data?.content.length ?? 0)

  return (
    <div className="grid gap-2 sm:grid-cols-2">
      <Field label={`${label}: buscar por nome, e-mail ou matrícula`}>
        {(props) => (
          <input
            {...props}
            type="search"
            className={inputClass}
            value={text}
            onChange={(e) => {
              setText(e.target.value)
              if (value !== null) onChange(null)   // a lista vai mudar: a escolha anterior deixa de estar nela
            }}
          />
        )}
      </Field>
      <Field label={label} hint={hidden > 0 ? `Mostrando ${PICKER_SIZE}. Refine a busca para ver os outros ${hidden}.` : undefined}>
        {(props) => (
          <select
            {...props}
            className={inputClass}
            value={value ?? ''}
            disabled={isLoading}
            onChange={(e) => onChange(e.target.value ? Number(e.target.value) : null)}
          >
            <option value="">{isLoading ? 'Carregando…' : emptyLabel}</option>
            {(data?.content ?? []).map((u) => (
              <option key={u.id} value={u.id} disabled={!u.active}>
                {u.fullName} ({u.email}){u.active ? '' : ' (inativo)'}
              </option>
            ))}
          </select>
        )}
      </Field>
      <div className="sm:col-span-2">
        <ErrorAlert error={error} />
      </div>
    </div>
  )
}

/** Busca obras (título, autor, ISBN ou código do exemplar) e devolve a escolhida. */
export function BookPicker({ onPick }: { onPick: (book: Book) => void }) {
  const [text, setText] = useState('')
  const [term, setTerm] = useState('')
  const { data, isFetching, error } = useQuery({
    queryKey: ['books', 'picker', term],
    queryFn: () => api<Page<Book>>('/books', { params: { q: term, size: 8 } }),
    enabled: term.length >= 2,
  })

  function search(e: FormEvent) {
    e.preventDefault()
    setTerm(text.trim())
  }

  return (
    <div className="space-y-2">
      <form onSubmit={search} className="space-y-1">
        <div className="flex items-end gap-2">
          <div className="flex-1">
            <Field label="Buscar obra pelo título, autor, ISBN ou código">
              {(props) => (
                <input
                  {...props}
                  aria-describedby="dica-busca-obra"
                  className={inputClass}
                  value={text}
                  onChange={(e) => setText(e.target.value)}
                />
              )}
            </Field>
          </div>
          <Button type="submit" variant="secondary" disabled={text.trim().length < 2}>
            Buscar
          </Button>
        </div>
        {/* A dica fica abaixo da linha do campo e do botão, para os dois ficarem alinhados. */}
        <p id="dica-busca-obra" className="text-sm text-slate-600">
          Digite pelo menos 2 letras.
        </p>
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

const LONG_LIST = 12
const normalize = (s: string) => s.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()

/** Grupo de caixas de marcar (várias escolhas), acessível por <fieldset>. Lista longa ganha filtro. */
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
  const [filter, setFilter] = useState('')
  const long = options.length > LONG_LIST
  const wanted = normalize(filter)
  // O que já foi marcado continua visível, para o filtro não esconder a escolha.
  const visible = options.filter((o) => value.includes(o.value) || normalize(o.label).includes(wanted))

  function toggle(v: string) {
    onChange(value.includes(v) ? value.filter((x) => x !== v) : [...value, v])
  }
  return (
    <fieldset className="space-y-1">
      <legend className="text-sm font-medium text-slate-700">{legend}</legend>
      {options.length === 0 && <p className="text-xs text-slate-500">Nenhum item cadastrado ainda.</p>}
      {long && (
        <input
          type="search"
          aria-label={`Filtrar ${legend.toLowerCase()}`}
          className={inputClass}
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && e.preventDefault()}
        />
      )}
      <div className={`flex flex-wrap gap-x-4 gap-y-1 ${long ? 'max-h-40 overflow-y-auto' : ''}`}>
        {visible.map((o) => (
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
