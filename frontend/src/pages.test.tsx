import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { Book } from './lib/types'
import { BookForm } from './components/BookForm'
import { mockFetch, page, problem, renderApp, renderWithProviders } from './test/utils'

const ana = { id: 7, fullName: 'Ana Souza', email: 'ana@x.com', registration: null, phone: null, active: true, roles: ['STUDENT'], createdAt: '2026-03-01T10:00:00Z' }

const domCasmurro = {
  id: 1, title: 'Dom Casmurro', isbn: null, edition: null, publicationYear: 1899, language: null, description: null,
  coverUrl: null, coverVersion: null, publisher: null, authors: [{ id: 1, name: 'Machado de Assis' }], categories: [],
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
    // o que pede ação abre o painel, em destaque, com atalho para a tela que resolve
    const overdue = screen.getByText(/empréstimos atrasados/).closest('[data-attention]')!
    expect(overdue).toHaveAttribute('data-attention', 'true')
    expect(overdue).toHaveTextContent('2 empréstimos atrasados')
    expect(within(overdue as HTMLElement).getByRole('link', { name: 'Ver empréstimos em andamento' })).toHaveAttribute('href', '/balcao')
    const fines = screen.getByText(/multa em aberto/).closest('[data-attention]')!
    expect(fines).toHaveTextContent('1 multa em aberto')
    expect(fines).toHaveTextContent('Somam R$ 3,00')
    expect(screen.getByText('Valor de multas em aberto').nextElementSibling).toHaveTextContent('R$ 3,00')
    expect(screen.getByRole('img', { name: /Obras mais emprestadas: Dom Casmurro, 5/ })).toBeInTheDocument()
  })

  it('sem pendências, o painel fica quieto: sem destaque e sem atalhos', async () => {
    mockFetch({
      'GET /api/v1/dashboard': () => ({
        totalBooks: 1, totalCopies: 2, activeUsers: 3, activeLoans: 4, overdueLoans: 0, pendingReservations: 0,
        openFinesCount: 0, openFinesAmount: 0, topBooks: [], topCategories: [], movements: [],
      }),
    })
    renderApp('/painel', ['LIBRARIAN'])

    expect(await screen.findByText('Todos os livros emprestados estão no prazo.', {}, { timeout: 10_000 })).toBeInTheDocument()
    expect(screen.getByText('Nenhuma cobrança pendente.')).toBeInTheDocument()
    expect(document.querySelectorAll('[data-attention="true"]')).toHaveLength(0)
    expect(screen.queryByRole('link', { name: 'Receber ou isentar multas' })).not.toBeInTheDocument()
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
    renderWithProviders(<BookForm onSaved={vi.fn()} onCancel={vi.fn()} />)
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
    renderWithProviders(<BookForm onSaved={onCreated} onCancel={vi.fn()} />)
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
    renderWithProviders(<BookForm onSaved={vi.fn()} onCancel={vi.fn()} />)
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
      'GET /api/v1/users/lookup': () => [ana],
      'GET /api/v1/loans': () => page([]),
      'GET /api/v1/books': () => page([domCasmurro]),
      ...extra,
    })
  }

  async function pickReaderAndCopy(user: ReturnType<typeof userEvent.setup>) {
    await screen.findAllByRole('option', { name: /Ana Souza/ })   // uma lista no formulário e outra no filtro
    await user.selectOptions(screen.getByLabelText('Leitor'), '7')
    await user.type(screen.getByLabelText('Buscar obra pelo título, autor, ISBN ou código'), 'dom')
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

  it('filtra os empréstimos em andamento por leitor (para achar o que devolver entre muitos)', async () => {
    const { calls } = openCounter()
    renderApp('/balcao', ['LIBRARIAN'])
    const user = userEvent.setup()

    await screen.findAllByRole('option', { name: /Ana Souza/ })
    await user.selectOptions(screen.getByLabelText('Filtrar por leitor'), '7')

    await waitFor(() =>
      expect(calls.some((c) => c.key === 'GET /api/v1/loans' && c.search.includes('userId=7') && c.search.includes('activeOnly=true'))).toBe(true),
    )
    expect(await screen.findByText('Este leitor não tem empréstimos em andamento.')).toBeInTheDocument()
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
    expect(within(row).getByText(/Atrasado/)).toBeInTheDocument()
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
      'GET /api/v1/users/lookup': () => [ana],
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

describe('exclusão de obra', () => {
  it('só o administrador vê o botão; a exclusão pede confirmação e mostra a recusa do servidor', async () => {
    const { calls } = mockFetch({
      'GET /api/v1/books/1': () => domCasmurro,
      'DELETE /api/v1/books/1': () => problem(422, 'Esta obra tem empréstimos ou reservas no histórico e não pode ser excluída.'),
    })
    const librarian = renderApp('/acervo/1', ['LIBRARIAN'])
    await screen.findByRole('button', { name: 'Editar obra' })
    expect(screen.queryByRole('button', { name: 'Excluir obra' })).not.toBeInTheDocument()
    librarian.unmount()

    renderApp('/acervo/1', ['ADMIN_INSTITUTION'])
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Excluir obra' }))
    expect(calls.some((c) => c.key === 'DELETE /api/v1/books/1')).toBe(false)
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Excluir' }))

    expect(await screen.findByText(/histórico e não pode ser excluída/)).toBeInTheDocument()
  })

  it('exclusão bem-sucedida volta para a lista do acervo', async () => {
    const { calls } = mockFetch({
      'GET /api/v1/books/1': () => domCasmurro,
      'DELETE /api/v1/books/1': () => new Response(null, { status: 204 }),
      'GET /api/v1/books': () => page([]),
    })
    renderApp('/acervo/1', ['ADMIN_INSTITUTION'])
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Excluir obra' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Excluir' }))

    await waitFor(() => expect(calls.some((c) => c.key === 'DELETE /api/v1/books/1')).toBe(true))
    expect(await screen.findByRole('heading', { name: 'Acervo' })).toBeInTheDocument()
  })
})

