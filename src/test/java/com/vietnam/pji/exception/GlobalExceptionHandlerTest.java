package com.vietnam.pji.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ServletWebRequest request = new ServletWebRequest(accountRequest());

    @Test
    void storageFailureReturnsStableServiceUnavailablePayload() {
        ErrorResponse response = handler.handleStorageUnavailableException(
                new StorageUnavailableException("Storage temporarily unavailable", new RuntimeException("secret")),
                request);

        assertEquals(503, response.getStatus());
        assertEquals("Service Unavailable", response.getError());
        assertEquals("Storage temporarily unavailable", response.getMessage());
        assertEquals("/api/v1/auth/account", response.getPath());
    }

    @Test
    void oversizedMultipartReturnsPayloadTooLarge() {
        ErrorResponse response = handler.handleMaxUploadSizeExceededException(
                new MaxUploadSizeExceededException(5L * 1024L * 1024L), request);

        assertEquals(413, response.getStatus());
        assertEquals("Tệp tải lên vượt quá dung lượng cho phép.", response.getMessage());
    }

    @Test
    void avatarLimitReturnsSpecificPayloadTooLargeMessage() {
        ErrorResponse response = handler.handleMaxUploadSizeExceededException(
                new PayloadTooLargeException("Ảnh đại diện không được vượt quá 5 MB."), request);

        assertEquals(413, response.getStatus());
        assertEquals("Ảnh đại diện không được vượt quá 5 MB.", response.getMessage());
    }

    @Test
    void malformedMultipartReturnsBadRequest() {
        ErrorResponse response = handler.handleMultipartException(
                new MultipartException("broken boundary"), request);

        assertEquals(400, response.getStatus());
        assertEquals("Yêu cầu tải tệp không hợp lệ.", response.getMessage());
    }

    @Test
    void authenticationExceptionReturnsUnauthorizedWithFriendlyMessage() {
        ErrorResponse response = handler.handleAuthenticationException(
                new org.springframework.security.authentication.BadCredentialsException("Bad credentials"),
                request);

        assertEquals(401, response.getStatus());
        assertEquals("Unauthorized", response.getError());
        assertEquals("Email hoặc mật khẩu không chính xác.", response.getMessage());
        assertEquals("/api/v1/auth/account", response.getPath());
    }

    @Test
    void forbiddenExceptionIsStatedAsForbiddenAndAnnotatedSoTheHttpStatusMatches() throws Exception {
        ErrorResponse response = handler.handleForbiddenException(
                new ForbiddenException("You don't have permission to access"), request);

        assertEquals(403, response.getStatus());
        assertEquals("Forbidden", response.getError());

        // Without @ResponseStatus the advice returns HTTP 200 with a 403 body, which the
        // frontend cannot branch on. Pin the annotation, not just the payload.
        ResponseStatus status = GlobalExceptionHandler.class
                .getMethod("handleForbiddenException", Exception.class, WebRequest.class)
                .getAnnotation(ResponseStatus.class);
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, status.value());
    }

    @Test
    void uploadSessionFailuresKeepTheir410And401Statuses() {
        // A class-level @ResponseStatus is outranked by the advice's catch-all
        // @ExceptionHandler(Exception.class), so these need explicit handlers.
        ResponseEntity<ErrorResponse> gone = handler.handleUploadSessionFailure(
                new UploadSessionGoneException("Upload session expired"), request);
        assertEquals(410, gone.getStatusCode().value());
        assertEquals(410, gone.getBody().getStatus());
        assertEquals("Upload session expired", gone.getBody().getMessage());

        ResponseEntity<ErrorResponse> unauthorized = handler.handleUploadSessionFailure(
                new UploadSessionUnauthorizedException("Invalid upload token"), request);
        assertEquals(401, unauthorized.getStatusCode().value());
        assertEquals(401, unauthorized.getBody().getStatus());
    }

    @Test
    void unmappedThrowableDoesNotLeakItsInternalMessage() {
        ErrorResponse response = handler.handleException(
                new IllegalStateException("PreparedStatementCallback; SQL [select 1]; password=hunter2"), request);

        assertEquals(500, response.getStatus());
        assertEquals("Internal Server Error", response.getError());
        assertEquals("Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau hoặc liên hệ quản trị viên.", response.getMessage());
    }

    @Test
    void missingUploadSessionExceptionHandlersWouldDowngradeTo500() throws Exception {
        // Guard the wiring itself: registering the handler is what preserves the status.
        ExceptionHandler annotation = GlobalExceptionHandler.class
                .getMethod("handleUploadSessionFailure", Exception.class, WebRequest.class)
                .getAnnotation(ExceptionHandler.class);
        assertEquals(2, annotation.value().length);
    }

    private static MockHttpServletRequest accountRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/account");
        return request;
    }
}
