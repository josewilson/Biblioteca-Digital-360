import { mkdirSync } from 'node:fs'
import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page, type TestInfo } from '@playwright/test'
import { ADMIN, PASSWORD, apiLogin, seedBook, seedLoan, seedReader, uiLogin } from './support'

// Auditoria de acessibilidade com axe-core (WCAG 2.0/2.1 A e AA) em todas as telas, em desktop e celular.
// Também salva uma imagem de cada tela em e2e-shots/ para conferência visual.

const TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']

async function audit(page: Page, info: TestInfo, name: string) {
  mkdirSync('e2e-shots', { recursive: true })
  await page.screenshot({ path: `e2e-shots/${info.project.name}-${name}.png`, fullPage: true })
  const { violations } = await new AxeBuilder({ page }).withTags(TAGS).analyze()
  const resumo = violations.map(
    (v) => `[${v.impact}] ${v.id}: ${v.help}\n   ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join('\n   ')}`,
  )
  expect(violations, `${name}: ${violations.length} violação(ões) de acessibilidade\n${resumo.join('\n')}`).toEqual([])
}

const heading = (page: Page) => expect(page.getByRole('heading', { level: 1 })).toBeVisible()

test.describe('acessibilidade (axe) — equipe', () => {
  test('todas as telas da equipe, com dados reais', async ({ page, request }, info) => {
    test.slow()   // percorre 12 telas com auditoria e captura em cada uma: no celular passa de 30 s
    const admin = await apiLogin(request, ADMIN.email, ADMIN.password)
    const reader = await seedReader(request, admin, 'STUDENT', 'Acessivel')
    const book = await seedBook(request, admin, 'Acessibilidade')
    await seedLoan(request, admin, reader.id, book.lendableCopyId)

    await page.goto('/login')
    await heading(page)
    await audit(page, info, 'login')

    await uiLogin(page, ADMIN.email, ADMIN.password)
    const nav = page.getByRole('navigation', { name: 'Principal' })
    if (info.project.name === 'mobile') await page.getByRole('button', { name: 'Abrir menu' }).click()

    const open = async (name: string) => {
      if (info.project.name === 'mobile' && !(await nav.isVisible())) await page.getByRole('button', { name: 'Abrir menu' }).click()
      await nav.getByRole('link', { name }).click()
      await heading(page)
    }

    await open('Painel')
    await expect(page.getByText('Obras cadastradas')).toBeVisible()
    await audit(page, info, 'painel')

    await open('Acervo')
    await expect(page.getByRole('table', { name: 'Obras do acervo' })).toBeVisible()
    await audit(page, info, 'acervo')

    await page.getByRole('button', { name: 'Nova obra' }).click()
    await expect(page.getByRole('dialog', { name: 'Nova obra' })).toBeVisible()
    await audit(page, info, 'acervo-nova-obra-dialogo')
    await page.keyboard.press('Escape')

    await page.getByLabel('Buscar por título').fill(book.title)
    await page.getByRole('button', { name: 'Buscar' }).click()
    await page.getByRole('link', { name: book.title }).click()
    await expect(page.getByRole('table', { name: 'Exemplares da obra' })).toBeVisible()
    await audit(page, info, 'obra-detalhe')

    await page.getByRole('button', { name: 'Editar obra' }).click()
    await expect(page.getByRole('dialog', { name: 'Editar obra' })).toBeVisible()
    await audit(page, info, 'obra-editar-dialogo')
    await page.keyboard.press('Escape')

    await open('Balcão')
    await expect(page.getByRole('table', { name: 'Empréstimos em andamento' })).toBeVisible()
    await audit(page, info, 'balcao')

    await open('Reservas')
    await audit(page, info, 'reservas')

    await open('Multas')
    await audit(page, info, 'multas')

    await open('Relatórios')
    await page.getByRole('button', { name: 'Gerar' }).click()
    await expect(page.getByRole('table', { name: 'Resultado do relatório' })).toBeVisible()
    await audit(page, info, 'relatorios')

    await open('Usuários')
    await expect(page.getByRole('table', { name: 'Usuários' })).toBeVisible()
    await audit(page, info, 'usuarios')
    await page.getByRole('button', { name: 'Novo usuário' }).click()
    await expect(page.getByRole('dialog', { name: 'Novo usuário' })).toBeVisible()
    await audit(page, info, 'usuarios-novo-dialogo')
    await page.keyboard.press('Escape')

    await page.getByLabel('Buscar por nome, e-mail ou matrícula').fill(reader.email)
    await page.getByRole('button', { name: 'Buscar' }).click()
    await page.getByRole('button', { name: `Editar ${reader.name}` }).click()
    await expect(page.getByRole('dialog', { name: 'Editar usuário' })).toBeVisible()
    await audit(page, info, 'usuarios-editar-dialogo')
    await page.keyboard.press('Escape')

    await open('Autores e categorias')
    await audit(page, info, 'cadastros')

    await open('Regras de empréstimo')
    await expect(page.getByRole('button', { name: 'Salvar regras de Aluno' })).toBeVisible()
    await audit(page, info, 'regras')

    await open('Minha senha')
    await audit(page, info, 'minha-senha')

    await open('Minha área')
    await audit(page, info, 'minha-area-equipe')
  })

  test('diálogo de confirmação (devolução) acessível', async ({ page, request }, info) => {
    const admin = await apiLogin(request, ADMIN.email, ADMIN.password)
    const reader = await seedReader(request, admin, 'STUDENT', 'Confirma')
    const book = await seedBook(request, admin, 'Confirmacao')
    await seedLoan(request, admin, reader.id, book.lendableCopyId)

    await uiLogin(page, ADMIN.email, ADMIN.password)
    await page.goto('/balcao')
    await page.getByLabel('Filtrar por leitor: buscar').fill(reader.email)
    await page.getByLabel('Filtrar por leitor', { exact: true }).selectOption({ label: `${reader.name} (${reader.email})` })
    const row = page.getByRole('row', { name: new RegExp(book.title) })
    await expect(row).toBeVisible()
    await row.getByRole('button', { name: 'Devolver' }).click()
    await expect(page.getByRole('dialog', { name: 'Registrar devolução' })).toBeVisible()
    await audit(page, info, 'balcao-confirmar-devolucao')
  })
})

test.describe('acessibilidade (axe) — leitor', () => {
  test('Minha área e detalhe de obra do leitor', async ({ page, request }, info) => {
    const admin = await apiLogin(request, ADMIN.email, ADMIN.password)
    const reader = await seedReader(request, admin, 'STUDENT', 'Leitor Axe')
    const book = await seedBook(request, admin, 'Leitura')
    await seedLoan(request, admin, reader.id, book.lendableCopyId)

    await uiLogin(page, reader.email, PASSWORD)
    await expect(page.getByRole('table', { name: 'Meus empréstimos' })).toBeVisible()
    await audit(page, info, 'leitor-minha-area')

    await page.goto(`/acervo`)
    await heading(page)
    await audit(page, info, 'leitor-acervo')
  })

  test('acesso negado também é acessível', async ({ page, request }, info) => {
    const admin = await apiLogin(request, ADMIN.email, ADMIN.password)
    const reader = await seedReader(request, admin, 'STUDENT', 'Negado')

    await uiLogin(page, reader.email, PASSWORD)
    await page.goto('/balcao')
    await expect(page.getByRole('alert')).toContainText('Acesso negado')
    await audit(page, info, 'acesso-negado')
  })
})
