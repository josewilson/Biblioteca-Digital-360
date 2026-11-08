import { expect, test } from '@playwright/test'
import { ADMIN, apiLogin, uiLogin } from './support'

test.describe('cabeçalhos e política de segurança do conteúdo (CSP)', () => {
  test('a página vem com CSP restritiva e cabeçalhos de proteção', async ({ request }) => {
    const res = await request.get('/')
    const h = res.headers()

    expect(h['content-security-policy']).toContain("script-src 'self'")
    expect(h['content-security-policy']).not.toMatch(/script-src[^;]*unsafe-inline/)   // script em linha proibido
    expect(h['content-security-policy']).toContain("frame-ancestors 'none'")
    expect(h['x-frame-options']).toBe('DENY')
    expect(h['x-content-type-options']).toBe('nosniff')
    expect(h['referrer-policy']).toBe('no-referrer')
    expect(h['server']).not.toMatch(/\d/)   // sem número de versão do nginx
  })

  test('os cabeçalhos também valem nas rotas internas e nos arquivos estáticos', async ({ request, page }) => {
    const spa = await request.get('/painel')   // rota do React Router: nginx devolve o index.html
    expect(spa.headers()['content-security-policy']).toContain("default-src 'self'")

    await page.goto('/login')
    const asset = await page.evaluate(() => document.querySelector('script[src^="/assets/"]')?.getAttribute('src'))
    expect(asset).toBeTruthy()
    const js = await request.get(asset!)
    expect(js.headers()['content-security-policy']).toContain("script-src 'self'")
    expect(js.headers()['cache-control']).toContain('immutable')
  })

  test('a API responde com CSP "default-src none" e sem cache', async ({ request }) => {
    const token = await apiLogin(request, ADMIN.email, ADMIN.password)
    const res = await request.get('/api/v1/books', { headers: { Authorization: `Bearer ${token}` } })

    expect(res.status()).toBe(200)
    expect(res.headers()['content-security-policy']).toContain("default-src 'none'")
    expect(res.headers()['cache-control']).toContain('no-store')
  })

  test('o backend não é alcançável sem passar pelo nginx e sem token a API recusa', async ({ request }) => {
    expect((await request.get('/api/v1/books')).status()).toBe(401)
    expect((await request.get('/api/v1/users')).status()).toBe(401)
    expect((await request.get('/api/v1/dashboard')).status()).toBe(401)
  })

  test('navegar pelo sistema não gera violação de CSP nem erro no console', async ({ page }) => {
    const problems: string[] = []
    page.on('console', (m) => m.type() === 'error' && problems.push(`console: ${m.text()}`))
    page.on('pageerror', (e) => problems.push(`erro de página: ${e.message}`))
    page.on('requestfailed', (r) => problems.push(`requisição falhou: ${r.url()}`))

    await uiLogin(page, ADMIN.email, ADMIN.password)
    const nav = page.getByRole('navigation', { name: 'Principal' })
    for (const link of ['Painel', 'Acervo', 'Balcão', 'Reservas', 'Multas', 'Relatórios', 'Usuários', 'Autores e categorias', 'Minha área']) {
      await nav.getByRole('link', { name: link }).click()
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    }
    // o painel usa estilos em linha dos gráficos (permitidos em style-src); espera o gráfico para exercitar isso
    await nav.getByRole('link', { name: 'Painel' }).click()
    await expect(page.getByText('Obras cadastradas')).toBeVisible()

    expect(problems, problems.join('\n')).toEqual([])
  })

  test('texto com HTML vindo do servidor aparece como texto e não é executado (XSS)', async ({ page, request }) => {
    const token = await apiLogin(request, ADMIN.email, ADMIN.password)
    const tag = Math.random().toString(36).slice(2, 8)   // único por execução: o banco acumula entre rodadas
    const payload = `<img src=x onerror="window.__xss=1">Obra ${tag}`
    const created = await request.post('/api/v1/books', {
      headers: { Authorization: `Bearer ${token}` },
      data: { title: payload, copies: [{ barcode: `X-${Date.now()}-1`, kind: 'CONSULTA' }, { barcode: `X-${Date.now()}-2`, kind: 'CIRCULANTE' }] },
    })
    expect(created.status()).toBe(201)

    await uiLogin(page, ADMIN.email, ADMIN.password)
    await page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: 'Acervo' }).click()
    await page.getByLabel('Buscar por título').fill(tag)
    await page.getByRole('button', { name: 'Buscar' }).click()

    await expect(page.getByRole('link', { name: payload })).toBeVisible()   // o texto literal, com os sinais < >
    expect(await page.evaluate(() => (window as unknown as { __xss?: number }).__xss)).toBeUndefined()
    expect(await page.locator('img[src="x"]').count()).toBe(0)
  })
})
