import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { tokenStore } from './lib/api'
import { fakeJwt, mockFetch, page, problem, renderApp } from './test/utils'

describe('acesso e navegação', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('quem não entrou é mandado para o login, mesmo digitando uma rota interna', async () => {
    mockFetch({})
    renderApp('/balcao')

    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument()
  })

  it('token vencido guardado no navegador não vale: volta ao login e é descartado', async () => {
    tokenStore.set(fakeJwt(['LIBRARIAN'], { expiresInSec: -60 }))
    mockFetch({})
    renderApp('/painel')

    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument()
    expect(tokenStore.get()).toBeNull()
  })

  it('leitor não vê o menu da equipe e leva "Acesso negado" ao digitar a rota', async () => {
    mockFetch({})
    renderApp('/balcao', ['STUDENT'])

    expect(await screen.findByRole('alert')).toHaveTextContent('Acesso negado')
    const nav = screen.getByRole('navigation', { name: 'Principal' })
    expect(within(nav).getByRole('link', { name: 'Acervo' })).toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Minha área' })).toBeInTheDocument()
    expect(within(nav).queryByRole('link', { name: 'Balcão' })).not.toBeInTheDocument()
    expect(within(nav).queryByRole('link', { name: 'Relatórios' })).not.toBeInTheDocument()
    expect(within(nav).queryByRole('link', { name: 'Usuários' })).not.toBeInTheDocument()
  })

  it('equipe vê o menu completo e entra no balcão', async () => {
    mockFetch({
      'GET /api/v1/users': () => page([]),
      'GET /api/v1/loans': () => page([]),
    })
    renderApp('/balcao', ['ASSISTANT'])

    expect(await screen.findByRole('heading', { name: 'Balcão' })).toBeInTheDocument()
    const nav = screen.getByRole('navigation', { name: 'Principal' })
    for (const label of ['Painel', 'Balcão', 'Reservas', 'Multas', 'Relatórios', 'Usuários']) {
      expect(within(nav).getByRole('link', { name: label })).toBeInTheDocument()
    }
  })

  it('a raiz leva leitor à Minha área e equipe ao Painel', async () => {
    mockFetch({
      'GET /api/v1/me/loans': () => page([]),
      'GET /api/v1/me/reservations': () => page([]),
      'GET /api/v1/me/fines': () => page([]),
    })
    const reader = renderApp('/', ['TEACHER'])
    expect(await screen.findByRole('heading', { name: 'Minha área' })).toBeInTheDocument()
    reader.unmount()
    tokenStore.clear()

    mockFetch({
      'GET /api/v1/dashboard': () => ({
        totalBooks: 1, totalCopies: 2, activeUsers: 3, activeLoans: 0, overdueLoans: 0, pendingReservations: 0,
        openFinesCount: 0, openFinesAmount: 0, topBooks: [], topCategories: [], movements: [],
      }),
    })
    renderApp('/', ['LIBRARIAN'])
    expect(await screen.findByRole('heading', { name: 'Painel' }, { timeout: 10_000 })).toBeInTheDocument()
  })

  it('sair limpa a sessão e volta ao login', async () => {
    mockFetch({
      'GET /api/v1/me/loans': () => page([]),
      'GET /api/v1/me/reservations': () => page([]),
      'GET /api/v1/me/fines': () => page([]),
    })
    renderApp('/minha-area', ['STUDENT'])
    await screen.findByRole('heading', { name: 'Minha área' })

    await userEvent.setup().click(screen.getByRole('button', { name: 'Sair' }))

    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument()
    expect(tokenStore.get()).toBeNull()
  })

  it('servidor recusando o token (401) derruba a sessão', async () => {
    mockFetch({
      'GET /api/v1/me/loans': () => problem(401, 'expirou'),
      'GET /api/v1/me/reservations': () => page([]),
      'GET /api/v1/me/fines': () => page([]),
    })
    renderApp('/minha-area', ['STUDENT'])

    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument()
    expect(tokenStore.get()).toBeNull()
  })
})
