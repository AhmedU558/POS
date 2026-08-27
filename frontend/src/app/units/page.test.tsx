import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { expect, test, describe, vi, beforeEach, afterEach } from 'vitest'
import UnitsPage from './page'
import * as AuthContext from '@/features/auth/AuthContext'
import { getUnits, createUnit, updateUnit } from '@/lib/api/catalog'

vi.mock('@/lib/api/catalog', () => ({
  getUnits: vi.fn(),
  createUnit: vi.fn(),
  updateUnit: vi.fn(),
  ApiError: class ApiError extends Error {}
}))

describe('Units Administration Screen', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    cleanup()
  })

  const renderWithAuth = (permissions: string[]) => {
    vi.spyOn(AuthContext, 'useAuth').mockReturnValue({
      user: { id: '1', username: 'test', email: 'test@test.com', firstName: 'T', lastName: 'T', permissions, storeIds: [] },
      token: 'valid',
      isAuthenticated: true,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn()
    } as any)

    return render(<UnitsPage />)
  }

  test('fetches and displays units', async () => {
    vi.mocked(getUnits).mockResolvedValue([
      { id: 'u-1', name: 'Kilogram', abbreviation: 'kg', allowFractions: true, active: true, createdAt: '', updatedAt: '' }
    ])
    
    renderWithAuth(['PRODUCT_READ'])
    
    await waitFor(() => {
      expect(screen.queryByText('Loading units...')).toBeNull()
    })
    
    expect(screen.getByText('Kilogram')).toBeDefined()
    expect(screen.getByText('kg')).toBeDefined()
  })

  test('hides Create/Edit buttons for users without PRODUCT_WRITE permission', async () => {
    vi.mocked(getUnits).mockResolvedValue([
      { id: 'u-1', name: 'Kilogram', abbreviation: 'kg', allowFractions: true, active: true, createdAt: '', updatedAt: '' }
    ])
    
    renderWithAuth(['PRODUCT_READ'])
    
    await waitFor(() => {
      expect(screen.queryByText('Loading units...')).toBeNull()
    })
    
    expect(screen.queryByText('Create Unit')).toBeNull()
    expect(screen.queryByText('Edit')).toBeNull()
  })
})
