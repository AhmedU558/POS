import { render } from '@testing-library/react'
import Page from './page'
import { expect, test } from 'vitest'

test('App Router: Page renders without crashing', () => {
  const { container } = render(<Page />)
  expect(container).toBeTruthy()
})
