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
  /** Refresh token is set as an HttpOnly cookie — absent from the JSON body. */
  refreshToken?: string
  email: string
  firstname: string
  lastname: string
  role: string
}

/** Returned by POST /login — the actual JWT is issued after OTP verification. */
export interface OtpChallengeResponse {
  status: 'OTP_REQUIRED'
  sessionToken: string
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

// ── Scheduled Transfers ───────────────────────────────────────────────────────

export type TransferFrequency = 'WEEKLY' | 'MONTHLY'

export interface ScheduledTransfer {
  id: string
  fromAccountId: string
  toAccountId: string
  amount: number
  currency: string
  description?: string | null
  frequency: TransferFrequency
  nextExecutionDate: string   // ISO date: "2025-07-01"
  active: boolean
  createdAt: string
}

export interface ScheduledTransferRequest {
  fromAccountId: string
  toAccountId: string
  amount: number
  description?: string
  frequency: TransferFrequency
  firstExecutionDate: string  // ISO date
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

// ── Loans ─────────────────────────────────────────────────────────────────────

export type LoanStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'DISBURSED' | 'CLOSED'

export interface AdminLoan {
  id: string
  userId: string
  accountId: string
  amount: number
  interestRate: number
  durationMonths: number
  monthlyPayment: number
  totalRepayment: number
  status: LoanStatus
  purpose: string | null
  adminNote: string | null
  disbursedAt: string | null
  createdAt: string
  updatedAt: string
}
