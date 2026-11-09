import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { changePasswordSchema, type ChangePasswordForm } from '../schemas'
import { Alert, Button, Card, ErrorAlert, Field, PageHeader, inputClass } from '../components/ui'

export function ChangePasswordPage() {
  const { user, adoptToken } = useAuth()
  const navigate = useNavigate()
  const forced = user?.mustChangePassword ?? false
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ChangePasswordForm>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmation: '' },
  })

  const change = useMutation({
    mutationFn: ({ currentPassword, newPassword }: ChangePasswordForm) =>
      api<{ accessToken: string }>('/auth/password', { method: 'POST', body: { currentPassword, newPassword } }),
    onSuccess: ({ accessToken }) => {
      adoptToken(accessToken)   // o token anterior acabou de deixar de valer
      reset()
      if (forced) void navigate('/', { replace: true })
    },
  })

  return (
    <>
      <PageHeader title="Minha senha" subtitle="Ao trocar, as outras sessões abertas com esta conta são encerradas." />
      <div className="max-w-md">
        <Card>
          <form onSubmit={handleSubmit((v) => change.mutate(v))} noValidate className="space-y-3">
            {forced && <Alert tone="amber">Esta senha é temporária. Crie uma senha sua para continuar.</Alert>}
            <Field label="Senha atual" error={errors.currentPassword?.message}>
              {(p) => <input {...p} type="password" autoComplete="current-password" className={inputClass} {...register('currentPassword')} />}
            </Field>
            <Field label="Nova senha" hint="Mínimo de 8 caracteres." error={errors.newPassword?.message}>
              {(p) => <input {...p} type="password" autoComplete="new-password" className={inputClass} {...register('newPassword')} />}
            </Field>
            <Field label="Repita a nova senha" error={errors.confirmation?.message}>
              {(p) => <input {...p} type="password" autoComplete="new-password" className={inputClass} {...register('confirmation')} />}
            </Field>
            <ErrorAlert error={change.error} />
            {change.isSuccess && !forced && <Alert tone="green">Senha alterada.</Alert>}
            <Button type="submit" loading={change.isPending}>
              Trocar senha
            </Button>
          </form>
        </Card>
      </div>
    </>
  )
}
