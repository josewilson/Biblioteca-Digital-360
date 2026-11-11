import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { mockFetch, page, renderApp } from './test/utils'

afterEach(() => vi.unstubAllGlobals())

const item = (id: number, extra: Record<string, unknown> = {}) => ({
  id, barcode: `COD-${id}`, kind: 'CIRCULANTE', status: 'DISPONIVEL', condition: 'BOM', location: 'Estante A',
  bookId: 40 + id, title: `Obra ${id}`, isbn: null, publisher: 'Japorama', authors: ['Ana Autora'], categories: ['Matemática'],
  ...extra,
})

describe('listagem de exemplares', () => {
  const categories = [{ id: 3, name: 'Matemática' }, { id: 4, name: 'História' }]

  it('lista um exemplar por linha, com a obra, a situação e o link para a obra', async () => {
    mockFetch({
      'GET /api/v1/copies': () => page([item(1), item(2, { status: 'PERDIDO', kind: 'CONSULTA', location: null })]),
      'GET /api/v1/categories': () => categories,
    })
    renderApp('/exemplares', ['LIBRARIAN'])

    const table = await screen.findByRole('table', { name: 'Exemplares do acervo' })
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(2)
    expect(within(rows[0]!).getByText('COD-1')).toBeInTheDocument()
    expect(within(rows[0]!).getByRole('link', { name: 'Obra 1' })).toHaveAttribute('href', '/acervo/41')
    expect(within(rows[0]!).getByText('Disponível')).toBeInTheDocument()
    expect(within(rows[1]!).getByText('Perdido')).toBeInTheDocument()
    expect(within(rows[1]!).getByText('Consulta')).toBeInTheDocument()
    expect(screen.getByText('2 exemplares')).toBeInTheDocument()
  })

  it('manda a busca e os filtros ao servidor, e "Limpar filtros" volta ao começo', async () => {
    const { calls } = mockFetch({
      'GET /api/v1/copies': () => page([item(1)]),
      'GET /api/v1/categories': () => categories,
    })
    renderApp('/exemplares', ['LIBRARIAN'])
    const user = userEvent.setup()
    await screen.findByRole('table', { name: 'Exemplares do acervo' })

    await user.type(screen.getByLabelText('Buscar exemplares'), 'álgebra{Enter}')
    await user.selectOptions(screen.getByLabelText('Situação'), 'PERDIDO')
    await user.selectOptions(screen.getByLabelText('Tipo'), 'CONSULTA')
    await user.selectOptions(screen.getByLabelText('Categoria (disciplina)'), 'Matemática')

    await waitFor(() => {
      const last = new URLSearchParams(calls.filter((c) => c.key === 'GET /api/v1/copies').at(-1)!.search)
      expect(last.get('q')).toBe('álgebra')
      expect(last.get('status')).toBe('PERDIDO')
      expect(last.get('kind')).toBe('CONSULTA')
      expect(last.get('categoryId')).toBe('3')
      expect(last.get('page')).toBe('0')
    })

    await user.click(screen.getByRole('button', { name: 'Limpar filtros' }))
    await waitFor(() => {
      const last = new URLSearchParams(calls.filter((c) => c.key === 'GET /api/v1/copies').at(-1)!.search)
      expect(last.has('q') || last.has('status') || last.has('kind') || last.has('categoryId')).toBe(false)
    })
    expect(screen.getByLabelText('Buscar exemplares')).toHaveValue('')
    expect(screen.queryByRole('button', { name: 'Limpar filtros' })).not.toBeInTheDocument()
  })

  it('avisa quando nada é encontrado', async () => {
    mockFetch({ 'GET /api/v1/copies': () => page([]), 'GET /api/v1/categories': () => categories })
    renderApp('/exemplares', ['LIBRARIAN'])
    expect(await screen.findByText('Nenhum exemplar encontrado.')).toBeInTheDocument()
  })

  it('aparece no menu da equipe e não abre para o leitor', async () => {
    mockFetch({ 'GET /api/v1/copies': () => page([item(1)]), 'GET /api/v1/categories': () => categories, 'GET /api/v1/books': () => page([]) })
    const staff = renderApp('/acervo', ['LIBRARIAN'])
    expect(await screen.findByRole('link', { name: 'Exemplares' })).toHaveAttribute('href', '/exemplares')
    staff.unmount()

    renderApp('/exemplares', ['STUDENT'])
    await waitFor(() => expect(screen.queryByRole('heading', { name: 'Exemplares' })).not.toBeInTheDocument())
  })
})
