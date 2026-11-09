import { useState, type FormEvent } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Search } from 'lucide-react'
import { api, downloadFile } from '../lib/api'
import { hasAnyRole, useAuth, type AuthUser } from '../lib/auth'
import { formatDate } from '../lib/format'
import { ALL_ROLES, ROLE_LABELS, type Page, type Role, type UserRow } from '../lib/types'
import {
  resetPasswordSchema,
  userEditSchema,
  userSchema,
  type ResetPasswordForm as ResetPasswordFormValues,
  type UserEditForm,
  type UserForm,
} from '../schemas'
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

function EditUserForm({ target, roles, onDone }: { target: UserRow; roles: Role[]; onDone: () => void }) {
  const queryClient = useQueryClient()
  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<UserEditForm>({
    resolver: zodResolver(userEditSchema),
    defaultValues: {
      fullName: target.fullName,
      email: target.email,
      cpf: '',
      registration: target.registration ?? '',
      phone: target.phone ?? '',
      roles: target.roles,
      removeCpf: false,
    },
  })
  const save = useMutation({
    mutationFn: ({ removeCpf, ...v }: UserEditForm) =>
      api<UserRow>(`/users/${target.id}`, {
        method: 'PUT',
        // CPF nunca sai na API: null mantém o atual, vazio apaga.
        body: { ...v, cpf: removeCpf ? '' : v.cpf || null, registration: v.registration || null, phone: v.phone || null },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['users'] })
      onDone()
    },
  })

  return (
    <form onSubmit={handleSubmit((v) => save.mutate(v))} noValidate className="space-y-3">
      <Field label="Nome completo" error={errors.fullName?.message}>
        {(p) => <input {...p} className={inputClass} {...register('fullName')} />}
      </Field>
      <Field label="E-mail" error={errors.email?.message}>
        {(p) => <input {...p} type="email" autoComplete="off" className={inputClass} {...register('email')} />}
      </Field>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Matrícula" error={errors.registration?.message}>
          {(p) => <input {...p} className={inputClass} {...register('registration')} />}
        </Field>
        <Field label="Telefone" error={errors.phone?.message}>
          {(p) => <input {...p} type="tel" className={inputClass} {...register('phone')} />}
        </Field>
      </div>
      <Field label="Novo CPF (opcional)" hint="Em branco mantém o que está salvo." error={errors.cpf?.message}>
        {(p) => <input {...p} inputMode="numeric" className={inputClass} {...register('cpf')} />}
      </Field>
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" {...register('removeCpf')} />
        Apagar o CPF salvo
      </label>
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
      <ErrorAlert error={save.error} />
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onDone}>
          Cancelar
        </Button>
        <Button type="submit" loading={save.isPending}>
          Salvar alterações
        </Button>
      </div>
    </form>
  )
}

function ResetPasswordForm({ target, onDone }: { target: UserRow; onDone: () => void }) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ResetPasswordFormValues>({ resolver: zodResolver(resetPasswordSchema), defaultValues: { newPassword: '' } })
  const reset = useMutation({
    mutationFn: (v: ResetPasswordFormValues) => api<void>(`/users/${target.id}/password`, { method: 'POST', body: v }),
    onSuccess: onDone,
  })
  return (
    <form onSubmit={handleSubmit((v) => reset.mutate(v))} noValidate className="space-y-3">
      <p className="text-sm text-slate-700">
        <strong>{target.fullName}</strong> sai de todas as sessões e precisa criar uma senha própria ao entrar.
      </p>
      <Field label="Senha temporária" hint="Mínimo de 8 caracteres. Entregue pessoalmente." error={errors.newPassword?.message}>
        {(p) => <input {...p} type="password" autoComplete="new-password" className={inputClass} {...register('newPassword')} />}
      </Field>
      <ErrorAlert error={reset.error} />
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onDone}>
          Cancelar
        </Button>
        <Button type="submit" loading={reset.isPending}>
          Redefinir senha
        </Button>
      </div>
    </form>
  )
}

