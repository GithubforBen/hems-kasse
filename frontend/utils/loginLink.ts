/**
 * The QR code on a Passwort-Zettel encodes a login link that pre-fills the form.
 *
 * The payload rides in the URL **fragment** rather than the query string: fragments are never
 * sent to the server, so the password stays out of access logs, proxy logs and the Referer
 * header. The login page strips it from the address bar as soon as it has been read.
 *
 * Encoding and decoding live together here so the two sides cannot drift apart.
 */

export interface LoginPrefill {
  /** Role to preselect. */
  r: 'VERKAUF' | 'ADMIN'
  /** Gruppe (VERKAUF) or username (ADMIN). */
  n: string
  /** Password. */
  p: string
}

const PREFIX = '#login='

function toBase64Url(bytes: Uint8Array): string {
  let binary = ''
  for (const b of bytes) binary += String.fromCharCode(b)
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function fromBase64Url(value: string): Uint8Array {
  const padded = value.replace(/-/g, '+').replace(/_/g, '/')
    + '='.repeat((4 - (value.length % 4)) % 4)
  const binary = atob(padded)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return bytes
}

/** Absolute login URL whose fragment pre-fills role, name and password. */
export function buildLoginUrl(origin: string, prefill: LoginPrefill): string {
  const json = JSON.stringify(prefill)
  const encoded = toBase64Url(new TextEncoder().encode(json))
  return `${origin.replace(/\/$/, '')}/login${PREFIX}${encoded}`
}

/**
 * Reads a prefill out of a location hash. Returns null for anything unexpected — a truncated
 * scan or a hand-edited URL must never throw on the login page.
 */
export function readLoginPrefill(hash: string): LoginPrefill | null {
  if (!hash.startsWith(PREFIX)) return null
  try {
    const raw = JSON.parse(new TextDecoder().decode(fromBase64Url(hash.slice(PREFIX.length))))
    if (!raw || typeof raw !== 'object') return null
    const role = raw.r === 'ADMIN' ? 'ADMIN' : raw.r === 'VERKAUF' ? 'VERKAUF' : null
    if (!role || typeof raw.n !== 'string' || typeof raw.p !== 'string') return null
    return { r: role, n: raw.n, p: raw.p }
  } catch {
    return null
  }
}
