import { render, screen } from '@testing-library/react'
import { expect, test, describe, vi } from 'vitest'
import Page from './page'
import { AuthProvider } from '@/features/auth/AuthContext'

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  usePathname: () => '/'
}))

describe('App shell', () => {
  test('renders a single top-level heading naming the system', () => {
    render(<AuthProvider><Page /></AuthProvider>)

    const headings = screen.getAllByRole('heading', { level: 1 })
    expect(headings).toHaveLength(1)
    expect(headings[0].textContent).toBe('POS Management System')
  })

  test('wraps content in a main landmark for assistive technology', () => {
    const { container } = render(<AuthProvider><Page /></AuthProvider>)

    expect(container.querySelector('main')).not.toBeNull()
  })
})