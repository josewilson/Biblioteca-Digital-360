import { render, screen } from '@testing-library/react'
import { DueStamp } from './DueStamp'

// Datas sem "Z" são locais: o teste não depende do fuso da máquina.
const at = (local: string) => new Date(local).toISOString()
const NOW = new Date('2026-03-10T14:00:00')

describe('DueStamp (carimbo de prazo)', () => {
  it('prazo distante: texto discreto, sem carimbo', () => {
    render(<DueStamp dueAt={at('2026-03-20T09:00:00')} now={NOW} />)

    expect(screen.getByText(/Devolver até/)).toBeInTheDocument()
    expect(screen.queryByText(/Atrasado|Vence/)).not.toBeInTheDocument()
  })

  it('vence hoje e vence amanhã ganham carimbo âmbar', () => {
    const { unmount } = render(<DueStamp dueAt={at('2026-03-10T18:00:00')} now={NOW} />)
    expect(screen.getByText('Vence hoje')).toHaveClass('text-due')
    unmount()

    render(<DueStamp dueAt={at('2026-03-11T09:00:00')} now={NOW} />)
    expect(screen.getByText('Vence amanhã')).toBeInTheDocument()
  })

  it('atrasado ganha carimbo vermelho com os dias de atraso e a data que venceu', () => {
    render(<DueStamp dueAt={at('2026-03-07T09:00:00')} now={NOW} />)

    const stamp = screen.getByText('Atrasado há 3 dias')
    expect(stamp).toHaveClass('text-stamp', 'border-stamp')
    expect(screen.getByText(/venceu/)).toBeInTheDocument()
  })

  it('um dia de atraso fica no singular; atraso de horas no mesmo dia não inventa dias', () => {
    const { unmount } = render(<DueStamp dueAt={at('2026-03-09T09:00:00')} now={NOW} />)
    expect(screen.getByText('Atrasado há 1 dia')).toBeInTheDocument()
    unmount()

    render(<DueStamp dueAt={at('2026-03-10T09:00:00')} now={NOW} />)   // venceu hoje às 9h; agora são 14h
    expect(screen.getByText('Atrasado')).toBeInTheDocument()
  })

  it('devolvido mostra quando, sem carimbo, mesmo que tenha sido devolvido atrasado', () => {
    render(<DueStamp dueAt={at('2026-03-01T09:00:00')} returnedAt={at('2026-03-05T09:00:00')} now={NOW} />)

    expect(screen.getByText(/Devolvido em/)).toBeInTheDocument()
    expect(screen.queryByText(/Atrasado/)).not.toBeInTheDocument()
  })

  it('o estado é sempre dito em texto, nunca só por cor', () => {
    render(<DueStamp dueAt={at('2026-03-07T09:00:00')} now={NOW} />)
    expect(screen.getByText(/Atrasado/).textContent).toMatch(/\S/)
  })
})
