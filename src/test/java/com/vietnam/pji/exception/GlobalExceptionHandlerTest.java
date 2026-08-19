package com.vietnam.pji.exception;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
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

    private static MockHttpServletRequest accountRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/account");
        return request;
    }
}
