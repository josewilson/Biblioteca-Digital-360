import { expect, test } from '@playwright/test'
import { ADMIN, apiLogin, seedBook, uiLogin } from './support'

// PNG de 1x1 pixel (válido): o que importa aqui é o caminho todo, do formulário ao nginx, ao backend e de volta ao <img>.
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==',
  'base64',
)

test('operador envia a capa no formulário; ela aparece no detalhe e no acervo e pode ser removida', async ({ page, request }) => {
  const admin = await apiLogin(request, ADMIN.email, ADMIN.password)
  const book = await seedBook(request, admin, 'Capa E2E')
  await uiLogin(page, ADMIN.email, ADMIN.password)

  await page.goto(`/acervo/${book.id}`)
  await page.getByRole('button', { name: 'Editar obra' }).click()
  await page.getByLabel('Capa da obra').setInputFiles({ name: 'capa.png', mimeType: 'image/png', buffer: PNG })
  await page.getByRole('button', { name: 'Salvar alterações' }).click()

  const cover = page.getByRole('img', { name: `Capa de ${book.title}` })
  await expect(cover).toBeVisible()
  await expect.poll(() => cover.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0)

  // Leitura pública (a tag <img> não manda token) e sempre JPEG regravado pelo servidor.
  const thumb = await request.get(`/api/v1/books/${book.id}/cover?size=thumb`)
  expect(thumb.status()).toBe(200)
  expect(thumb.headers()['content-type']).toContain('image/jpeg')

  await page.goto('/acervo')
  await page.getByRole('searchbox').or(page.getByLabel(/Buscar por título/)).fill(book.title)
  await page.getByRole('button', { name: 'Buscar' }).click()
  await expect(page.getByRole('link', { name: book.title })).toBeVisible()
  const listed = page.locator('table img').first()
  await expect.poll(() => listed.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0)

  await page.goto(`/acervo/${book.id}`)
  await page.getByRole('button', { name: 'Editar obra' }).click()
  await page.getByLabel('Remover a capa enviada').check()
  await page.getByRole('button', { name: 'Salvar alterações' }).click()
  await expect(page.getByRole('img', { name: `Capa de ${book.title}` })).toHaveCount(0)
  expect((await request.get(`/api/v1/books/${book.id}/cover`)).status()).toBe(404)
})

test('arquivo que não é imagem é recusado pelo servidor com mensagem clara', async ({ request }) => {
  const admin = await apiLogin(request, ADMIN.email, ADMIN.password)
  const book = await seedBook(request, admin, 'Capa inválida')
  const res = await request.put(`/api/v1/books/${book.id}/cover`, {
    headers: { Authorization: `Bearer ${admin}` },
    multipart: { file: { name: 'x.png', mimeType: 'image/png', buffer: Buffer.from('<script>alert(1)</script>') } },
  })
  expect(res.status()).toBe(422)
  expect(await res.text()).toContain('JPEG ou PNG')
})
