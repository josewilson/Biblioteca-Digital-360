import { useMutation, useQuery } from '@tanstack/react-query'
import { Download } from 'lucide-react'
import { Link } from 'react-router'
import { api, downloadFile } from '../lib/api'
import { useAuth } from '../lib/auth'
import { Button, Card, ErrorAlert } from '../components/ui'

/** Pública (aberta também antes do login). Descreve o que o sistema faz hoje; a instituição deve revisar o texto. */
export function PrivacyPage() {
  const { user } = useAuth()
  const info = useQuery({ queryKey: ['privacy'], queryFn: () => api<{ contact: string }>('/privacy') })
  const download = useMutation({ mutationFn: () => downloadFile('/me/data', {}, 'meus-dados.json') })

  return (
    <main id="conteudo" className="mx-auto max-w-3xl space-y-6 p-4 sm:p-8">
      <header>
        <h1 className="text-3xl font-semibold tracking-tight">Privacidade e seus dados</h1>
        <p className="mt-1 text-slate-600">
          Como a Biblioteca Digital 360 trata os dados pessoais de quem usa a biblioteca (Lei Geral de Proteção de Dados).
        </p>
      </header>

      <Card title="O que guardamos">
        <ul className="list-disc space-y-1 pl-5">
          <li>Nome, e-mail e perfil (aluno, professor, funcionário, visitante ou equipe).</li>
          <li>Matrícula, telefone, endereço e CPF, só quando a biblioteca pediu (são opcionais). O CPF não aparece nas telas.</li>
          <li>Seus empréstimos, reservas e multas, e os avisos enviados a você.</li>
          <li>Registro dos seus acessos: tipo de ação, data e endereço de rede (IP).</li>
          <li>A senha fica protegida por hash: nem a equipe consegue lê-la.</li>
        </ul>
      </Card>

      <Card title="Para que usamos">
        <p>
          Controlar empréstimos, reservas e multas, avisar prazos e proteger o sistema contra acesso indevido. Não usamos seus
          dados para publicidade nem os repassamos a terceiros.
        </p>
      </Card>

      <Card title="Quem vê">
        <p>
          A equipe da biblioteca vê o seu cadastro e o seu histórico para atender você. Cada leitor vê apenas a própria área.
        </p>
      </Card>

      <Card title="Por quanto tempo">
        <ul className="list-disc space-y-1 pl-5">
          <li>Cadastro e histórico: enquanto a sua conta existir.</li>
          <li>Tentativas de login que falharam: apagadas após 90 dias.</li>
          <li>
            Ao pedir a eliminação, a administração anonimiza a conta: nome, e-mail, CPF, telefone, endereço, avisos e IPs são
            apagados. Empréstimos e multas continuam no histórico da biblioteca, sem identificar você.
          </li>
        </ul>
      </Card>

      <Card title="Seus direitos">
        <ul className="list-disc space-y-1 pl-5">
          <li>
            <strong>Ver seus dados:</strong> use o botão abaixo para baixar tudo o que o sistema guarda sobre você.
          </li>
          <li>
            <strong>Corrigir:</strong> peça à equipe da biblioteca, que edita o cadastro.
          </li>
          <li>
            <strong>Eliminar:</strong> peça à administração da biblioteca. Só é possível sem empréstimo, multa ou reserva em
            aberto.
          </li>
        </ul>
        {user && !user.mustChangePassword ? (
          <div className="mt-4">
            <ErrorAlert error={download.error} />
            <Button loading={download.isPending} onClick={() => download.mutate()}>
              <Download className="h-4 w-4" aria-hidden="true" />
              Baixar meus dados
            </Button>
          </div>
        ) : (
          <p className="mt-4 text-sm text-slate-600">Entre no sistema para baixar os seus dados.</p>
        )}
      </Card>

      <Card title="Contato">
        <p>Dúvidas, pedidos ou reclamações: procure a administração da biblioteca da sua instituição.</p>
        {info.data?.contact && <p className="mt-2 font-medium">{info.data.contact}</p>}
      </Card>

      <p>
        <Link to={user ? '/' : '/login'} className="text-sm font-medium text-brand-700 underline">
          {user ? 'Voltar ao sistema' : 'Voltar ao login'}
        </Link>
      </p>
    </main>
  )
}
