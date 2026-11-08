import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BookForm } from './components/BookForm'
import { mockFetch, page, problem, renderApp, renderWithProviders } from './test/utils'

const ana = { id: 7, fullName: 'Ana Souza', email: 'ana@x.com', registration: null, phone: null, active: true, roles: ['STUDENT'], createdAt: '2026-03-01T10:00:00Z' }

const domCasmurro = {
  id: 1, title: 'Dom Casmurro', isbn: null, edition: null, publicationYear: 1899, language: null, description: null,
  coverUrl: null, publisher: null, authors: [{ id: 1, name: 'Machado de Assis' }], categories: [],
  copies: [
    { id: 11, barcode: 'C-1', kind: 'CONSULTA', status: 'DISPONIVEL', condition: 'BOM', location: null },
    { id: 12, barcode: 'E-1', kind: 'CIRCULANTE', status: 'DISPONIVEL', condition: 'BOM', location: null },
  ],
}

afterEach(() => vi.unstubAllGlobals())

describe('painel', () => {
  it('mostra os indicadores e destaca atrasos e multas', async () => {
    mockFetch({
      'GET /api/v1/dashboard': () => ({
        totalBooks: 12, totalCopies: 40, activeUsers: 30, activeLoans: 8, overdueLoans: 2, pendingReservations: 3,
        openFinesCount: 1, openFinesAmount: 3, topBooks: [{ name: 'Dom Casmurro', total: 5 }], topCategories: [],
        movements: [{ day: '2026-03-01', loans: 2, returns: 1 }],
      }),
    })
    renderApp('/painel', ['LIBRARIAN'])

    // o painel é carregado sob demanda (gráficos): a primeira importação é mais lenta que o padrão de 1 s
    expect(await screen.findByText('Obras cadastradas', {}, { timeout: 10_000 })).toBeInTheDocument()
    const overdue = screen.getByText('Empréstimos atrasados').nextElementSibling
    expect(overdue).toHaveTextContent('2')
    expect(overdue).toHaveClass('text-red-700')
    expect(screen.getByText('Valor em aberto').nextElementSibling).toHaveTextContent('R$ 3,00')
    expect(screen.getByRole('img', { name: /Obras mais emprestadas: Dom Casmurro, 5/ })).toBeInTheDocument()
  })

  it('mostra o erro do servidor em vez de uma tela vazia', async () => {
    mockFetch({ 'GET /api/v1/dashboard': () => problem(500, 'Erro interno.') })
    renderApp('/painel', ['LIBRARIAN'])

    expect(await screen.findByRole('alert', {}, { timeout: 10_000 })).toHaveTextContent('Erro interno.')
  })
})

