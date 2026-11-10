import { useEffect, useRef, useState } from 'react'
import { Controller, useFieldArray, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { api } from '../lib/api'
import type { Book, NameItem } from '../lib/types'
import { bookSchema, toBookRequest, type BookForm as BookFormValues } from '../schemas'
import { BookCover } from './BookCover'
import { CheckGroup } from './pickers'
import { Alert, Button, ErrorAlert, Field, inputClass } from './ui'

const COVER_TYPES = ['image/jpeg', 'image/png']
const COVER_MAX_BYTES = 2 * 1024 * 1024

const EMPTY_COPY = { barcode: '', kind: 'CIRCULANTE', condition: 'BOM', location: '' } as const

/** Qual cadastro de apoio o campo "novo" cria (editora, autor ou categoria). */
interface LookupKind {
  path: '/publishers' | '/authors' | '/categories'
  label: string        // "Nova editora"
  button: string       // "Cadastrar editora"
}

/**
 * Cadastra um item de apoio sem sair do formulário da obra. Não é um <form> (já estamos dentro de um): Enter cria o item, não salva a obra.
 */
function NewLookup({ kind, onCreated }: { kind: LookupKind; onCreated: (item: NameItem) => void }) {
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const create = useMutation({
    mutationFn: () => api<NameItem>(kind.path, { method: 'POST', body: { name: name.trim() } }),
    onSuccess: (item) => {
      setName('')
      void queryClient.invalidateQueries({ queryKey: [kind.path.slice(1)] })
      onCreated(item)
    },
  })
  const submit = () => name.trim() && create.mutate()

  return (
    <div className="space-y-2">
      <ErrorAlert error={create.error} />
      <div className="flex items-end gap-2">
        <div className="flex-1">
          <Field label={kind.label} hint="Não está na lista? Digite o nome e cadastre.">
            {(p) => (
              <input
                {...p}
                maxLength={150}
                className={inputClass}
                value={name}
                onChange={(e) => setName(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault()
                    submit()
                  }
                }}
              />
            )}
          </Field>
        </div>
        <Button variant="secondary" loading={create.isPending} disabled={!name.trim()} onClick={submit}>
          <Plus className="h-4 w-4" aria-hidden="true" />
          {kind.button}
        </Button>
      </div>
    </div>
  )
}

const PUBLISHER: LookupKind = { path: '/publishers', label: 'Nova editora', button: 'Cadastrar editora' }
const AUTHOR: LookupKind = { path: '/authors', label: 'Novo autor', button: 'Cadastrar autor' }
const CATEGORY: LookupKind = { path: '/categories', label: 'Nova categoria', button: 'Cadastrar categoria' }

/**
 * O item recém-cadastrado só existe nas opções depois que a lista é recarregada; quando chega, `select` o escolhe.
 * Devolve a função que avisa qual id aguardar.
 */
function useSelectWhenListed(list: NameItem[] | undefined, select: (id: number) => void) {
  const [pending, setPending] = useState<number | null>(null)
  useEffect(() => {
    if (pending !== null && list?.some((item) => item.id === pending)) {
      select(pending)
      setPending(null)
    }
  }, [pending, list, select])
  return setPending
}

