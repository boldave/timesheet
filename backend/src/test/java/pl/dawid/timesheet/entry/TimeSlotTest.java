package pl.dawid.timesheet.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class TimeSlotTest {

    private static TimeSlot slot(String start, String end) {
        return new TimeSlot(LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    void rejectsEndBeforeStart() {
        assertThatThrownBy(() -> slot("13:00", "09:00"))
                .isInstanceOf(InvalidTimeSlotException.class)
                .hasMessageContaining("późniejsza");
    }

    @Test
    void rejectsEmptySlot() {
        assertThatThrownBy(() -> slot("09:00", "09:00"))
                .isInstanceOf(InvalidTimeSlotException.class);
    }

    @Test
    void rejectsMissingTime() {
        assertThatThrownBy(() -> new TimeSlot(null, LocalTime.NOON))
                .isInstanceOf(InvalidTimeSlotException.class);
    }

    @Test
    void overlappingSlotsOverlap() {
        assertThat(slot("09:00", "13:00").overlaps(slot("12:00", "14:00"))).isTrue();
        assertThat(slot("12:00", "14:00").overlaps(slot("09:00", "13:00"))).isTrue();
    }

    @Test
    void slotInsideAnotherOverlaps() {
        assertThat(slot("09:00", "17:00").overlaps(slot("10:00", "11:00"))).isTrue();
        assertThat(slot("10:00", "11:00").overlaps(slot("09:00", "17:00"))).isTrue();
    }

    @Test
    void touchingSlotsDoNotOverlap() {
        assertThat(slot("09:00", "13:00").overlaps(slot("13:00", "14:00"))).isFalse();
        assertThat(slot("13:00", "14:00").overlaps(slot("09:00", "13:00"))).isFalse();
    }

    @Test
    void separateSlotsDoNotOverlap() {
        assertThat(slot("09:00", "10:00").overlaps(slot("15:00", "16:00"))).isFalse();
    }
}
