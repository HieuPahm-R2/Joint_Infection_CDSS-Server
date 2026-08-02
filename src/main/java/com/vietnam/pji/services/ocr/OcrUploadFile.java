package com.vietnam.pji.services.ocr;

public record OcrUploadFile(
        String filename,
        String contentType,
        byte[] content) {
}
