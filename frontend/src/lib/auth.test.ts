import { decodeToken, hasAnyRole } from './auth'
import { fakeJwt } from '../test/utils'

describe('decodeToken', () => {
  it('lê id e perfis do token', () => {
    const user = decodeToken(fakeJwt(['LIBRARIAN', 'TEACHER'], { sub: '42' }))

    expect(user?.id).toBe(42)
    expect(user?.roles).toEqual(['LIBRARIAN', 'TEACHER'])
  })

  it('recusa token vencido', () => {
    expect(decodeToken(fakeJwt(['STUDENT'], { expiresInSec: -10 }))).toBeNull()
  })

  it('recusa lixo, token incompleto e payload sem validade', () => {
    expect(decodeToken('isto-nao-e-jwt')).toBeNull()
    expect(decodeToken('a.b.c')).toBeNull()
    const semExp = `x.${btoa(JSON.stringify({ sub: '1', roles: [] }))}.y`
    expect(decodeToken(semExp)).toBeNull()
  })
})

describe('hasAnyRole', () => {
  it('confere pelo menos um perfil', () => {
    const user = decodeToken(fakeJwt(['ASSISTANT']))
    expect(hasAnyRole(user, ['LIBRARIAN', 'ASSISTANT'])).toBe(true)
    expect(hasAnyRole(user, ['SUPER_ADMIN'])).toBe(false)
    expect(hasAnyRole(null, ['SUPER_ADMIN'])).toBe(false)
  })
})
