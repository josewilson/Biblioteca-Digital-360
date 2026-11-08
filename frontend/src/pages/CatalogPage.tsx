import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { NameItem } from '../lib/types'
import { nameSchema, type NameForm } from '../schemas'
import { Button, Card, Empty, ErrorAlert, Field, Loading, PageHeader, inputClass } from '../components/ui'

function LookupCard({ title, path, singular }: { title: string; path: string; singular: string }) {
  const queryClient = useQueryClient()
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
            <li key={i.id} className="py-1.5">
              {i.name}
            </li>
          ))}
        </ul>
      )}
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
