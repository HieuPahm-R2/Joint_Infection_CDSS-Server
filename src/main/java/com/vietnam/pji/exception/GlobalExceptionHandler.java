package com.vietnam.pji.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import static org.springframework.http.HttpStatus.*;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.ConstraintViolationException;

import java.util.Date;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(StorageUnavailableException.class)
    @ResponseStatus(SERVICE_UNAVAILABLE)
    public ErrorResponse handleStorageUnavailableException(
            StorageUnavailableException e,
            WebRequest request) {
        log.warn("Storage unavailable on {}: {}", request.getDescription(false), e.getMessage());
        return errorResponse(SERVICE_UNAVAILABLE.value(), SERVICE_UNAVAILABLE.getReasonPhrase(),
                e.getMessage(), request);
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, PayloadTooLargeException.class})
    @ResponseStatus(PAYLOAD_TOO_LARGE)
    public ErrorResponse handleMaxUploadSizeExceededException(
            Exception e,
            WebRequest request) {
        String message = e instanceof PayloadTooLargeException
                ? e.getMessage()
                : "Tệp tải lên vượt quá dung lượng cho phép.";
        return errorResponse(PAYLOAD_TOO_LARGE.value(), PAYLOAD_TOO_LARGE.getReasonPhrase(),
                message, request);
    }

    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(BAD_REQUEST)
    public ErrorResponse handleMultipartException(MultipartException e, WebRequest request) {
        log.warn("Invalid multipart request on {}: {}", request.getDescription(false), e.getMessage());
        return errorResponse(BAD_REQUEST.value(), "Invalid Multipart Request",
                "Yêu cầu tải tệp không hợp lệ.", request);
    }

    /**
     * Handle exception when validate data
     * 
     * @param e
     * @param request
     * @return errorResponse
     */
    @ExceptionHandler({ ConstraintViolationException.class,
            BusinessException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentNotValidException.class })
    @ResponseStatus(BAD_REQUEST)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "400", description = "Bad Request", content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(name = "Handle exception when the data invalid. (@RequestBody, @RequestParam, @PathVariable)", summary = "Handle Bad Request", value = """
                            {
                                 "timestamp": "2024-04-07T11:38:56.368+00:00",
                                 "status": 400,
                                 "path": "/api/v1/...",
                                 "error": "Invalid Payload",
                                 "message": "{data} must be not blank"
                             }
                            """)) })
    })
    public ErrorResponse handleValidationException(Exception e, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setTimestamp(new Date());
        errorResponse.setStatus(BAD_REQUEST.value());
        errorResponse.setPath(request.getDescription(false).replace("uri=", ""));

        String message = e.getMessage();
        if (e instanceof MethodArgumentNotValidException) {
            int start = message.lastIndexOf("[") + 1;
            int end = message.lastIndexOf("]") - 1;
            message = message.substring(start, end);
            errorResponse.setError("Invalid Payload");
            errorResponse.setMessage(message);
        } else if (e instanceof MissingServletRequestParameterException) {
            errorResponse.setError("Invalid Parameter");
            errorResponse.setMessage(message);
        } else if (e instanceof ConstraintViolationException) {
            errorResponse.setError("Invalid Parameter");
            errorResponse.setMessage(message.substring(message.indexOf(" ") + 1));
        } else {
            errorResponse.setError("Invalid Data");
            errorResponse.setMessage(message);
        }

        return errorResponse;
    }

    /**
     * Handle exception when the request not found data
     *
     * @param e
     * @param request
     * @return
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(NOT_FOUND)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "404", description = "Bad Request", content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(name = "404 Response", summary = "Handle exception when resource not found", value = """
                            {
                              "timestamp": "2023-10-19T06:07:35.321+00:00",
                              "status": 404,
                              "path": "/api/v1/...",
                              "error": "Not Found",
                              "message": "{data} not found"
                            }
                            """)) })
    })
    public ErrorResponse handleResourceNotFoundException(ResourceNotFoundException e, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setTimestamp(new Date());
        errorResponse.setPath(request.getDescription(false).replace("uri=", ""));
        errorResponse.setStatus(NOT_FOUND.value());
        errorResponse.setError(NOT_FOUND.getReasonPhrase());
        errorResponse.setMessage(e.getMessage());

        return errorResponse;
    }

    /**
     * Handle exception when the data is conflicted
     *
     * @param e
     * @param request
     * @return
     */
    @ExceptionHandler(InvalidDataException.class)
    @ResponseStatus(CONFLICT)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "409", description = "Conflict", content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(name = "409 Response", summary = "Handle exception when input data is conflicted", value = """
                            {
                              "timestamp": "2023-10-19T06:07:35.321+00:00",
                              "status": 409,
                              "path": "/api/v1/...",
                              "error": "Conflict",
                              "message": "{data} exists, Please try again!"
                            }
                            """)) })
    })
    public ErrorResponse handleDuplicateKeyException(InvalidDataException e, WebRequest request) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setTimestamp(new Date());
        errorResponse.setPath(request.getDescription(false).replace("uri=", ""));
        errorResponse.setStatus(CONFLICT.value());
        errorResponse.setError(CONFLICT.getReasonPhrase());
        errorResponse.setMessage(e.getMessage());

        return errorResponse;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(CONFLICT)
    public ErrorResponse handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException e,
            WebRequest request) {
        return errorResponse(
                CONFLICT.value(),
                CONFLICT.getReasonPhrase(),
                "Dữ liệu đã được thay đổi ở nơi khác. Vui lòng tải lại trước khi lưu.",
                request);
    }

    /**
     * Handle database integrity violations (e.g. value too long for a column,
     * unique / foreign-key / not-null constraint failures). Returns a clean
     * client-facing message instead of leaking the raw SQL / JDBC driver text,
     * while the underlying cause is logged server-side for debugging.
     *
     * @param e
     * @param request
     * @return errorResponse
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(BAD_REQUEST)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "400", description = "Bad Request", content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(name = "Handle exception when persisting data violates a database constraint", summary = "Handle Data Integrity Violation", value = """
                            {
                                 "timestamp": "2024-04-07T11:38:56.368+00:00",
                                 "status": 400,
                                 "path": "/api/v1/...",
                                 "error": "Invalid Data",
                                 "message": "Dữ liệu không hợp lệ hoặc vượt quá độ dài cho phép. Vui lòng kiểm tra lại thông tin."
                             }
                            """)) })
    })
    public ErrorResponse handleDataIntegrityViolationException(DataIntegrityViolationException e, WebRequest request) {
        log.error("Data integrity violation on {}: {}", request.getDescription(false), e.getMessage());

        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setTimestamp(new Date());
        errorResponse.setPath(request.getDescription(false).replace("uri=", ""));
        errorResponse.setStatus(BAD_REQUEST.value());
        errorResponse.setError("Invalid Data");
        errorResponse.setMessage("Dữ liệu không hợp lệ hoặc vượt quá độ dài cho phép. Vui lòng kiểm tra lại thông tin.");

        return errorResponse;
    }

    /**
     * Handle exception when internal server error
     *
     * @param e
     * @param request
     * @return error
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(INTERNAL_SERVER_ERROR)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(name = "500 Response", summary = "Handle exception when internal server error", value = """
                            {
                              "timestamp": "2023-10-19T06:35:52.333+00:00",
                              "status": 500,
                              "path": "/api/v1/...",
                              "error": "Internal Server Error",
                              "message": "Connection timeout, please try again"
                            }
                            """)) })
    })
    public ErrorResponse handleException(Exception e, WebRequest req) {
        log.error("Unhandled exception on {}", req.getDescription(false), e);
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setTimestamp(new Date());
        errorResponse.setPath(req.getDescription(false).replace("uri=", ""));
        errorResponse.setStatus(INTERNAL_SERVER_ERROR.value());
        errorResponse.setError(INTERNAL_SERVER_ERROR.getReasonPhrase());
        errorResponse.setMessage(e.getMessage());

        return errorResponse;
    }

    private ErrorResponse errorResponse(int status, String error, String message, WebRequest request) {
        ErrorResponse response = new ErrorResponse();
        response.setTimestamp(new Date());
        response.setPath(request.getDescription(false).replace("uri=", ""));
        response.setStatus(status);
        response.setError(error);
        response.setMessage(message);
        return response;
    }

    @ExceptionHandler(value = {
            ForbiddenException.class
    })
    public ErrorResponse handleForbiddenException(Exception e, WebRequest req) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setTimestamp(new Date());
        errorResponse.setPath(req.getDescription(false).replace("uri=", ""));
        errorResponse.setStatus(FORBIDDEN.value());
        errorResponse.setError(FORBIDDEN.getReasonPhrase());
        errorResponse.setMessage(e.getMessage());

        return errorResponse;
    }

    /**
     * Handle pessimistic-lock conflicts (e.g. another doctor is editing the
     * same episode). Returns HTTP 423 LOCKED with holder + TTL so the client
     * can surface a clear "resource busy" UX.
     */
    @ExceptionHandler(ResourceBusyException.class)
    @ResponseStatus(LOCKED)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "423", description = "Locked", content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(name = "423 Response", summary = "Handle exception when a resource is locked by another user", value = """
                            {
                              "timestamp": "2026-05-28T06:07:35.321+00:00",
                              "status": 423,
                              "path": "/api/v1/episodes/{id}/lock",
                              "error": "Locked",
                              "message": "Episode 42 is being edited by user 7",
                              "heldBy": 7,
                              "ttlSeconds": 142
                            }
                            """)) })
    })
    public LockedErrorResponse handleResourceBusyException(ResourceBusyException e, WebRequest req) {
        LockedErrorResponse errorResponse = new LockedErrorResponse();
        errorResponse.setTimestamp(new Date());
        errorResponse.setPath(req.getDescription(false).replace("uri=", ""));
        errorResponse.setStatus(LOCKED.value());
        errorResponse.setError(LOCKED.getReasonPhrase());
        errorResponse.setMessage(e.getMessage());
        errorResponse.setHeldBy(e.getHeldBy());
        errorResponse.setTtlSeconds(e.getTtlSeconds());
        return errorResponse;
    }

    /**
     * Handle authentication failure (wrong email or password, user not found, bad credentials, etc.)
     *
     * @param e
     * @param request
     * @return errorResponse
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(UNAUTHORIZED)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, examples = @ExampleObject(name = "401 Response", summary = "Handle authentication failure", value = """
                            {
                              "timestamp": "2024-04-07T11:38:56.368+00:00",
                              "status": 401,
                              "path": "/api/v1/auth/login",
                              "error": "Unauthorized",
                              "message": "Email hoặc mật khẩu không chính xác."
                            }
                            """)) })
    })
    public ErrorResponse handleAuthenticationException(AuthenticationException e, WebRequest request) {
        log.warn("Authentication failed on {}: {}", request.getDescription(false), e.getMessage());
        return errorResponse(UNAUTHORIZED.value(), UNAUTHORIZED.getReasonPhrase(),
                "Email hoặc mật khẩu không chính xác.", request);
    }
}
