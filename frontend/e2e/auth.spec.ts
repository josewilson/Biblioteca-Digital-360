import { expect, test } from '@playwright/test'
import { ADMIN, PASSWORD, TEMPORARY_PASSWORD, apiLogin, seedReader, uid, uiLogin } from './support'

test.describe('autenticação (navegador real)', () => {
  test('senha errada mostra o erro e continua no login', async ({ page, request }) => {
    // Leitor descartável: errar a senha do ADMIN contaria para o bloqueio e travaria os outros testes.
    const admin = await apiLogin(request, ADMIN.email, ADMIN.password)
    const reader = await seedReader(request, admin, 'STUDENT', 'Erra')
    await page.goto('/login')
    await page.getByLabel('E-mail').fill(reader.email)
    await page.getByLabel('Senha').fill('senha-errada')
    await page.getByRole('button', { name: 'Entrar' }).click()

    await expect(page.getByRole('alert')).toContainText('Credenciais inválidas.')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('campos vazios são apontados sem chamar o servidor', async ({ page }) => {
    let loginCalls = 0
    page.on('request', (r) => r.url().includes('/auth/login') && loginCalls++)
    await page.goto('/login')
    await page.getByRole('button', { name: 'Entrar' }).click()

    await expect(page.getByText('Informe o e-mail.')).toBeVisible()
    await expect(page.getByText('Informe a senha.')).toBeVisible()
    expect(loginCalls).toBe(0)
  })

  test('rota interna sem login leva ao login e, depois de entrar, volta para ela', async ({ page }) => {
    await page.goto('/balcao')
    await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible()

    await page.getByLabel('E-mail').fill(ADMIN.email)
    await page.getByLabel('Senha').fill(ADMIN.password)
    await page.getByRole('button', { name: 'Entrar' }).click()

    await expect(page.getByRole('heading', { name: 'Balcão', level: 1 })).toBeVisible()
  })

  test('administrador entra no painel, sai, e a sessão realmente acaba', async ({ page }) => {
    await uiLogin(page, ADMIN.email, ADMIN.password)
    await expect(page.getByRole('heading', { name: 'Painel', level: 1 })).toBeVisible()

    await page.getByRole('button', { name: 'Sair' }).click()
    await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible()

    await page.goto('/painel')   // recarrega a página: sem token, volta ao login
    await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible()
  })

  test('o token fica em sessionStorage e nunca em localStorage', async ({ page }) => {
    await uiLogin(page, ADMIN.email, ADMIN.password)

    const stored = await page.evaluate(() => ({
      session: sessionStorage.getItem('bd360.token'),
      local: JSON.stringify(localStorage),
    }))
    expect(stored.session).toBeTruthy()
    expect(stored.local).not.toContain('eyJ')   // nenhum JWT em localStorage
  })

  test('senha temporária obriga a criar uma nova antes de usar o sistema; a sessão antiga cai ao redefinir', async ({ page, request }) => {
    const admin = await apiLogin(request, ADMIN.email, ADMIN.password)
    const email = `novo.${uid()}@e2e.test`
    const created = await request.post('/api/v1/users', {
      data: { fullName: `Novo ${uid()}`, email, password: TEMPORARY_PASSWORD, roles: ['STUDENT'] },
      headers: { Authorization: `Bearer ${admin}` },
    })
    expect(created.status()).toBe(201)

    await uiLogin(page, email, TEMPORARY_PASSWORD)
    await expect(page.getByText('Esta senha é temporária')).toBeVisible()
    await page.goto('/acervo')
    await expect(page).toHaveURL(/\/minha-senha$/)   // não dá para fugir digitando a rota

    await page.getByLabel('Senha atual').fill(TEMPORARY_PASSWORD)
    await page.getByLabel('Nova senha', { exact: true }).fill(PASSWORD)
    await page.getByLabel('Repita a nova senha').fill(PASSWORD)
    await page.getByRole('button', { name: 'Trocar senha' }).click()
    await expect(page.getByRole('heading', { name: 'Minha área', level: 1 })).toBeVisible()

    const { id } = (await created.json()) as { id: number }
    const reset = await request.post(`/api/v1/users/${id}/password`, {
      data: { newPassword: TEMPORARY_PASSWORD },
      headers: { Authorization: `Bearer ${admin}` },
    })
    expect(reset.status()).toBe(204)
    await page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: 'Acervo' }).click()
    await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible()   // o servidor recusou o token antigo
  })

  test('cinco senhas erradas bloqueiam o login, mesmo com a senha certa depois', async ({ page, request }) => {
    const admin = await apiLogin(request, ADMIN.email, ADMIN.password)
    const reader = await seedReader(request, admin, 'STUDENT', 'Bloqueio')

    await page.goto('/login')
    for (let i = 0; i < 5; i++) {
      await page.getByLabel('E-mail').fill(reader.email)
      await page.getByLabel('Senha').fill('errada')
      await page.getByRole('button', { name: 'Entrar' }).click()
      await expect(page.getByRole('alert')).toContainText('Credenciais inválidas.')
    }

    await page.getByLabel('Senha').fill(PASSWORD)   // agora a senha CERTA
    await page.getByRole('button', { name: 'Entrar' }).click()
    await expect(page.getByRole('alert')).toContainText('Muitas tentativas')
    await expect(page.getByRole('navigation', { name: 'Principal' })).toHaveCount(0)
  })
})
