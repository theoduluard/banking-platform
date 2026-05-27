// ── Auth ─────────────────────────────────────────────────────────────────────

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
}

export interface AuthResponse {
  accessToken: string
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
  createdAt: string
}

export interface TransferRequest {
  fromAccountId: string
  toAccountId: string
  amount: number
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
