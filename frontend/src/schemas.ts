import { z } from 'zod'

// Validação do lado do navegador: dá resposta imediata ao usuário. O servidor valida de novo (é ele quem manda).

export const loginSchema = z.object({
  email: z.string().trim().min(1, 'Informe o e-mail.').email('E-mail inválido.'),
  password: z.string().min(1, 'Informe a senha.'),
})
export type LoginForm = z.infer<typeof loginSchema>

export const nameSchema = z.object({
  name: z.string().trim().min(1, 'Informe o nome.').max(150, 'No máximo 150 caracteres.'),
})
export type NameForm = z.infer<typeof nameSchema>

export const copySchema = z.object({
  barcode: z.string().trim().min(1, 'Informe o código.').max(40, 'No máximo 40 caracteres.'),
  kind: z.enum(['CONSULTA', 'CIRCULANTE']),
  condition: z.enum(['NOVO', 'BOM', 'REGULAR', 'RUIM']),
  location: z.string().max(100, 'No máximo 100 caracteres.'),
})
export type CopyForm = z.infer<typeof copySchema>

export const bookSchema = z
  .object({
    title: z.string().trim().min(1, 'Informe o título.').max(250, 'No máximo 250 caracteres.'),
    isbn: z.string().max(20, 'No máximo 20 caracteres.'),
    edition: z.string().max(40, 'No máximo 40 caracteres.'),
    publicationYear: z.string().regex(/^(\d{4})?$/, 'Use 4 dígitos (ex.: 1899).'),
    language: z.string().max(30, 'No máximo 30 caracteres.'),
    description: z.string(),
    coverUrl: z.string().max(500, 'No máximo 500 caracteres.'),
    publisherId: z.string(),
    authorIds: z.array(z.string()),
    categoryIds: z.array(z.string()),
    copies: z.array(copySchema),
  })
  .superRefine((book, ctx) => {
    const kinds = book.copies.map((c) => c.kind)
    if (!kinds.includes('CONSULTA') || !kinds.includes('CIRCULANTE')) {
      ctx.addIssue({
        code: 'custom',
        path: ['copies'],
        message: 'Toda obra precisa de pelo menos 1 exemplar de consulta e 1 exemplar para empréstimo.',
      })
    }
    const codes = book.copies.map((c) => c.barcode.trim()).filter(Boolean)
    if (new Set(codes).size !== codes.length) {
      ctx.addIssue({ code: 'custom', path: ['copies'], message: 'Há códigos de exemplar repetidos.' })
    }
  })
export type BookForm = z.infer<typeof bookSchema>

export const userSchema = z.object({
  fullName: z.string().trim().min(1, 'Informe o nome.').max(150, 'No máximo 150 caracteres.'),
  email: z.string().trim().min(1, 'Informe o e-mail.').email('E-mail inválido.'),
  password: z.string().min(8, 'A senha precisa de pelo menos 8 caracteres.').max(72, 'No máximo 72 caracteres.'),
  cpf: z.string().regex(/^(\d{11})?$/, 'CPF deve ter 11 dígitos, sem pontos.'),
  registration: z.string().max(30, 'No máximo 30 caracteres.'),
  phone: z.string().max(20, 'No máximo 20 caracteres.'),
  roles: z.array(z.string()).min(1, 'Escolha pelo menos um perfil.'),
})
export type UserForm = z.infer<typeof userSchema>

export const waiveSchema = z.object({
  reason: z.string().trim().min(1, 'A justificativa é obrigatória.').max(300, 'No máximo 300 caracteres.'),
})
export type WaiveForm = z.infer<typeof waiveSchema>

/** Converte o formulário de obra (tudo texto) no corpo que a API espera. */
export function toBookRequest(form: BookForm) {
  const orNull = (s: string) => (s.trim() === '' ? null : s.trim())
  return {
    title: form.title.trim(),
    isbn: orNull(form.isbn),
    edition: orNull(form.edition),
    publicationYear: form.publicationYear ? Number(form.publicationYear) : null,
    language: orNull(form.language),
    description: orNull(form.description),
    coverUrl: orNull(form.coverUrl),
    publisherId: form.publisherId ? Number(form.publisherId) : null,
    authorIds: form.authorIds.map(Number),
    categoryIds: form.categoryIds.map(Number),
    copies: form.copies.map((c) => ({
      barcode: c.barcode.trim(),
      kind: c.kind,
      condition: c.condition,
      location: orNull(c.location),
    })),
  }
}
