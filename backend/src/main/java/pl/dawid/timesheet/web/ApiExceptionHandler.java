package pl.dawid.timesheet.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import pl.dawid.timesheet.entry.InvalidTimeSlotException;
import pl.dawid.timesheet.entry.NotFoundException;
import pl.dawid.timesheet.entry.OverlapException;

@RestControllerAdvice
class ApiExceptionHandler {

    record ErrorBody(String message) {
    }

    @ExceptionHandler(InvalidTimeSlotException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorBody invalidSlot(InvalidTimeSlotException e) {
        return new ErrorBody(e.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorBody malformed(Exception e) {
        return new ErrorBody("Niepoprawne dane. Sprawdź projekt, datę i godziny (format HH:mm).");
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorBody notFound(NotFoundException e) {
        return new ErrorBody(e.getMessage());
    }

    @ExceptionHandler(OverlapException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorBody overlap(OverlapException e) {
        return new ErrorBody(e.getMessage());
    }
}
