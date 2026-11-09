import { useState } from 'react'
import { BookOpen } from 'lucide-react'
import type { Book } from '../lib/types'

type CoverSource = Pick<Book, 'id' | 'title' | 'coverVersion' | 'coverUrl'>

/** Capa enviada (miniatura ou ampliada); sem ela, o endereço externo que a obra tenha; sem nada, null. */
export function coverSrc(book: CoverSource, size: 'thumb' | 'full'): string | null {
  return book.coverVersion != null ? `/api/v1/books/${book.id}/cover?size=${size}&v=${book.coverVersion}` : book.coverUrl
}

const BOX = { thumb: 'h-16 w-11', full: 'h-48 w-32' } as const

/**
 * Capa da obra, ou uma capa padrão quando não há imagem (ou ela não carrega).
 * `decorative`: na lista o título já está ao lado, então a imagem não precisa ser anunciada de novo.
 */
export function BookCover({ book, size, decorative = false }: { book: CoverSource; size: 'thumb' | 'full'; decorative?: boolean }) {
  const [failed, setFailed] = useState(false)
  const src = coverSrc(book, size)
  if (!src || failed) {
    return (
      <div aria-hidden="true" className={`${BOX[size]} flex shrink-0 items-center justify-center rounded border border-slate-200 bg-slate-100 text-slate-400`}>
        <BookOpen className={size === 'thumb' ? 'h-5 w-5' : 'h-10 w-10'} />
      </div>
    )
  }
  return (
    <img
      src={src}
      alt={decorative ? '' : `Capa de ${book.title}`}
      loading="lazy"
      referrerPolicy="no-referrer"
      onError={() => setFailed(true)}
      className={`${BOX[size]} shrink-0 rounded border border-slate-200 object-cover`}
    />
  )
}
