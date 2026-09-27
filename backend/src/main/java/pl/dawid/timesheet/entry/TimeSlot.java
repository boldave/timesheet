package pl.dawid.timesheet.entry;

import java.time.LocalTime;

/** Godziny pracy w obrębie jednego dnia: od {@code start} (włącznie) do {@code end} (wyłącznie). */
public record TimeSlot(LocalTime start, LocalTime end) {

    public TimeSlot {
        if (start == null || end == null) {
            throw new InvalidTimeSlotException("Podaj godzinę rozpoczęcia i zakończenia.");
        }
        if (!end.isAfter(start)) {
            throw new InvalidTimeSlotException("Godzina zakończenia musi być późniejsza niż rozpoczęcia.");
        }
    }

    /** Sloty stykające się (jeden kończy się o 13:00, drugi o 13:00 zaczyna) nie nachodzą na siebie. */
    public boolean overlaps(TimeSlot other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
