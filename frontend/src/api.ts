export type Project = { id: number; name: string; color: string }

/** Wpis czasu pracy; `date` jako YYYY-MM-DD, `start`/`end` jako HH:mm. */
export type Entry = { id: number; projectId: number; date: string; start: string; end: string }

export type EntryInput = Omit<Entry, 'id'>

const CONNECTION_ERROR = 'Nie można połączyć się z serwerem.'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, { ...init, headers: { 'Content-Type': 'application/json' } })
  } catch {
    throw new Error(CONNECTION_ERROR)
  }
  if (!response.ok) {
    throw new Error(await errorMessage(response))
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

async function errorMessage(response: Response): Promise<string> {
  try {
    const body: unknown = await response.json()
    if (body && typeof body === 'object' && 'message' in body && typeof body.message === 'string' && body.message) {
      return body.message
    }
  } catch {
    // brak JSON-a w odpowiedzi, np. gdy serwer deweloperski Vite nie może dosięgnąć backendu
  }
  return response.status >= 500 ? CONNECTION_ERROR : `Błąd (${response.status}).`
}

export const api = {
  projects: () => request<Project[]>('/api/projects'),
  entries: (from: string, to: string) =>
    request<Entry[]>(`/api/entries?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
  createEntry: (input: EntryInput) =>
    request<Entry>('/api/entries', { method: 'POST', body: JSON.stringify(input) }),
  updateEntry: (id: number, input: EntryInput) =>
    request<Entry>(`/api/entries/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
  deleteEntry: (id: number) => request<void>(`/api/entries/${id}`, { method: 'DELETE' }),
}
