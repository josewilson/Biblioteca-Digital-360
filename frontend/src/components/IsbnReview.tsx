import { Plus } from 'lucide-react'
import { Alert, Button, ErrorAlert } from './ui'

/** Campo que o usuário já tinha preenchido e que a busca por ISBN sugere trocar. Só troca com confirmação. */
export interface Conflict {
  key: string
  label: string
  current: string
  suggested: string
}

export type NewKind = 'publisher' | 'author' | 'category'

/** O que sobrou para o usuário decidir depois de uma busca por ISBN. */
export interface Review {
  existingBookId: number | null
  sources: string[]
  conflicts: Conflict[]
  newPublisher: string | null
  newAuthors: string[]
  newCategories: string[]
}

const SOURCE_NAMES: Record<string, string> = { brasilapi: 'BrasilAPI', openlibrary: 'Open Library' }
const KIND_LABEL: Record<NewKind, string> = { publisher: 'editora', author: 'autor', category: 'categoria' }

const short = (text: string) => (text.length > 80 ? `${text.slice(0, 80)}…` : text)

/**
 * Resultado da busca por ISBN: aviso de obra já cadastrada, campos em conflito e nomes que ainda não existem no cadastro
 * (cada um vira um botão: nada é cadastrado sem o clique). Não grava a obra.
 */
export function IsbnReview({
  review,
  creating,
  errors,
  onReplace,
  onKeep,
  onAdd,
}: {
  review: Review
  creating: { kind: NewKind; name: string } | null
  errors: unknown[]
  onReplace: (key: string) => void
  onKeep: () => void
  onAdd: (kind: NewKind, name: string) => void
}) {
  if (review.existingBookId != null) {
    return (
      <Alert tone="amber">
        Já existe uma obra com este ISBN no acervo.{' '}
        <a href={`/acervo/${review.existingBookId}`} target="_blank" rel="noopener noreferrer" className="font-medium underline">
          Abrir a obra existente (nova aba)
        </a>
        . Nada foi preenchido, para evitar cadastro em duplicidade.
      </Alert>
    )
  }

  const suggestions: { kind: NewKind; name: string }[] = [
    ...(review.newPublisher ? [{ kind: 'publisher' as const, name: review.newPublisher }] : []),
    ...review.newAuthors.map((name) => ({ kind: 'author' as const, name })),
    ...review.newCategories.map((name) => ({ kind: 'category' as const, name })),
  ]

  return (
    <div className="space-y-3">
      <Alert tone="green">
        Dados encontrados ({review.sources.map((s) => SOURCE_NAMES[s] ?? s).join(' e ')}). Revise os campos antes de cadastrar: nada
        foi salvo ainda.
      </Alert>

      {review.conflicts.length > 0 && (
        <section aria-label="Campos já preenchidos" className="space-y-2 rounded-md border border-slate-200 p-3">
          <p className="text-sm font-medium">Estes campos já tinham valor e foram mantidos. Quer trocar pelo que a busca encontrou?</p>
          <ul className="space-y-2">
            {review.conflicts.map((c) => (
              <li key={c.key} className="flex flex-wrap items-center gap-2 text-sm">
                <span className="flex-1">
                  <strong>{c.label}:</strong> «{short(c.current)}» → «{short(c.suggested)}»
                </span>
                <Button variant="secondary" aria-label={`Trocar ${c.label.toLowerCase()} pelo sugerido`} onClick={() => onReplace(c.key)}>
                  Trocar
                </Button>
              </li>
            ))}
          </ul>
          <Button variant="ghost" onClick={onKeep}>
            Manter tudo como está
          </Button>
        </section>
      )}

      {suggestions.length > 0 && (
        <section aria-label="Nomes ainda não cadastrados" className="space-y-2 rounded-md border border-slate-200 p-3">
          <p className="text-sm font-medium">Estes nomes não estão no cadastro. Clique para cadastrar e já escolher:</p>
          <div className="flex flex-wrap gap-2">
            {suggestions.map((s) => (
              <Button
                key={`${s.kind}:${s.name}`}
                variant="secondary"
                loading={creating?.kind === s.kind && creating.name === s.name}
                onClick={() => onAdd(s.kind, s.name)}
              >
                <Plus className="h-4 w-4" aria-hidden="true" />
                {`Adicionar ${KIND_LABEL[s.kind]} «${s.name}»`}
              </Button>
            ))}
          </div>
          {errors.map((e, i) => (
            <ErrorAlert key={i} error={e} />
          ))}
        </section>
      )}
    </div>
  )
}
