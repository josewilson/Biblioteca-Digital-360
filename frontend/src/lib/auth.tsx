import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, setUnauthorizedHandler, tokenStore } from './api'
import { ADMIN_ROLES, STAFF_ROLES, WAIVER_ROLES, type Role } from './types'

export interface AuthUser {
  id: number
  roles: Role[]
  /** Fim da validade do token, em milissegundos (epoch). */
  expiresAt: number
  /** Senha temporária: o servidor não dá perfil nenhum até ela ser trocada. */
  mustChangePassword: boolean
}

/**
 * Lê id e perfis do payload do JWT. Isto serve só para ajustar a interface (menus, botões):
 * quem decide o que é permitido é sempre o servidor, que valida a assinatura.
 */
export function decodeToken(token: string, now: number = Date.now()): AuthUser | null {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    const json = decodeURIComponent(
      atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    )
    const claims = JSON.parse(json) as { sub?: string; roles?: Role[]; exp?: number; mustChangePassword?: boolean }
    if (!claims.sub || !claims.exp) return null
    const user: AuthUser = {
      id: Number(claims.sub),
      roles: claims.roles ?? [],
      expiresAt: claims.exp * 1000,
      mustChangePassword: claims.mustChangePassword === true,
    }
    return user.expiresAt > now ? user : null
  } catch {
    return null
  }
}

export function hasAnyRole(user: AuthUser | null, roles: Role[]): boolean {
  return !!user && user.roles.some((r) => roles.includes(r))
}

interface AuthContextValue {
  user: AuthUser | null
  isStaff: boolean
  canWaive: boolean
  isAdmin: boolean
  login: (email: string, password: string) => Promise<void>
  logout: () => void
  /** Assume o token novo devolvido pela troca de senha (o anterior deixa de valer). */
  adoptToken: (token: string) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function loadUser(): AuthUser | null {
  const token = tokenStore.get()
  const user = token ? decodeToken(token) : null
  if (token && !user) tokenStore.clear()
  return user
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(loadUser)

  const logout = useCallback(() => {
    tokenStore.clear()
    setUser(null)
  }, [])

  const adoptToken = useCallback((token: string) => {
    tokenStore.set(token)
    setUser(decodeToken(token))
  }, [])

  const login = useCallback(
    async (email: string, password: string) => {
      const { accessToken } = await api<{ accessToken: string }>('/auth/login', {
        method: 'POST',
        body: { email, password },
      })
      adoptToken(accessToken)
    },
    [adoptToken],
  )

  // Servidor recusou o token -> sai da sessão. Também sai sozinho quando o token vence.
  useEffect(() => {
    setUnauthorizedHandler(logout)
    return () => setUnauthorizedHandler(null)
  }, [logout])

  useEffect(() => {
    if (!user) return
    const ms = Math.min(Math.max(user.expiresAt - Date.now(), 0), 2_000_000_000)
    const timer = setTimeout(logout, ms)
    return () => clearTimeout(timer)
  }, [user, logout])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isStaff: hasAnyRole(user, STAFF_ROLES),
      canWaive: hasAnyRole(user, WAIVER_ROLES),
      isAdmin: hasAnyRole(user, ADMIN_ROLES),
      login,
      logout,
      adoptToken,
    }),
    [user, login, logout, adoptToken],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth deve ser usado dentro de <AuthProvider>')
  return ctx
}
