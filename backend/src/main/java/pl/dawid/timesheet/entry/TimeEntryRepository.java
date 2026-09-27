package pl.dawid.timesheet.entry;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

    List<TimeEntry> findByWorkDate(LocalDate workDate);

    /** Wpisy z dni w przedziale [from, to). */
    List<TimeEntry> findByWorkDateGreaterThanEqualAndWorkDateLessThanOrderByWorkDateAscStartTimeAsc(
            LocalDate from, LocalDate to);
}
