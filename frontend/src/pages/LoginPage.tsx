import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../lib/auth'
import { loginSchema, type LoginForm } from '../schemas'
import { Button, ErrorAlert, Field, inputClass } from '../components/ui'

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [error, setError] = useState<unknown>(null)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({ resolver: zodResolver(loginSchema), defaultValues: { email: '', password: '' } })

  const from = (location.state as { from?: string } | null)?.from ?? '/'
  if (user) return <Navigate to={from} replace />

  async function onSubmit(values: LoginForm) {
    setError(null)
    try {
      await login(values.email, values.password)
      navigate(from, { replace: true })
    } catch (e) {
      setError(e)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center p-4">
      <form
        onSubmit={handleSubmit(onSubmit)}
        noValidate
        aria-labelledby="login-titulo"
        className="w-full max-w-sm space-y-5 rounded border border-l-[6px] border-slate-200 border-l-brand-600 bg-white p-7"
      >
        <div>
          <h1 id="login-titulo" className="text-3xl font-semibold tracking-tight">
            Biblioteca Digital 360
          </h1>
          <p className="mt-1 text-slate-600">Entre com o e-mail e a senha cadastrados na biblioteca.</p>
        </div>
        <ErrorAlert error={error} />
        <Field label="E-mail" error={errors.email?.message}>
          {(props) => <input {...props} type="email" autoComplete="username" className={inputClass} {...register('email')} />}
        </Field>
        <Field label="Senha" error={errors.password?.message}>
          {(props) => (
            <input {...props} type="password" autoComplete="current-password" className={inputClass} {...register('password')} />
          )}
        </Field>
        <Button type="submit" className="w-full" loading={isSubmitting}>
          Entrar
        </Button>
        <p className="text-center text-sm">
          <Link to="/esqueci-senha" className="font-medium text-brand-700 underline">
            Esqueci a senha
          </Link>
        </p>
        <p className="text-center text-sm">
          <Link to="/privacidade" className="font-medium text-brand-700 underline">
            Privacidade e seus dados
          </Link>
        </p>
      </form>
    </main>
  )
}
