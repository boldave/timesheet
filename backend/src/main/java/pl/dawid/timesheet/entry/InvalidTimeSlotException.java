package pl.dawid.timesheet.entry;

public class InvalidTimeSlotException extends RuntimeException {

    public InvalidTimeSlotException(String message) {
        super(message);
    }
}
