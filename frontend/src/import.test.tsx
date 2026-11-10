import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { parseIsbns, MAX_BATCH } from './lib/isbnBatch'
import { mockFetch, page, problem, renderApp } from './test/utils'

afterEach(() => vi.unstubAllGlobals())

describe('parseIsbns', () => {
  it('aceita uma linha por ISBN, hífen, vírgula, ponto e vírgula e ignora o resto', () => {
    const r = parseIsbns('ISBN;Título\n978-85-359-1484-9, 8545702876\n 9788545702870 ;Akira\n\nlixo')
    expect(r.isbns).toEqual(['9788535914849', '8545702876', '9788545702870'])
    expect(r.ignored).toBe(4)   // "ISBN", "Título", "Akira" e "lixo"
    expect(r.truncated).toBe(false)
  })

  it('tira repetidos e aceita X no ISBN-10', () => {
    expect(parseIsbns('080442957x\n080442957X\n0804429573').isbns).toEqual(['080442957X', '0804429573'])
  })

  it(`limita o lote a ${MAX_BATCH} e avisa`, () => {
    const many = Array.from({ length: MAX_BATCH + 5 }, (_, i) => String(9780000000000 + i)).join('\n')
    const r = parseIsbns(many)
    expect(r.isbns).toHaveLength(MAX_BATCH)
    expect(r.truncated).toBe(true)
  })
})

