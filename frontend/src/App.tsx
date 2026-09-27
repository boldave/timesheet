import { useEffect, useState } from 'react'
import { api, type Project } from './api'
import { Timesheet } from './Timesheet'

export default function App() {
  const [projects, setProjects] = useState<Project[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.projects().then(setProjects, (e: Error) => setError(e.message))
  }, [])

  return (
    <div className="page">
      <h1>Timesheet</h1>
      {error && <p className="error">{error}</p>}
      {!projects && !error && <p>Ładowanie…</p>}
      {projects && projects.length === 0 && <p className="error">Brak projektów w bazie.</p>}
      {projects && projects.length > 0 && <Timesheet projects={projects} />}
    </div>
  )
}
