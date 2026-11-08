import { defineConfig, devices } from '@playwright/test'

// Testes de ponta a ponta: navegador real contra a pilha COMPLETA (nginx + backend + PostgreSQL) subida por `npm run e2e:up`.
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:3100'

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.ts',
  timeout: 45_000,
  expect: { timeout: 8_000 },
  // Um banco só, com dados compartilhados: sem paralelismo, para os testes não se atrapalharem.
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } }, testIgnore: /mobile\.spec/ },
    { name: 'mobile', use: { ...devices['Pixel 5'] }, testMatch: /(mobile|a11y)\.spec/ },
  ],
})
