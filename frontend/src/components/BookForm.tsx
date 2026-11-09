import { useState } from 'react'
import { Controller, useFieldArray, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { api } from '../lib/api'
import type { Book, NameItem } from '../lib/types'
import { bookSchema, toBookRequest, type BookForm as BookFormValues } from '../schemas'
import { CheckGroup } from './pickers'
import { Alert, Button, ErrorAlert, Field, inputClass } from './ui'

const EMPTY_COPY = { barcode: '', kind: 'CIRCULANTE', condition: 'BOM', location: '' } as const

/** Com `book`, edita os dados da obra; os exemplares têm operações próprias na página da obra. */
export function BookForm({ book, onSaved, onCancel }: { book?: Book; onSaved: (book: Book) => void; onCancel: () => void }) {
  const queryClient = useQueryClient()
  const [serverError, setServerError] = useState<unknown>(null)

  const authors = useQuery({ queryKey: ['authors'], queryFn: () => api<NameItem[]>('/authors') })
  const publishers = useQuery({ queryKey: ['publishers'], queryFn: () => api<NameItem[]>('/publishers') })
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => api<NameItem[]>('/categories') })

  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<BookFormValues>({
    resolver: zodResolver(bookSchema),
    defaultValues: {
      title: book?.title ?? '',
      isbn: book?.isbn ?? '',
      edition: book?.edition ?? '',
      publicationYear: book?.publicationYear?.toString() ?? '',
      language: book?.language ?? '',
      description: book?.description ?? '',
      coverUrl: book?.coverUrl ?? '',
      publisherId: book?.publisherId?.toString() ?? '',
      authorIds: book?.authors.map((a) => String(a.id)) ?? [],
      categoryIds: book?.categories.map((c) => String(c.id)) ?? [],
      // Nova obra já nasce com os dois tipos de exemplar que a regra exige. Na edição os exemplares
      // só acompanham o formulário para passar pela mesma validação; não são exibidos nem enviados.
      copies: book
        ? book.copies.map((c) => ({ barcode: c.barcode, kind: c.kind, condition: c.condition, location: c.location ?? '' }))
        : [
            { ...EMPTY_COPY, kind: 'CONSULTA' },
            { ...EMPTY_COPY, kind: 'CIRCULANTE' },
          ],
    },
  })
  const { fields, append, remove } = useFieldArray({ control, name: 'copies' })

  const save = useMutation({
    mutationFn: (values: BookFormValues) =>
      book
        ? api<Book>(`/books/${book.id}`, { method: 'PUT', body: { ...toBookRequest(values), copies: undefined } })
        : api<Book>('/books', { method: 'POST', body: toBookRequest(values) }),
    onSuccess: (saved) => {
      void queryClient.invalidateQueries({ queryKey: ['books'] })
      void queryClient.invalidateQueries({ queryKey: ['book', saved.id] })
      onSaved(saved)
    },
    onError: setServerError,
  })

  const toOptions = (items?: NameItem[]) => (items ?? []).map((i) => ({ value: String(i.id), label: i.name }))
  // Erro do array inteiro (ex.: falta exemplar de consulta) vem em copies.root ou copies.message.
  const copiesError = errors.copies?.root?.message ?? errors.copies?.message

  return (
    <form
      onSubmit={handleSubmit((v) => {
        setServerError(null)
        save.mutate(v)
      })}
      noValidate
      className="space-y-4"
    >
      <Field label="Título" error={errors.title?.message}>
        {(p) => <input {...p} className={inputClass} {...register('title')} />}
      </Field>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="ISBN" error={errors.isbn?.message}>
          {(p) => <input {...p} className={inputClass} {...register('isbn')} />}
        </Field>
        <Field label="Ano de publicação" error={errors.publicationYear?.message}>
          {(p) => <input {...p} inputMode="numeric" className={inputClass} {...register('publicationYear')} />}
        </Field>
        <Field label="Edição" error={errors.edition?.message}>
          {(p) => <input {...p} className={inputClass} {...register('edition')} />}
        </Field>
        <Field label="Idioma" error={errors.language?.message}>
          {(p) => <input {...p} className={inputClass} {...register('language')} />}
        </Field>
      </div>

      <Field label="Editora">
        {(p) => (
          <select {...p} className={inputClass} {...register('publisherId')}>
            <option value="">Sem editora</option>
            {(publishers.data ?? []).map((pub) => (
              <option key={pub.id} value={pub.id}>
                {pub.name}
              </option>
            ))}
          </select>
        )}
      </Field>

      <Controller
        control={control}
        name="authorIds"
        render={({ field }) => (
          <CheckGroup legend="Autores" options={toOptions(authors.data)} value={field.value} onChange={field.onChange} />
        )}
      />
      <Controller
        control={control}
        name="categoryIds"
        render={({ field }) => (
          <CheckGroup legend="Categorias" options={toOptions(categories.data)} value={field.value} onChange={field.onChange} />
        )}
      />

      <Field label="Descrição" error={errors.description?.message}>
        {(p) => <textarea {...p} rows={3} className={inputClass} {...register('description')} />}
      </Field>
      <Field label="Endereço da imagem da capa" hint="Opcional. Envio de arquivo ainda não existe." error={errors.coverUrl?.message}>
        {(p) => <input {...p} type="url" className={inputClass} {...register('coverUrl')} />}
      </Field>

      {!book && (
      <fieldset className="space-y-3 rounded-md border border-slate-200 p-3">
        <legend className="px-1 text-sm font-semibold">Exemplares</legend>
        <p className="text-xs text-slate-600">
          Toda obra precisa de pelo menos 1 exemplar de <strong>consulta</strong> (nunca é emprestado) e 1 exemplar{' '}
          <strong>emprestável</strong>.
        </p>
        {fields.map((field, index) => (
          <div key={field.id} className="grid gap-2 rounded-md bg-slate-50 p-2 sm:grid-cols-[1fr_9rem_7rem_auto]">
            <Field label={`Código do exemplar ${index + 1}`} error={errors.copies?.[index]?.barcode?.message}>
              {(p) => <input {...p} className={inputClass} {...register(`copies.${index}.barcode`)} />}
            </Field>
            <Field label="Tipo">
              {(p) => (
                <select {...p} className={inputClass} {...register(`copies.${index}.kind`)}>
                  <option value="CONSULTA">Consulta</option>
                  <option value="CIRCULANTE">Emprestável</option>
                </select>
              )}
            </Field>
            <Field label="Estado">
              {(p) => (
                <select {...p} className={inputClass} {...register(`copies.${index}.condition`)}>
                  <option value="NOVO">Novo</option>
                  <option value="BOM">Bom</option>
                  <option value="REGULAR">Regular</option>
                  <option value="RUIM">Ruim</option>
                </select>
              )}
            </Field>
            <div className="flex items-end">
              <Button
                variant="ghost"
                aria-label={`Remover exemplar ${index + 1}`}
                disabled={fields.length <= 1}
                onClick={() => remove(index)}
              >
                <Trash2 className="h-4 w-4" aria-hidden="true" />
              </Button>
            </div>
          </div>
        ))}
        {copiesError && <Alert tone="red">{copiesError}</Alert>}
        <Button variant="secondary" onClick={() => append({ ...EMPTY_COPY })}>
          <Plus className="h-4 w-4" aria-hidden="true" />
          Adicionar exemplar
        </Button>
      </fieldset>
      )}

      <ErrorAlert error={serverError} />
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onCancel}>
          Cancelar
        </Button>
        <Button type="submit" loading={save.isPending}>
          {book ? 'Salvar alterações' : 'Cadastrar obra'}
        </Button>
      </div>
    </form>
  )
}
