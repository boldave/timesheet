package pl.dawid.timesheet.entry;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.dawid.timesheet.project.ProjectRepository;

@Service
public class TimeEntryService {

    private final TimeEntryRepository entries;
    private final ProjectRepository projects;

    public TimeEntryService(TimeEntryRepository entries, ProjectRepository projects) {
        this.entries = entries;
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public List<TimeEntry> list(LocalDate from, LocalDate to) {
        if (!from.isBefore(to)) {
            throw new InvalidTimeSlotException("Data „od” musi być wcześniejsza niż data „do”.");
        }
        return entries.findByWorkDateGreaterThanEqualAndWorkDateLessThanOrderByWorkDateAscStartTimeAsc(from, to);
    }

    @Transactional
    public TimeEntry create(Long projectId, LocalDate date, TimeSlot slot) {
        requireProject(projectId);
        requireNoOverlap(date, slot, null);
        return entries.save(new TimeEntry(projectId, date, slot));
    }

    private void requireProject(Long projectId) {
        if (!projects.existsById(projectId)) {
            throw new NotFoundException("Nie ma takiego projektu.");
        }
    }

    private void requireNoOverlap(LocalDate date, TimeSlot slot, Long ignoredEntryId) {
        boolean overlaps = entries.findByWorkDate(date).stream()
                .filter(other -> !other.getId().equals(ignoredEntryId))
                .anyMatch(other -> other.slot().overlaps(slot));
        if (overlaps) {
            throw new OverlapException("Te godziny nachodzą na inny wpis tego dnia.");
        }
    }
}
