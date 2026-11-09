// Teste de carga (k6) da Biblioteca Digital 360: equipe consultando o sistema com um acervo e uma base de usuários grandes.
// Uso (na raiz do projeto, com a pilha no ar):  veja loadtest/README.md
import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE = __ENV.BASE || 'http://host.docker.internal:3100'
const ADMIN = { email: __ENV.ADMIN_EMAIL || 'admin@e2e.test', password: __ENV.ADMIN_PASSWORD || 'e2e-admin-fake-1' }
const BOOKS = Number(__ENV.BOOKS || 1000)
const USERS = Number(__ENV.USERS || 300)
const PEAK = Number(__ENV.VUS || 50)
const THINK = Number(__ENV.THINK || 1)   // pausa média entre ações de cada usuário, em segundos (0,1 = quase sem pausa)

export const options = {
  stages: [
    { duration: '30s', target: Math.ceil(PEAK / 2) },
    { duration: '90s', target: PEAK },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],      // menos de 1% de erro
    http_req_duration: ['p(95)<800'],    // 95% das respostas em menos de 800 ms
  },
  setupTimeout: '10m',
}

const WORDS = ['Ação', 'Coração', 'Memória', 'História', 'Lição', 'Razão', 'Viagem', 'Mistério', 'Jardim', 'Oceano']
const NAMES = ['Ana', 'João', 'Maria', 'José', 'Beatriz', 'Conceição', 'Sérgio', 'Lúcia']

function call(token, method, path, body) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  return http.request(method, `${BASE}/api/v1${path}`, body ? JSON.stringify(body) : null, { headers })
}

function login() {
  const res = call(null, 'POST', '/auth/login', ADMIN)
  if (res.status !== 200) throw new Error(`login do administrador falhou (${res.status}): ${res.body}`)
  return res.json('accessToken')
}

// Monta a base antes da medição (não entra nas métricas de tempo da carga).
export function setup() {
  const token = login()
  const run = Date.now()
  const authors = []
  for (let i = 0; i < 10; i++) {
    authors.push(call(token, 'POST', '/authors', { name: `Autor de Carga ${run} ${i}` }).json('id'))
  }
  const bookIds = []
  for (let i = 0; i < BOOKS; i++) {
    const res = call(token, 'POST', '/books', {
      title: `Livro de Carga ${i} ${WORDS[i % WORDS.length]}`,
      authorIds: [authors[i % authors.length]],
      copies: [
        { barcode: `LC-${run}-${i}-C`, kind: 'CONSULTA' },
        { barcode: `LC-${run}-${i}-E`, kind: 'CIRCULANTE' },
      ],
    })
    if (res.status === 201) bookIds.push(res.json('id'))
  }
  for (let i = 0; i < USERS; i++) {
    call(token, 'POST', '/users', {
      fullName: `${NAMES[i % NAMES.length]} Carga ${i}`,
      email: `carga${run}-${i}@teste.com`,
      password: 'senha-de-carga-1',
      roles: ['STUDENT'],
    })
  }
  return { bookIds }
}

// Cada usuário virtual tem o próprio escopo de módulo: o token fica aqui, uma vez por VU.
let vuToken

function pick(list) {
  return list[Math.floor(Math.random() * list.length)]
}

// Cada usuário virtual entra uma vez como administrador e reaproveita o token: login não é o alvo (tem limite de tentativas por IP de propósito).
export default function (data) {
  vuToken = vuToken || login()
  const token = vuToken
  const roll = Math.random()
  let res
  if (roll < 0.4) {
    res = call(token, 'GET', `/books?q=${encodeURIComponent(pick(WORDS))}&size=20`)
  } else if (roll < 0.6) {
    res = call(token, 'GET', `/books/${pick(data.bookIds)}`)
  } else if (roll < 0.75) {
    res = call(token, 'GET', `/users?q=${encodeURIComponent(pick(NAMES))}&size=15`)
  } else if (roll < 0.85) {
    res = call(token, 'GET', '/dashboard')
  } else if (roll < 0.95) {
    res = call(token, 'GET', '/loans?size=20')
  } else {
    res = call(token, 'GET', '/books?size=20&page=3')
  }
  check(res, { 'respondeu 200': (r) => r.status === 200 })
  sleep(THINK * (0.5 + Math.random()))
}