describe('capa da obra', () => {
  const lookups = {
    'GET /api/v1/authors': () => [{ id: 1, name: 'Machado de Assis' }],
    'GET /api/v1/publishers': () => [],
    'GET /api/v1/categories': () => [],
  }
  const png = (name = 'capa.png', size = 1000) => new File([new Uint8Array(size)], name, { type: 'image/png' })

  async function fillRequired(user: ReturnType<typeof userEvent.setup>) {
    await user.type(screen.getByLabelText('Título'), 'Dom Casmurro')
    await user.type(screen.getByLabelText('Código do exemplar 1'), 'C-1')
    await user.type(screen.getByLabelText('Código do exemplar 2'), 'E-1')
  }

  it('cadastra a obra e envia a capa escolhida em seguida, como arquivo', async () => {
    const onCreated = vi.fn()
    const withCover = { ...domCasmurro, coverVersion: 123 }
    const { calls } = mockFetch({
      ...lookups,
      'POST /api/v1/books': () => domCasmurro,
      'PUT /api/v1/books/1/cover': () => withCover,
    })
    renderWithProviders(<BookForm onSaved={onCreated} onCancel={vi.fn()} />)
    const user = userEvent.setup()

    await fillRequired(user)
    await user.upload(screen.getByLabelText('Capa da obra'), png())
    expect(await screen.findByAltText('Prévia da capa escolhida')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Cadastrar obra' }))

    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(withCover))
    const upload = calls.find((c) => c.key === 'PUT /api/v1/books/1/cover')!
    expect(upload.init.body).toBeInstanceOf(FormData)
    expect((upload.init.body as FormData).get('file')).toBeInstanceOf(File)
    expect((upload.init.headers as Record<string, string>)['Content-Type']).toBeUndefined()   // o navegador põe o limite do multipart
  })

  it('recusa arquivo que não é JPEG/PNG ou passa de 2 MB, sem chamar o servidor', async () => {
    const { calls } = mockFetch(lookups)
    renderWithProviders(<BookForm onSaved={vi.fn()} onCancel={vi.fn()} />)
    const user = userEvent.setup({ applyAccept: false })

    await user.upload(screen.getByLabelText('Capa da obra'), new File(['x'], 'capa.gif', { type: 'image/gif' }))
    expect(await screen.findByText('Use uma imagem JPEG ou PNG.')).toBeInTheDocument()
    await user.upload(screen.getByLabelText('Capa da obra'), png('grande.png', 2 * 1024 * 1024 + 1))
    expect(await screen.findByText('A imagem passa de 2 MB.')).toBeInTheDocument()
    expect(calls.some((c) => c.key.includes('/cover'))).toBe(false)
  })

  it('se o envio da capa falhar, a obra já existe: avisa e, ao tentar de novo, não cadastra de novo', async () => {
    let attempt = 0
    const { calls } = mockFetch({
      ...lookups,
      'POST /api/v1/books': () => domCasmurro,
      'PUT /api/v1/books/1/cover': () => (++attempt === 1 ? problem(422, 'Envie uma imagem JPEG ou PNG válida.') : { ...domCasmurro, coverVersion: 9 }),
    })
    const onCreated = vi.fn()
    renderWithProviders(<BookForm onSaved={onCreated} onCancel={vi.fn()} />)
    const user = userEvent.setup()

    await fillRequired(user)
    await user.upload(screen.getByLabelText('Capa da obra'), png())
    await user.click(screen.getByRole('button', { name: 'Cadastrar obra' }))
    expect(await screen.findByText(/A obra foi cadastrada, mas a capa não foi enviada/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Cadastrar obra' }))
    await waitFor(() => expect(onCreated).toHaveBeenCalledOnce())
    expect(calls.filter((c) => c.key === 'POST /api/v1/books')).toHaveLength(1)
  })

  it('na edição, mostra a capa atual e permite removê-la', async () => {
    const book = { ...domCasmurro, coverVersion: 5, publisherId: null } as unknown as Book
    const { calls } = mockFetch({
      ...lookups,
      'PUT /api/v1/books/1': () => book,
      'DELETE /api/v1/books/1/cover': () => ({ ...book, coverVersion: null }),
    })
    const onSaved = vi.fn()
    renderWithProviders(<BookForm book={book} onSaved={onSaved} onCancel={vi.fn()} />)
    const user = userEvent.setup()

    expect(document.querySelector('img')?.getAttribute('src')).toBe('/api/v1/books/1/cover?size=thumb&v=5')
    await user.click(screen.getByLabelText('Remover a capa enviada'))
    await user.click(screen.getByRole('button', { name: 'Salvar alterações' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalledOnce())
    expect(calls.some((c) => c.key === 'DELETE /api/v1/books/1/cover')).toBe(true)
  })

  it('no acervo, mostra a miniatura enviada e a capa padrão para quem não tem', async () => {
    mockFetch({
      'GET /api/v1/books': () => page([{ ...domCasmurro, coverVersion: 7 }, { ...domCasmurro, id: 2, title: 'Sem capa' }]),
    })
    renderApp('/acervo', ['LIBRARIAN'])

    expect(await screen.findByText('Sem capa')).toBeInTheDocument()
    const imgs = document.querySelectorAll('table img')
    expect(imgs).toHaveLength(1)
    expect(imgs[0]!.getAttribute('src')).toBe('/api/v1/books/1/cover?size=thumb&v=7')
    expect(imgs[0]!.getAttribute('alt')).toBe('')   // decorativa: o título está ao lado
  })
})

describe('editora no cadastro da obra', () => {
  function publishersApi() {
    const list: { id: number; name: string }[] = []
    return {
      'GET /api/v1/authors': () => [],
      'GET /api/v1/categories': () => [],
      'GET /api/v1/publishers': () => [...list],
      'POST /api/v1/publishers': (init: RequestInit) => {
        const item = { id: list.length + 1, name: (JSON.parse(String(init.body)) as { name: string }).name }
        list.push(item)
        return item
      },
    }
  }

  it('cadastra a editora no próprio formulário e já a deixa escolhida, sem salvar a obra', async () => {
    const { calls } = mockFetch(publishersApi())
    renderWithProviders(<BookForm onSaved={vi.fn()} onCancel={vi.fn()} />)
    const user = userEvent.setup()

    expect(screen.getByRole('button', { name: 'Cadastrar editora' })).toBeDisabled()
    await user.type(screen.getByLabelText('Nova editora'), '  Companhia das Letras {Enter}')

    const select = screen.getByLabelText('Editora') as HTMLSelectElement
    await waitFor(() => expect(select.selectedOptions[0]?.textContent).toBe('Companhia das Letras'))
    expect(JSON.parse(String(calls.find((c) => c.key === 'POST /api/v1/publishers')!.init.body))).toEqual({ name: 'Companhia das Letras' })
    expect(calls.some((c) => c.key === 'POST /api/v1/books')).toBe(false)   // Enter não salvou a obra
    expect((screen.getByLabelText('Nova editora') as HTMLInputElement).value).toBe('')
  })

  it('mostra a recusa do servidor quando a editora já existe', async () => {
    mockFetch({ ...publishersApi(), 'POST /api/v1/publishers': () => problem(422, 'Já existe editora com este nome.') })
    renderWithProviders(<BookForm onSaved={vi.fn()} onCancel={vi.fn()} />)
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Nova editora'), 'Garnier')
    await user.click(screen.getByRole('button', { name: 'Cadastrar editora' }))

    expect(await screen.findByText('Já existe editora com este nome.')).toBeInTheDocument()
  })
})

describe('autor e categoria no cadastro da obra', () => {
  function lookupsApi() {
    const lists: Record<string, { id: number; name: string }[]> = { authors: [{ id: 1, name: 'Machado de Assis' }], categories: [] }
    const handlers: Record<string, (init: RequestInit) => unknown> = {
      'GET /api/v1/publishers': () => [],
    }
    for (const kind of ['authors', 'categories']) {
      handlers[`GET /api/v1/${kind}`] = () => [...lists[kind]!]
      handlers[`POST /api/v1/${kind}`] = (init) => {
        const item = { id: lists[kind]!.length + 10, name: (JSON.parse(String(init.body)) as { name: string }).name }
        lists[kind]!.push(item)
        return item
      }
    }
    return handlers
  }

  it('cadastra um autor novo e o marca, sem desmarcar o que já estava escolhido', async () => {
    const { calls } = mockFetch(lookupsApi())
    renderWithProviders(<BookForm onSaved={vi.fn()} onCancel={vi.fn()} />)
    const user = userEvent.setup()

    await user.click(await screen.findByLabelText('Machado de Assis'))
    await user.type(screen.getByLabelText('Novo autor'), 'Clarice Lispector{Enter}')

    await waitFor(() => expect(screen.getByLabelText('Clarice Lispector')).toBeChecked())
    expect(screen.getByLabelText('Machado de Assis')).toBeChecked()
    expect(calls.some((c) => c.key === 'POST /api/v1/books')).toBe(false)
  })

  it('cadastra uma categoria nova e a marca', async () => {
    mockFetch(lookupsApi())
    renderWithProviders(<BookForm onSaved={vi.fn()} onCancel={vi.fn()} />)
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Nova categoria'), 'Romance')
    await user.click(screen.getByRole('button', { name: 'Cadastrar categoria' }))

    await waitFor(() => expect(screen.getByLabelText('Romance')).toBeChecked())
  })

  it('mostra a recusa do servidor quando o autor já existe', async () => {
    mockFetch({ ...lookupsApi(), 'POST /api/v1/authors': () => problem(422, 'Já existe autor com este nome.') })
    renderWithProviders(<BookForm onSaved={vi.fn()} onCancel={vi.fn()} />)
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Novo autor'), 'Machado de Assis')
    await user.click(screen.getByRole('button', { name: 'Cadastrar autor' }))

    expect(await screen.findByText('Já existe autor com este nome.')).toBeInTheDocument()
  })
})

describe('busca de capa pelo ISBN', () => {
  const lookups = {
    'GET /api/v1/authors': () => [],
    'GET /api/v1/publishers': () => [],
    'GET /api/v1/categories': () => [],
  }
  const withIsbn = { ...domCasmurro, isbn: '9788535910663', publisherId: null } as unknown as Book

  it('na edição, o botão busca a capa do ISBN salvo e mostra a nova imagem na hora', async () => {
    const { calls } = mockFetch({ ...lookups, 'POST /api/v1/books/1/cover/lookup': () => ({ ...withIsbn, coverVersion: 42 }) })
    renderWithProviders(<BookForm book={withIsbn} onSaved={vi.fn()} onCancel={vi.fn()} />)
    const user = userEvent.setup()

    expect(document.querySelector('img')).toBeNull()   // ainda sem capa
    await user.click(screen.getByRole('button', { name: 'Buscar capa pelo ISBN' }))

    await waitFor(() => expect(document.querySelector('img')?.getAttribute('src')).toBe('/api/v1/books/1/cover?size=thumb&v=42'))
    expect(calls.some((c) => c.key === 'POST /api/v1/books/1/cover/lookup')).toBe(true)
  })

  it('só busca com o ISBN já salvo: se o campo mudou, pede para salvar antes', async () => {
    mockFetch(lookups)
    renderWithProviders(<BookForm book={withIsbn} onSaved={vi.fn()} onCancel={vi.fn()} />)
    const user = userEvent.setup()

    await user.clear(screen.getByLabelText('ISBN'))
    expect(screen.getByRole('button', { name: 'Buscar capa pelo ISBN' })).toBeDisabled()
    await user.type(screen.getByLabelText('ISBN'), '9780140328721')
    expect(screen.getByRole('button', { name: 'Buscar capa pelo ISBN' })).toBeDisabled()
    expect(screen.getByText('Salve a obra com o novo ISBN antes de buscar.')).toBeInTheDocument()
  })

  it('mostra a mensagem do servidor quando não há capa para o ISBN', async () => {
    mockFetch({ ...lookups, 'POST /api/v1/books/1/cover/lookup': () => problem(422, 'Nenhuma capa encontrada para o ISBN 9788535910663. Envie a imagem.') })
    renderWithProviders(<BookForm book={withIsbn} onSaved={vi.fn()} onCancel={vi.fn()} />)
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Buscar capa pelo ISBN' }))

    expect(await screen.findByText(/Nenhuma capa encontrada para o ISBN/)).toBeInTheDocument()
  })

  it('no cadastro, a opção só aparece com ISBN e a busca roda depois de salvar a obra', async () => {
    const onCreated = vi.fn()
    const found = { ...domCasmurro, coverVersion: 7 }
    const { calls } = mockFetch({ ...lookups, 'POST /api/v1/books': () => domCasmurro, 'POST /api/v1/books/1/cover/lookup': () => found })
    renderWithProviders(<BookForm onSaved={onCreated} onCancel={vi.fn()} />)
    const user = userEvent.setup()

    expect(screen.queryByLabelText('Buscar a capa pelo ISBN ao cadastrar')).toBeNull()
    await user.type(screen.getByLabelText('Título'), 'Dom Casmurro')
    await user.type(screen.getByLabelText('ISBN'), '9788535910663')
    await user.type(screen.getByLabelText('Código do exemplar 1'), 'C-1')
    await user.type(screen.getByLabelText('Código do exemplar 2'), 'E-1')
    await user.click(screen.getByLabelText('Buscar a capa pelo ISBN ao cadastrar'))
    await user.click(screen.getByRole('button', { name: 'Cadastrar obra' }))

    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(found))
    expect(calls.map((c) => c.key).filter((k) => k.startsWith('POST'))).toEqual(['POST /api/v1/books', 'POST /api/v1/books/1/cover/lookup'])
  })
})

describe('busca de dados pelo ISBN no cadastro', () => {
  const ISBN_PATH = 'GET /api/v1/books/isbn/9788545702870'
  const found = {
    isbn: '9788545702870', alreadyRegistered: false, existingBookId: null, title: 'Akira', edition: null, publicationYear: 2017,
    language: 'Português', description: 'Um marco da ficção científica.', coverUrl: 'https://covers.openlibrary.org/b/id/1-L.jpg',
    publisher: { id: 5, name: 'Japorama' },
    authors: [{ id: 9, name: 'Katsuhiro Otomo' }, { id: null, name: 'Drik Sada' }],
    categories: [{ id: 3, name: 'Mangá' }, { id: null, name: 'Ficção científica' }],
    sources: { title: 'brasilapi', language: 'openlibrary' },
  }

  /** Cadastros de apoio com estado: o POST de um nome novo o põe na lista seguinte. */
  function api(extra: Record<string, (init: RequestInit) => unknown> = {}) {
    const lists: Record<string, { id: number; name: string }[]> = {
      publishers: [{ id: 5, name: 'Japorama' }],
      authors: [{ id: 9, name: 'Katsuhiro Otomo' }],
      categories: [{ id: 3, name: 'Mangá' }],
    }
    const handlers: Record<string, (init: RequestInit) => unknown> = { [ISBN_PATH]: () => found }
    for (const kind of Object.keys(lists)) {
      handlers[`GET /api/v1/${kind}`] = () => [...lists[kind]!]
      handlers[`POST /api/v1/${kind}`] = (init) => {
        const item = { id: lists[kind]!.length + 20, name: (JSON.parse(String(init.body)) as { name: string }).name }
        lists[kind]!.push(item)
        return item
      }
    }
    return { ...handlers, ...extra }
  }

  async function search(isbn = '9788545702870') {
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('ISBN'), isbn)
    await user.click(screen.getByRole('button', { name: 'Buscar por ISBN' }))
    return user
  }

  it('preenche os campos vazios, escolhe o cadastrado, mostra a prévia da capa e não salva nada', async () => {
    const { calls } = mockFetch(api({ 'GET /api/v1/books/isbn/978-85-4570-287-0': () => found }))   // o ISBN vai como foi digitado
    renderWithProviders(<BookForm onSaved={vi.fn()} onCancel={vi.fn()} />)
    expect(screen.getByRole('button', { name: 'Buscar por ISBN' })).toBeDisabled()   // sem ISBN
    await search('978-85-4570-287-0')

    await waitFor(() => expect((screen.getByLabelText('Título') as HTMLInputElement).value).toBe('Akira'))
    expect((screen.getByLabelText('Ano de publicação') as HTMLInputElement).value).toBe('2017')
    expect((screen.getByLabelText('Idioma') as HTMLInputElement).value).toBe('Português')
    expect((screen.getByLabelText('Descrição') as HTMLTextAreaElement).value).toBe('Um marco da ficção científica.')
    expect((screen.getByLabelText('ISBN') as HTMLInputElement).value).toBe('9788545702870')   // sem hífen
    expect((screen.getByLabelText('Editora') as HTMLSelectElement).selectedOptions[0]?.textContent).toBe('Japorama')
    expect(await screen.findByLabelText('Katsuhiro Otomo')).toBeChecked()
    expect(screen.getByLabelText('Mangá')).toBeChecked()
    expect(screen.getByAltText('Prévia da capa pelo endereço informado')).toHaveAttribute('src', found.coverUrl)
    expect(screen.getByText(/Dados encontrados \(BrasilAPI e Open Library\)/)).toBeInTheDocument()
    expect(calls.some((c) => c.key === 'POST /api/v1/books')).toBe(false)   // nada foi salvo
  })

  it('não sobrescreve o que o usuário digitou: pergunta e só troca se ele confirmar', async () => {
    mockFetch(api())
    renderWithProviders(<BookForm onSaved={vi.fn()} onCancel={vi.fn()} />)
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Título'), 'Meu título')
    await search()

    expect(await screen.findByText(/«Meu título» → «Akira»/)).toBeInTheDocument()
    expect((screen.getByLabelText('Título') as HTMLInputElement).value).toBe('Meu título')
    expect((screen.getByLabelText('Idioma') as HTMLInputElement).value).toBe('Português')   // o vazio foi preenchido

    await user.click(screen.getByRole('button', { name: 'Trocar título pelo sugerido' }))
    expect((screen.getByLabelText('Título') as HTMLInputElement).value).toBe('Akira')
    expect(screen.queryByText(/«Meu título»/)).not.toBeInTheDocument()
  })

  it('"Manter tudo como está" descarta as trocas sugeridas', async () => {
    mockFetch(api())
    renderWithProviders(<BookForm onSaved={vi.fn()} onCancel={vi.fn()} />)
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Título'), 'Meu título')
    await search()

    await user.click(await screen.findByRole('button', { name: 'Manter tudo como está' }))
    expect(screen.queryByRole('button', { name: 'Trocar título pelo sugerido' })).not.toBeInTheDocument()
    expect((screen.getByLabelText('Título') as HTMLInputElement).value).toBe('Meu título')
  })

  it('autor e categoria novos viram botões: nada é cadastrado até o clique, e o clique cadastra e marca', async () => {
    const { calls } = mockFetch(api())
    renderWithProviders(<BookForm onSaved={vi.fn()} onCancel={vi.fn()} />)
    const user = await search()

    const author = await screen.findByRole('button', { name: 'Adicionar autor «Drik Sada»' })
    expect(calls.some((c) => c.key.startsWith('POST'))).toBe(false)

    await user.click(author)
    await waitFor(() => expect(screen.getByLabelText('Drik Sada')).toBeChecked())
    expect(screen.queryByRole('button', { name: 'Adicionar autor «Drik Sada»' })).not.toBeInTheDocument()
    expect(JSON.parse(String(calls.find((c) => c.key === 'POST /api/v1/authors')!.init.body))).toEqual({ name: 'Drik Sada' })

    await user.click(screen.getByRole('button', { name: 'Adicionar categoria «Ficção científica»' }))
    await waitFor(() => expect(screen.getByLabelText('Ficção científica')).toBeChecked())
    expect(screen.getByLabelText('Katsuhiro Otomo')).toBeChecked()   // a seleção anterior continua
    expect(calls.some((c) => c.key === 'POST /api/v1/books')).toBe(false)
  })

  it('editora nova vira botão e, ao cadastrar, fica escolhida', async () => {
    mockFetch(api({ [ISBN_PATH]: () => ({ ...found, publisher: { id: null, name: 'Editora Inédita' } }) }))
    renderWithProviders(<BookForm onSaved={vi.fn()} onCancel={vi.fn()} />)
    const user = await search()

    await user.click(await screen.findByRole('button', { name: 'Adicionar editora «Editora Inédita»' }))
    const select = screen.getByLabelText('Editora') as HTMLSelectElement
    await waitFor(() => expect(select.selectedOptions[0]?.textContent).toBe('Editora Inédita'))
  })

  it('obra já cadastrada: avisa, leva à obra e não preenche nada', async () => {
    mockFetch(api({ [ISBN_PATH]: () => ({ ...found, alreadyRegistered: true, existingBookId: 7, title: null, sources: {} }) }))
    renderWithProviders(<BookForm onSaved={vi.fn()} onCancel={vi.fn()} />)
    await search()

    expect(await screen.findByText(/Já existe uma obra com este ISBN/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Abrir a obra existente/ })).toHaveAttribute('href', '/acervo/7')
    expect((screen.getByLabelText('Título') as HTMLInputElement).value).toBe('')
  })

  it.each([
    [400, 'ISBN inválido: informe 10 ou 13 dígitos com o dígito verificador correto.'],
    [404, 'Nenhuma base de livros conhece o ISBN 9788545702870. Preencha manualmente.'],
    [503, 'Não foi possível consultar as bases de livros agora. Preencha manualmente.'],
  ])('mostra a mensagem do servidor no erro %i e deixa preencher à mão', async (status, detail) => {
    mockFetch(api({ [ISBN_PATH]: () => problem(status, detail) }))
    renderWithProviders(<BookForm onSaved={vi.fn()} onCancel={vi.fn()} />)
    await search()

    expect(await screen.findByText(detail)).toBeInTheDocument()
    expect(screen.getByLabelText('Título')).toBeEnabled()
  })

  it('Enter no campo ISBN busca em vez de cadastrar a obra', async () => {
    const { calls } = mockFetch(api())
    renderWithProviders(<BookForm onSaved={vi.fn()} onCancel={vi.fn()} />)
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('ISBN'), '9788545702870{Enter}')

    await waitFor(() => expect(calls.some((c) => c.key === ISBN_PATH)).toBe(true))
    expect(calls.some((c) => c.key === 'POST /api/v1/books')).toBe(false)
  })

  it('imagem do endereço que não carrega mostra aviso em vez de ficar quebrada', async () => {
    mockFetch(api())
    renderWithProviders(<BookForm onSaved={vi.fn()} onCancel={vi.fn()} />)
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Endereço da imagem da capa'), 'https://exemplo.test/capa.jpg')
    fireEvent.error(screen.getByAltText('Prévia da capa pelo endereço informado'))

    expect(await screen.findByText('Não foi possível carregar a imagem deste endereço.')).toBeInTheDocument()
    expect(screen.queryByAltText('Prévia da capa pelo endereço informado')).not.toBeInTheDocument()
  })

  it('na edição não há botão de busca (a obra já existe)', async () => {
    mockFetch(api())
    renderWithProviders(<BookForm book={domCasmurro as unknown as Book} onSaved={vi.fn()} onCancel={vi.fn()} />)
    expect(screen.queryByRole('button', { name: 'Buscar por ISBN' })).not.toBeInTheDocument()
  })
})
