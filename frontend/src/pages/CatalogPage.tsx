import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { NameItem } from '../lib/types'
import { nameSchema, type NameForm } from '../schemas'
import { Button, Card, Dialog, Empty, ErrorAlert, Field, Loading, PageHeader, inputClass } from '../components/ui'

function RenameForm({ item, path, onClose }: { item: NameItem; path: string; onClose: () => void }) {
  const queryClient = useQueryClient()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<NameForm>({ resolver: zodResolver(nameSchema), defaultValues: { name: item.name } })
  const rename = useMutation({
    mutationFn: (v: NameForm) => api<NameItem>(`${path}/${item.id}`, { method: 'PUT', body: v }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [path.slice(1)] })
      void queryClient.invalidateQueries({ queryKey: ['books'] })
      void queryClient.invalidateQueries({ queryKey: ['book'] })
      onClose()
    },
  })
  return (
    <form onSubmit={handleSubmit((v) => rename.mutate(v))} noValidate className="space-y-3">
      <Field label="Nome" error={errors.name?.message}>
        {(p) => <input {...p} className={inputClass} {...register('name')} />}
      </Field>
      <ErrorAlert error={rename.error} />
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>
          Cancelar
        </Button>
        <Button type="submit" loading={rename.isPending}>
          Salvar nome
        </Button>
      </div>
    </form>
  )
}

function LookupCard({ title, path, singular }: { title: string; path: string; singular: string }) {
  const queryClient = useQueryClient()
  const [renaming, setRenaming] = useState<NameItem | null>(null)
  const list = useQuery({ queryKey: [path.slice(1)], queryFn: () => api<NameItem[]>(path) })
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<NameForm>({ resolver: zodResolver(nameSchema), defaultValues: { name: '' } })

  const create = useMutation({
    mutationFn: (v: NameForm) => api<NameItem>(path, { method: 'POST', body: v }),
    onSuccess: () => {
      reset()
      void queryClient.invalidateQueries({ queryKey: [path.slice(1)] })
    },
  })

  return (
    <Card title={title}>
      <form onSubmit={handleSubmit((v) => create.mutate(v))} noValidate className="mb-4 flex items-start gap-2">
        <div className="flex-1">
          <Field label={`Novo ${singular}`} error={errors.name?.message}>
            {(p) => <input {...p} className={inputClass} {...register('name')} />}
          </Field>
        </div>
        <Button type="submit" className="mt-6" loading={create.isPending}>
          Adicionar
        </Button>
      </form>
      <ErrorAlert error={create.error} />
      {list.isLoading && <Loading />}
      {list.data && list.data.length === 0 && <Empty>Nada cadastrado ainda.</Empty>}
      {list.data && list.data.length > 0 && (
        <ul className="mt-2 max-h-72 divide-y divide-slate-100 overflow-y-auto text-sm">
          {list.data.map((i) => (
            <li key={i.id} className="flex items-center justify-between gap-2 py-1">
              <span>{i.name}</span>
              <Button variant="ghost" aria-label={`Renomear ${i.name}`} onClick={() => setRenaming(i)}>
                Renomear
              </Button>
            </li>
          ))}
        </ul>
      )}
      <Dialog open={renaming !== null} title={`Renomear ${singular}`} onClose={() => setRenaming(null)}>
        {renaming && <RenameForm item={renaming} path={path} onClose={() => setRenaming(null)} />}
      </Dialog>
    </Card>
  )
}

export function CatalogPage() {
  return (
    <>
      <PageHeader title="Autores, editoras e categorias" subtitle="Cadastros usados ao registrar uma obra." />
      <div className="grid gap-6 lg:grid-cols-3">
        <LookupCard title="Autores" path="/authors" singular="autor" />
        <LookupCard title="Editoras" path="/publishers" singular="editora" />
        <LookupCard title="Categorias" path="/categories" singular="categoria" />
      </div>
    </>
  )
}