describe('importação em lote por ISBN', () => {
  const suggestion = (isbn: string, extra: Record<string, unknown> = {}) => ({
    isbn, alreadyRegistered: false, existingBookId: null, title: `Obra ${isbn}`, edition: null, publicationYear: 2020, language: 'Português',
    description: null, coverUrl: null, publisher: { id: 5, name: 'Japorama' },
    authors: [{ id: 9, name: 'Autor Antigo' }, { id: null, name: 'Autor Novo' }],
    categories: [{ id: 3, name: 'Mangá' }, { id: null, name: 'Futurology' }], sources: { title: 'brasilapi' }, ...extra,
  })
  const OK = '9788545702870'
  const OK2 = '9788535914849'

  function api(extra: Record<string, (init: RequestInit) => unknown> = {}) {
    let next = 100
    return {
      [`GET /api/v1/books/isbn/${OK}`]: () => suggestion(OK),
      [`GET /api/v1/books/isbn/${OK2}`]: () => suggestion(OK2),
      'POST /api/v1/books': (init: RequestInit) => ({ id: next++, ...JSON.parse(String(init.body)) }),
      'GET /api/v1/publishers': () => [{ id: 5, name: 'Japorama' }],
      'GET /api/v1/authors': () => [{ id: 9, name: 'Autor Antigo' }],
      'POST /api/v1/publishers': (init: RequestInit) => ({ id: 50, name: (JSON.parse(String(init.body)) as { name: string }).name }),
      'POST /api/v1/authors': (init: RequestInit) => ({ id: 90, name: (JSON.parse(String(init.body)) as { name: string }).name }),
      ...extra,
    }
  }

  async function consult(list: string) {
    const user = userEvent.setup()
    await user.click(await screen.findByLabelText('ISBNs'))
    await user.paste(list)
    await user.click(screen.getByRole('button', { name: 'Consultar' }))
    return user
  }

  it('mostra a tabela de revisão com cada situação e não grava nada antes da confirmação', async () => {
    const { calls } = mockFetch(api({
      'GET /api/v1/books/isbn/9780000000002': () => suggestion('9780000000002', { alreadyRegistered: true, existingBookId: 7, title: null }),
      'GET /api/v1/books/isbn/9781000000009': () => problem(404, 'Nenhuma base de livros conhece o ISBN 9781000000009. Preencha manualmente.'),
      'GET /api/v1/books/isbn/9788535914840': () => problem(400, 'ISBN inválido: informe 10 ou 13 dígitos com o dígito verificador correto.'),
    }))
    renderApp('/acervo/importar', ['LIBRARIAN'])
    await consult(`${OK}\n9780000000002\n9781000000009\n9788535914840`)

    const table = await screen.findByRole('table', { name: 'Revisão da importação' })
    await waitFor(() => expect(within(table).queryByText('Consultando…')).not.toBeInTheDocument())
    const rows = within(table).getAllByRole('row').slice(1)
    expect(within(rows[0]!).getByText('Encontrada')).toBeInTheDocument()
    expect(within(rows[0]!).getByText(`Obra ${OK}`)).toBeInTheDocument()
    expect(within(rows[1]!).getByText('Já cadastrada')).toBeInTheDocument()
    expect(within(rows[1]!).getByRole('link', { name: 'Abrir obra' })).toHaveAttribute('href', '/acervo/7')
    expect(within(rows[2]!).getByText('Não encontrada')).toBeInTheDocument()
    expect(within(rows[3]!).getByText('ISBN inválido')).toBeInTheDocument()
    expect(within(rows[3]!).getByText(/dígito verificador/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cadastrar 1 obra selecionada' })).toBeEnabled()
    expect(calls.some((c) => c.key.startsWith('POST'))).toBe(false)   // nada gravado ainda
  })

  it('cadastra só o que está marcado, com 2 exemplares gerados e só o que já existe no cadastro', async () => {
    const { calls } = mockFetch(api())
    renderApp('/acervo/importar', ['LIBRARIAN'])
    const user = await consult(`${OK}\n${OK2}`)

    await user.click(await screen.findByLabelText(`Incluir ${OK2}`))   // desmarca a segunda
    await user.click(await screen.findByRole('button', { name: 'Cadastrar 1 obra selecionada' }))

    expect(await screen.findByText('Cadastrada')).toBeInTheDocument()
    const posts = calls.filter((c) => c.key === 'POST /api/v1/books')
    expect(posts).toHaveLength(1)
    expect(JSON.parse(String(posts[0]!.init.body))).toMatchObject({
      title: `Obra ${OK}`, isbn: OK, publisherId: 5, authorIds: [9], categoryIds: [3],   // sem "Autor Novo" nem "Futurology"
      copies: [{ barcode: `${OK}-C`, kind: 'CONSULTA' }, { barcode: `${OK}-E`, kind: 'CIRCULANTE' }],
    })
    expect(calls.some((c) => c.key === 'POST /api/v1/authors' || c.key === 'POST /api/v1/publishers')).toBe(false)
    expect(screen.getByLabelText(`Incluir ${OK2}`)).not.toBeChecked()
  })

  it('com a opção marcada, cria editoras e autores novos uma vez só, mas nunca categorias', async () => {
    const { calls } = mockFetch(api({
      [`GET /api/v1/books/isbn/${OK}`]: () => suggestion(OK, { publisher: { id: null, name: 'Editora Inédita' } }),
      [`GET /api/v1/books/isbn/${OK2}`]: () => suggestion(OK2, { publisher: { id: null, name: 'Editora Inédita' } }),
    }))
    renderApp('/acervo/importar', ['LIBRARIAN'])
    const user = await consult(`${OK}\n${OK2}`)

    await user.click(await screen.findByLabelText(/Cadastrar também as editoras e os autores novos/))
    await user.click(await screen.findByRole('button', { name: 'Cadastrar 2 obras selecionadas' }))

    await waitFor(() => expect(screen.getAllByText('Cadastrada')).toHaveLength(2))
    expect(calls.filter((c) => c.key === 'POST /api/v1/publishers')).toHaveLength(1)   // a mesma editora nas duas obras
    expect(calls.filter((c) => c.key === 'POST /api/v1/authors')).toHaveLength(1)
    expect(calls.some((c) => c.key === 'POST /api/v1/categories')).toBe(false)
    const body = JSON.parse(String(calls.find((c) => c.key === 'POST /api/v1/books')!.init.body))
    expect(body).toMatchObject({ publisherId: 50, authorIds: [9, 90], categoryIds: [3] })
  })

  it('erro temporário pode ser tentado de novo, e o ISBN repetido na lista aparece como repetido', async () => {
    let attempts = 0
    mockFetch(api({
      [`GET /api/v1/books/isbn/${OK}`]: () => (++attempts === 1 ? problem(503, 'Não foi possível consultar as bases de livros agora. Preencha manualmente.') : suggestion(OK)),
      // o ISBN-10 equivalente volta com o mesmo ISBN-13 do OK2
      'GET /api/v1/books/isbn/8535914846': () => suggestion(OK2),
    }))
    renderApp('/acervo/importar', ['LIBRARIAN'])
    const user = await consult(`${OK}\n${OK2}\n8535914846`)

    expect(await screen.findByText('Erro temporário')).toBeInTheDocument()
    expect(await screen.findByText('Repetida na lista')).toBeInTheDocument()

    await user.click(await screen.findByRole('button', { name: /Tentar de novo os 1 com erro/ }))
    await waitFor(() => expect(screen.queryByText('Erro temporário')).not.toBeInTheDocument())
    expect(screen.getAllByText('Encontrada')).toHaveLength(2)
  })

  it('uma obra que o servidor recusa não impede as outras, e a mensagem aparece na linha', async () => {
    let n = 0
    mockFetch(api({
      'POST /api/v1/books': () => (++n === 1 ? problem(422, 'Já existe exemplar com um desses códigos.') : { id: 101 }),
    }))
    renderApp('/acervo/importar', ['LIBRARIAN'])
    const user = await consult(`${OK}\n${OK2}`)

    await user.click(await screen.findByRole('button', { name: 'Cadastrar 2 obras selecionadas' }))

    expect(await screen.findByText('Não cadastrada')).toBeInTheDocument()
    expect(screen.getByText('Já existe exemplar com um desses códigos.')).toBeInTheDocument()
    expect(await screen.findByText('Cadastrada')).toBeInTheDocument()
  })

  it('ignora o que não parece ISBN e avisa', async () => {
    mockFetch(api())
    renderApp('/acervo/importar', ['LIBRARIAN'])
    await consult(`Título;ISBN\nAkira;${OK}`)

    expect(await screen.findByText(/3 itens foram ignorados/)).toBeInTheDocument()
  })

  it('o acervo mostra o atalho para a equipe e a página não abre para o leitor', async () => {
    mockFetch({ 'GET /api/v1/books': () => page([]) })
    const staff = renderApp('/acervo', ['LIBRARIAN'])
    expect(await screen.findByRole('link', { name: 'Importar por ISBN' })).toHaveAttribute('href', '/acervo/importar')
    staff.unmount()

    renderApp('/acervo/importar', ['STUDENT'])
    await waitFor(() => expect(screen.queryByRole('heading', { name: 'Importar obras por ISBN' })).not.toBeInTheDocument())
  })
})
