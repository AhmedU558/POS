import { render, screen } from '@testing-library/react'
import { expect, test, describe } from 'vitest'
import Page from './page'

describe('App shell', () => {
  test('renders a single top-level heading naming the system', () => {
    render(<Page />)

    const headings = screen.getAllByRole('heading', { level: 1 })
    expect(headings).toHaveLength(1)
    expect(headings[0].textContent).toBe('POS Management System')
  })

  test('wraps content in a main landmark for assistive technology', () => {
    // UI/UX Specification section 29 requires screens to be usable with assistive technology.
    const { container } = render(<Page />)

    expect(container.querySelector('main')).not.toBeNull()
  })
})
