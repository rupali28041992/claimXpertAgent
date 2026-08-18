package com.nextgen.claims.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class FileValidationServiceTest {

    private FileValidationService service;

    @BeforeEach
    void setUp() {
        service = new FileValidationService();
        ReflectionTestUtils.setField(service, "maxFileSize", 1024L); // 1 KB, easy to exceed in tests
    }

    @Test
    void nullFileIsRejected() {
        var result = service.validate(null);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("FILE_NULL");
    }

    @Test
    void emptyFileIsRejected() {
        var file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        var result = service.validate(file);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("FILE_EMPTY");
    }

    @Test
    void unsupportedContentTypeIsRejected() {
        var file = new MockMultipartFile("file", "doc.txt", "text/plain", "hello".getBytes());
        var result = service.validate(file);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void oversizedFileIsRejected() {
        byte[] bytes = new byte[2048]; // exceeds the 1 KB test limit
        bytes[0] = 1;
        var file = new MockMultipartFile("file", "big.pdf", "application/pdf", bytes);
        var result = service.validate(file);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("FILE_TOO_LARGE");
    }

    @Test
    void validPdfPasses() {
        var file = new MockMultipartFile("file", "bill.pdf", "application/pdf", new byte[]{1, 2, 3});
        var result = service.validate(file);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void validJpegAndPngPass() {
        assertThat(service.validate(
                new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1})).valid()).isTrue();
        assertThat(service.validate(
                new MockMultipartFile("file", "a.png", "image/png", new byte[]{1})).valid()).isTrue();
    }
}
