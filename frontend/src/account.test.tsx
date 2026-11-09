import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { tokenStore } from './lib/api'
import { fakeJwt, mockFetch, page, problem, renderApp } from './test/utils'

const STAFF_JWT = fakeJwt(['LIBRARIAN'])

describe('senha', () => {
  it('senha temporária prende na troca; ao trocar, assume o token novo e libera o sistema', async () => {
    tokenStore.set(fakeJwt(['STUDENT'], { mustChangePassword: true }))
    const newToken = fakeJwt(['STUDENT'])
    const { calls } = mockFetch({
      'POST /api/v1/auth/password': () => ({ accessToken: newToken }),
      'GET /api/v1/me/loans': () => page([]),
      'GET /api/v1/me/reservations': () => page([]),
      'GET /api/v1/me/fines': () => page([]),
    })
    const { router } = renderApp('/acervo')
    const user = userEvent.setup()

    expect(await screen.findByText(/senha é temporária/)).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/minha-senha')

    await user.type(screen.getByLabelText('Senha atual'), 'temporaria-1')
    await user.type(screen.getByLabelText('Nova senha'), 'definitiva-1')
    await user.type(screen.getByLabelText('Repita a nova senha'), 'diferente-1')
    await user.click(screen.getByRole('button', { name: 'Trocar senha' }))
    expect(await screen.findByText('As senhas não são iguais.')).toBeInTheDocument()
    expect(calls.some((c) => c.key === 'POST /api/v1/auth/password')).toBe(false)

    await user.clear(screen.getByLabelText('Repita a nova senha'))
    await user.type(screen.getByLabelText('Repita a nova senha'), 'definitiva-1')
    await user.click(screen.getByRole('button', { name: 'Trocar senha' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/minha-area'))
    expect(tokenStore.get()).toBe(newToken)
  })

  it('senha atual errada mostra a mensagem do servidor e mantém a sessão', async () => {
    tokenStore.set(STAFF_JWT)
    mockFetch({ 'POST /api/v1/auth/password': () => problem(422, 'Senha atual incorreta.') })
    renderApp('/minha-senha')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Senha atual'), 'errada')
    await user.type(screen.getByLabelText('Nova senha'), 'definitiva-1')
    await user.type(screen.getByLabelText('Repita a nova senha'), 'definitiva-1')
    await user.click(screen.getByRole('button', { name: 'Trocar senha' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Senha atual incorreta.')
    expect(tokenStore.get()).toBe(STAFF_JWT)
  })
})

describe('edição', () => {
  const book = {
    id: 1, title: 'Dom Casmuro', isbn: null, edition: null, publicationYear: 1899, language: null, description: null,
    coverUrl: null, publisherId: 3, publisher: 'Garnier', authors: [{ id: 2, name: 'Machado' }], categories: [],
    copies: [
      { id: 10, barcode: 'C-1', kind: 'CONSULTA', status: 'DISPONIVEL', condition: 'BOM', location: null },
      { id: 11, barcode: 'E-1', kind: 'CIRCULANTE', status: 'DISPONIVEL', condition: 'BOM', location: null },
    ],
  }

  it('edita a obra: traz os dados atuais e não envia exemplares', async () => {
    const { calls } = mockFetch({
      'GET /api/v1/books/1': () => book,
      'GET /api/v1/authors': () => [{ id: 2, name: 'Machado' }],
      'GET /api/v1/publishers': () => [{ id: 3, name: 'Garnier' }],
      'GET /api/v1/categories': () => [],
      'PUT /api/v1/books/1': () => ({ ...book, title: 'Dom Casmurro' }),
    })
    renderApp('/acervo/1', ['LIBRARIAN'])
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Editar obra' }))
    const title = await screen.findByLabelText('Título')
    expect(title).toHaveValue('Dom Casmuro')
    expect(screen.queryByText('Adicionar exemplar')).not.toBeInTheDocument()
    await user.clear(title)
    await user.type(title, 'Dom Casmurro')
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }))

    await waitFor(() => expect(calls.some((c) => c.key === 'PUT /api/v1/books/1')).toBe(true))
    const body = JSON.parse(calls.find((c) => c.key === 'PUT /api/v1/books/1')!.init.body as string)
    expect(body).toMatchObject({ title: 'Dom Casmurro', publisherId: 3, authorIds: [2], publicationYear: 1899 })
    expect(body).not.toHaveProperty('copies')
  })

  it('leitor não vê os botões de edição', async () => {
    mockFetch({ 'GET /api/v1/books/1': () => book })
    renderApp('/acervo/1', ['STUDENT'])
    await screen.findByRole('heading', { name: 'Dom Casmuro', level: 1 })
    expect(screen.queryByRole('button', { name: 'Editar obra' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Editar exemplar C-1' })).not.toBeInTheDocument()
  })
})

describe('regras de empréstimo', () => {
  const policy = { role: 'STUDENT', maxLoans: 3, loanDays: 14, maxRenewals: 2, renewalDays: 7, reservationPickupDays: 3, dailyRate: 1 }

  it('administrador salva com vírgula e o valor vai como texto decimal', async () => {
    const { calls } = mockFetch({
      'GET /api/v1/policies': () => [policy],
      'PUT /api/v1/policies/STUDENT': () => ({ ...policy, dailyRate: 1.5 }),
    })
    renderApp('/regras', ['ADMIN_INSTITUTION'])
    const user = userEvent.setup()

    const rate = await screen.findByLabelText('Multa por dia de atraso (R$)')
    expect(rate).toHaveValue('1,00')
    await user.clear(rate)
    await user.type(rate, '1,50')
    await user.click(screen.getByRole('button', { name: 'Salvar regras de Aluno' }))

    expect(await screen.findByText('Regras de Aluno salvas.')).toBeInTheDocument()
    const body = JSON.parse(calls.find((c) => c.key === 'PUT /api/v1/policies/STUDENT')!.init.body as string)
    expect(body).toMatchObject({ dailyRate: '1.50', loanDays: 14 })
  })

  it('bibliotecário não entra na tela de regras', async () => {
    mockFetch({})
    renderApp('/regras', ['LIBRARIAN'])
    expect(await screen.findByRole('alert')).toHaveTextContent('Acesso negado')
  })
})
