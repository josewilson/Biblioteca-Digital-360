import { expect, type APIRequestContext, type Page } from '@playwright/test'

/** Administrador criado pelo backend no primeiro start (valores de e2e/e2e.env). */
export const ADMIN = { email: 'admin@e2e.test', password: 'e2e-admin-fake-1' }
export const PASSWORD = 'senha-e2e-1234'
export const TEMPORARY_PASSWORD = 'temporaria-e2e-1234'

/** Sufixo curto e único, para os testes não esbarrarem em dados de execuções anteriores. */
export const uid = () => Math.random().toString(36).slice(2, 8)

export async function apiLogin(request: APIRequestContext, email: string, password: string): Promise<string> {
  const res = await request.post('/api/v1/auth/login', { data: { email, password } })
  expect(res.status(), `login de ${email}`).toBe(200)
  return ((await res.json()) as { accessToken: string }).accessToken
}

async function post<T>(request: APIRequestContext, token: string, path: string, data: unknown): Promise<T> {
  const res = await request.post(`/api/v1${path}`, { data, headers: { Authorization: `Bearer ${token}` } })
  expect(res.status(), `POST ${path}: ${await res.text()}`).toBeLessThan(300)
  return (await res.json()) as T
}

export interface Reader {
  id: number
  name: string
  email: string
}

export async function seedReader(request: APIRequestContext, token: string, role = 'STUDENT', prefix = 'Leitor'): Promise<Reader> {
  const id = uid()
  const name = `${prefix} ${id}`
  const email = `${prefix.toLowerCase().replace(/\W/g, '')}.${id}@e2e.test`
  const user = await post<{ id: number }>(request, token, '/users', {
    fullName: name, email, password: TEMPORARY_PASSWORD, roles: [role],
  })
  // Senha de cadastro é temporária: o leitor pronto para os testes já criou a própria.
  const temporary = await apiLogin(request, email, TEMPORARY_PASSWORD)
  await post(request, temporary, '/auth/password', { currentPassword: TEMPORARY_PASSWORD, newPassword: PASSWORD })
  return { id: user.id, name, email }
}

export interface SeededBook {
  id: number
  title: string
  lendableCopyId: number
  consultaBarcode: string
  lendableBarcode: string
}

export async function seedBook(request: APIRequestContext, token: string, titlePrefix = 'Obra'): Promise<SeededBook> {
  const id = uid()
  const title = `${titlePrefix} ${id}`
  const book = await post<{ id: number; copies: { id: number; kind: string; barcode: string }[] }>(request, token, '/books', {
    title,
    copies: [
      { barcode: `C-${id}`, kind: 'CONSULTA' },
      { barcode: `E-${id}`, kind: 'CIRCULANTE' },
    ],
  })
  const lendable = book.copies.find((c) => c.kind === 'CIRCULANTE')!
  const consulta = book.copies.find((c) => c.kind === 'CONSULTA')!
  return { id: book.id, title, lendableCopyId: lendable.id, lendableBarcode: lendable.barcode, consultaBarcode: consulta.barcode }
}

export async function seedLoan(request: APIRequestContext, token: string, userId: number, copyId: number): Promise<number> {
  return (await post<{ id: number }>(request, token, '/loans', { userId, copyId })).id
}

export async function uiLogin(page: Page, email: string, password: string): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('E-mail').fill(email)
  await page.getByLabel('Senha').fill(password)
  await page.getByRole('button', { name: 'Entrar' }).click()
  // Espera sair da tela de login (no celular o menu fica recolhido, então não dá para esperar por ele).
  await page.waitForURL((url) => !url.pathname.endsWith('/login'))
  // 20 s: o primeiro login depois de subir o backend também carrega o painel pela primeira vez (JVM fria).
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible({ timeout: 20_000 })
}
