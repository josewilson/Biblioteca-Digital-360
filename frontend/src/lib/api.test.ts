import { ApiError, api, setUnauthorizedHandler, tokenStore } from './api'
import { mockFetch, problem } from '../test/utils'

describe('api', () => {
  afterEach(() => {
    setUnauthorizedHandler(null)
    vi.unstubAllGlobals()
  })

  it('manda o token e a query string, ignorando parâmetros vazios', async () => {
    tokenStore.set('abc')
    const { calls } = mockFetch({ 'GET /api/v1/books': () => ({ ok: true }) })

    await api('/books', { params: { title: 'dom', page: 2, vazio: '', nulo: null, indefinido: undefined } })

    const headers = calls[0]!.init.headers as Record<string, string>
    expect(headers.Authorization).toBe('Bearer abc')
    const fetched = (fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0]![0] as string
    expect(fetched).toBe('/api/v1/books?title=dom&page=2')
  })

  it('envia JSON no corpo', async () => {
    const { calls } = mockFetch({ 'POST /api/v1/loans': () => ({ id: 1 }) })

    await api('/loans', { method: 'POST', body: { userId: 7, copyId: 3 } })

    expect(calls[0]!.init.body).toBe('{"userId":7,"copyId":3}')
    expect((calls[0]!.init.headers as Record<string, string>)['Content-Type']).toBe('application/json')
  })

  it('transforma o ProblemDetail do servidor numa mensagem para o usuário', async () => {
    mockFetch({ 'POST /api/v1/loans': () => problem(422, 'Limite de 3 empréstimos simultâneos atingido.') })

    const error = await api('/loans', { method: 'POST', body: {} }).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(422)
    expect((error as ApiError).message).toBe('Limite de 3 empréstimos simultâneos atingido.')
  })

  it('usa mensagem padrão quando o erro não vem em JSON', async () => {
    mockFetch({ 'GET /api/v1/x': () => new Response('<html>', { status: 403 }) })

    await expect(api('/x')).rejects.toThrow('Você não tem permissão para esta ação.')
  })

  it('avisa quando o servidor está fora do ar', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    await expect(api('/x')).rejects.toMatchObject({ status: 0 })
  })

  it('token recusado (401) limpa a sessão e dispara a saída', async () => {
    tokenStore.set('vencido')
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    mockFetch({ 'GET /api/v1/books': () => problem(401, 'x') })

    await expect(api('/books')).rejects.toBeInstanceOf(ApiError)

    expect(tokenStore.get()).toBeNull()
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('401 do login (sem token) não dispara a saída', async () => {
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    mockFetch({ 'POST /api/v1/auth/login': () => problem(401, 'Credenciais inválidas.') })

    await expect(api('/auth/login', { method: 'POST', body: {} })).rejects.toThrow('Credenciais inválidas.')

    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('204 devolve vazio sem tentar ler JSON', async () => {
    mockFetch({ 'DELETE /api/v1/users/5': () => new Response(null, { status: 204 }) })

    await expect(api('/users/5', { method: 'DELETE' })).resolves.toBeUndefined()
  })
})
