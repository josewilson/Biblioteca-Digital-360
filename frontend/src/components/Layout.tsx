import { useState } from 'react'
import { NavLink, Navigate, Outlet, useLocation } from 'react-router'
import {
  BookOpen,
  ChartColumn,
  Handshake,
  LayoutDashboard,
  LogOut,
  Menu,
  Receipt,
  Tags,
  UserRound,
  Users,
  X,
  Bookmark,
  type LucideIcon,
} from 'lucide-react'
import { useAuth } from '../lib/auth'
import { ROLE_LABELS, type Role } from '../lib/types'
import { Button } from './ui'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  staffOnly?: boolean
}

const NAV: NavItem[] = [
  { to: '/painel', label: 'Painel', icon: LayoutDashboard, staffOnly: true },
  { to: '/acervo', label: 'Acervo', icon: BookOpen },
  { to: '/balcao', label: 'Balcão', icon: Handshake, staffOnly: true },
  { to: '/reservas', label: 'Reservas', icon: Bookmark, staffOnly: true },
  { to: '/multas', label: 'Multas', icon: Receipt, staffOnly: true },
  { to: '/relatorios', label: 'Relatórios', icon: ChartColumn, staffOnly: true },
  { to: '/usuarios', label: 'Usuários', icon: Users, staffOnly: true },
  { to: '/cadastros', label: 'Autores e categorias', icon: Tags, staffOnly: true },
  { to: '/minha-area', label: 'Minha área', icon: UserRound },
]

/** Só deixa passar quem está logado; senão manda para o login e volta depois. */
export function RequireAuth() {
  const { user } = useAuth()
  const location = useLocation()
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}

/** Marca: a lombada de um livro ao lado do nome. */
function Wordmark() {
  return (
    <div className="flex items-center gap-3">
      <span aria-hidden="true" className="h-8 w-1.5 rounded-sm bg-brand-600" />
      <div className="leading-tight">
        <div className="font-serif text-xl font-semibold text-slate-900">Biblioteca</div>
        <div className="font-serif text-sm text-slate-600">Digital 360</div>
      </div>
    </div>
  )
}

export function Layout() {
  const { user, isStaff, logout } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  const items = NAV.filter((i) => !i.staffOnly || isStaff)
  const roles = (user?.roles ?? []).map((r: Role) => ROLE_LABELS[r] ?? r).join(', ')

  return (
    <div className="min-h-screen md:flex">
      <a
        href="#conteudo"
        className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-white focus:px-3 focus:py-2"
      >
        Pular para o conteúdo
      </a>

      <header className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3 md:hidden">
        <Wordmark />
        <button
          type="button"
          aria-label={menuOpen ? 'Fechar menu' : 'Abrir menu'}
          aria-expanded={menuOpen}
          aria-controls="menu-principal"
          onClick={() => setMenuOpen((o) => !o)}
          className="rounded p-2 text-slate-800 hover:bg-slate-100"
        >
          {menuOpen ? <X aria-hidden="true" /> : <Menu aria-hidden="true" />}
        </button>
      </header>

      <aside
        id="menu-principal"
        className={`${menuOpen ? 'block' : 'hidden'} border-b border-slate-200 bg-slate-100 md:sticky md:top-0 md:block md:h-screen md:w-64 md:shrink-0 md:overflow-y-auto md:border-b-0 md:border-r`}
      >
        <div className="hidden px-5 py-6 md:block">
          <Wordmark />
        </div>
        <nav aria-label="Principal" className="space-y-0.5 px-3 py-2">
          {items.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              onClick={() => setMenuOpen(false)}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded border-l-4 px-3 py-2 ${
                  isActive
                    ? 'border-brand-600 bg-white font-bold text-brand-700'
                    : 'border-transparent text-slate-800 hover:bg-slate-200'
                }`
              }
            >
              <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="mt-2 border-t border-slate-200 px-5 py-4">
          <p className="text-sm text-slate-600">{roles}</p>
          <Button variant="secondary" className="mt-3 w-full" onClick={logout}>
            <LogOut className="h-4 w-4" aria-hidden="true" />
            Sair
          </Button>
        </div>
      </aside>

      <main id="conteudo" className="min-w-0 flex-1 p-4 md:p-10">
        <div className="mx-auto max-w-6xl">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
