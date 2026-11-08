import { bookSchema, toBookRequest, userSchema, waiveSchema, type BookForm } from './schemas'

const baseBook = (copies: BookForm['copies']): BookForm => ({
  title: 'Dom Casmurro',
  isbn: '',
  edition: '',
  publicationYear: '1899',
  language: '',
  description: '',
  coverUrl: '',
  publisherId: '',
  authorIds: ['1'],
  categoryIds: [],
  copies,
})

const copy = (barcode: string, kind: 'CONSULTA' | 'CIRCULANTE') => ({ barcode, kind, condition: 'BOM' as const, location: '' })

describe('bookSchema', () => {
  it('aceita obra com um exemplar de consulta e um emprestável', () => {
    expect(bookSchema.safeParse(baseBook([copy('A1', 'CONSULTA'), copy('A2', 'CIRCULANTE')])).success).toBe(true)
  })

  it('recusa obra sem exemplar de consulta', () => {
    const r = bookSchema.safeParse(baseBook([copy('A1', 'CIRCULANTE'), copy('A2', 'CIRCULANTE')]))
    expect(r.success).toBe(false)
    expect(r.error?.issues[0]?.message).toMatch(/consulta/)
  })

  it('recusa obra sem exemplar emprestável', () => {
    expect(bookSchema.safeParse(baseBook([copy('A1', 'CONSULTA')])).success).toBe(false)
  })

  it('recusa códigos repetidos', () => {
    const r = bookSchema.safeParse(baseBook([copy('A1', 'CONSULTA'), copy('A1', 'CIRCULANTE')]))
    expect(r.error?.issues.some((i) => /repetidos/.test(i.message))).toBe(true)
  })

  it('exige título e exemplares com código', () => {
    const r = bookSchema.safeParse({ ...baseBook([copy('', 'CONSULTA'), copy('A2', 'CIRCULANTE')]), title: '  ' })
    const messages = r.error?.issues.map((i) => i.message)
    expect(messages).toContain('Informe o título.')
    expect(messages).toContain('Informe o código.')
  })

  it('ano precisa ter 4 dígitos', () => {
    const r = bookSchema.safeParse({ ...baseBook([copy('A1', 'CONSULTA'), copy('A2', 'CIRCULANTE')]), publicationYear: '99' })
    expect(r.success).toBe(false)
  })
})

describe('toBookRequest', () => {
  it('converte texto vazio em nulo e ids em números', () => {
    const body = toBookRequest({
      ...baseBook([copy(' A1 ', 'CONSULTA'), copy('A2', 'CIRCULANTE')]),
      publisherId: '3',
      categoryIds: ['5', '6'],
    })

    expect(body.isbn).toBeNull()
    expect(body.publicationYear).toBe(1899)
    expect(body.publisherId).toBe(3)
    expect(body.authorIds).toEqual([1])
    expect(body.categoryIds).toEqual([5, 6])
    expect(body.copies[0]).toEqual({ barcode: 'A1', kind: 'CONSULTA', condition: 'BOM', location: null })
  })
})

describe('userSchema e waiveSchema', () => {
  const user = { fullName: 'Ana', email: 'ana@x.com', password: 'senha-forte', cpf: '', registration: '', phone: '', roles: ['STUDENT'] }

  it('valida cadastro de usuário', () => {
    expect(userSchema.safeParse(user).success).toBe(true)
    expect(userSchema.safeParse({ ...user, password: 'curta' }).success).toBe(false)
    expect(userSchema.safeParse({ ...user, cpf: '123.456.789-00' }).success).toBe(false)
    expect(userSchema.safeParse({ ...user, roles: [] }).success).toBe(false)
    expect(userSchema.safeParse({ ...user, email: 'sem-arroba' }).success).toBe(false)
  })

  it('isenção exige justificativa', () => {
    expect(waiveSchema.safeParse({ reason: '   ' }).success).toBe(false)
    expect(waiveSchema.safeParse({ reason: 'Doença comprovada' }).success).toBe(true)
  })
})

describe('bookSchema: endereço da capa', () => {
  const book = (coverUrl: string) => bookSchema.safeParse({ ...baseBook([copy('A1', 'CONSULTA'), copy('A2', 'CIRCULANTE')]), coverUrl })

  it('aceita http, https ou vazio', () => {
    expect(book('https://exemplo.org/capa.png').success).toBe(true)
    expect(book('http://exemplo.org/capa.png').success).toBe(true)
    expect(book('').success).toBe(true)
  })

  it('recusa esquemas perigosos e endereços com espaço', () => {
    for (const url of ['javascript:alert(1)', 'data:text/html;base64,AAAA', 'ftp://x.org/a.png', 'https://x.org/a b.png']) {
      expect(book(url).success, url).toBe(false)
    }
  })
})
