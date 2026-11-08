import { render, screen } from '@testing-library/react'
import { Table } from './ui'

describe('Table', () => {
  it('o quadro rolável é alcançável por teclado e tem nome (WCAG 2.1.1)', () => {
    render(
      <Table caption="Empréstimos em andamento" head={['Obra']}>
        <tr>
          <td>Dom Casmurro</td>
        </tr>
      </Table>,
    )

    // No celular a tabela rola na horizontal; sem foco, quem usa só teclado não alcança as colunas escondidas.
    const region = screen.getByRole('region', { name: 'Empréstimos em andamento' })
    expect(region).toHaveAttribute('tabindex', '0')
    region.focus()
    expect(region).toHaveFocus()
    expect(screen.getByRole('table', { name: 'Empréstimos em andamento' })).toBeInTheDocument()
  })
})
