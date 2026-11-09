import { expect, test } from '@playwright/test'
import { ADMIN, PASSWORD, uid, uiLogin } from './support'

test('equipe: cadastra autor, obra, leitor, empresta e devolve — tudo pela interface', async ({ page }) => {
  const id = uid()
  const author = `Autora ${id}`
  const title = `Memórias ${id}`
  const readerName = `AAA Leitor ${id}`
  const readerEmail = `aaa.${id}@e2e.test`

  await uiLogin(page, ADMIN.email, ADMIN.password)
  const nav = page.getByRole('navigation', { name: 'Principal' })

  // 1) autor
  await nav.getByRole('link', { name: 'Autores e categorias' }).click()
  await page.getByLabel('Novo autor').fill(author)
  await page.getByRole('button', { name: 'Adicionar' }).first().click()
  await expect(page.getByRole('listitem').filter({ hasText: author })).toBeVisible()

  // 2) obra: a regra "1 consulta + 1 emprestável" é recusada na tela antes de enviar...
  await nav.getByRole('link', { name: 'Acervo' }).click()
  await page.getByRole('button', { name: 'Nova obra' }).click()
  const dialog = page.getByRole('dialog', { name: 'Nova obra' })
  await dialog.getByLabel('Título', { exact: true }).fill(title)
  await dialog.getByLabel('Código do exemplar 1').fill(`C-${id}`)
  await dialog.getByLabel('Código do exemplar 2').fill(`E-${id}`)
  await dialog.getByLabel('Tipo').first().selectOption('CIRCULANTE')   // nenhum de consulta
  await dialog.getByRole('button', { name: 'Cadastrar obra' }).click()
  await expect(dialog.getByRole('alert')).toContainText('pelo menos 1 exemplar de consulta')

  // ...e aceita depois de corrigir
  await dialog.getByLabel('Tipo').first().selectOption('CONSULTA')
  await dialog.getByLabel(author).check()
  await dialog.getByRole('button', { name: 'Cadastrar obra' }).click()
  await expect(page.getByRole('heading', { name: title, level: 1 })).toBeVisible()
  const copies = page.getByRole('table', { name: 'Exemplares da obra' })
  await expect(copies.getByRole('row', { name: new RegExp(`C-${id}.*Consulta`) })).toBeVisible()
  await expect(copies.getByRole('row', { name: new RegExp(`E-${id}.*Emprestável`) })).toBeVisible()

  // 3) leitor
  await nav.getByRole('link', { name: 'Usuários' }).click()
  await page.getByRole('button', { name: 'Novo usuário' }).click()
  const userDialog = page.getByRole('dialog', { name: 'Novo usuário' })
  await userDialog.getByLabel('Nome completo').fill(readerName)
  await userDialog.getByLabel('E-mail').fill(readerEmail)
  await userDialog.getByLabel('Senha inicial').fill(PASSWORD)
  await userDialog.getByLabel('Aluno').check()
  await userDialog.getByRole('button', { name: 'Cadastrar' }).click()
  await expect(page.getByRole('cell', { name: readerEmail })).toBeVisible()

  // 4) empréstimo no balcão: exemplar de consulta nem aparece; só o emprestável
  await nav.getByRole('link', { name: 'Balcão' }).click()
  await page.getByLabel('Leitor: buscar por nome, e-mail ou matrícula', { exact: true }).fill(readerEmail)
  await page.getByLabel('Leitor', { exact: true }).selectOption({ label: `${readerName} (${readerEmail})` })
  await page.getByLabel('Buscar obra pelo título').fill(title)
  await page.getByRole('button', { name: 'Buscar' }).click()
  await page.getByRole('button', { name: 'Escolher' }).click()
  await expect(page.getByRole('radio', { name: new RegExp(`C-${id}`) })).toHaveCount(0)
  await page.getByRole('radio', { name: new RegExp(`E-${id}`) }).check()
  await page.getByRole('button', { name: 'Emprestar' }).click()
  await expect(page.getByText('Empréstimo registrado')).toBeVisible()

  // 5) a lista de em andamento é paginada: filtra pelo leitor para achar o empréstimo recém-feito
  await page.getByLabel('Filtrar por leitor: buscar').fill(readerEmail)
  await page.getByLabel('Filtrar por leitor', { exact: true }).selectOption({ label: `${readerName} (${readerEmail})` })
  const row = page.getByRole('row', { name: new RegExp(title) })
  await expect(row).toBeVisible()
  await expect(row.getByRole('button', { name: 'Renovar' })).toBeEnabled()

  // 6) devolução com confirmação
  await row.getByRole('button', { name: 'Devolver' }).click()
  const confirm = page.getByRole('dialog', { name: 'Registrar devolução' })
  await expect(confirm).toContainText(readerName)
  await confirm.getByRole('button', { name: 'Registrar devolução' }).click()
  await expect(page.getByText(/Devolução de .* registrada/)).toBeVisible()
  await expect(page.getByRole('row', { name: new RegExp(title) })).toHaveCount(0)
})