export function UsersPage() {
  const { user, isAdmin } = useAuth()
  const queryClient = useQueryClient()
  const [text, setText] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<UserRow | null>(null)
  const [resetting, setResetting] = useState<UserRow | null>(null)
  const [deactivating, setDeactivating] = useState<UserRow | null>(null)
  const [anonymizing, setAnonymizing] = useState<UserRow | null>(null)

  const assignable = assignableRoles(user)
  const canManage = (u: UserRow) => u.roles.every((r) => assignable.includes(r))

  const list = useQuery({
    queryKey: ['users', 'page', query, page],
    queryFn: () => api<Page<UserRow>>('/users', { params: { q: query, page, size: 15, sort: 'fullName' } }),
  })

  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['users'] })

  const deactivate = useMutation({
    mutationFn: (u: UserRow) => api<void>(`/users/${u.id}`, { method: 'DELETE' }),
    onSuccess: () => {
      setDeactivating(null)
      refresh()
    },
  })
  const anonymize = useMutation({
    mutationFn: (u: UserRow) => api<void>(`/users/${u.id}/anonymize`, { method: 'POST' }),
    onSuccess: () => {
      setAnonymizing(null)
      refresh()
    },
  })
  const exportData = useMutation({
    mutationFn: (u: UserRow) => downloadFile(`/users/${u.id}/export`, {}, `dados-usuario-${u.id}.json`),
  })
  const reactivate = useMutation({
    mutationFn: (u: UserRow) => api<UserRow>(`/users/${u.id}/reactivate`, { method: 'POST' }),
    onSuccess: refresh,
  })

  function search(e: FormEvent) {
    e.preventDefault()
    setPage(0)
    setQuery(text.trim())
  }

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
      <form onSubmit={search} role="search" className="mb-4 flex max-w-xl items-end gap-2">
        <div className="flex-1">
          <Field label="Buscar por nome, e-mail ou matrícula">
            {(p) => <input {...p} className={inputClass} value={text} onChange={(e) => setText(e.target.value)} />}
          </Field>
        </div>
        <Button type="submit" variant="secondary">
          <Search className="h-4 w-4" aria-hidden="true" />
          Buscar
        </Button>
      </form>
      <Card>
        {list.isLoading && <Loading />}
        <ErrorAlert error={list.error} />
        <ErrorAlert error={reactivate.error} />
        <ErrorAlert error={exportData.error} />
        {list.data && list.data.content.length === 0 && <Empty>Nenhum usuário encontrado.</Empty>}
        {list.data && list.data.content.length > 0 && (
          <>
            <Table caption="Usuários" head={['Nome', 'E-mail', 'Perfis', 'Cadastro', 'Situação', 'Ações']}>
              {list.data.content.map((u) => (
                <tr key={u.id}>
                  <td className={cellClass}>{u.fullName}</td>
                  <td className={cellClass}>{u.email}</td>
                  <td className={cellClass}>{u.roles.map((r) => ROLE_LABELS[r] ?? r).join(', ')}</td>
                  <td className={cellClass}>{formatDate(u.createdAt)}</td>
                  <td className={cellClass}>{u.active ? <Badge tone="green">Ativo</Badge> : <Badge>Inativo</Badge>}</td>
                  <td className={cellClass}>
                    {canManage(u) && (
                      <div className="flex flex-wrap gap-1">
                        <Button variant="ghost" aria-label={`Editar ${u.fullName}`} onClick={() => setEditing(u)}>
                          Editar
                        </Button>
                        <Button variant="ghost" aria-label={`Redefinir senha de ${u.fullName}`} onClick={() => setResetting(u)}>
                          Redefinir senha
                        </Button>
                        {isAdmin && u.active && u.id !== user?.id && (
                          <Button variant="secondary" onClick={() => setDeactivating(u)}>
                            Desativar
                          </Button>
                        )}
                        {isAdmin && (
                          <Button
                            variant="ghost"
                            aria-label={`Exportar dados de ${u.fullName}`}
                            loading={exportData.isPending && exportData.variables?.id === u.id}
                            onClick={() => exportData.mutate(u)}
                          >
                            Exportar dados
                          </Button>
                        )}
                        {isAdmin && u.id !== user?.id && (
                          <Button variant="ghost" aria-label={`Anonimizar ${u.fullName}`} onClick={() => setAnonymizing(u)}>
                            Anonimizar
                          </Button>
                        )}
                        {isAdmin && !u.active && (
                          <Button variant="secondary" loading={reactivate.isPending && reactivate.variables?.id === u.id} onClick={() => reactivate.mutate(u)}>
                            Reativar
                          </Button>
                        )}
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </Table>
            <Pagination page={list.data.number} totalPages={list.data.totalPages} onChange={setPage} />
          </>
        )}
      </Card>

      <Dialog open={creating} title="Novo usuário" onClose={() => setCreating(false)}>
        <NewUserForm roles={assignable} onDone={() => setCreating(false)} />
      </Dialog>
      <Dialog open={editing !== null} title="Editar usuário" onClose={() => setEditing(null)}>
        {editing && <EditUserForm target={editing} roles={assignable} onDone={() => setEditing(null)} />}
      </Dialog>
      <Dialog open={resetting !== null} title="Redefinir senha" onClose={() => setResetting(null)}>
        {resetting && <ResetPasswordForm target={resetting} onDone={() => setResetting(null)} />}
      </Dialog>
      <ConfirmDialog
        open={deactivating !== null}
        title="Desativar usuário"
        message={
          deactivating && (
            <>
              Desativar <strong>{deactivating.fullName}</strong>? A pessoa sai do sistema na hora e não poderá pegar livros. O
              histórico é mantido, e dá para reativar depois.
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
      <ConfirmDialog
        open={anonymizing !== null}
        title="Anonimizar usuário"
        message={
          anonymizing && (
            <>
              Anonimizar <strong>{anonymizing.fullName}</strong>? Nome, e-mail, CPF, telefone, endereço, avisos e IPs são
              apagados e a conta é fechada. Empréstimos e multas continuam no histórico, sem identificar a pessoa.{' '}
              <strong>Não dá para desfazer.</strong> Só é possível sem empréstimo, multa ou reserva em aberto.
            </>
          )
        }
        confirmLabel="Anonimizar"
        danger
        loading={anonymize.isPending}
        error={anonymize.error}
        onConfirm={() => anonymizing && anonymize.mutate(anonymizing)}
        onCancel={() => setAnonymizing(null)}
      />
    </>
  )
}
