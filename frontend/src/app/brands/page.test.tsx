import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { expect, test, describe, vi, beforeEach, afterEach } from 'vitest'
import BrandsPage from './page'
import * as AuthContext from '@/features/auth/AuthContext'
import { getBrands, createBrand, updateBrand } from '@/lib/api/catalog'

vi.mock('@/lib/api/catalog', () => ({
  getBrands: vi.fn(),
  createBrand: vi.fn(),
  updateBrand: vi.fn(),
  ApiError: class ApiError extends Error {}
}))

describe('Brands Administration Screen', () => {
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

    return render(<BrandsPage />)
  }

  test('fetches and displays brands', async () => {
    vi.mocked(getBrands).mockResolvedValue([
      { id: 'b-1', name: 'Nike', description: null, active: true, createdAt: '', updatedAt: '' }
    ])
    
    renderWithAuth(['PRODUCT_READ'])
    
    await waitFor(() => {
      expect(screen.queryByText('Loading brands...')).toBeNull()
    })
    
    expect(screen.getByText('Nike')).toBeDefined()
  })

  test('hides Create/Edit buttons for users without PRODUCT_WRITE permission', async () => {
    vi.mocked(getBrands).mockResolvedValue([
      { id: 'b-1', name: 'Nike', description: null, active: true, createdAt: '', updatedAt: '' }
    ])
    
    renderWithAuth(['PRODUCT_READ'])
    
    await waitFor(() => {
      expect(screen.queryByText('Loading brands...')).toBeNull()
    })
    
    expect(screen.queryByText('Create Brand')).toBeNull()
    expect(screen.queryByText('Edit')).toBeNull()
  })
})
