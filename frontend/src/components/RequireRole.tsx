import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { hasAnyRole, useAuth } from '../lib/auth'
import type { Role } from '../lib/types'

/**
 * Esconde telas de equipe de quem não é da equipe. É conveniência de interface:
 * a proteção de verdade está no servidor (cada endpoint responde 403 a quem não pode).
 */
export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { user } = useAuth()
  if (!hasAnyRole(user, roles)) {
    return (
      <div role="alert" className="mx-auto mt-16 max-w-md text-center">
        <h1 className="text-xl font-bold">Acesso negado</h1>
        <p className="mt-2 text-sm text-slate-600">Seu perfil não tem permissão para ver esta página.</p>
        <Link to="/" className="mt-4 inline-block text-sm font-medium text-brand-700 underline">
          Voltar ao início
        </Link>
      </div>
    )
  }
  return <>{children}</>
}