describe('formulário de obra', () => {
  const lookups = {
    'GET /api/v1/authors': () => [{ id: 1, name: 'Machado de Assis' }],
    'GET /api/v1/publishers': () => [],
    'GET /api/v1/categories': () => [],
  }

  it('recusa obra sem exemplar de consulta, sem chamar o servidor', async () => {
    const { calls } = mockFetch(lookups)
    renderWithProviders(<BookForm onCreated={vi.fn()} onCancel={vi.fn()} />)
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Título'), 'Dom Casmurro')
    await user.type(screen.getByLabelText('Código do exemplar 1'), 'A1')
    await user.type(screen.getByLabelText('Código do exemplar 2'), 'A2')
    await user.selectOptions(screen.getAllByLabelText('Tipo')[0]!, 'CIRCULANTE')   // agora nenhum é de consulta
    await user.click(screen.getByRole('button', { name: 'Cadastrar obra' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('pelo menos 1 exemplar de consulta')
    expect(calls.some((c) => c.key === 'POST /api/v1/books')).toBe(false)
  })

  it('cadastra com consulta + emprestável e manda o corpo no formato da API', async () => {
    const onCreated = vi.fn()
    const { calls } = mockFetch({ ...lookups, 'POST /api/v1/books': () => domCasmurro })
    renderWithProviders(<BookForm onCreated={onCreated} onCancel={vi.fn()} />)
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Título'), 'Dom Casmurro')
    await user.type(screen.getByLabelText('Ano de publicação'), '1899')
    await user.click(await screen.findByLabelText('Machado de Assis'))
    await user.type(screen.getByLabelText('Código do exemplar 1'), 'C-1')
    await user.type(screen.getByLabelText('Código do exemplar 2'), 'E-1')
    await user.click(screen.getByRole('button', { name: 'Cadastrar obra' }))

    await waitFor(() => expect(onCreated).toHaveBeenCalledOnce())
    const body = JSON.parse(calls.find((c) => c.key === 'POST /api/v1/books')!.init.body as string)
    expect(body).toMatchObject({ title: 'Dom Casmurro', publicationYear: 1899, authorIds: [1], isbn: null })
    expect(body.copies.map((c: { kind: string }) => c.kind)).toEqual(['CONSULTA', 'CIRCULANTE'])
  })

  it('mostra a recusa do servidor (ex.: código repetido)', async () => {
    mockFetch({ ...lookups, 'POST /api/v1/books': () => problem(422, 'Já existe exemplar com um desses códigos.') })
    renderWithProviders(<BookForm onCreated={vi.fn()} onCancel={vi.fn()} />)
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Título'), 'Dom Casmurro')
    await user.type(screen.getByLabelText('Código do exemplar 1'), 'C-1')
    await user.type(screen.getByLabelText('Código do exemplar 2'), 'E-1')
    await user.click(screen.getByRole('button', { name: 'Cadastrar obra' }))

    expect(await screen.findByText('Já existe exemplar com um desses códigos.')).toBeInTheDocument()
  })
})

describe('balcão', () => {
  function openCounter(extra: Parameters<typeof mockFetch>[0] = {}) {
    return mockFetch({
      'GET /api/v1/users': () => page([ana]),
      'GET /api/v1/loans': () => page([]),
      'GET /api/v1/books': () => page([domCasmurro]),
      ...extra,
    })
  }

  async function pickReaderAndCopy(user: ReturnType<typeof userEvent.setup>) {
    await screen.findByRole('option', { name: /Ana Souza/ })
    await user.selectOptions(screen.getByLabelText('Leitor'), '7')
    await user.type(screen.getByLabelText('Buscar obra pelo título'), 'dom')
    await user.click(screen.getByRole('button', { name: 'Buscar' }))
    await user.click(await screen.findByRole('button', { name: 'Escolher' }))
  }

  it('empresta: só oferece exemplar emprestável e manda leitor e exemplar certos', async () => {
    const { calls } = openCounter({
      'POST /api/v1/loans': () => ({ id: 90, userId: 7, copyId: 12, barcode: 'E-1', bookTitle: 'Dom Casmurro', loanedAt: '2026-03-02T15:00:00Z', dueAt: '2026-03-16T15:00:00Z', returnedAt: null }),
    })
    renderApp('/balcao', ['ASSISTANT'])
    const user = userEvent.setup()
    await pickReaderAndCopy(user)

    expect(screen.queryByRole('radio', { name: /C-1/ })).not.toBeInTheDocument()   // consulta nunca sai
    expect(screen.getByRole('button', { name: 'Emprestar' })).toBeDisabled()       // falta escolher o exemplar
    await user.click(screen.getByRole('radio', { name: /E-1/ }))
    await user.click(screen.getByRole('button', { name: 'Emprestar' }))

    expect(await screen.findByText(/Empréstimo registrado/)).toBeInTheDocument()
    const post = calls.find((c) => c.key === 'POST /api/v1/loans')!
    expect(JSON.parse(post.init.body as string)).toEqual({ userId: 7, copyId: 12 })
  })

  it('mostra a regra de negócio recusada pelo servidor', async () => {
    openCounter({ 'POST /api/v1/loans': () => problem(422, 'Limite de 3 empréstimos simultâneos atingido.') })
    renderApp('/balcao', ['ASSISTANT'])
    const user = userEvent.setup()
    await pickReaderAndCopy(user)

    await user.click(screen.getByRole('radio', { name: /E-1/ }))
    await user.click(screen.getByRole('button', { name: 'Emprestar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Limite de 3 empréstimos simultâneos atingido.')
  })

  it('devolução pede confirmação e avisa da multa gerada', async () => {
    const loan = { id: 90, userId: 7, copyId: 12, barcode: 'E-1', bookTitle: 'Dom Casmurro', loanedAt: '2026-01-01T15:00:00Z', dueAt: '2026-01-15T15:00:00Z', returnedAt: null }
    const { calls } = openCounter({
      'GET /api/v1/loans': () => page([loan]),
      'POST /api/v1/loans/90/return': () => ({
        loan: { ...loan, returnedAt: '2026-01-18T15:00:00Z' },
        fine: { id: 5, loanId: 90, userId: 7, amount: 3, daysLate: 3, status: 'ABERTA', waiverReason: null },
      }),
    })
    renderApp('/balcao', ['LIBRARIAN'])
    const user = userEvent.setup()

    const row = (await screen.findByText('E-1')).closest('tr')!
    expect(within(row).getByText('Atrasado')).toBeInTheDocument()
    expect(within(row).getByRole('button', { name: 'Renovar' })).toBeDisabled()   // atrasado não renova
    await user.click(within(row).getByRole('button', { name: 'Devolver' }))

    expect(calls.some((c) => c.key === 'POST /api/v1/loans/90/return')).toBe(false)   // ainda só pediu confirmação
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Registrar devolução' }))

    expect(await screen.findByText(/multa de/)).toHaveTextContent('R$ 3,00')
  })
})

describe('multas', () => {
  const fine = { id: 5, loanId: 90, userId: 7, amount: 3, daysLate: 3, status: 'ABERTA', waiverReason: null }
  const handlers = () => ({
    'GET /api/v1/users': () => page([ana]),
    'GET /api/v1/fines': () => page([fine]),
  })

  it('recebe o pagamento e mostra o comprovante', async () => {
    const { calls } = mockFetch({
      ...handlers(),
      'POST /api/v1/fines/5/payments': () => ({ id: 33, fineId: 5, loanId: 90, userId: 7, amount: 3, method: 'PIX', receivedBy: 1, paidAt: '2026-03-02T15:00:00Z' }),
    })
    renderApp('/multas', ['ASSISTANT'])
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Receber' }))
    await user.selectOptions(await screen.findByLabelText('Forma de pagamento'), 'PIX')
    await user.click(screen.getByRole('button', { name: 'Confirmar pagamento' }))

    expect(await screen.findByText('Comprovante de pagamento')).toBeInTheDocument()
    expect(screen.getByText('Pix')).toBeInTheDocument()
    const body = JSON.parse(calls.find((c) => c.key === 'POST /api/v1/fines/5/payments')!.init.body as string)
    expect(body).toEqual({ method: 'PIX' })   // o valor nunca é enviado: o servidor cobra o integral
  })

  it('auxiliar não vê "Isentar"; bibliotecário vê e exige justificativa', async () => {
    mockFetch(handlers())
    const assistant = renderApp('/multas', ['ASSISTANT'])
    await screen.findByRole('button', { name: 'Receber' })
    expect(screen.queryByRole('button', { name: 'Isentar' })).not.toBeInTheDocument()
    assistant.unmount()

    const { calls } = mockFetch({ ...handlers(), 'POST /api/v1/fines/5/waive': () => ({ ...fine, status: 'ISENTA' }) })
    renderApp('/multas', ['LIBRARIAN'])
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Isentar' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Isentar multa' }))

    expect(await within(dialog).findByText('A justificativa é obrigatória.')).toBeInTheDocument()
    expect(calls.some((c) => c.key === 'POST /api/v1/fines/5/waive')).toBe(false)
  })
})

describe('acervo para o leitor', () => {
  it('leitor vê o botão de reservar e a recusa do servidor; equipe não vê o botão', async () => {
    mockFetch({
      'GET /api/v1/books/1': () => domCasmurro,
      'POST /api/v1/me/reservations': () => problem(422, 'Há exemplar disponível: faça o empréstimo direto, sem reserva.'),
    })
    const reader = renderApp('/acervo/1', ['STUDENT'])
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Reservar esta obra' }))
    expect(await screen.findByText(/faça o empréstimo direto/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Acrescentar exemplar' })).not.toBeInTheDocument()
    reader.unmount()

    mockFetch({ 'GET /api/v1/books/1': () => domCasmurro })
    renderApp('/acervo/1', ['LIBRARIAN'])
    expect(await screen.findByRole('button', { name: 'Acrescentar exemplar' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reservar esta obra' })).not.toBeInTheDocument()
  })

  it('baixa do exemplar pede confirmação e mostra a regra do último exemplar de consulta', async () => {
    const { calls } = mockFetch({
      'GET /api/v1/books/1': () => domCasmurro,
      'PATCH /api/v1/copies/11/status': () => problem(422, 'Esta é o único exemplar de consulta da obra. Cadastre outro exemplar de consulta antes.'),
    })
    renderApp('/acervo/1', ['LIBRARIAN'])
    const user = userEvent.setup()

    await user.selectOptions(await screen.findByLabelText('Situação do exemplar C-1'), 'BAIXADO')
    expect(calls.some((c) => c.key === 'PATCH /api/v1/copies/11/status')).toBe(false)
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Confirmar' }))

    expect(await screen.findByText(/único exemplar de consulta/)).toBeInTheDocument()
  })
})
