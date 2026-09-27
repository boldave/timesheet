import { useCallback, useMemo, useState } from 'react'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import plLocale from '@fullcalendar/core/locales/pl'
import type { EventInput, EventSourceFuncArg } from '@fullcalendar/core'
import { api, type Project } from './api'
import { toIsoDate } from './time'

export function Timesheet({ projects }: { projects: Project[] }) {
  const [error, setError] = useState<string | null>(null)

  const projectById = useMemo(() => new Map(projects.map((p) => [p.id, p])), [projects])

  // Stabilna referencja: nowa funkcja przy każdym renderze kazałaby FullCalendar pobierać wpisy od nowa.
  const loadEvents = useCallback(
    (info: EventSourceFuncArg, success: (events: EventInput[]) => void, failure: (error: Error) => void) => {
      api.entries(toIsoDate(info.start), toIsoDate(info.end)).then(
        (entries) => {
          setError(null)
          success(
            entries.map((entry) => {
              const project = projectById.get(entry.projectId)
              return {
                id: String(entry.id),
                title: project?.name ?? `Projekt ${entry.projectId}`,
                start: `${entry.date}T${entry.start}`,
                end: `${entry.date}T${entry.end}`,
                backgroundColor: project?.color,
                borderColor: project?.color,
                extendedProps: { entry },
              }
            }),
          )
        },
        (e: Error) => {
          setError(e.message)
          failure(e)
        },
      )
    },
    [projectById],
  )

  return (
    <>
      {error && <p className="error">{error}</p>}
      <FullCalendar
        plugins={[timeGridPlugin, dayGridPlugin, interactionPlugin]}
        locale={plLocale}
        initialView="timeGridWeek"
        headerToolbar={{ left: 'prev,next today', center: 'title', right: 'timeGridWeek,dayGridMonth' }}
        firstDay={1}
        allDaySlot={false}
        slotDuration="00:15:00"
        slotLabelInterval="01:00"
        slotLabelFormat={{ hour: '2-digit', minute: '2-digit', hour12: false }}
        eventTimeFormat={{ hour: '2-digit', minute: '2-digit', hour12: false }}
        scrollTime="08:00:00"
        height="auto"
        events={loadEvents}
      />
    </>
  )
}
