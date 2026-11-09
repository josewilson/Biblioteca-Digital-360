import { expect, test, type Page } from '@playwright/test'
import { ADMIN, uiLogin } from './support'

// Roda no projeto "mobile" (Pixel 5): menu recolhido, sem rolagem horizontal da página, alvos de toque acessíveis.

async function noHorizontalOverflow(page: Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  expect(overflow, 'a página não pode rolar na horizontal (tabelas rolam dentro do próprio quadro)').toBeLessThanOrEqual(1)
}

test('menu: começa recolhido, abre, navega e fecha sozinho', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('E-mail').fill(ADMIN.email)
  await page.getByLabel('Senha').fill(ADMIN.password)
  await page.getByRole('button', { name: 'Entrar' }).click()

  const toggle = page.getByRole('button', { name: 'Abrir menu' })
  const nav = page.getByRole('navigation', { name: 'Principal' })
  await expect(toggle).toBeVisible()
  await expect(toggle).toHaveAttribute('aria-expanded', 'false')
  await expect(nav).toBeHidden()

  await toggle.click()
  await expect(page.getByRole('button', { name: 'Fechar menu' })).toHaveAttribute('aria-expanded', 'true')
  await expect(nav).toBeVisible()

  await nav.getByRole('link', { name: 'Acervo' }).click()
  await expect(page.getByRole('heading', { name: 'Acervo', level: 1 })).toBeVisible()
  await expect(nav).toBeHidden()   // fechou ao navegar
})

test('nenhuma tela principal rola na horizontal no celular', async ({ page }) => {
  await uiLogin(page, ADMIN.email, ADMIN.password)
  for (const path of ['/painel', '/acervo', '/balcao', '/reservas', '/multas', '/relatorios', '/usuarios', '/cadastros', '/regras', '/minha-area', '/minha-senha']) {
    await page.goto(path)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await noHorizontalOverflow(page)
  }
})

test('o formulário de nova obra cabe na tela do celular', async ({ page }) => {
  await uiLogin(page, ADMIN.email, ADMIN.password)
  await page.goto('/acervo')
  await page.getByRole('button', { name: 'Nova obra' }).click()
  const dialog = page.getByRole('dialog', { name: 'Nova obra' })
  await expect(dialog).toBeVisible()

  const box = await dialog.boundingBox()
  const viewport = page.viewportSize()!
  expect(box!.width).toBeLessThanOrEqual(viewport.width)
  await expect(dialog.getByRole('button', { name: 'Cadastrar obra' })).toBeVisible()
})
