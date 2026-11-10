/** Quantos ISBNs entram num lote (as APIs públicas têm limite de uso e a tela fica longa demais). */
export const MAX_BATCH = 50

const LOOKS_LIKE_ISBN = /^(\d{9}[\dXx]|\d{13})$/

/**
 * Extrai os ISBNs de um texto colado (uma linha por ISBN, ou separados por vírgula, ponto e vírgula ou tabulação, como
 * numa coluna de CSV). Tira hífen e espaço, ignora o que não parece ISBN (cabeçalho, título) e repetidos. O dígito
 * verificador é conferido pelo servidor.
 */
export function parseIsbns(text: string): { isbns: string[]; ignored: number; truncated: boolean } {
  const seen = new Set<string>()
  let ignored = 0
  for (const token of text.split(/[\n\r,;\t]+/)) {
    const cleaned = token.replace(/[\s-]/g, '')
    if (cleaned === '') continue
    if (!LOOKS_LIKE_ISBN.test(cleaned)) {
      ignored++
      continue
    }
    seen.add(cleaned.toUpperCase())
  }
  const all = [...seen]
  return { isbns: all.slice(0, MAX_BATCH), ignored, truncated: all.length > MAX_BATCH }
}
