/**
 * Authenticated file download.
 *
 * The browser's native download path (`<a href download>`) can't attach an
 * Authorization header, so we fetch the body as a blob with our existing
 * auth-injecting $fetch wrapper, then synthesise a click on a temporary <a>.
 *
 * The server sets a Content-Disposition with a suggested filename. We honour
 * it when present, otherwise fall back to the last URL segment.
 */
export const useDownload = () => {
  const { public: { apiBase } } = useRuntimeConfig()
  const auth = useAuthStore()

  return async (path: string, query: Record<string, any> = {}) => {
    // Use native fetch so we can read response headers (Content-Disposition).
    const url = new URL(path, apiBase)
    for (const [k, v] of Object.entries(query)) {
      if (v === undefined || v === null || v === '') continue
      url.searchParams.set(k, String(v))
    }

    const res = await fetch(url, {
      headers: auth.token ? { Authorization: `Bearer ${auth.token}` } : {},
    })
    if (!res.ok) {
      throw createError({ statusCode: res.status, statusMessage: await res.text() })
    }
    const blob = await res.blob()
    const filename = filenameFrom(res.headers.get('content-disposition')) ?? fallbackName(path)
    triggerDownload(blob, filename)
  }
}

function filenameFrom(header: string | null): string | null {
  if (!header) return null
  // matches:  attachment; filename="foo.csv"   or   attachment; filename=foo.csv
  const m = /filename\*?=(?:UTF-8'')?\"?([^\";]+)\"?/i.exec(header)
  return m ? decodeURIComponent(m[1]!) : null
}

function fallbackName(p: string) {
  const seg = p.split('?')[0]!.split('/').pop() ?? 'download.csv'
  return seg || 'download.csv'
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
