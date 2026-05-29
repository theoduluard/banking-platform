import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import TransferPage from '@/pages/TransferPage'
import { setToken } from '@/lib/auth'
import api from '@/lib/api'

// ── Mock @/lib/api so no real HTTP calls are made ────────────────────────────

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    default: {
      ...actual.default,
      get:  vi.fn(),
      post: vi.fn(),
    },
  }
})

// ── Helpers ───────────────────────────────────────────────────────────────────

function buildJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const body   = btoa(JSON.stringify(payload))
  return `${header}.${body}.sig`
}

const CLIENT_TOKEN = buildJwt({ sub: 'user-1', role: 'CLIENT' })

const MOCK_ACCOUNTS = [
  {
    id: 'acc-aaa-111',
    userId: 'user-1',
    iban: 'FR7630006000011234567890189',
    balance: 1000,
    currency: 'EUR',
    type: 'CHECKING',
    status: 'ACTIVE',
    createdAt: '2024-01-01T00:00:00',
  },
  {
    id: 'acc-bbb-222',
    userId: 'user-1',
    iban: 'FR7630006000019876543210189',
    balance: 500,
    currency: 'EUR',
    type: 'SAVINGS',
    status: 'ACTIVE',
    createdAt: '2024-01-01T00:00:00',
  },
]

function makeQueryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

function renderTransferPage() {
  return render(
    <QueryClientProvider client={makeQueryClient()}>
      <MemoryRouter>
        <TransferPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  localStorage.clear()
  setToken(CLIENT_TOKEN)
  vi.mocked(api.get).mockResolvedValue({ data: MOCK_ACCOUNTS })
})

afterEach(() => {
  localStorage.clear()
  vi.restoreAllMocks()
})

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('TransferPage', () => {
  it('renders the transfer form', () => {
    renderTransferPage()
    expect(screen.getByText('Effectuer un virement')).toBeInTheDocument()
    expect(screen.getByLabelText(/montant/i)).toBeInTheDocument()
    expect(screen.getByText(/vérifier et confirmer/i)).toBeInTheDocument()
  })

  it('disables the submit button when no destination account is selected', () => {
    renderTransferPage()
    // Button is disabled until a valid destination is resolved — this is the
    // primary guard that prevents submitting an incomplete transfer.
    const submitBtn = screen.getByText(/vérifier et confirmer/i).closest('button')
    expect(submitBtn).toBeDisabled()
  })

  it('does not open the modal when the form is invalid', async () => {
    const user = userEvent.setup()
    renderTransferPage()

    await user.click(screen.getByText(/vérifier et confirmer/i))

    // Confirmation modal title must NOT appear
    expect(screen.queryByText(/confirmer le virement/i)).not.toBeInTheDocument()
  })

  it('renders the description textarea and accepts input', async () => {
    renderTransferPage()

    const textarea = screen.getByPlaceholderText(/remboursement/i)
    expect(textarea).toBeInTheDocument()
    expect(textarea).toHaveAttribute('id', 'description')

    // Can type into the field
    await userEvent.setup().type(textarea, 'Remboursement dîner')
    expect(textarea).toHaveValue('Remboursement dîner')
  })
})
