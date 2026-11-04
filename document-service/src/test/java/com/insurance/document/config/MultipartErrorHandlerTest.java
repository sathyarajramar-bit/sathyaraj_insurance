package com.insurance.document.config;

import com.insurance.common.dto.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

/** MockMvc never enforces the multipart limit, so the mapping is checked directly. */
class MultipartErrorHandlerTest {

    @Test
    void mapsTheMultipartLimitToA413InThePlatformShape() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/documents/extract");

        ResponseEntity<ApiErrorResponse> response = new MultipartErrorHandler()
                .handleTooLarge(new MaxUploadSizeExceededException(10L * 1024 * 1024), request);

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(413);
        assertThat(response.getBody().error()).isEqualTo("PAYLOAD_TOO_LARGE");
        assertThat(response.getBody().path()).isEqualTo("/api/documents/extract");
        assertThat(response.getBody().message()).contains("maximum allowed size");
    }
}
