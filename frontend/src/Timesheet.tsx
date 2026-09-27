import { useCallback, useMemo, useRef, useState } from 'react'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import plLocale from '@fullcalendar/core/locales/pl'
import type { DateSelectArg, EventClickArg, EventInput, EventSourceFuncArg } from '@fullcalendar/core'
import { api, type Entry, type EntryInput, type Project } from './api'
import { EntryDialog } from './EntryDialog'
import { toHHmm, toIsoDate, withinOneDay } from './time'

type Editing = { entryId?: number; initial: EntryInput }

export function Timesheet({ projects }: { projects: Project[] }) {
  const calendarRef = useRef<FullCalendar>(null)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<Editing | null>(null)

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

  function onSelect(sel: DateSelectArg) {
    const date = toIsoDate(sel.start)
    const start = sel.allDay ? '09:00' : toHHmm(sel.start)
    // Zaznaczenie do północy kończy się o 00:00 następnego dnia; wpis musi zmieścić się w jednym dniu.
    const end = sel.allDay ? '17:00' : toIsoDate(sel.end) === date ? toHHmm(sel.end) : '23:59'
    setEditing({ initial: { projectId: projects[0].id, date, start, end } })
  }

  function onEventClick(click: EventClickArg) {
    const entry = click.event.extendedProps.entry as Entry
    setEditing({
      entryId: entry.id,
      initial: { projectId: entry.projectId, date: entry.date, start: entry.start, end: entry.end },
    })
  }

  function close() {
    setEditing(null)
    calendarRef.current?.getApi().unselect()
  }

  function afterChange() {
    close()
    calendarRef.current?.getApi().refetchEvents()
  }

  const entryId = editing?.entryId

  return (
    <>
      {error && <p className="error">{error}</p>}
      <FullCalendar
        ref={calendarRef}
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
        selectable
        selectMirror
        selectAllow={(span) => withinOneDay(span.start, span.end)}
        select={onSelect}
        eventClick={onEventClick}
        events={loadEvents}
      />
      {editing && (
        <EntryDialog
          projects={projects}
          initial={editing.initial}
          onClose={close}
          onSave={async (input) => {
            if (entryId === undefined) await api.createEntry(input)
            else await api.updateEntry(entryId, input)
            afterChange()
          }}
          onDelete={
            entryId === undefined
              ? undefined
              : async () => {
                  await api.deleteEntry(entryId)
                  afterChange()
                }
          }
        />
      )}
    </>
  )
}
