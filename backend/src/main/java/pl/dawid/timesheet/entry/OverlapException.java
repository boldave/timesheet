package pl.dawid.timesheet.entry;

public class OverlapException extends RuntimeException {

    public OverlapException(String message) {
        super(message);
    }
}
