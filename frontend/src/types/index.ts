// ── Auth ─────────────────────────────────────────────────────────────────────

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  firstname: string
  lastname: string
  email: string
  password: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  email: string
  firstname: string
  lastname: string
  role: string
}

// ── Account ───────────────────────────────────────────────────────────────────

export type AccountType = 'CHECKING' | 'SAVINGS'
export type AccountStatus = 'ACTIVE' | 'CLOSED'

export interface Account {
  id: string
  userId: string
  iban: string
  balance: number
  currency: string
  type: AccountType
  status: AccountStatus
  createdAt: string
}

export interface CreateAccountRequest {
  type: AccountType
}

// ── Transaction ───────────────────────────────────────────────────────────────

export type TransactionType = 'TRANSFER' | 'DEPOSIT' | 'WITHDRAWAL'
export type TransactionStatus = 'PENDING' | 'COMPLETED' | 'FAILED'

export interface Transaction {
  id: string
  fromAccountId: string
  toAccountId: string
  amount: number
  currency: string
  type: TransactionType
  status: TransactionStatus
  description?: string | null
  createdAt: string
}

export interface TransferRequest {
  fromAccountId: string
  toAccountId: string
  amount: number
  description?: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

// ── Beneficiary ───────────────────────────────────────────────────────────────

export interface Beneficiary {
  id: string
  name: string
  iban: string
  createdAt: string
}

export interface BeneficiaryRequest {
  name: string
  iban: string
}

// ── Admin Operations ──────────────────────────────────────────────────────────

export interface AdminOperationRequest {
  accountId: string
  amount: number
  description?: string
}

// ── Admin ─────────────────────────────────────────────────────────────────────

export type UserRole = 'CLIENT' | 'ADMIN'

export interface AdminUser {
  userId: string
  email: string
  firstname: string
  lastname: string
  role: UserRole
  isActive: boolean
  createdAt: string
}

export type AccountStatusAdmin = 'ACTIVE' | 'BLOCKED' | 'CLOSED'

export interface AdminAccount {
  id: string
  iban: string
  type: AccountType
  balance: number
  currency: string
  status: AccountStatusAdmin
  createdAt: string
}

export interface AdminTransaction {
  id: string
  fromAccountId: string
  toAccountId: string
  amount: number
  currency: string
  type: TransactionType
  status: TransactionStatus
  description: string | null
  createdAt: string
  completedAt: string | null
}