/** Com `book`, edita os dados da obra; os exemplares têm operações próprias na página da obra. */
export function BookForm({ book, onSaved, onCancel }: { book?: Book; onSaved: (book: Book) => void; onCancel: () => void }) {
  const queryClient = useQueryClient()
  const [serverError, setServerError] = useState<unknown>(null)
  // Capa: enviada depois de salvar a obra. Se o envio falhar, a obra já existe; `created` evita cadastrá-la de novo ao tentar outra vez.
  const [cover, setCover] = useState<File | null>(null)
  const [coverError, setCoverError] = useState<string | null>(null)
  const [removeCover, setRemoveCover] = useState(false)
  const [preview, setPreview] = useState<string | null>(null)
  const created = useRef<Book | null>(null)

  useEffect(() => {
    if (!cover) return setPreview(null)
    const url = URL.createObjectURL(cover)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [cover])

  function pickCover(file: File | undefined) {
    setCoverError(null)
    if (file && !COVER_TYPES.includes(file.type)) {
      setCover(null)
      return setCoverError('Use uma imagem JPEG ou PNG.')
    }
    if (file && file.size > COVER_MAX_BYTES) {
      setCover(null)
      return setCoverError('A imagem passa de 2 MB.')
    }
    setCover(file ?? null)
  }

  const authors = useQuery({ queryKey: ['authors'], queryFn: () => api<NameItem[]>('/authors') })
  const publishers = useQuery({ queryKey: ['publishers'], queryFn: () => api<NameItem[]>('/publishers') })
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => api<NameItem[]>('/categories') })

  const {
    register,
    control,
    setValue,
    getValues,
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

  const selectPublisher = useSelectWhenListed(publishers.data, (id) => setValue('publisherId', String(id)))
  const selectAuthor = useSelectWhenListed(authors.data, (id) => setValue('authorIds', [...getValues('authorIds'), String(id)]))
  const selectCategory = useSelectWhenListed(categories.data, (id) => setValue('categoryIds', [...getValues('categoryIds'), String(id)]))

  const save = useMutation({
    mutationFn: async (values: BookFormValues) => {
      let saved = book
        ? await api<Book>(`/books/${book.id}`, { method: 'PUT', body: { ...toBookRequest(values), copies: undefined } })
        : (created.current ??= await api<Book>('/books', { method: 'POST', body: toBookRequest(values) }))
      if (cover) {
        const data = new FormData()
        data.append('file', cover)
        saved = await api<Book>(`/books/${saved.id}/cover`, { method: 'PUT', body: data })
      } else if (removeCover && saved.coverVersion != null) {
        saved = await api<Book>(`/books/${saved.id}/cover`, { method: 'DELETE' })
      }
      return saved
    },
    onSuccess: (saved) => {
      void queryClient.invalidateQueries({ queryKey: ['books'] })
      void queryClient.invalidateQueries({ queryKey: ['book', saved.id] })
      onSaved(saved)
    },
    onError: (e) => {
      setServerError(e)
      if (created.current) void queryClient.invalidateQueries({ queryKey: ['books'] })   // a obra já entrou no acervo
    },
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

      <NewLookup kind={PUBLISHER} onCreated={(item) => selectPublisher(item.id)} />

      <Controller
        control={control}
        name="authorIds"
        render={({ field }) => (
          <CheckGroup legend="Autores" options={toOptions(authors.data)} value={field.value} onChange={field.onChange} />
        )}
      />
      <NewLookup kind={AUTHOR} onCreated={(item) => selectAuthor(item.id)} />
      <Controller
        control={control}
        name="categoryIds"
        render={({ field }) => (
          <CheckGroup legend="Categorias" options={toOptions(categories.data)} value={field.value} onChange={field.onChange} />
        )}
      />
      <NewLookup kind={CATEGORY} onCreated={(item) => selectCategory(item.id)} />

      <Field label="Descrição" error={errors.description?.message}>
        {(p) => <textarea {...p} rows={3} className={inputClass} {...register('description')} />}
      </Field>
      <div className="flex items-start gap-4">
        {preview ? (
          <img src={preview} alt="Prévia da capa escolhida" className="h-24 w-16 shrink-0 rounded border border-slate-200 object-cover" />
        ) : (
          book && !removeCover && <BookCover book={book} size="thumb" />
        )}
        <div className="flex-1 space-y-2">
          <Field label="Capa da obra" hint="Opcional. JPEG ou PNG, até 2 MB. O sistema gera a miniatura." error={coverError ?? undefined}>
            {(p) => <input {...p} type="file" accept={COVER_TYPES.join(',')} onChange={(e) => pickCover(e.target.files?.[0])} className={inputClass} />}
          </Field>
          {book?.coverVersion != null && !cover && (
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={removeCover} onChange={(e) => setRemoveCover(e.target.checked)} />
              Remover a capa enviada
            </label>
          )}
        </div>
      </div>
      <Field label="Endereço da imagem da capa" hint="Opcional. Só é usado quando não há capa enviada." error={errors.coverUrl?.message}>
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
      {Boolean(serverError) && created.current && (
        <Alert tone="amber">A obra foi cadastrada, mas a capa não foi enviada. Escolha outra imagem e salve de novo, ou cancele e envie depois pela edição.</Alert>
      )}
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
