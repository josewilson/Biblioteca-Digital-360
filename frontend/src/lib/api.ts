// Cliente HTTP fino sobre fetch. Fala com /api/v1 (em desenvolvimento, o Vite repassa ao backend).

const TOKEN_KEY = 'bd360.token'

/**
 * O token fica em sessionStorage: some ao fechar a aba e não é compartilhado entre abas.
 * Risco conhecido: script injetado (XSS) conseguiria lê-lo. Por isso a interface nunca usa
 * dangerouslySetInnerHTML e o React escapa todo texto vindo da API.
 */
export const tokenStore = {
  get: (): string | null => sessionStorage.getItem(TOKEN_KEY),
  set: (token: string): void => sessionStorage.setItem(TOKEN_KEY, token),
  clear: (): void => sessionStorage.removeItem(TOKEN_KEY),
}

let onUnauthorized: (() => void) | null = null

/** Chamado quando o servidor recusa o token (expirado ou inválido): a tela volta ao login. */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler
}

export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: string[]

  constructor(status: number, message: string, fieldErrors: string[] = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

type Params = Record<string, string | number | boolean | null | undefined>

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  params?: Params
}

function buildUrl(path: string, params?: Params): string {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params ?? {})) {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value))
    }
  }
  const qs = query.toString()
  return `/api/v1${path}${qs ? `?${qs}` : ''}`
}

const FALLBACK_MESSAGES: Record<number, string> = {
  400: 'Dados inválidos.',
  401: 'Sessão expirada. Entre novamente.',
  403: 'Você não tem permissão para esta ação.',
  404: 'Não encontrado.',
  422: 'Operação não permitida.',
  429: 'Muitas tentativas. Aguarde alguns minutos.',
}

async function toApiError(res: Response): Promise<ApiError> {
  let detail: string | undefined
  let errors: string[] = []
  try {
    // O backend responde ProblemDetail (RFC 7807): { detail, errors? }
    const body = (await res.json()) as { detail?: string; errors?: string[] }
    detail = body.detail
    errors = body.errors ?? []
  } catch {
    // corpo vazio ou não-JSON: usa a mensagem padrão do status
  }
  return new ApiError(res.status, detail ?? FALLBACK_MESSAGES[res.status] ?? 'Erro inesperado no servidor.', errors)
}

async function send(path: string, options: RequestOptions): Promise<Response> {
  const token = tokenStore.get()
  const headers: Record<string, string> = {}
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  const isForm = options.body instanceof FormData   // upload: o navegador define o Content-Type com o limite do corpo
  if (options.body !== undefined && !isForm) {
    headers['Content-Type'] = 'application/json'
  }

  let res: Response
  try {
    res = await fetch(buildUrl(path, options.params), {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : isForm ? (options.body as FormData) : JSON.stringify(options.body),
    })
  } catch {
    throw new ApiError(0, 'Não foi possível falar com o servidor. Verifique sua conexão.')
  }

  if (!res.ok) {
    if (res.status === 401 && token) {
      tokenStore.clear()
      onUnauthorized?.()
    }
    throw await toApiError(res)
  }
  return res
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const res = await send(path, options)
  return (res.status === 204 ? undefined : await res.json()) as T
}

/** Baixa um arquivo (ex.: CSV) mandando o token, que um simples link não enviaria. */
export async function downloadFile(path: string, params: Params, filename: string): Promise<void> {
  const res = await send(path, { params })
  const url = URL.createObjectURL(await res.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
