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
export type AccountStatus = 'PENDING_APPROVAL' | 'ACTIVE' | 'BLOCKED' | 'CLOSED' | 'REJECTED'

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

export type AccountStatusAdmin = 'PENDING_APPROVAL' | 'ACTIVE' | 'BLOCKED' | 'CLOSED' | 'REJECTED'

export interface AdminAccount {
  id: string
  userId: string
  iban: string
  type: AccountType
  balance: number
  currency: string
  status: AccountStatusAdmin
  createdAt: string
}

// ── Messaging ─────────────────────────────────────────────────────────────────

export type MessageType = 'INFO' | 'WARNING' | 'DOCUMENT' | 'APPROVAL' | 'REJECTION'

export interface Message {
  id: string
  userId: string
  subject: string
  body: string
  type: MessageType
  isRead: boolean
  attachmentBase64?: string | null
  attachmentContentType?: string | null
  attachmentFilename?: string | null
  createdAt: string
}

export type RequestType = 'ACCOUNT_CLOSURE' | 'DISPUTE' | 'DOCUMENT_REQUEST' | 'OTHER'
export type RequestStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'REJECTED'

export interface SupportRequest {
  id: string
  userId: string
  type: RequestType
  subject: string
  body: string
  status: RequestStatus
  hasAttachment: boolean
  createdAt: string
  updatedAt: string
}

export type ReplyAuthorType = 'CLIENT' | 'ADMIN'

export interface RequestReply {
  id: string
  authorType: ReplyAuthorType
  authorId: string
  body: string
  attachmentBase64?: string | null
  attachmentContentType?: string | null
  attachmentFilename?: string | null
  createdAt: string
}

export interface SupportRequestDetail extends SupportRequest {
  attachmentBase64?: string | null
  attachmentContentType?: string | null
  attachmentFilename?: string | null
  replies: RequestReply[]
}

export interface VerificationDocumentResponse {
  id: string
  accountId: string
  userId: string
  selfieBase64: string
  selfieContentType: string
  idCardBase64: string
  idCardContentType: string
  submittedAt: string
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
