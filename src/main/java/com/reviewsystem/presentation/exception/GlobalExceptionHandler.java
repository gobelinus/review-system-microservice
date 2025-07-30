package com.reviewsystem.presentation.exception;

import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(NoHandlerFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String, Object> handle404(NoHandlerFoundException ex) {
    Map<String, Object> errorAttributes = new HashMap<>();
    errorAttributes.put("timestamp", LocalDateTime.now().toString());
    errorAttributes.put("status", HttpStatus.NOT_FOUND.value());
    errorAttributes.put("error", "Not Found");
    errorAttributes.put("message", "The requested resource was not found");
    errorAttributes.put("path", ex.getRequestURL());
    return errorAttributes;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, Object> handleValidationExceptions(MethodArgumentNotValidException ex) {
    List<String> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
            .collect(Collectors.toList());

    Map<String, Object> errorAttributes = new HashMap<>();
    errorAttributes.put("timestamp", LocalDateTime.now().toString());
    errorAttributes.put("status", HttpStatus.BAD_REQUEST.value());
    errorAttributes.put("error", "Validation Failed");
    errorAttributes.put("message", errors);
    return errorAttributes;
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
    Map<String, Object> errorAttributes = new HashMap<>();
    errorAttributes.put("timestamp", LocalDateTime.now().toString());
    errorAttributes.put("status", HttpStatus.BAD_REQUEST.value());
    errorAttributes.put("error", "Malformed JSON Request");
    errorAttributes.put("message", ex.getMostSpecificCause().getMessage());
    return errorAttributes;
  }

  @ExceptionHandler(ConstraintViolationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, Object> handleConstraintViolation(ConstraintViolationException ex) {
    List<String> errors =
        ex.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .collect(Collectors.toList());

    Map<String, Object> errorAttributes = new HashMap<>();
    errorAttributes.put("timestamp", LocalDateTime.now().toString());
    errorAttributes.put("status", HttpStatus.BAD_REQUEST.value());
    errorAttributes.put("error", "Constraint Validation Failed");
    errorAttributes.put("message", errors);
    return errorAttributes;
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleAllUncaughtException(Exception ex) {
    Map<String, Object> errorAttributes = new HashMap<>();
    errorAttributes.put("timestamp", LocalDateTime.now().toString());
    errorAttributes.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
    errorAttributes.put("error", "Internal Server Error");
    errorAttributes.put("message", ex.getMessage());
    return new ResponseEntity<>(errorAttributes, HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
