import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { tokenStore } from './lib/api'
import { fakeJwt, mockFetch, page, problem, renderApp } from './test/utils'

describe('login', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('não chama o servidor com campos vazios e mostra o que falta', async () => {
    const { fn } = mockFetch({})
    renderApp('/login')

    await userEvent.setup().click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByText('Informe o e-mail.')).toBeInTheDocument()
    expect(screen.getByText('Informe a senha.')).toBeInTheDocument()
    expect(fn).not.toHaveBeenCalled()
  })

  it('e-mail inválido é apontado no próprio campo', async () => {
    mockFetch({})
    renderApp('/login')
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('E-mail'), 'sem-arroba')
    await user.type(screen.getByLabelText('Senha'), 'qualquer')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    const field = screen.getByLabelText('E-mail')
    expect(await screen.findByText('E-mail inválido.')).toBeInTheDocument()
    expect(field).toHaveAttribute('aria-invalid', 'true')
    expect(field).toHaveAccessibleDescription('E-mail inválido.')
  })

  it('entra, guarda o token e abre a área do perfil (aluno vai para Minha área)', async () => {
    const { calls } = mockFetch({
      'POST /api/v1/auth/login': () => ({ accessToken: fakeJwt(['STUDENT']), tokenType: 'Bearer', expiresInSeconds: 3600 }),
      'GET /api/v1/me/loans': () => page([]),
      'GET /api/v1/me/reservations': () => page([]),
      'GET /api/v1/me/fines': () => page([]),
    })
    renderApp('/login')
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('E-mail'), 'ana@x.com')
    await user.type(screen.getByLabelText('Senha'), 'senha-forte-1')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('heading', { name: 'Minha área' })).toBeInTheDocument()
    expect(tokenStore.get()).not.toBeNull()
    const login = calls.find((c) => c.key === 'POST /api/v1/auth/login')!
    expect(JSON.parse(login.init.body as string)).toEqual({ email: 'ana@x.com', password: 'senha-forte-1' })
  })

  it('credenciais erradas mostram a mensagem do servidor e não guardam token', async () => {
    mockFetch({ 'POST /api/v1/auth/login': () => problem(401, 'Credenciais inválidas.') })
    renderApp('/login')
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('E-mail'), 'ana@x.com')
    await user.type(screen.getByLabelText('Senha'), 'errada')
    await user.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Credenciais inválidas.')
    expect(tokenStore.get()).toBeNull()
  })
})
