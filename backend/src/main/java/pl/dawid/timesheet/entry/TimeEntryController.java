package pl.dawid.timesheet.entry;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/entries")
class TimeEntryController {

    private final TimeEntryService service;

    TimeEntryController(TimeEntryService service) {
        this.service = service;
    }

    @GetMapping
    List<EntryView> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(from, to).stream().map(EntryView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EntryView create(@Valid @RequestBody EntryRequest request) {
        return EntryView.of(service.create(request.projectId(), request.date(), request.slot()));
    }

    record EntryRequest(
            @NotNull Long projectId,
            @NotNull LocalDate date,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime start,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime end) {

        TimeSlot slot() {
            return new TimeSlot(start, end);
        }
    }

    record EntryView(
            Long id,
            Long projectId,
            LocalDate date,
            @JsonFormat(pattern = "HH:mm") LocalTime start,
            @JsonFormat(pattern = "HH:mm") LocalTime end) {

        static EntryView of(TimeEntry entry) {
            TimeSlot slot = entry.slot();
            return new EntryView(entry.getId(), entry.getProjectId(), entry.getWorkDate(), slot.start(), slot.end());
        }
    }
}
