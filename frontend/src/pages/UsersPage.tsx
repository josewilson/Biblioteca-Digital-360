import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { api } from '../lib/api'
import { hasAnyRole, useAuth, type AuthUser } from '../lib/auth'
import { formatDate } from '../lib/format'
import { ALL_ROLES, ROLE_LABELS, type Page, type Role, type UserRow } from '../lib/types'
import { userSchema, type UserForm } from '../schemas'
import { CheckGroup } from '../components/pickers'
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
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

/** Perfis que quem está logado pode atribuir (o servidor confere de novo): bibliotecário não cria equipe. */
function assignableRoles(user: AuthUser | null): Role[] {
  if (hasAnyRole(user, ['SUPER_ADMIN'])) return ALL_ROLES
  if (hasAnyRole(user, ['ADMIN_INSTITUTION'])) return ALL_ROLES.filter((r) => r !== 'SUPER_ADMIN')
  return ['TEACHER', 'STUDENT', 'EMPLOYEE', 'VISITOR']
}

function NewUserForm({ roles, onDone }: { roles: Role[]; onDone: () => void }) {
  const queryClient = useQueryClient()
  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<UserForm>({
    resolver: zodResolver(userSchema),
    defaultValues: { fullName: '', email: '', password: '', cpf: '', registration: '', phone: '', roles: [] },
  })
  const create = useMutation({
    mutationFn: (v: UserForm) =>
      api<UserRow>('/users', {
        method: 'POST',
        body: { ...v, cpf: v.cpf || null, registration: v.registration || null, phone: v.phone || null },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['users'] })
      onDone()
    },
  })

  return (
    <form onSubmit={handleSubmit((v) => create.mutate(v))} noValidate className="space-y-3">
      <Field label="Nome completo" error={errors.fullName?.message}>
        {(p) => <input {...p} className={inputClass} {...register('fullName')} />}
      </Field>
      <Field label="E-mail" error={errors.email?.message}>
        {(p) => <input {...p} type="email" autoComplete="off" className={inputClass} {...register('email')} />}
      </Field>
      <Field label="Senha inicial" hint="Mínimo de 8 caracteres." error={errors.password?.message}>
        {(p) => <input {...p} type="password" autoComplete="new-password" className={inputClass} {...register('password')} />}
      </Field>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Matrícula" error={errors.registration?.message}>
          {(p) => <input {...p} className={inputClass} {...register('registration')} />}
        </Field>
        <Field label="Telefone" error={errors.phone?.message}>
          {(p) => <input {...p} type="tel" className={inputClass} {...register('phone')} />}
        </Field>
      </div>
      <Field label="CPF (opcional)" hint="Só números. Colete apenas se for necessário (LGPD)." error={errors.cpf?.message}>
        {(p) => <input {...p} inputMode="numeric" className={inputClass} {...register('cpf')} />}
      </Field>
      <Controller
        control={control}
        name="roles"
        render={({ field }) => (
          <CheckGroup
            legend="Perfis"
            options={roles.map((r) => ({ value: r, label: ROLE_LABELS[r] }))}
            value={field.value}
            onChange={field.onChange}
            error={errors.roles?.message}
          />
        )}
      />
      <ErrorAlert error={create.error} />
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onDone}>
          Cancelar
        </Button>
        <Button type="submit" loading={create.isPending}>
          Cadastrar
        </Button>
      </div>
    </form>
  )
}

export function UsersPage() {
  const { user, isAdmin } = useAuth()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [creating, setCreating] = useState(false)
  const [deactivating, setDeactivating] = useState<UserRow | null>(null)

  const list = useQuery({
    queryKey: ['users', 'page', page],
    queryFn: () => api<Page<UserRow>>('/users', { params: { page, size: 15, sort: 'fullName' } }),
  })

  const deactivate = useMutation({
    mutationFn: (u: UserRow) => api<void>(`/users/${u.id}`, { method: 'DELETE' }),
    onSuccess: () => {
      setDeactivating(null)
      void queryClient.invalidateQueries({ queryKey: ['users'] })
    },
  })

  return (
    <>
      <PageHeader
        title="Usuários"
        subtitle="Alunos, professores, funcionários e equipe da biblioteca."
        actions={
          <Button onClick={() => setCreating(true)}>
            <Plus className="h-4 w-4" aria-hidden="true" />
            Novo usuário
          </Button>
        }
      />
      <Card>
        {list.isLoading && <Loading />}
        <ErrorAlert error={list.error} />
        {list.data && list.data.content.length === 0 && <Empty>Nenhum usuário.</Empty>}
        {list.data && list.data.content.length > 0 && (
          <>
            <Table caption="Usuários" head={isAdmin ? ['Nome', 'E-mail', 'Perfis', 'Cadastro', 'Situação', 'Ações'] : ['Nome', 'E-mail', 'Perfis', 'Cadastro', 'Situação']}>
              {list.data.content.map((u) => (
                <tr key={u.id}>
                  <td className={cellClass}>{u.fullName}</td>
                  <td className={cellClass}>{u.email}</td>
                  <td className={cellClass}>{u.roles.map((r) => ROLE_LABELS[r] ?? r).join(', ')}</td>
                  <td className={cellClass}>{formatDate(u.createdAt)}</td>
                  <td className={cellClass}>{u.active ? <Badge tone="green">Ativo</Badge> : <Badge>Inativo</Badge>}</td>
                  {isAdmin && (
                    <td className={cellClass}>
                      {u.active && (
                        <Button variant="secondary" onClick={() => setDeactivating(u)}>
                          Desativar
                        </Button>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </Table>
            <Pagination page={list.data.number} totalPages={list.data.totalPages} onChange={setPage} />
          </>
        )}
      </Card>

      <Dialog open={creating} title="Novo usuário" onClose={() => setCreating(false)}>
        <NewUserForm roles={assignableRoles(user)} onDone={() => setCreating(false)} />
      </Dialog>
      <ConfirmDialog
        open={deactivating !== null}
        title="Desativar usuário"
        message={
          deactivating && (
            <>
              Desativar <strong>{deactivating.fullName}</strong>? Ele não poderá mais entrar nem pegar livros. O histórico é
              mantido.
            </>
          )
        }
        confirmLabel="Desativar"
        danger
        loading={deactivate.isPending}
        error={deactivate.error}
        onConfirm={() => deactivating && deactivate.mutate(deactivating)}
        onCancel={() => setDeactivating(null)}
      />
    </>
  )
}
