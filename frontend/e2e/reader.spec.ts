import { expect, test } from '@playwright/test'
import { ADMIN, PASSWORD, apiLogin, seedBook, seedLoan, seedReader, uiLogin } from './support'

test('leitor: vê só a própria área, não acessa telas da equipe e reserva obra emprestada', async ({ page, request }) => {
  const admin = await apiLogin(request, ADMIN.email, ADMIN.password)
  const holder = await seedReader(request, admin, 'TEACHER', 'Professor')
  const reader = await seedReader(request, admin, 'STUDENT', 'Aluna')
  const book = await seedBook(request, admin, 'Iracema')
  await seedLoan(request, admin, holder.id, book.lendableCopyId)   // a única cópia emprestável já está com o professor

  await uiLogin(page, reader.email, PASSWORD)
  await expect(page.getByRole('heading', { name: 'Minha área', level: 1 })).toBeVisible()

  // menu sem itens da equipe
  const nav = page.getByRole('navigation', { name: 'Principal' })
  for (const item of ['Painel', 'Balcão', 'Reservas', 'Multas', 'Relatórios', 'Usuários']) {
    await expect(nav.getByRole('link', { name: item })).toHaveCount(0)
  }

  // digitando a rota da equipe: negado (e o servidor também negaria: ver testes do backend)
  await page.goto('/balcao')
  await expect(page.getByRole('alert')).toContainText('Acesso negado')

  // acervo: encontra a obra e vê que o exemplar de consulta existe, mas não há botão de equipe
  await page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: 'Acervo' }).click()
  await page.getByLabel('Buscar por título').fill(book.title)
  await page.getByRole('button', { name: 'Buscar' }).click()
  await page.getByRole('link', { name: book.title }).click()
  await expect(page.getByRole('heading', { name: book.title, level: 1 })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Acrescentar exemplar' })).toHaveCount(0)
  await expect(page.getByLabel(`Situação do exemplar ${book.lendableBarcode}`)).toHaveCount(0)

  // reserva: a obra está emprestada, então entra na fila
  await page.getByRole('button', { name: 'Reservar esta obra' }).click()
  await expect(page.getByText(/Reserva feita/)).toContainText('1º da fila')

  // reservar de novo é recusado, com a mensagem do servidor
  await page.getByRole('button', { name: 'Reservar esta obra' }).click()
  await expect(page.getByRole('alert')).toContainText('já tem uma reserva')

  // Minha área mostra a reserva; cancelar pede confirmação
  await page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: 'Minha área' }).click()
  const reservations = page.getByRole('table', { name: 'Minhas reservas' })
  await expect(reservations.getByRole('row', { name: new RegExp(book.title) })).toContainText('Na fila')
  await reservations.getByRole('button', { name: 'Cancelar' }).click()
  await page.getByRole('dialog', { name: 'Cancelar reserva' }).getByRole('button', { name: 'Cancelar reserva' }).click()
  await expect(reservations.getByRole('row', { name: new RegExp(book.title) })).toContainText('Cancelada')
})

test('leitor com empréstimo vê o prazo na Minha área', async ({ page, request }) => {
  const admin = await apiLogin(request, ADMIN.email, ADMIN.password)
  const reader = await seedReader(request, admin, 'EMPLOYEE', 'Funcionario')
  const book = await seedBook(request, admin, 'Senhora')
  await seedLoan(request, admin, reader.id, book.lendableCopyId)

  await uiLogin(page, reader.email, PASSWORD)

  const loans = page.getByRole('table', { name: 'Meus empréstimos' })
  await expect(loans.getByRole('row', { name: new RegExp(book.title) })).toContainText(/Devolver até|Vence/)
})
