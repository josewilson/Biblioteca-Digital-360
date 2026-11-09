// Tipos espelham os DTOs do backend (docs/API.md). Dinheiro vem como número em reais.

export type Role =
  | 'SUPER_ADMIN'
  | 'ADMIN_INSTITUTION'
  | 'LIBRARIAN'
  | 'ASSISTANT'
  | 'TEACHER'
  | 'STUDENT'
  | 'EMPLOYEE'
  | 'VISITOR'

export const ALL_ROLES: Role[] = [
  'SUPER_ADMIN',
  'ADMIN_INSTITUTION',
  'LIBRARIAN',
  'ASSISTANT',
  'TEACHER',
  'STUDENT',
  'EMPLOYEE',
  'VISITOR',
]

export const ROLE_LABELS: Record<Role, string> = {
  SUPER_ADMIN: 'Super administrador',
  ADMIN_INSTITUTION: 'Administrador',
  LIBRARIAN: 'Bibliotecário',
  ASSISTANT: 'Auxiliar',
  TEACHER: 'Professor',
  STUDENT: 'Aluno',
  EMPLOYEE: 'Funcionário',
  VISITOR: 'Visitante',
}

/** Equipe da biblioteca (opera o balcão e os cadastros). */
export const STAFF_ROLES: Role[] = ['SUPER_ADMIN', 'ADMIN_INSTITUTION', 'LIBRARIAN', 'ASSISTANT']
/** Quem pode isentar multa. */
export const WAIVER_ROLES: Role[] = ['SUPER_ADMIN', 'ADMIN_INSTITUTION', 'LIBRARIAN']
/** Quem pode desativar usuários. */
export const ADMIN_ROLES: Role[] = ['SUPER_ADMIN', 'ADMIN_INSTITUTION']

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface NameItem {
  id: number
  name: string
}

export type CopyKind = 'CONSULTA' | 'CIRCULANTE'
export type CopyStatus = 'DISPONIVEL' | 'EMPRESTADO' | 'RESERVADO' | 'PERDIDO' | 'BAIXADO'
export type CopyCondition = 'NOVO' | 'BOM' | 'REGULAR' | 'RUIM'

export interface Copy {
  id: number
  barcode: string
  kind: CopyKind
  status: CopyStatus
  condition: CopyCondition
  location: string | null
}

export interface Book {
  id: number
  title: string
  isbn: string | null
  edition: string | null
  publicationYear: number | null
  language: string | null
  description: string | null
  coverUrl: string | null
  publisherId: number | null
  publisher: string | null
  authors: NameItem[]
  categories: NameItem[]
  copies: Copy[]
}

export interface UserRow {
  id: number
  fullName: string
  email: string
  registration: string | null
  phone: string | null
  active: boolean
  roles: Role[]
  createdAt: string
}

export interface Policy {
  role: Role
  maxLoans: number
  loanDays: number
  maxRenewals: number
  renewalDays: number
  reservationPickupDays: number
  dailyRate: number
}

export interface Loan {
  id: number
  userId: number
  copyId: number
  barcode: string
  bookTitle: string
  loanedAt: string
  dueAt: string
  returnedAt: string | null
}

export type FineStatus = 'ABERTA' | 'PAGA' | 'ISENTA'

export interface Fine {
  id: number
  loanId: number
  userId: number
  amount: number
  daysLate: number
  status: FineStatus
  waiverReason: string | null
}

export interface ReturnResult {
  loan: Loan
  fine: Fine | null
}

export type ReservationStatus = 'NA_FILA' | 'DISPONIVEL' | 'RETIRADA' | 'CANCELADA' | 'EXPIRADA'

export interface Reservation {
  id: number
  bookId: number
  bookTitle: string
  userId: number
  status: ReservationStatus
  queuePosition: number | null
  copyId: number | null
  queuedAt: string
  expiresAt: string | null
}

export type PaymentMethod = 'DINHEIRO' | 'PIX' | 'CARTAO_DEBITO' | 'CARTAO_CREDITO'

export interface Payment {
  id: number
  fineId: number
  loanId: number
  userId: number
  amount: number
  method: PaymentMethod
  receivedBy: number
  paidAt: string
}

export interface RankItem {
  name: string
  total: number
}

export interface DailyMovement {
  day: string
  loans: number
  returns: number
}

export interface Dashboard {
  totalBooks: number
  totalCopies: number
  activeUsers: number
  activeLoans: number
  overdueLoans: number
  pendingReservations: number
  openFinesCount: number
  openFinesAmount: number
  topBooks: RankItem[]
  topCategories: RankItem[]
  movements: DailyMovement[]
}

export interface ReportTable {
  report: string
  columns: string[]
  rows: unknown[][]
  truncated: boolean
}
