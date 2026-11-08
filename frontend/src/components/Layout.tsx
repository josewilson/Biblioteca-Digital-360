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

      <header className="flex items-center justify-between bg-brand-900 px-4 py-3 text-white md:hidden">
        <span className="font-semibold">Biblioteca Digital 360</span>
        <button
          type="button"
          aria-label={menuOpen ? 'Fechar menu' : 'Abrir menu'}
          aria-expanded={menuOpen}
          aria-controls="menu-principal"
          onClick={() => setMenuOpen((o) => !o)}
          className="rounded p-1 hover:bg-brand-700"
        >
          {menuOpen ? <X aria-hidden="true" /> : <Menu aria-hidden="true" />}
        </button>
      </header>

      <aside
        id="menu-principal"
        className={`${menuOpen ? 'block' : 'hidden'} bg-brand-900 text-white md:sticky md:top-0 md:block md:h-screen md:w-64 md:shrink-0`}
      >
        <div className="hidden px-5 py-5 text-lg font-bold md:block">Biblioteca Digital 360</div>
        <nav aria-label="Principal" className="space-y-1 px-3 py-2">
          {items.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              onClick={() => setMenuOpen(false)}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-md px-3 py-2 text-sm ${isActive ? 'bg-brand-700 font-semibold' : 'hover:bg-brand-700/60'}`
              }
            >
              <Icon className="h-4 w-4" aria-hidden="true" />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-white/20 px-5 py-4 text-sm">
          <p className="text-white/80">{roles}</p>
          <Button variant="secondary" className="mt-3 w-full" onClick={logout}>
            <LogOut className="h-4 w-4" aria-hidden="true" />
            Sair
          </Button>
        </div>
      </aside>

      <main id="conteudo" className="min-w-0 flex-1 p-4 md:p-8">
        <Outlet />
      </main>
    </div>
  )
}
