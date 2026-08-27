import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { expect, test, describe, vi, beforeEach, afterEach } from 'vitest'
import CategoriesPage from './page'
import * as AuthContext from '@/features/auth/AuthContext'
import { getCategories, createCategory, updateCategory } from '@/lib/api/catalog'

vi.mock('@/lib/api/catalog', () => ({
  getCategories: vi.fn(),
  createCategory: vi.fn(),
  updateCategory: vi.fn(),
  ApiError: class ApiError extends Error {}
}))

describe('Categories Administration Screen', () => {
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

    return render(<CategoriesPage />)
  }

  test('renders loading state initially', () => {
    vi.mocked(getCategories).mockReturnValueOnce(new Promise(() => {}))
    renderWithAuth(['PRODUCT_READ'])
    expect(screen.getByText('Loading categories...')).toBeDefined()
  })

  test('fetches and displays categories', async () => {
    vi.mocked(getCategories).mockResolvedValue([
      { id: 'cat-1', name: 'Drinks', description: null, parentId: null, active: true, createdAt: '', updatedAt: '' }
    ])
    
    renderWithAuth(['PRODUCT_READ'])
    
    await waitFor(() => {
      expect(screen.queryByText('Loading categories...')).toBeNull()
    })
    
    expect(screen.getByText('Drinks')).toBeDefined()
  })

  test('hides Create/Edit buttons for users without PRODUCT_WRITE permission', async () => {
    vi.mocked(getCategories).mockResolvedValue([
      { id: 'cat-1', name: 'Drinks', description: null, parentId: null, active: true, createdAt: '', updatedAt: '' }
    ])
    
    renderWithAuth(['PRODUCT_READ'])
    
    await waitFor(() => {
      expect(screen.queryByText('Loading categories...')).toBeNull()
    })
    
    expect(screen.queryByText('Create Category')).toBeNull()
    expect(screen.queryByText('Edit')).toBeNull()
  })

  test('shows Create/Edit buttons and allows form opening if user has PRODUCT_WRITE', async () => {
    vi.mocked(getCategories).mockResolvedValue([
      { id: 'cat-1', name: 'Drinks', description: null, parentId: null, active: true, createdAt: '', updatedAt: '' }
    ])
    
    renderWithAuth(['PRODUCT_READ', 'PRODUCT_WRITE'])
    
    await waitFor(() => {
      expect(screen.queryByText('Loading categories...')).toBeNull()
    })
    
    const createBtn = screen.getByText('Create Category')
    expect(createBtn).toBeDefined()
    expect(screen.getByText('Edit')).toBeDefined()

    fireEvent.click(createBtn)
    expect(screen.getByText('New Category')).toBeDefined()
  })

  test('surfaces API errors correctly during creation', async () => {
    vi.mocked(getCategories).mockResolvedValue([])
    
    renderWithAuth(['PRODUCT_READ', 'PRODUCT_WRITE'])
    
    await waitFor(() => {
      expect(screen.queryByText('Loading categories...')).toBeNull()
    })
    
    fireEvent.click(screen.getByText('Create Category'))
    
    const nameInput = screen.getByLabelText('Category Name')
    fireEvent.change(nameInput, { target: { value: 'BadCategory' } })
    
    vi.mocked(createCategory).mockRejectedValue(new Error('Hierarchy depth limit exceeded'))
    
    fireEvent.click(screen.getByText('Save'))
    
    await waitFor(() => {
      expect(screen.getByText('Hierarchy depth limit exceeded')).toBeDefined()
    })
  })
})
