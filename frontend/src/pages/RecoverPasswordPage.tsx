import type { ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { api } from '../lib/api'
import { forgotSchema, newPasswordSchema, type ForgotForm, type NewPasswordForm } from '../schemas'
import { Alert, Button, ErrorAlert, Field, inputClass } from '../components/ui'

/** Moldura das duas telas públicas de recuperação (mesma aparência do login). */
function Frame({ id, title, intro, children }: { id: string; title: string; intro: string; children: ReactNode }) {
  return (
    <main className="flex min-h-screen items-center justify-center p-4">
      <div className="w-full max-w-sm space-y-5 rounded border border-l-[6px] border-slate-200 border-l-brand-600 bg-white p-7">
        <div>
          <h1 id={id} className="text-3xl font-semibold tracking-tight">
            {title}
          </h1>
          <p className="mt-1 text-slate-600">{intro}</p>
        </div>
        {children}
        <p className="text-center text-sm">
          <Link to="/login" className="font-medium text-brand-700 underline">
            Voltar ao login
          </Link>
        </p>
      </div>
    </main>
  )
}

/** Pede o link. A resposta é sempre a mesma, exista o e-mail ou não. */
export function ForgotPasswordPage() {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ForgotForm>({ resolver: zodResolver(forgotSchema), defaultValues: { email: '' } })
  const send = useMutation({ mutationFn: (v: ForgotForm) => api('/auth/forgot', { method: 'POST', body: v }) })

  return (
    <Frame id="esqueci-titulo" title="Esqueci a senha" intro="Informe o e-mail da sua conta. Enviaremos um link para criar uma senha nova.">
      {send.isSuccess ? (
        <Alert tone="green">
          Se este e-mail tiver conta, o link já foi enviado. Ele vale por 1 hora. Confira também o spam.
        </Alert>
      ) : (
        <form onSubmit={handleSubmit((v) => send.mutate(v))} noValidate aria-labelledby="esqueci-titulo" className="space-y-5">
          <ErrorAlert error={send.error} />
          <Field label="E-mail" error={errors.email?.message}>
            {(p) => <input {...p} type="email" autoComplete="username" className={inputClass} {...register('email')} />}
          </Field>
          <Button type="submit" className="w-full" loading={send.isPending}>
            Enviar link
          </Button>
        </form>
      )}
    </Frame>
  )
}

/** Aberta pelo link do e-mail (?token=...). */
export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<NewPasswordForm>({ resolver: zodResolver(newPasswordSchema), defaultValues: { newPassword: '', confirmation: '' } })
  const reset = useMutation({
    mutationFn: (v: NewPasswordForm) => api('/auth/reset', { method: 'POST', body: { token, newPassword: v.newPassword } }),
  })

  return (
    <Frame id="redefinir-titulo" title="Redefinir senha" intro="Crie a senha que vai usar daqui para frente.">
      {reset.isSuccess ? (
        <Alert tone="green">Senha alterada. Já pode entrar com a senha nova.</Alert>
      ) : !token ? (
        <Alert tone="red">
          Link incompleto. <Link to="/esqueci-senha" className="underline">Peça um novo</Link>.
        </Alert>
      ) : (
        <form onSubmit={handleSubmit((v) => reset.mutate(v))} noValidate aria-labelledby="redefinir-titulo" className="space-y-5">
          <ErrorAlert error={reset.error} />
          <Field label="Nova senha" hint="Mínimo de 8 caracteres." error={errors.newPassword?.message}>
            {(p) => <input {...p} type="password" autoComplete="new-password" className={inputClass} {...register('newPassword')} />}
          </Field>
          <Field label="Repita a nova senha" error={errors.confirmation?.message}>
            {(p) => <input {...p} type="password" autoComplete="new-password" className={inputClass} {...register('confirmation')} />}
          </Field>
          <Button type="submit" className="w-full" loading={reset.isPending}>
            Salvar senha nova
          </Button>
        </form>
      )}
    </Frame>
  )
}
