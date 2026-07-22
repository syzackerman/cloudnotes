package com.cloudnotes.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorResponseFactory errorResponseFactory;

    public GlobalExceptionHandler(ErrorResponseFactory errorResponseFactory) {
        this.errorResponseFactory = errorResponseFactory;
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ErrorResponse> handleDuplicateEmail(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_EMAIL, "Email is already registered", request);
    }

    @ExceptionHandler(DuplicateTagNameException.class)
    ResponseEntity<ErrorResponse> handleDuplicateTagName(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_TAG, "Tag name already exists", request);
    }

    @ExceptionHandler(AttachmentNotFoundException.class)
    ResponseEntity<ErrorResponse> handleAttachmentNotFound(HttpServletRequest request) {
        return notFound("Attachment not found", request);
    }

    @ExceptionHandler(EmptyFileException.class)
    ResponseEntity<ErrorResponse> handleEmptyFile(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ErrorCode.EMPTY_FILE, "Uploaded file is empty", request);
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    ResponseEntity<ErrorResponse> handleUnsupportedFileType(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ErrorCode.UNSUPPORTED_FILE_TYPE, "Unsupported file type", request);
    }

    @ExceptionHandler(InvalidFileNameException.class)
    ResponseEntity<ErrorResponse> handleInvalidFileName(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Invalid filename", request);
    }

    @ExceptionHandler(FileTooLargeException.class)
    ResponseEntity<ErrorResponse> handleFileTooLarge(HttpServletRequest request) {
        return error(
                HttpStatus.PAYLOAD_TOO_LARGE,
                ErrorCode.FILE_TOO_LARGE,
                "Uploaded file exceeds the size limit",
                request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(HttpServletRequest request) {
        return handleFileTooLarge(request);
    }

    @ExceptionHandler(StorageException.class)
    ResponseEntity<ErrorResponse> handleStorageFailure(StorageException ex, HttpServletRequest request) {
        log.warn("File storage operation failed", ex);
        return error(HttpStatus.BAD_GATEWAY, ErrorCode.STORAGE_ERROR, "File storage operation failed", request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ErrorResponse> handleInvalidCredentials(HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Invalid email or password", request);
    }

    @ExceptionHandler(NoteNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNoteNotFound(HttpServletRequest request) {
        return notFound("Note not found", request);
    }

    @ExceptionHandler(TagNotFoundException.class)
    ResponseEntity<ErrorResponse> handleTagNotFound(HttpServletRequest request) {
        return notFound("Tag not found", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNoResourceFound(HttpServletRequest request) {
        return notFound("Resource not found", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(fieldError -> fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage()));
        return error(
                HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Request validation failed", request, fieldErrors);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpRequestMethodNotSupportedException.class,
        BadRequestException.class
    })
    ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, safeBadRequestMessage(ex), request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(
                        "Retry-After",
                        String.valueOf(Math.max(1, ex.retryAfter().toSeconds())))
                .body(errorResponseFactory.create(
                        HttpStatus.TOO_MANY_REQUESTS,
                        ErrorCode.RATE_LIMIT_EXCEEDED,
                        "Rate limit exceeded. Please retry later.",
                        request,
                        null));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> handleConflict(HttpServletRequest request) {
        return error(
                HttpStatus.CONFLICT,
                ErrorCode.CONFLICT,
                "The resource was modified by another request. Refresh and retry.",
                request);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorResponse> handleInvalidState(IllegalStateException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "Invalid request state", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled application exception", ex);
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> notFound(String message, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, message, request);
    }

    private ResponseEntity<ErrorResponse> error(
            HttpStatus status, ErrorCode code, String message, HttpServletRequest request) {
        return error(status, code, message, request, null);
    }

    private ResponseEntity<ErrorResponse> error(
            HttpStatus status,
            ErrorCode code,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors) {
        return ResponseEntity.status(status)
                .body(errorResponseFactory.create(status, code, message, request, fieldErrors));
    }

    private String safeBadRequestMessage(Exception ex) {
        if (ex instanceof BadRequestException
                && ex.getMessage() != null
                && !ex.getMessage().isBlank()) {
            return ex.getMessage();
        }
        return "Malformed or invalid request";
    }
}
