import { useState, type FormEvent } from 'react'
import type { EntryInput, Project } from './api'

type Props = {
  projects: Project[]
  initial: EntryInput
  /** Obecny przy edycji istniejącego wpisu; włącza przycisk „Usuń”. */
  onDelete?: () => Promise<void>
  onSave: (input: EntryInput) => Promise<void>
  onClose: () => void
}

export function EntryDialog({ projects, initial, onDelete, onSave, onClose }: Props) {
  const [projectId, setProjectId] = useState(initial.projectId)
  const [start, setStart] = useState(initial.start)
  const [end, setEnd] = useState(initial.end)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function run(action: () => Promise<void>) {
    setBusy(true)
    setError(null)
    try {
      await action()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
      setBusy(false)
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    void run(() => onSave({ projectId, date: initial.date, start, end }))
  }

  return (
    <div className="backdrop">
      <form className="dialog" onSubmit={submit}>
        <h2>{onDelete ? 'Edytuj wpis' : 'Nowy wpis'} — {initial.date}</h2>
        <label>
          Projekt
          <select value={projectId} onChange={(e) => setProjectId(Number(e.target.value))}>
            {projects.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        <div className="row">
          <label>
            Od
            <input type="time" required value={start} onChange={(e) => setStart(e.target.value)} />
          </label>
          <label>
            Do
            <input type="time" required value={end} onChange={(e) => setEnd(e.target.value)} />
          </label>
        </div>
        {error && <p className="error">{error}</p>}
        <div className="actions">
          {onDelete && (
            <button type="button" className="danger" disabled={busy} onClick={() => void run(onDelete)}>
              Usuń
            </button>
          )}
          <span className="spacer" />
          <button type="button" disabled={busy} onClick={onClose}>
            Anuluj
          </button>
          <button type="submit" className="primary" disabled={busy}>
            Zapisz
          </button>
        </div>
      </form>
    </div>
  )
}
