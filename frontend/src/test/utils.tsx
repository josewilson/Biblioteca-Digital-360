import type { ReactElement } from 'react'
import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createMemoryRouter } from 'react-router'
import { routes } from '../App'
import { tokenStore } from '../lib/api'
import { AuthProvider } from '../lib/auth'
import type { Role } from '../lib/types'

const b64 = (o: unknown) => btoa(JSON.stringify(o)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_')

/** JWT de mentira (sem assinatura válida): a interface só lê id, perfis e validade. */
export function fakeJwt(roles: Role[], { sub = '1', expiresInSec = 3600, mustChangePassword = false } = {}): string {
  return `${b64({ alg: 'HS256' })}.${b64({ sub, roles, mustChangePassword, exp: Math.floor(Date.now() / 1000) + expiresInSec })}.assinatura`
}

type Handler = (init: RequestInit) => unknown | Response

/**
 * Troca o fetch por um mapa "MÉTODO /caminho" -> resposta. Caminho sem query string.
 * Rota não prevista responde 404, o que faz o teste falhar de forma visível.
 */
export function mockFetch(handlers: Record<string, Handler>) {
  const calls: { key: string; init: RequestInit; search: string }[] = []
  const fn = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = new URL(String(input), 'http://localhost')
    const key = `${init.method ?? 'GET'} ${url.pathname}`
    calls.push({ key, init, search: url.search })
    const handler = handlers[key]
    if (!handler) {
      return new Response(JSON.stringify({ detail: `sem mock para ${key}` }), { status: 404 })
    }
    const result = handler(init)
    if (result instanceof Response) return result
    return new Response(JSON.stringify(result), { status: 200, headers: { 'Content-Type': 'application/json' } })
  })
  vi.stubGlobal('fetch', fn)
  return { fn, calls }
}

export function problem(status: number, detail: string): Response {
  return new Response(JSON.stringify({ detail }), { status, headers: { 'Content-Type': 'application/problem+json' } })
}

export const page = <T,>(content: T[]) => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  number: 0,
  size: 10,
})

function testQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
}

export function renderWithProviders(ui: ReactElement) {
  return render(
    <QueryClientProvider client={testQueryClient()}>
      <AuthProvider>{ui}</AuthProvider>
    </QueryClientProvider>,
  )
}

/** Renderiza o app inteiro (rotas reais) numa URL, opcionalmente já logado com os perfis dados. */
export function renderApp(path: string, roles?: Role[]) {
  if (roles) tokenStore.set(fakeJwt(roles))
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  return {
    router,
    ...render(
      <QueryClientProvider client={testQueryClient()}>
        <AuthProvider>
          <RouterProvider router={router} />
        </AuthProvider>
      </QueryClientProvider>,
    ),
  }
}
