import { Suspense, lazy } from 'react'
import { Link, Navigate, RouterProvider, createBrowserRouter, type RouteObject } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ApiError } from './lib/api'
import { AuthProvider, useAuth } from './lib/auth'
import { STAFF_ROLES } from './lib/types'
import { Layout, RequireAuth } from './components/Layout'
import { RequireRole } from './components/RequireRole'
import { Loading } from './components/ui'
import { BookDetailPage } from './pages/BookDetailPage'
import { BooksPage } from './pages/BooksPage'
import { CatalogPage } from './pages/CatalogPage'
import { CounterPage } from './pages/CounterPage'
const DashboardPage = lazy(() => import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage })))
import { FinesPage } from './pages/FinesPage'
import { LoginPage } from './pages/LoginPage'
import { MyAreaPage } from './pages/MyAreaPage'
import { ReportsPage } from './pages/ReportsPage'
import { ReservationsPage } from './pages/ReservationsPage'
import { UsersPage } from './pages/UsersPage'

function HomeRedirect() {
  const { isStaff } = useAuth()
  return <Navigate to={isStaff ? '/painel' : '/minha-area'} replace />
}

function NotFound() {
  return (
    <div className="mx-auto mt-16 max-w-md text-center">
      <h1 className="text-xl font-bold">Página não encontrada</h1>
      <Link to="/" className="mt-4 inline-block text-sm font-medium text-brand-700 underline">
        Voltar ao início
      </Link>
    </div>
  )
}

const staff = (element: React.ReactNode) => <RequireRole roles={STAFF_ROLES}>{element}</RequireRole>

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <Layout />,
        children: [
          { index: true, element: <HomeRedirect /> },
          { path: 'painel', element: staff(<Suspense fallback={<Loading />}><DashboardPage /></Suspense>) },
          { path: 'acervo', element: <BooksPage /> },
          { path: 'acervo/:id', element: <BookDetailPage /> },
          { path: 'balcao', element: staff(<CounterPage />) },
          { path: 'reservas', element: staff(<ReservationsPage />) },
          { path: 'multas', element: staff(<FinesPage />) },
          { path: 'relatorios', element: staff(<ReportsPage />) },
          { path: 'usuarios', element: staff(<UsersPage />) },
          { path: 'cadastros', element: staff(<CatalogPage />) },
          { path: 'minha-area', element: <MyAreaPage /> },
          { path: '*', element: <NotFound /> },
        ],
      },
    ],
  },
]

export function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        refetchOnWindowFocus: false,
        // Erros 4xx (permissão, regra de negócio) não melhoram tentando de novo.
        retry: (count, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
      },
    },
  })
}

const queryClient = makeQueryClient()
const router = createBrowserRouter(routes)

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  )
}
